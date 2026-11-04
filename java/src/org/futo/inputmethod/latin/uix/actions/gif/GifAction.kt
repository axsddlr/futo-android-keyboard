package org.futo.inputmethod.latin.uix.actions.gif

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionHeaderSearch
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue

private const val SEARCH_DEBOUNCE_MS = 350L

private enum class GifMode(@StringRes val label: Int) {
    Trending(R.string.action_gif_mode_trending),
    Recent(R.string.action_gif_mode_recent),
    Saved(R.string.action_gif_mode_saved)
}

private class GifActionWindow(
    private val manager: KeyboardManagerForAction
) : ActionWindow() {
    private val searchText = mutableStateOf("")
    private val searching = mutableStateOf(false)

    override val expandedWindowHeight: Dp
        get() {
            val metrics = manager.getContext().resources.displayMetrics
            return (metrics.heightPixels / metrics.density * 0.52f).dp
        }

    /**
     * Search mode shows the keyboard below the panel. Keep enough room for the search field,
     * chips and several result rows instead of collapsing to a single row.
     */
    override val fixedWindowHeight: Dp
        get() {
            val metrics = manager.getContext().resources.displayMetrics
            return (metrics.heightPixels / metrics.density * 0.42f).dp
        }

    @Composable
    override fun windowName(): String = stringResource(R.string.action_gif_title)

    @Composable
    override fun WindowTitleBar(rowScope: RowScope) = with(rowScope) {
        val enabled = useDataStoreValue(GifSearchSettings.Enabled)
        val provider = GifProviderKind.fromId(useDataStoreValue(GifSearchSettings.Provider))
        val tenorKey = useDataStoreValue(GifSearchSettings.TenorApiKey)
        val giphyKey = useDataStoreValue(GifSearchSettings.GiphyApiKey)
        val apiKey = if (provider == GifProviderKind.Tenor) tenorKey else giphyKey
        val ready = enabled && apiKey.isNotBlank()

        if (searching.value && ready) {
            ActionHeaderSearch(
                searchText,
                Modifier.weight(1.0f),
                placeholder = stringResource(R.string.action_gif_search_hint)
            )
            IconButton(onClick = {
                searching.value = false
                searchText.value = ""
            }) {
                Icon(
                    painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.action_gif_close_search)
                )
            }
            return
        }

        Text(windowName(), modifier = Modifier.align(Alignment.CenterVertically))
        Spacer(Modifier.weight(1.0f))

        if (ready) {
            IconButton(onClick = { searching.value = true }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = stringResource(R.string.action_gif_search)
                )
            }
        }
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val enabled = useDataStoreValue(GifSearchSettings.Enabled)
        val provider = GifProviderKind.fromId(useDataStoreValue(GifSearchSettings.Provider))
        val tenorKey = useDataStoreValue(GifSearchSettings.TenorApiKey)
        val giphyKey = useDataStoreValue(GifSearchSettings.GiphyApiKey)
        val apiKey = if (provider == GifProviderKind.Tenor) tenorKey else giphyKey

        if (!enabled || apiKey.isBlank()) {
            GifSetupPanel(
                enabled = enabled,
                provider = provider,
                missingKey = apiKey.isBlank()
            )
        } else {
            GifSearchPanel(
                manager = manager,
                provider = provider,
                apiKey = apiKey,
                searchText = searchText,
                searching = searching
            )
        }
    }
}

