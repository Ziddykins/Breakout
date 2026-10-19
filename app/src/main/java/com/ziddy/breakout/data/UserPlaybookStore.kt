package com.ziddy.breakout.data

import android.content.Context
import com.ziddy.breakout.engine.UserPlaybookEngine
import com.ziddy.breakout.model.UserPlaybook
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

class UserPlaybookStore(context: Context) {
    private val preferences = context.getSharedPreferences("user_playbooks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<UserPlaybook> {
        val encoded = preferences.getString(KEY_PLAYBOOKS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<UserPlaybook>>(encoded).also(::validateAll)
        } catch (error: SerializationException) {
            throw IOException("Saved playbooks could not be read: ${error.message}", error)
        } catch (error: IllegalArgumentException) {
            throw IOException("Saved playbooks are invalid: ${error.message}", error)
        }
    }

    fun save(playbooks: List<UserPlaybook>) {
        validateAll(playbooks)
        val encoded = json.encodeToString(playbooks)
        if (!preferences.edit(commit = true) {putString(KEY_PLAYBOOKS, encoded)}) {
            throw IOException("Android could not persist the playbooks.")
        }
    }

    private fun validateAll(playbooks: List<UserPlaybook>) {
        require(playbooks.map(UserPlaybook::id).distinct().size == playbooks.size) {
            "Playbook IDs must be unique."
        }
        playbooks.forEach { playbook ->
            val validationError = UserPlaybookEngine.validate(playbook)
            require(validationError == null) {
                validationError ?: "Invalid playbook."
            }
        }
    }

    private companion object {
        const val KEY_PLAYBOOKS = "playbooks"
    }
}
