package sk.ziacik.androidstreamplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sk.ziacik.androidstreamplayer.catalog.MovieBrowseController
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.catalog.SeriesController
import sk.ziacik.androidstreamplayer.catalog.TmdbMovieCatalog
import sk.ziacik.androidstreamplayer.playback.PlaybackController
import sk.ziacik.androidstreamplayer.player.Media3PlayerPort
import sk.ziacik.androidstreamplayer.search.CompositeTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.KnabenTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.OkHttpSkTorrentSession
import sk.ziacik.androidstreamplayer.search.SharedPreferencesSkTorrentCredentialsStore
import sk.ziacik.androidstreamplayer.search.SkTorrentSearchProvider
import sk.ziacik.androidstreamplayer.search.SkTorrentTorrentFileFetcher
import sk.ziacik.androidstreamplayer.search.TorrentSearchController
import sk.ziacik.androidstreamplayer.settings.SettingsController
import sk.ziacik.androidstreamplayer.subtitle.OpenSubtitlesSubtitleProvider
import sk.ziacik.androidstreamplayer.torrent.LocalTorrServerRuntime
import sk.ziacik.androidstreamplayer.torrent.TorrServerClient
import sk.ziacik.androidstreamplayer.torrent.TorrServerProcess
import sk.ziacik.androidstreamplayer.torrent.TorrServerRuntime
import sk.ziacik.androidstreamplayer.torrent.TorrServerTorrentStreamer
import sk.ziacik.androidstreamplayer.ui.KinoApp
import sk.ziacik.androidstreamplayer.ui.KinoPlayerScreen
import sk.ziacik.androidstreamplayer.ui.UpdatePrompt
import sk.ziacik.androidstreamplayer.ui.WatchProgressEffect
import sk.ziacik.androidstreamplayer.ui.theme.AndroidStreamPlayerTheme
import sk.ziacik.androidstreamplayer.update.AppUpdateState
import sk.ziacik.androidstreamplayer.update.GithubAppUpdater
import sk.ziacik.androidstreamplayer.update.UpdateInfo
import sk.ziacik.androidstreamplayer.watch.SharedPreferencesWatchProgressStorage
import sk.ziacik.androidstreamplayer.watch.WatchProgressRepository

