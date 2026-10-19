package com.ziddy.breakout.engine

import com.ziddy.breakout.model.UserPlaybook
import com.ziddy.breakout.model.UserPlaybookRunResult
import com.ziddy.breakout.model.UserPlaybookStep
import com.ziddy.breakout.model.UserPlaybookStepResult

object UserPlaybookEngine {
    const val MAX_STEPS = 30
    const val MAX_RUN_STEPS = 60
    const val PACKAGE_VARIABLE = "{{ package }}"
    private val packageVariablePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun validate(playbook: UserPlaybook): String? {
        if (!playbook.id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return "Invalid playbook ID."
        if (playbook.name.isBlank() || playbook.name.length > 80) {
            return "Playbook name must contain 1 to 80 characters."
        }
        if (playbook.steps.isEmpty() || playbook.steps.size > MAX_STEPS) {
            return "A playbook must contain 1 to $MAX_STEPS steps."
        }
        val ids = playbook.steps.map { it.id }
        if (ids.any { !it.matches(Regex("[A-Za-z0-9_-]{1,64}")) } || ids.distinct().size != ids.size) {
            return "Step IDs must be unique and contain only letters, numbers, underscores, or hyphens."
        }
        for (step in playbook.steps) {
            if (step.title.isBlank() || step.title.length > 80) {
                return "Every step needs a title of 1 to 80 characters."
            }
            if (step.command.isBlank() || step.command.length > 2000) {
                return "Every step needs a command of 1 to 2,000 characters."
            }
            if (step.onSuccessStepId != null && step.onSuccessStepId !in ids) {
                return "Step '${step.title}' has an unknown success destination."
            }
            if (step.onFailureStepId != null && step.onFailureStepId !in ids) {
                return "Step '${step.title}' has an unknown failure destination."
            }
        }
        return null
    }

    fun requiresPackageVariable(playbook: UserPlaybook): Boolean =
        playbook.steps.any { PACKAGE_VARIABLE in it.command }

    fun validatePackageVariable(value: String?): String? {
        if (value.isNullOrBlank()) return "Enter a package name for {{ package }}."
        if (value.length > 255 || !packageVariablePattern.matches(value)) {
            return "Enter a valid Android package name."
        }
        return null
    }

    fun renderCommand(command: String, packageVariable: String?): String {
        if (PACKAGE_VARIABLE !in command) return command
        val validationError = validatePackageVariable(packageVariable)
        require(validationError == null) { validationError ?: "Invalid package variable." }
        return command.replace(PACKAGE_VARIABLE, requireNotNull(packageVariable))
    }

    suspend fun run(
        playbook: UserPlaybook,
        packageVariable: String? = null,
        execute: suspend (String) -> CommandResult,
        onStepStarted: (UserPlaybookStep) -> Unit = {},
        onStepFinished: (UserPlaybookStepResult) -> Unit = {},
    ): UserPlaybookRunResult {
        require(validate(playbook) == null) { validate(playbook) ?: "Invalid playbook." }
        if (requiresPackageVariable(playbook)) {
            val validationError = validatePackageVariable(packageVariable)
            require(validationError == null) { validationError ?: "Invalid package variable." }
        }
        val stepsById = playbook.steps.associateBy(UserPlaybookStep::id)
        val results = mutableListOf<UserPlaybookStepResult>()
        var nextStepId: String? = playbook.steps.first().id

        while (nextStepId != null && results.size < MAX_RUN_STEPS) {
            val step = requireNotNull(stepsById[nextStepId])
            val command = renderCommand(step.command, packageVariable)
            onStepStarted(step)
            val commandResult = execute(command)
            val stepResult = UserPlaybookStepResult(
                stepId = step.id,
                title = step.title,
                command = command,
                succeeded = commandResult.exitCode == 0 && !commandResult.timedOut,
                output = listOf(commandResult.stdout, commandResult.stderr)
                    .filter(String::isNotBlank)
                    .joinToString("\n")
                    .take(4000),
                timedOut = commandResult.timedOut,
            )
            results += stepResult
            onStepFinished(stepResult)
            nextStepId = if (stepResult.succeeded) step.onSuccessStepId else step.onFailureStepId
        }

        return UserPlaybookRunResult(
            playbookId = playbook.id,
            steps = results,
            reachedStepLimit = nextStepId != null,
            isRunning = false,
        )
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String = "",
        val stderr: String = "",
        val timedOut: Boolean = false,
    )
}
