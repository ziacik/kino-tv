package sk.ziacik.androidstreamplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import sk.ziacik.androidstreamplayer.catalog.Movie
import sk.ziacik.androidstreamplayer.catalog.MovieSearchController
import sk.ziacik.androidstreamplayer.catalog.tmdbPosterUrl
import sk.ziacik.androidstreamplayer.watch.WatchProgressEntry

@Composable
fun MovieSearchScreen(
	controller: MovieSearchController,
	onMovieSelected: (Movie) -> Unit,
	modifier: Modifier = Modifier,
	resumeWatching: List<WatchProgressEntry> = emptyList(),
	onResumeWatching: (WatchProgressEntry) -> Unit = {},
	onCancelResumeWatching: () -> Unit = {},
	onRemoveResumeWatching: (Int) -> Unit = {},
	startingResumeMovieId: Int? = null,
) {
	val state by controller.state.collectAsState()
	val searchRequester = remember { FocusRequester() }
	val gridState = rememberLazyGridState()
	val movieIds = state.results.map { it.browseKey }
	val posterRequesters = remember(movieIds) {
		movieIds.associateWith { FocusRequester() }
	}
	val resumeIds = resumeWatching.map { it.movie.resumeKey }
	val resumeRequesters = remember(resumeIds) {
		resumeIds.associateWith { FocusRequester() }
	}
	val firstResumeRequester = resumeWatching.firstOrNull()?.let { resumeRequesters[it.movie.resumeKey] }
	val firstPosterRequester = state.results.firstOrNull()?.let { posterRequesters[it.browseKey] }
	var initialFocusHandled by remember { mutableStateOf(false) }
	var resumeActionEntry by remember { mutableStateOf<WatchProgressEntry?>(null) }

	BackHandler(enabled = resumeActionEntry != null) {
		resumeActionEntry = null
	}
	BackHandler(enabled = resumeActionEntry == null && startingResumeMovieId != null) {
		onCancelResumeWatching()
	}

	LaunchedEffect(state.results, state.focusedMovieId) {
		if (initialFocusHandled) return@LaunchedEffect

		val rememberedMovieId = state.focusedMovieId
		if (rememberedMovieId != null) {
			val index = state.results.indexOfFirst { it.browseKey == rememberedMovieId }
			if (index >= 0) {
				gridState.scrollToItem(index)
				posterRequesters[rememberedMovieId]?.requestFocus()
				initialFocusHandled = true
			}
		} else {
			searchRequester.requestFocus()
			initialFocusHandled = true
		}
	}

	Surface(
		modifier = modifier.fillMaxSize(),
		color = Color.Black,
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						listOf(
							MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
							MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
							Color.Black,
						),
					),
				),
		) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(
						Brush.radialGradient(
							colors = listOf(
								MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
								Color.Transparent,
							),
							radius = 900f,
						),
					),
			)

			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(horizontal = 64.dp, vertical = 36.dp),
			) {
				MovieSearchHeader(compact = state.results.isNotEmpty())
				Spacer(Modifier.height(if (state.results.isNotEmpty()) 18.dp else 28.dp))

				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(14.dp),
				) {
					OutlinedTextField(
						value = state.query,
						onValueChange = controller::setQuery,
						modifier = Modifier
							.width(620.dp)
							.testTag("movie-search-input")
							.focusRequester(searchRequester)
							.focusProperties {
								down = firstResumeRequester ?: firstPosterRequester ?: FocusRequester.Default
							},
						placeholder = { Text("Movie or series") },
						singleLine = true,
						keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
						keyboardActions = KeyboardActions(onSearch = { controller.searchNow() }),
					)
				}

				Spacer(Modifier.height(22.dp))

				when {
					state.isSearching && state.results.isEmpty() -> MovieSearchLoading()
					state.errorMessage != null -> MovieSearchError(onRetry = controller::retry)
					state.results.isNotEmpty() -> {
						if (state.isSearching) {
							Row(
								verticalAlignment = Alignment.CenterVertically,
								horizontalArrangement = Arrangement.spacedBy(10.dp),
							) {
								CircularProgressIndicator(modifier = Modifier.width(18.dp))
								Text(
									text = "Updating…",
									style = MaterialTheme.typography.labelLarge,
									color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
								)
							}
							Spacer(Modifier.height(10.dp))
						}

						LazyVerticalGrid(
							columns = GridCells.Fixed(6),
							state = gridState,
							modifier = Modifier.fillMaxSize(),
							contentPadding = PaddingValues(bottom = 40.dp),
							horizontalArrangement = Arrangement.spacedBy(18.dp),
							verticalArrangement = Arrangement.spacedBy(20.dp),
						) {
							itemsIndexed(
								items = state.results,
								key = { _, movie -> movie.browseKey },
							) { index, movie ->
								val requester = posterRequesters.getValue(movie.browseKey)
								MoviePosterCard(
									movie = movie,
									onClick = { onMovieSelected(movie) },
									focusRequester = requester,
									upFocusRequester = searchRequester.takeIf { index < POSTER_COLUMNS },
									onFocused = { controller.setFocusedMovie(movie.browseKey) },
									modifier = Modifier.fillMaxWidth(),
								)
							}
						}
					}
					state.query.trim().length >= 2 -> MovieSearchEmpty()
					else -> {
						if (resumeWatching.isNotEmpty()) {
							ResumeWatchingRow(
								entries = resumeWatching,
								focusRequesters = resumeRequesters,
								upFocusRequester = searchRequester,
								onResume = onResumeWatching,
								onCancelStarting = onCancelResumeWatching,
								onOpenActions = { resumeActionEntry = it },
								startingMovieId = startingResumeMovieId,
							)
							Spacer(Modifier.height(20.dp))
						}
						MovieSearchLanding()
					}
				}
			}

			resumeActionEntry?.let { entry ->
				ResumeWatchingActions(
					entry = entry,
					onRemove = {
						onRemoveResumeWatching(entry.movie.resumeKey)
						resumeActionEntry = null
					},
					onCancel = { resumeActionEntry = null },
				)
			}
		}
	}
}