@UnstableApi
class MainActivity : ComponentActivity() {
	private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Hidden)

	private lateinit var movieBrowseController: MovieBrowseController
	private lateinit var movieSearchController: MovieSearchController
	private lateinit var seriesController: SeriesController
	private lateinit var settingsController: SettingsController
	private lateinit var torrentSearchController: TorrentSearchController
	private lateinit var playbackController: PlaybackController
	private lateinit var playerPort: Media3PlayerPort
	private lateinit var torrentRuntime: TorrServerRuntime
	private lateinit var watchProgressRepository: WatchProgressRepository
	private lateinit var appUpdater: GithubAppUpdater
	private var pendingUpdateAfterPermission: UpdateInfo? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		@Suppress("DEPRECATION")
		window.decorView.systemUiVisibility =
			View.SYSTEM_UI_FLAG_FULLSCREEN or
				View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
				View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

		appUpdater = GithubAppUpdater(this)
		playerPort = Media3PlayerPort(this)
		watchProgressRepository = WatchProgressRepository(
			storage = SharedPreferencesWatchProgressStorage(applicationContext),
		)

		val torrServerClient = TorrServerClient()
		val torrServerProcess = TorrServerProcess(
			binaryPath = File(applicationInfo.nativeLibraryDir, TORRSERVER_BINARY).absolutePath,
			dataPath = File(noBackupFilesDir, TORRSERVER_DATA_DIR).absolutePath,
			client = torrServerClient,
			scope = cleanupScope,
		)
		torrentRuntime = LocalTorrServerRuntime(
			process = torrServerProcess,
			client = torrServerClient,
		)
		val movieCatalog = TmdbMovieCatalog(BuildConfig.TMDB_API_KEY)
		val subtitleProvider = OpenSubtitlesSubtitleProvider(
			apiKey = BuildConfig.OPENSUBTITLES_API_KEY,
			cacheDir = File(cacheDir, SUBTITLE_CACHE_DIR),
			userAgent = "Kino/${BuildConfig.VERSION_NAME}",
		)

		movieBrowseController = MovieBrowseController(
			scope = appScope,
			loadTrending = movieCatalog::trending,
		)
		movieSearchController = MovieSearchController(
			scope = appScope,
			catalog = movieCatalog,
		)
		seriesController = SeriesController(
			scope = appScope,
			catalog = movieCatalog,
		)
		val skTorrentCredentialsStore = SharedPreferencesSkTorrentCredentialsStore(applicationContext)
		val skTorrentSession = OkHttpSkTorrentSession()
		val skTorrentTorrentFileFetcher = SkTorrentTorrentFileFetcher(
			credentialsStore = skTorrentCredentialsStore,
			session = skTorrentSession,
		)
		val torrentStreamer = TorrServerTorrentStreamer(
			runtime = torrentRuntime,
			torrentFileFetcher = skTorrentTorrentFileFetcher::fetch,
		)
		settingsController = SettingsController(skTorrentCredentialsStore)
		torrentSearchController = TorrentSearchController(
			scope = appScope,
			catalog = movieCatalog,
			provider = CompositeTorrentSearchProvider(
				listOf(
					KnabenTorrentSearchProvider(),
					SkTorrentSearchProvider(
						credentialsStore = skTorrentCredentialsStore,
						session = skTorrentSession,
					),
				),
			),
		)
		playbackController = PlaybackController(
			scope = appScope,
			streamer = torrentStreamer,
			subtitleSearch = subtitleProvider::search,
			subtitleDownload = subtitleProvider::download,
			onStreamReady = { source ->
				playerPort.prepare(source)
				playerPort.play()
			},
			onSubtitleSelected = playerPort::selectSubtitle,
		)

		setContent {
			AndroidStreamPlayerTheme {
				val currentUpdateState by updateState.collectAsState()
				Box(Modifier.fillMaxSize()) {
					KinoApp(
						movieBrowseController = movieBrowseController,
						movieSearchController = movieSearchController,
						seriesController = seriesController,
						torrentSearchController = torrentSearchController,
						playbackController = playbackController,
						watchProgressRepository = watchProgressRepository,
						settingsController = settingsController,
						playerContent = {
							movie,
							result,
							resumePositionMs,
							subtitleState,
							onSubtitleSelected,
							onExit,
							->
							WatchProgressEffect(
								player = playerPort.player,
								movie = movie,
								result = result,
								resumePositionMs = resumePositionMs,
								repository = watchProgressRepository,
							)
							KinoPlayerScreen(
								player = playerPort.player,
								movieTitle = movie?.displayTitle ?: "Now playing",
								result = result,
								subtitleState = subtitleState,
								onSubtitleSelected = onSubtitleSelected,
								onExit = onExit,
							)
						},
					)
					UpdatePrompt(
						state = currentUpdateState,
						onUpdate = {
							val info = when (val state = updateState.value) {
								is AppUpdateState.Available -> state.info
								is AppUpdateState.Error -> state.info
								else -> null
							}
							if (info != null) requestOrInstallUpdate(info)
						},
						onLater = { updateState.value = AppUpdateState.Hidden },
						modifier = Modifier.align(Alignment.Center),
					)
				}
			}
		}

		handleIntent(intent)

		if (appUpdater.shouldUseSelfUpdater()) {
			appScope.launch {
				runCatching { appUpdater.checkForUpdate() }
					.onSuccess { info ->
						if (info != null) updateState.value = AppUpdateState.Available(info)
					}
					.onFailure { Log.w("Kino", "Update check failed", it) }
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleIntent(intent)
	}

	override fun onResume() {
		super.onResume()
		val pending = pendingUpdateAfterPermission ?: return
		if (appUpdater.canRequestPackageInstalls()) {
			pendingUpdateAfterPermission = null
			startUpdate(pending)
		}
	}

	override fun onStop() {
		if (::playerPort.isInitialized) {
			playerPort.pause()
		}
		super.onStop()
	}

	override fun onDestroy() {
		if (::playbackController.isInitialized) {
			playbackController.exit()
		}
		if (::playerPort.isInitialized) {
			playerPort.release()
		}
		appScope.cancel()

		if (::torrentRuntime.isInitialized) {
			cleanupScope.launch {
				try {
					torrentRuntime.stop()
				} finally {
					cleanupScope.cancel()
				}
			}
		} else {
			cleanupScope.cancel()
		}

		super.onDestroy()
	}

	private fun requestOrInstallUpdate(info: UpdateInfo) {
		if (!appUpdater.canRequestPackageInstalls()) {
			pendingUpdateAfterPermission = info
			appUpdater.requestInstallPermission(this)
			return
		}
		startUpdate(info)
	}

	private fun startUpdate(info: UpdateInfo) {
		if (updateState.value is AppUpdateState.Downloading) return
		updateState.value = AppUpdateState.Downloading(info)
		appScope.launch {
			runCatching { appUpdater.download(info) }
				.onSuccess { apk ->
					updateState.value = AppUpdateState.Hidden
					appUpdater.install(this@MainActivity, apk)
				}
				.onFailure { error ->
					Log.w("Kino", "Update download failed", error)
					updateState.value = AppUpdateState.Error(
						info = info,
						message = "Aktualizáciu sa nepodarilo stiahnuť alebo overiť.",
					)
				}
		}
	}

	private fun handleIntent(intent: Intent) {
		intent.getStringExtra(EXTRA_MAGNET)
			?.trim()
			?.takeIf { it.isNotEmpty() }
			?.let(::startMagnet)
	}

	private fun startMagnet(magnet: String) {
		playbackController.playMagnet(magnet)
	}

	private companion object {
		const val EXTRA_MAGNET = "magnet"
		const val TORRSERVER_BINARY = "libtorrserver.so"
		const val TORRSERVER_DATA_DIR = "torrserver"
		const val SUBTITLE_CACHE_DIR = "subtitles"
	}
}
