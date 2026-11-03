package org.futo.inputmethod.latin.uix.actions.gif

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
    val gridState = rememberLazyGridState()

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
            val uri = GifCache.registerForInsertion(file)
            manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)

            val inserted = try {
                manager.typeUri(
                    uri,
                    listOf("image/gif"),
                    showFailureToast = false
                )
            } catch (_: Exception) {
                false
            }

            if (inserted) {
                downloadingKey = null
                manager.closeActionWindow()
                return@launch
            }

            // Some editors reject commitContent; fall back to the clipboard so the GIF can
            // still be pasted into the app.
            val copied = GifCache.copyToClipboard(context, uri)
            downloadingKey = null
            if (copied) {
                Toast.makeText(
                    context,
                    R.string.action_gif_clipboard_fallback,
                    Toast.LENGTH_SHORT
                ).show()
                manager.closeActionWindow()
            } else {
                Toast.makeText(
                    context,
                    R.string.action_gif_insert_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
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

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayGifs, key = { it.fullUrl }) { item ->
                        GifCell(
                            item = item,
                            downloading = downloadingKey == item.fullUrl,
                            favorite = favoriteKeys.contains(providerFor(item).id to item.id),
                            onClick = { insert(item) },
                            onLongClick = { showOptions(item) }
                        )
                    }

                    if (networkMode) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
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

        GifAttributionRow(
            provider = provider,
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val previewBytes by produceState<ByteArray?>(initialValue = null, key1 = item.previewUrl) {
        value = GifPreviewLoader.load(item.previewUrl)
    }
    val description = stringResource(R.string.action_gif_image_description, item.title)

    Box(
        modifier = Modifier
            .aspectRatio(1.0f)
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
private fun GifAttributionRow(
    provider: GifProviderKind,
    busy: Boolean,
    error: String?
) {
    val context = LocalContext.current
    val providerUrl = if (provider == GifProviderKind.Tenor) "https://tenor.com" else "https://giphy.com"

    Column(Modifier.fillMaxWidth()) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.action_gif_powered_by, provider.displayName),
                style = MaterialTheme.typography.labelSmall,
                color = LocalKeyboardScheme.current.onKeyboardContainer.copy(alpha = 0.7f),
                modifier = Modifier.clickable {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(providerUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        // Ignore if no browser is available.
                    }
                }
            )
            Spacer(Modifier.weight(1.0f))

            if (busy) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp)
            }
        }
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