@Composable
private fun ResumeWatchingRow(
	entries: List<WatchProgressEntry>,
	focusRequesters: Map<Int, FocusRequester>,
	upFocusRequester: FocusRequester,
	onResume: (WatchProgressEntry) -> Unit,
	onCancelStarting: () -> Unit,
	onOpenActions: (WatchProgressEntry) -> Unit,
	startingMovieId: Int?,
) {
	Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
		Text(
			text = RESUME_WATCHING_LABEL,
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onBackground,
		)
		LazyRow(
			horizontalArrangement = Arrangement.spacedBy(18.dp),
			contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
		) {
			items(
				items = entries,
				key = { it.movie.tmdbId },
			) { entry ->
				ResumeWatchingCard(
					entry = entry,
					focusRequester = focusRequesters.getValue(entry.movie.resumeKey),
					upFocusRequester = upFocusRequester,
					onResume = { onResume(entry) },
					onCancelStarting = onCancelStarting,
					onOpenActions = { onOpenActions(entry) },
					isStarting = startingMovieId == entry.movie.resumeKey,
				)
			}
		}
	}
}

@Composable
private fun ResumeWatchingCard(
	entry: WatchProgressEntry,
	focusRequester: FocusRequester,
	upFocusRequester: FocusRequester,
	onResume: () -> Unit,
	onCancelStarting: () -> Unit,
	onOpenActions: () -> Unit,
	isStarting: Boolean,
) {
	var focused by remember { mutableStateOf(false) }
	var longPressTriggered by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(
		targetValue = if (focused) 1.04f else 1f,
		label = "resume-watching-scale",
	)
	val progress = if (entry.durationMs > 0L) {
		(entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
	} else {
		0f
	}
	val shape = RoundedCornerShape(12.dp)

	Card(
		onClick = {
			when (resumeWatchingActivation(isStarting)) {
				ResumeWatchingActivation.Resume -> onResume()
				ResumeWatchingActivation.Cancel -> onCancelStarting()
			}
		},
		modifier = Modifier
			.width(154.dp)
			.testTag("resume-watching-${entry.movie.resumeKey}")
			.focusRequester(focusRequester)
			.focusProperties { up = upFocusRequester }
			.onFocusChanged { focused = it.isFocused }
			.semantics {
				if (resumeWatchingOptionsEnabled(isStarting)) {
					onLongClick(label = "Show options") {
						onOpenActions()
						true
					}
				}
			}
			.onPreviewKeyEvent { event ->
				if (!resumeWatchingOptionsEnabled(isStarting)) {
					false
				} else {
					val isConfirm = event.key == Key.DirectionCenter ||
						event.key == Key.Enter ||
						event.key == Key.NumPadEnter
					when (
						resumeWatchingKeyAction(
							isConfirm = isConfirm,
							isDown = event.type == KeyEventType.KeyDown,
							isUp = event.type == KeyEventType.KeyUp,
							repeatCount = event.nativeKeyEvent.repeatCount,
							longPressTriggered = longPressTriggered,
						)
					) {
						ResumeWatchingKeyAction.OpenActions -> {
							longPressTriggered = true
							onOpenActions()
							true
						}
						ResumeWatchingKeyAction.Consume -> {
							if (event.type == KeyEventType.KeyUp) longPressTriggered = false
							true
						}
						ResumeWatchingKeyAction.PassThrough -> false
					}
				}
			}
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			},
		shape = shape,
		border = if (focused) {
			BorderStroke(3.dp, MaterialTheme.colorScheme.secondary)
		} else {
			BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
		},
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
	) {
		Column {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.aspectRatio(2f / 3f)
					.clip(shape),
			) {
				val posterUrl = tmdbPosterUrl(entry.movie.posterPath)
				if (posterUrl != null) {
					AsyncImage(
						model = posterUrl,
						contentDescription = entry.movie.title,
						contentScale = ContentScale.Crop,
						modifier = Modifier.fillMaxSize(),
					)
				} else {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(MaterialTheme.colorScheme.surfaceVariant),
						contentAlignment = Alignment.Center,
					) {
						Text(
							text = entry.movie.title.take(1).uppercase(),
							style = MaterialTheme.typography.displayMedium,
							fontWeight = FontWeight.Black,
							color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
						)
					}
				}

				if (isStarting) {
					Box(
						modifier = Modifier
							.fillMaxSize()
							.background(Color.Black.copy(alpha = 0.58f))
							.testTag("resume-watching-starting-${entry.movie.resumeKey}"),
						contentAlignment = Alignment.Center,
					) {
						CircularProgressIndicator(
							modifier = Modifier.size(38.dp),
							strokeWidth = 3.dp,
						)
					}
				}
			}
			LinearProgressIndicator(
				progress = { progress },
				modifier = Modifier.fillMaxWidth(),
			)
			Column(
				modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				Text(
					text = entry.movie.title,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = when {
						isStarting -> "Starting… · OK to cancel"
						focused -> "Hold OK for options"
						else -> "${(progress * 100).toInt()}% watched"
					},
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
					maxLines = 1,
				)
			}
		}
	}
}

