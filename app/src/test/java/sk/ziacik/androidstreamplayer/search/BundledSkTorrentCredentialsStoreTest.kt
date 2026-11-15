package sk.ziacik.androidstreamplayer.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BundledSkTorrentCredentialsStoreTest {
    @Test
    fun `bundled account is used without on-device configuration`() {
        val bundled = SkTorrentCredentials("bundled-user", "complex&%password")
        val store = BundledSkTorrentCredentialsStore(bundled)

        assertEquals(bundled, store.load())
    }

    @Test
    fun `incomplete bundled credentials are ignored`() {
        val store = BundledSkTorrentCredentialsStore(SkTorrentCredentials("bundled-user", ""))

        assertNull(store.load())
    }
}
