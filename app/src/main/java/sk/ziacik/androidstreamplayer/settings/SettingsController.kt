package sk.ziacik.androidstreamplayer.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sk.ziacik.androidstreamplayer.search.SkTorrentCredentials
import sk.ziacik.androidstreamplayer.search.SkTorrentCredentialsStore

data class SettingsUiState(
    val username: String = "",
    val password: String = "",
    val configured: Boolean = false,
    val feedback: String? = null,
)

class SettingsController(
    private val credentialsStore: SkTorrentCredentialsStore,
) {
    private val mutableState = MutableStateFlow(loadState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    fun setUsername(value: String) {
        mutableState.value = mutableState.value.copy(username = value, feedback = null)
    }

    fun setPassword(value: String) {
        mutableState.value = mutableState.value.copy(password = value, feedback = null)
    }

    fun save() {
        val current = mutableState.value
        val credentials = SkTorrentCredentials(current.username.trim(), current.password)
        if (credentials.isComplete) {
            credentialsStore.save(credentials)
            mutableState.value = loadState().copy(feedback = "Saved")
        } else {
            clear()
        }
    }

    fun clear() {
        credentialsStore.clear()
        val restored = loadState()
        mutableState.value = restored.copy(
            feedback = if (restored.configured) "Using bundled account" else "Credentials cleared",
        )
    }

    private fun loadState(): SettingsUiState {
        val credentials = credentialsStore.load() ?: return SettingsUiState()
        return SettingsUiState(
            username = credentials.username,
            password = credentials.password,
            configured = true,
        )
    }
}