@Composable
private fun ResumeWatchingActions(
	entry: WatchProgressEntry,
	onRemove: () -> Unit,
	onCancel: () -> Unit,
) {
	val cancelRequester = remember { FocusRequester() }
	var waitingForConfirmRelease by remember(entry.movie.resumeKey) { mutableStateOf(true) }
	var removeFocused by remember { mutableStateOf(false) }
	var cancelFocused by remember { mutableStateOf(false) }
	val removeFocusStyle = resumeActionFocusStyle(removeFocused)
	val cancelFocusStyle = resumeActionFocusStyle(cancelFocused)
	val buttonShape = RoundedCornerShape(12.dp)

	LaunchedEffect(entry.movie.resumeKey) {
		cancelRequester.requestFocus()
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.76f))
			.testTag("resume-watching-actions")
			.onPreviewKeyEvent { event ->
				val isConfirm = event.key == Key.DirectionCenter ||
					event.key == Key.Enter ||
					event.key == Key.NumPadEnter
				val decision = resumeActionsKeyDecision(
					isConfirm = isConfirm,
					isUp = event.type == KeyEventType.KeyUp,
					waitingForRelease = waitingForConfirmRelease,
				)
				waitingForConfirmRelease = decision.waitingForRelease
				decision.consume
			},
		contentAlignment = Alignment.Center,
	) {
		Surface(
			modifier = Modifier.width(430.dp),
			shape = RoundedCornerShape(20.dp),
			color = MaterialTheme.colorScheme.surface,
			shadowElevation = 18.dp,
		) {
			Column(
				modifier = Modifier.padding(24.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp),
			) {
				Text(
					text = entry.movie.title,
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				Text(
					text = "$RESUME_WATCHING_LABEL options",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
				)
				Spacer(Modifier.height(4.dp))
				Button(
					onClick = onRemove,
					modifier = Modifier
						.fillMaxWidth()
						.graphicsLayer {
							scaleX = removeFocusStyle.scale
							scaleY = removeFocusStyle.scale
						}
						.border(
							width = removeFocusStyle.borderWidthDp.dp,
							color = if (removeFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
							shape = buttonShape,
						)
						.onFocusChanged { removeFocused = it.isFocused }
						.testTag("resume-watching-remove"),
					shape = buttonShape,
				) {
					Text(resumeWatchingRemoveLabel())
				}
				TextButton(
					onClick = onCancel,
					modifier = Modifier
						.fillMaxWidth()
						.graphicsLayer {
							scaleX = cancelFocusStyle.scale
							scaleY = cancelFocusStyle.scale
						}
						.border(
							width = cancelFocusStyle.borderWidthDp.dp,
							color = if (cancelFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
							shape = buttonShape,
						)
						.onFocusChanged { cancelFocused = it.isFocused }
						.focusRequester(cancelRequester)
						.testTag("resume-watching-cancel"),
					shape = buttonShape,
				) {
					Text("Cancel")
				}
			}
		}
	}
}

@Composable
private fun MovieSearchHeader(compact: Boolean) {
	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(
			text = "KINO",
			style = MaterialTheme.typography.labelLarge,
			fontWeight = FontWeight.Black,
			letterSpacing = 3.sp,
			color = MaterialTheme.colorScheme.secondary,
		)
		Text(
			text = if (compact) "Search" else "What are we watching?",
			fontSize = if (compact) 28.sp else 42.sp,
			fontWeight = FontWeight.SemiBold,
			color = MaterialTheme.colorScheme.onBackground,
		)
		if (!compact) {
			Text(
				text = "Pick a movie or series. We’ll find the best available versions after.",
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f),
			)
		}
	}
}