@Composable
private fun GifSetupPanel(
    enabled: Boolean,
    provider: GifProviderKind,
    missingKey: Boolean
) {
    val context = LocalContext.current
    val enabledSetting = useDataStore(GifSearchSettings.Enabled)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.action_gif_setup_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.action_gif_setup_body),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            stringResource(R.string.action_gif_setup_provider_body, provider.displayName),
            style = MaterialTheme.typography.bodySmall
        )

        if (!enabled) {
            Button(onClick = { enabledSetting.setValue(true) }) {
                Text(stringResource(R.string.action_gif_enable))
            }
        } else if (missingKey) {
            Text(
                stringResource(R.string.action_gif_missing_key, provider.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        OutlinedButton(onClick = {
            SettingsActivity.openToNavDest(context, "actions/gif")
        }) {
            Text(stringResource(R.string.action_gif_open_settings))
        }
    }
}

@Composable
private fun GifSearchPanel(
    manager: KeyboardManagerForAction,
    provider: GifProviderKind,
    apiKey: String,
    searchText: MutableState<String>,
    searching: MutableState<Boolean>
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val query = searchText.value

    val libraryJson = useDataStoreValue(GifLibraryKey)
    val library = remember(libraryJson) { GifLibraryCodec.decode(libraryJson) }
    val recentGifs = remember(library) {
        library.sortedByDescending { it.lastUsed }.map { it.toGifItem() }
    }
    val savedGifs = remember(library) {
        library.filter { it.favorite }
            .sortedByDescending { it.lastUsed }
            .map { it.toGifItem() }
    }
    val favoriteKeys = remember(library) {
        library.filter { it.favorite }.map { it.provider to it.id }.toSet()
    }

    var mode by remember { mutableStateOf(GifMode.Trending) }
    var networkGifs by remember { mutableStateOf<List<GifItem>>(emptyList()) }
    var next by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var downloadingKey by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var autoLoadCursor by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyStaggeredGridState()

    val networkMode = query.isNotBlank() || mode == GifMode.Trending
    val displayGifs = when {
        networkMode -> networkGifs
        mode == GifMode.Recent -> recentGifs
        else -> savedGifs
    }

    LaunchedEffect(query, provider, apiKey, mode) {
        gridState.scrollToItem(0)
    }

    LaunchedEffect(provider, apiKey, query, reload, mode) {
        if (!networkMode) {
            loading = false
            error = null
            return@LaunchedEffect
        }

        if (query.isNotBlank()) delay(SEARCH_DEBOUNCE_MS)
        generation += 1
        val myGeneration = generation
        autoLoadCursor = null
        loading = true
        error = null

        when (val result = fetchGifPage(provider, apiKey, query, null)) {
            is GifResult.Success -> if (myGeneration == generation) {
                networkGifs = result.page.items.distinctBy { it.fullUrl }
                next = result.page.next
            }
            is GifResult.Error -> if (myGeneration == generation) {
                networkGifs = emptyList()
                next = null
                error = result.message
            }
        }

        if (myGeneration == generation) loading = false
    }

    fun loadMore() {
        if (!networkMode) return
        val cursor = next ?: return
        if (loading || loadingMore) return
        val myGeneration = generation
        loadingMore = true
        error = null
        scope.launch {
            when (val result = fetchGifPage(provider, apiKey, query, cursor)) {
                is GifResult.Success -> if (myGeneration == generation) {
                    networkGifs = (networkGifs + result.page.items).distinctBy { it.fullUrl }
                    next = result.page.next
                    error = null
                }
                is GifResult.Error -> if (myGeneration == generation) {
                    error = result.message
                }
            }
            loadingMore = false
        }
    }

    // Load the next page before the user reaches the end, like Gboard's endless grid.
    LaunchedEffect(gridState, networkGifs.size, next, error, networkMode) {
        if (!networkMode) return@LaunchedEffect

        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val cursor = next ?: return@collect
                if (loadingMore || error != null) return@collect
                if (lastVisible >= networkGifs.size - 6 && autoLoadCursor != cursor) {
                    autoLoadCursor = cursor
                    loadMore()
                }
            }
    }

    fun providerFor(item: GifItem): GifProviderKind =
        GifProviderKind.fromId(item.provider)

    fun insert(item: GifItem) {
        if (downloadingKey != null) return
        downloadingKey = item.fullUrl
        scope.launch {
            val itemProvider = providerFor(item)
            val file = GifCache.favoriteFileOrNull(context, item)
                ?: GifCache.download(context, item)

            if (file == null) {
                downloadingKey = null
                Toast.makeText(
                    context,
                    R.string.action_gif_download_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            if (favoriteKeys.contains(itemProvider.id to item.id) &&
                GifCache.favoriteFileOrNull(context, item) == null
            ) {
                // The favorite was saved before its bytes finished downloading; persist them
                // now so the next use works offline.
                GifCache.saveFavorite(context, item)
            }

            GifLibrary.recordUse(context, item, itemProvider)
            GifCache.prune(context)
            // Keep the URI alive long enough for the share-sheet fallback and its upload.
            val uri = GifCache.registerForInsertion(
                file,
                lifetimeMillis = 30L * 60L * 1000L
            )
            manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)

            val ignoreOverride = searching.value
            val latinIME = manager.getLatinIMEForDebug()
            val editorInfo = if (ignoreOverride) {
                latinIME.getBaseInputEditorInfo()
            } else {
                latinIME.currentInputEditorInfo
            }

            Log.d(
                "GifAction",
                "pkg=${editorInfo?.packageName} mimes=${editorInfo?.contentMimeTypes?.joinToString()}"
            )

            val supportsImageInsertion = try {
                manager.appSupportsImageInsertion("image/gif", ignoreOverride)
            } catch (_: Exception) {
                false
            }

            var inserted = false
            if (supportsImageInsertion) {
                inserted = try {
                    manager.typeUri(
                        uri,
                        // Gboard commits a single MIME type selected from the editor's
                        // supported list; don't advertise a wildcard alongside it.
                        listOf("image/gif"),
                        ignoreConnectionOverride = ignoreOverride,
                        showFailureToast = false
                    )
                } catch (_: Exception) {
                    false
                }
            }

            Log.d(
                "GifAction",
                "supportsImageInsertion=$supportsImageInsertion commitContent=$inserted searching=$ignoreOverride"
            )

            downloadingKey = null
            if (inserted) {
                manager.closeActionWindow()
                return@launch
            }

            // Gboard's fallback is not a clipboard paste: it sends the image URI through a
            // share intent so the target app (e.g. Google Chat) can attach it itself. This
            // works even when the editor advertises no image insertion support.
            shareGif(context, uri, item.title)
            manager.closeActionWindow()
        }
    }

    fun copyGif(item: GifItem) {
        if (downloadingKey != null) return
        downloadingKey = item.fullUrl
        scope.launch {
            val file = GifCache.favoriteFileOrNull(context, item)
                ?: GifCache.download(context, item)

            if (file == null) {
                downloadingKey = null
                Toast.makeText(
                    context,
                    R.string.action_gif_download_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val uri = GifCache.registerForInsertion(
                file,
                lifetimeMillis = 30L * 60L * 1000L
            )
            downloadingKey = null
            val copied = GifCache.copyToClipboard(context, uri)
            Toast.makeText(
                context,
                if (copied) R.string.action_gif_copied else R.string.action_gif_copy_failed,
                Toast.LENGTH_SHORT
            ).show()
            if (copied) manager.closeActionWindow()
        }
    }

    fun showOptions(item: GifItem) {
        val itemProvider = providerFor(item)
        val isFavorite = favoriteKeys.contains(itemProvider.id to item.id)

        manager.requestDialog(
            context.getString(R.string.action_gif_options),
            listOf(
                DialogRequestItem(
                    context.getString(
                        if (isFavorite) R.string.action_gif_unsave
                        else R.string.action_gif_save
                    )
                ) {
                    scope.launch {
                        val favorited = GifLibrary.toggleFavorite(
                            context,
                            item,
                            itemProvider
                        )
                        Toast.makeText(
                            context,
                            if (favorited) R.string.action_gif_favorite_added
                            else R.string.action_gif_favorite_removed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                DialogRequestItem(context.getString(R.string.action_gif_copy)) {
                    copyGif(item)
                },
                DialogRequestItem(context.getString(R.string.action_gif_cancel)) { }
            ),
            { }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (query.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GifMode.entries.forEach { candidate ->
                    FilterChip(
                        selected = query.isBlank() && mode == candidate,
                        onClick = {
                            mode = candidate
                            searchText.value = ""
                            searching.value = false
                        },
                        label = { Text(stringResource(candidate.label)) }
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1.0f)
        ) {
            when {
                displayGifs.isEmpty() && networkMode && loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center)
                )

                displayGifs.isEmpty() && networkMode && error != null -> GifErrorState(
                    message = error!!,
                    onRetry = { reload += 1 },
                    modifier = Modifier.align(Alignment.Center)
                )

                displayGifs.isEmpty() && mode == GifMode.Recent && query.isBlank() -> Text(
                    stringResource(R.string.action_gif_no_recent),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )

                displayGifs.isEmpty() && mode == GifMode.Saved && query.isBlank() -> Text(
                    stringResource(R.string.action_gif_no_saved),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )

                displayGifs.isEmpty() -> Text(
                    stringResource(R.string.action_gif_no_results),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium
                )

                else -> LazyVerticalStaggeredGrid(
                    columns = if (query.isNotBlank()) {
                        StaggeredGridCells.Fixed(3)
                    } else {
                        StaggeredGridCells.Adaptive(150.dp)
                    },
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalItemSpacing = 4.dp
                ) {
                    items(displayGifs, key = { it.fullUrl }) { item ->
                        val ratio = if (item.width > 0 && item.height > 0) {
                            (item.width.toFloat() / item.height.toFloat())
                                .coerceIn(0.55f, 1.8f)
                        } else {
                            1.0f
                        }

                        GifCell(
                            item = item,
                            downloading = downloadingKey == item.fullUrl,
                            favorite = favoriteKeys.contains(providerFor(item).id to item.id),
                            aspectRatio = ratio,
                            onClick = { insert(item) },
                            onLongClick = { showOptions(item) }
                        )
                    }

                    if (networkMode) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    loadingMore -> CircularProgressIndicator(
                                        Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )

                                    next != null -> TextButton(onClick = { loadMore() }) {
                                        Text(stringResource(R.string.action_gif_load_more))
                                    }

                                    else -> Text(
                                        stringResource(R.string.action_gif_no_more_results),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalKeyboardScheme.current.onKeyboardContainer
                                            .copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        GifStatusRow(
            busy = networkMode && (loading || loadingMore),
            error = if (networkMode && displayGifs.isNotEmpty()) error else null
        )
    }
}

@Composable
private fun GifErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.action_gif_error, message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_gif_retry))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GifCell(
    item: GifItem,
    downloading: Boolean,
    favorite: Boolean,
    aspectRatio: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val previewBytes by produceState<ByteArray?>(initialValue = null, key1 = item.previewUrl) {
        value = GifPreviewLoader.load(item.previewUrl)
    }
    val description = stringResource(R.string.action_gif_image_description, item.title)

    Box(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .background(LocalKeyboardScheme.current.keyboardContainer)
            .combinedClickable(
                enabled = !downloading,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        val bytes = previewBytes
        if (bytes != null) {
            AndroidView(
                factory = { context -> GifMovieView(context) },
                update = { view -> view.setGif(bytes) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }

        if (favorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("★", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp)
            }
        }

        if (downloading) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun GifStatusRow(
    busy: Boolean,
    error: String?
) {
    if (!busy && error == null) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
        }
    }
}

private fun shareGif(context: Context, uri: Uri, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/gif"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(
            context.contentResolver,
            title.ifBlank { "GIF" },
            uri
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(
        send,
        context.getString(R.string.action_gif_share)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            R.string.action_gif_insert_failed,
            Toast.LENGTH_SHORT
        ).show()
    }
}

val GifAction = Action(
    icon = R.drawable.gif,
    name = R.string.action_gif_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    windowImpl = { manager, _ -> GifActionWindow(manager) },
    settingsMenu = GifSettingsMenu
)
