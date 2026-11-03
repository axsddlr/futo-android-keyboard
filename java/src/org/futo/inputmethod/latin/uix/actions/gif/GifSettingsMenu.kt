package org.futo.inputmethod.latin.uix.actions.gif

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.setSetting

private fun providerApiKeySetting(provider: GifProviderKind) = when (provider) {
    GifProviderKind.Tenor -> GifSearchSettings.TenorApiKey
    GifProviderKind.Giphy -> GifSearchSettings.GiphyApiKey
}

@Composable
private fun GifProviderSelector() {
    val (providerId, setProvider) = useDataStore(GifSearchSettings.Provider)

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        GifProviderKind.entries.forEach { provider ->
            FilterChip(
                selected = GifProviderKind.fromId(providerId) == provider,
                onClick = { setProvider(provider.id) },
                label = { Text(provider.displayName) }
            )
        }
    }
}

@Composable
private fun GifApiKeyEditor() {
    val context = LocalContext.current
    val providerId = useDataStoreValue(GifSearchSettings.Provider)
    val provider = GifProviderKind.fromId(providerId)
    val setting = providerApiKeySetting(provider)
    val storedKey = useDataStoreValue(setting)
    val keyText = remember(provider, storedKey) { mutableStateOf(storedKey) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        SettingsTextEdit(
            text = keyText,
            placeholder = stringResource(
                if (provider == GifProviderKind.Tenor)
                    R.string.gif_settings_api_key_tenor
                else
                    R.string.gif_settings_api_key_giphy
            ),
            autocorrect = false
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                keyText.value = keyText.value.trim()
                scope.launch {
                    context.setSetting(setting, keyText.value)
                    Toast.makeText(context, R.string.gif_settings_saved, Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(stringResource(R.string.gif_settings_save))
            }

            TextButton(onClick = {
                val url = if (provider == GifProviderKind.Tenor) {
                    "https://developers.google.com/tenor/guides/quickstart"
                } else {
                    "https://developers.giphy.com/dashboard/"
                }
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    // No browser is available; the help text still explains where to get a key.
                }
            }) {
                Text(stringResource(R.string.gif_settings_get_key))
            }
        }

        Text(
            stringResource(
                if (provider == GifProviderKind.Tenor)
                    R.string.gif_settings_tenor_key_help
                else
                    R.string.gif_settings_giphy_key_help
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GifClearCacheSetting() {
    val context = LocalContext.current

    TextButton(onClick = {
        GifCache.clear(context)
        Toast.makeText(context, R.string.gif_settings_cache_cleared, Toast.LENGTH_SHORT).show()
    }) {
        Text(stringResource(R.string.gif_settings_clear_cache))
    }
}

@Composable
private fun GifClearLibrarySetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    TextButton(onClick = {
        scope.launch {
            GifLibrary.clear(context)
            Toast.makeText(context, R.string.gif_settings_library_cleared, Toast.LENGTH_SHORT).show()
        }
    }) {
        Text(stringResource(R.string.gif_settings_clear_library))
    }
}

val GifSettingsMenu = UserSettingsMenu(
    title = R.string.gif_settings_title,
    navPath = "actions/gif",
    registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.gif_settings_enable,
            setting = GifSearchSettings.Enabled,
            subtitle = R.string.gif_settings_enable_subtitle
        ).copy(searchTags = R.string.gif_settings_search_tags),

        UserSetting(
            name = R.string.gif_settings_provider,
            subtitle = R.string.gif_settings_provider_subtitle,
        ) {
            GifProviderSelector()
        },

        UserSetting(
            name = R.string.gif_settings_api_key,
        ) {
            GifApiKeyEditor()
        },

        UserSetting(
            name = R.string.gif_settings_clear_cache,
        ) {
            GifClearCacheSetting()
        },

        UserSetting(
            name = R.string.gif_settings_clear_library,
        ) {
            GifClearLibrarySetting()
        },

        userSettingDecorationOnly {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.gif_settings_privacy_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                stringResource(R.string.gif_settings_privacy_text),
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
)