@Composable
private fun MovieSearchLoading() {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		CircularProgressIndicator()
		Text(
			text = "Searching movies…",
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
		)
	}
}

@Composable
private fun MovieSearchError(onRetry: () -> Unit) {
	Column(
		modifier = Modifier.testTag("movie-search-error"),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Text(
			text = "Couldn’t search movies",
			style = MaterialTheme.typography.titleLarge,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			text = "The movie catalog didn’t answer. Your current search stays here.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
		)
		Button(onClick = onRetry) {
			Text("Retry")
		}
	}
}

@Composable
private fun MovieSearchEmpty() {
	Column(
		modifier = Modifier.testTag("movie-search-empty"),
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			text = "No movies found",
			style = MaterialTheme.typography.titleLarge,
			color = MaterialTheme.colorScheme.onBackground,
		)
		Text(
			text = "Try the original title or a shorter search.",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.58f),
		)
	}
}

@Composable
private fun MovieSearchLanding() {
	Column(
		modifier = Modifier.padding(top = 26.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Text(
			text = "Movies, not filenames.",
			fontSize = 26.sp,
			fontWeight = FontWeight.Medium,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
		)
		Text(
			text = "Start typing and Kino will show poster suggestions from the catalog.",
			style = MaterialTheme.typography.bodyLarge,
			color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.56f),
		)
	}
}

private const val POSTER_COLUMNS = 6
