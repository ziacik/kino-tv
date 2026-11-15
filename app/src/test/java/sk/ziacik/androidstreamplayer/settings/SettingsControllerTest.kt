package sk.ziacik.androidstreamplayer.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sk.ziacik.androidstreamplayer.search.FallbackSkTorrentCredentialsStore
import sk.ziacik.androidstreamplayer.search.SkTorrentCredentials
import sk.ziacik.androidstreamplayer.search.SkTorrentCredentialsStore

class SettingsControllerTest {
    @Test
    fun `clearing custom credentials restores the bundled account in settings`() {
        val bundled = SkTorrentCredentials("bundled-user", "bundled-password")
        val local = InMemoryStore()
        val controller = SettingsController(FallbackSkTorrentCredentialsStore(local, bundled))

        assertTrue(controller.state.value.configured)
        assertEquals("bundled-user", controller.state.value.username)

        controller.setUsername("custom-user")
        controller.setPassword("custom-password")
        controller.save()
        assertEquals("custom-user", controller.state.value.username)

        controller.clear()
        assertEquals("bundled-user", controller.state.value.username)
        assertEquals("bundled-password", controller.state.value.password)
        assertEquals("Using bundled account", controller.state.value.feedback)
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
