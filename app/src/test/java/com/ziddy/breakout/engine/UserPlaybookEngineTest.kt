package com.ziddy.breakout.engine

import com.ziddy.breakout.model.UserPlaybook
import com.ziddy.breakout.model.UserPlaybookStep
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPlaybookEngineTest {
    @Test
    fun validatesBranchTargetsAndStepBounds() {
        val invalidTarget = playbook(
            UserPlaybookStep("first", "First", "echo first", onSuccessStepId = "missing"),
        )
        val tooManySteps = playbook(
            *(1..(UserPlaybookEngine.MAX_STEPS + 1)).map { index ->
                UserPlaybookStep("step-$index", "Step $index", "echo $index")
            }.toTypedArray(),
        )

        assertNotNull(UserPlaybookEngine.validate(invalidTarget))
        assertNotNull(UserPlaybookEngine.validate(tooManySteps))
        assertNull(UserPlaybookEngine.validate(playbook(UserPlaybookStep("first", "First", "echo first"))))
    }

    @Test
    fun followsSuccessAndFailureRoutes() = runBlocking {
        val flow = playbook(
            UserPlaybookStep("start", "Start", "false", onSuccessStepId = "success", onFailureStepId = "failure"),
            UserPlaybookStep("success", "Success path", "echo success"),
            UserPlaybookStep("failure", "Failure path", "echo failure"),
        )

        val result = UserPlaybookEngine.run(
            playbook = flow,
            execute = { command ->
                if (command == "false") {
                    UserPlaybookEngine.CommandResult(exitCode = 1, stderr = "expected failure")
                } else {
                    UserPlaybookEngine.CommandResult(exitCode = 0, stdout = "done")
                }
            },
        )

        assertEquals(listOf("start", "failure"), result.steps.map { it.stepId })
        assertFalse(result.steps.first().succeeded)
        assertTrue(result.steps.last().succeeded)
        assertFalse(result.reachedStepLimit)
    }

    @Test
    fun stopsBranchCyclesAtSafetyLimit() = runBlocking {
        val loop = playbook(
            UserPlaybookStep("loop", "Loop", "true", onSuccessStepId = "loop"),
        )
        var calls = 0

        val result = UserPlaybookEngine.run(
            playbook = loop,
            execute = {
                calls++
                UserPlaybookEngine.CommandResult(exitCode = 0)
            },
        )

        assertEquals(UserPlaybookEngine.MAX_RUN_STEPS, calls)
        assertEquals(UserPlaybookEngine.MAX_RUN_STEPS, result.steps.size)
        assertTrue(result.reachedStepLimit)
    }

    @Test
    fun serializesBranchDestinationsForLocalPersistence() {
        val expected = playbook(
            UserPlaybookStep("check", "Check", "test -e /data/local/tmp/file", onSuccessStepId = "done"),
            UserPlaybookStep("done", "Done", "echo complete"),
        )
        val encoded = Json.encodeToString(listOf(expected))
        val decoded = Json.decodeFromString<List<UserPlaybook>>(encoded).single()

        assertEquals(expected, decoded)
    }

    @Test
    fun replacesEveryPackageVariableOccurrenceWithValidatedValue() {
        val rendered = UserPlaybookEngine.renderCommand(
            "pm disable {{ package }} && pm clear {{ package }}",
            "com.example.mdm",
        )

        assertEquals("pm disable com.example.mdm && pm clear com.example.mdm", rendered)
        assertNotNull(UserPlaybookEngine.validatePackageVariable("com.example.mdm; reboot"))
    }

    @Test
    fun executesAndRecordsCommandsAfterPackageVariableReplacement() = runBlocking {
        val playbook = playbook(UserPlaybookStep("run", "Run", "pm disable {{ package }}"))
        var executedCommand = ""
        val progress = mutableListOf<String>()

        val result = UserPlaybookEngine.run(
            playbook = playbook,
            packageVariable = "com.example.mdm",
            execute = { command ->
                executedCommand = command
                UserPlaybookEngine.CommandResult(exitCode = 0)
            },
            onStepStarted = { progress += "started:${it.id}" },
            onStepFinished = { progress += "finished:${it.stepId}" },
        )

        assertEquals("pm disable com.example.mdm", executedCommand)
        assertEquals(executedCommand, result.steps.single().command)
        assertEquals(listOf("started:run", "finished:run"), progress)
        assertFalse(result.isRunning)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingPackageVariableBeforeExecution() {
        runBlocking {
            UserPlaybookEngine.run(
                playbook = playbook(UserPlaybookStep("run", "Run", "pm disable {{ package }}")),
                execute = { UserPlaybookEngine.CommandResult(exitCode = 0) },
            )
        }
    }

    private fun playbook(vararg steps: UserPlaybookStep) = UserPlaybook(
        id = "test-playbook",
        name = "Test",
        steps = steps.toList(),
    )
}
