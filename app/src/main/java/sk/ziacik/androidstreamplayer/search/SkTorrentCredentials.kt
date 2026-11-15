package sk.ziacik.androidstreamplayer.search

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

/** Read-only login credentials supplied when building the APK. */
class BundledSkTorrentCredentialsStore(
    private val credentials: SkTorrentCredentials,
) : SkTorrentCredentialsStore {
    override fun load(): SkTorrentCredentials? = credentials.takeIf { it.isComplete }

    override fun save(credentials: SkTorrentCredentials) = Unit

    override fun clear() = Unit
}
