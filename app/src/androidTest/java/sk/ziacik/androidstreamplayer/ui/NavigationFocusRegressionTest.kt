package sk.ziacik.androidstreamplayer.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
import sk.ziacik.androidstreamplayer.catalog.MovieCatalog
import sk.ziacik.androidstreamplayer.catalog.MovieExternalIds
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.catalog.SeriesController
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.search.MovieTorrentSearchRequest
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.search.TorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.TorrentSearchResult
import sk.ziacik.androidstreamplayer.torrent.TorrentSource
import sk.ziacik.androidstreamplayer.torrent.TorrentStreamer
import sk.ziacik.androidstreamplayer.watch.WatchProgressRepository
import sk.ziacik.androidstreamplayer.watch.WatchProgressStorage

class NavigationFocusRegressionTest {
	@get:Rule
	val composeRule = createComposeRule()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	@After
	fun tearDown() {
		scope.cancel()
	}

	@Test
	fun searchDetailHomeRoundTripKeepsDpadPathToScrolledTrendingCard() {
		val trending = (0..13).map(::dashboardMovie)
		val movieSearchController = MovieSearchController(
			scope = scope,
			catalog = FakeCatalog,
			debounceMs = 0,
		)
		val torrentSearchController = TorrentSearchController(
			scope = scope,
			catalog = FakeCatalog,
			provider = TorrentSearchProvider { request -> listOf(torrent(request)) },
		)
		val playbackController = PlaybackController(
			scope = scope,
			streamer = TorrentStreamer { TorrentSource("http://127.0.0.1/movie.mkv") },
			onStreamReady = {},
		)

		composeRule.setContent {
			KinoApp(
				movieBrowseController = MovieBrowseController(
					scope = scope,
					loadTrending = { trending },
				),
				movieSearchController = movieSearchController,
				seriesController = SeriesController(scope, FakeCatalog),
				torrentSearchController = torrentSearchController,
				playbackController = playbackController,
				watchProgressRepository = watchProgressRepository(),
				playerContent = { _, _, _, _, _, _ -> Box(Modifier.testTag("kino-player")) },
			)
		}

		val targetIndex = 12
		val firstTrendingTag = "home-trending-${trending.first().tmdbId}"
		val targetTrendingTag = "home-trending-${trending[targetIndex].tmdbId}"

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag(firstTrendingTag)).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onAllNodes(
			hasScrollAction() and hasAnyDescendant(hasTestTag(firstTrendingTag)),
		)[1].performScrollToIndex(targetIndex)
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag(targetTrendingTag)).fetchSemanticsNodes().isNotEmpty()
		}

		composeRule.onNodeWithTag(targetTrendingTag)
			.requestFocus()
			.assertIsFocused()
			.performKeyInput { pressKey(Key.DirectionUp) }
		composeRule.onNodeWithTag("home-search-nav")
			.assertIsFocused()
			.performClick()

		composeRule.onNodeWithTag("movie-search-input").performTextInput("Matrix")
		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-603")).fetchSemanticsNodes().isNotEmpty()
		}
		composeRule.onNodeWithTag("movie-603")
			.requestFocus()
			.assertIsFocused()
			.performClick()

		composeRule.waitUntil(timeoutMillis = 5_000) {
			composeRule.onAllNodes(hasTestTag("movie-detail")).fetchSemanticsNodes().isNotEmpty()
		}
		pressBack()
		waitUntilFocused("movie-603")

		pressBack()
		waitUntilFocused("home-search-nav")
		composeRule.onNodeWithTag("home-search-nav")
			.performKeyInput { pressKey(Key.DirectionDown) }
		waitUntilFocused(targetTrendingTag)
	}

	private fun pressBack() {
		InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
	}

	private fun waitUntilFocused(tag: String) {
		composeRule.waitUntil(timeoutMillis = 5_000) {
			runCatching {
				composeRule.onNodeWithTag(tag).assertIsFocused()
			}.isSuccess
		}
	}

	private fun torrent(request: MovieTorrentSearchRequest) = TorrentSearchResult(
		id = "hit-1",
		title = "${request.title}.${request.year}.1080p.BluRay",
		magnetUri = "magnet:?xt=urn:btih:matrix",
		quality = "1080p",
		seeders = 42,
	)

	private fun watchProgressRepository(): WatchProgressRepository = WatchProgressRepository(
		storage = object : WatchProgressStorage {
			override fun load() = emptyList<sk.ziacik.androidstreamplayer.watch.WatchProgressEntry>()

			override fun save(entries: List<sk.ziacik.androidstreamplayer.watch.WatchProgressEntry>) = Unit
		},
	)

	private fun dashboardMovie(index: Int) = Movie(
		tmdbId = 10_000 + index,
		title = "Dashboard Movie $index",
		originalTitle = "Dashboard Movie $index",
		releaseYear = 2020 + index % 7,
		overview = "Dashboard movie used for focus restoration testing.",
		voteAverage = 7.0,
		posterPath = null,
		backdropPath = null,
	)

	private object FakeCatalog : MovieCatalog {
		override suspend fun search(query: String): List<Movie> = listOf(matrix())

		override suspend fun externalIds(tmdbId: Int): MovieExternalIds =
			MovieExternalIds("tt0133093")

		private fun matrix() = Movie(
			tmdbId = 603,
			title = "The Matrix",
			originalTitle = "The Matrix",
			releaseYear = 1999,
			overview = "A hacker discovers the truth about his world.",
			voteAverage = 8.2,
			posterPath = null,
			backdropPath = null,
		)
	}
}
