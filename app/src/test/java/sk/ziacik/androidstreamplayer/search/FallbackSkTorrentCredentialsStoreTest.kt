package sk.ziacik.androidstreamplayer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FallbackSkTorrentCredentialsStoreTest {
    @Test
    fun `bundled account is available without local credentials`() {
        val bundled = SkTorrentCredentials("bundled-user", "complex&%password")
        val store = FallbackSkTorrentCredentialsStore(InMemoryStore(), bundled)

        assertEquals(bundled, store.load())
    }

    @Test
    fun `saved account overrides bundled account and clear restores it`() {
        val bundled = SkTorrentCredentials("bundled-user", "bundled-password")
        val custom = SkTorrentCredentials("custom-user", "custom-password")
        val store = FallbackSkTorrentCredentialsStore(InMemoryStore(), bundled)

        store.save(custom)
        assertEquals(custom, store.load())

        store.clear()
        assertEquals(bundled, store.load())
    }

    @Test
    fun `missing or incomplete bundled credentials are ignored`() {
        assertNull(FallbackSkTorrentCredentialsStore(InMemoryStore(), null).load())
        assertNull(
            FallbackSkTorrentCredentialsStore(
                InMemoryStore(),
                SkTorrentCredentials("bundled-user", ""),
            ).load(),
        )
    }

    private class InMemoryStore : SkTorrentCredentialsStore {
        private var credentials: SkTorrentCredentials? = null

        override fun load(): SkTorrentCredentials? = credentials

        override fun save(credentials: SkTorrentCredentials) {
            this.credentials = credentials
        }

        override fun clear() {
            credentials = null
        }
    }
}
