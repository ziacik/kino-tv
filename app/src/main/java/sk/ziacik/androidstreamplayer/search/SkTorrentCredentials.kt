package sk.ziacik.androidstreamplayer.search

import android.content.Context
import androidx.core.content.edit

data class SkTorrentCredentials(
    val username: String,
    val password: String,
) {
    val isComplete: Boolean
        get() = username.isNotBlank() && password.isNotBlank()
}

interface SkTorrentCredentialsStore {
    fun load(): SkTorrentCredentials?
    fun save(credentials: SkTorrentCredentials)
    fun clear()
}

class SharedPreferencesSkTorrentCredentialsStore(context: Context) : SkTorrentCredentialsStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): SkTorrentCredentials? {
        val username = preferences.getString(KEY_USERNAME, null).orEmpty()
        val password = preferences.getString(KEY_PASSWORD, null).orEmpty()
        return SkTorrentCredentials(username, password).takeIf { it.isComplete }
    }

    override fun save(credentials: SkTorrentCredentials) {
        if (!credentials.isComplete) {
            clear()
            return
        }
        preferences.edit {
            putString(KEY_USERNAME, credentials.username.trim())
            putString(KEY_PASSWORD, credentials.password)
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private companion object {
        const val PREFERENCES_NAME = "sktorrent_credentials"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}

/**
 * Uses locally saved credentials when present, otherwise the account packaged at build time.
 * Clearing a local override restores the bundled account.
 */
class FallbackSkTorrentCredentialsStore(
    private val local: SkTorrentCredentialsStore,
    private val bundled: SkTorrentCredentials?,
) : SkTorrentCredentialsStore {
    override fun load(): SkTorrentCredentials? =
        local.load()?.takeIf { it.isComplete } ?: bundled?.takeIf { it.isComplete }

    override fun save(credentials: SkTorrentCredentials) = local.save(credentials)

    override fun clear() = local.clear()
}
