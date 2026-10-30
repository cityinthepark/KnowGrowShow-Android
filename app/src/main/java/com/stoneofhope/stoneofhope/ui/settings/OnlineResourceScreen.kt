package com.stoneofhope.stoneofhope.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stoneofhope.stoneofhope.R
import com.stoneofhope.stoneofhope.data.church.ChurchProfile
import com.stoneofhope.stoneofhope.ui.notes.textStyle
import com.stoneofhope.stoneofhope.ui.theme.AppTheme
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * iOS `OnlineResourceDetailView`: name, logo, intro, one import button, the internet note, a
 * dimmed "Importing Sermons..." overlay and a result dialog. Also the church's website and YouTube
 * links, which iOS keeps in the profile but never shows (IOS_ISSUES M6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineResourceScreen(onBack: () -> Unit, viewModel: OnlineResourceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    // Leaving mid-import would cancel it; iOS's overlay blocks the page too.
    BackHandler(enabled = state.importing) {}

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("onlineResource")) {
            TopAppBar(
                title = { Text(stringResource(R.string.online_resources)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.importing) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets(0),
            )
            val profile = state.profile
            if (profile == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(profile.name, style = textStyle(12, weight = FontWeight.SemiBold), color = c.neutral100)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = c.surface0),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ChurchLogo(profile)
                            profile.intro?.let { Text(it, style = textStyle(17), color = c.neutral100) }
                            Button(
                                onClick = viewModel::import,
                                enabled = !state.importing,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("onlineImport"),
                            ) {
                                Text(
                                    stringResource(if (state.imported) R.string.online_import_updates else R.string.online_import_all),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                            Text(
                                stringResource(R.string.online_needs_internet),
                                style = textStyle(13),
                                color = c.neutral90,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }
                    }
                    if (profile.churchWebsite != null || profile.youtubeHomeLink != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            profile.churchWebsite?.let { url ->
                                OutlinedButton(onClick = { open(url) }, modifier = Modifier.weight(1f).testTag("onlineWebsite")) {
                                    Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.online_website), Modifier.padding(start = 8.dp))
                                }
                            }
                            profile.youtubeHomeLink?.let { url ->
                                OutlinedButton(onClick = { open(url) }, modifier = Modifier.weight(1f).testTag("onlineYouTube")) {
                                    Icon(Icons.Outlined.PlayCircle, null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.online_youtube), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.importing) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                    // Swallows taps so nothing behind the overlay reacts.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .testTag("onlineImporting"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(12.dp), shadowElevation = 8.dp, color = c.surface0) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.online_importing), color = c.neutral100)
                    }
                }
            }
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val text = message.counts?.let { (series, sermons) ->
                        stringResource(
                            message.text,
                            pluralStringResource(R.plurals.online_series_count, series, series),
                            pluralStringResource(R.plurals.online_sermon_count, sermons, sermons),
                        )
                    } ?: stringResource(message.text)
                    Text(text, modifier = Modifier.testTag("onlineImportMessage"))
                    message.retryHint?.let { Text(stringResource(it), style = textStyle(14), color = c.neutral90) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** Logos shipped with the app, by the profile's `logoImageName` (iOS asset names). */
@DrawableRes
private fun bundledLogo(name: String?): Int? = when (name) {
    "riversideLogo" -> R.drawable.riverside_logo
    else -> null
}

/** The bundled logo, else `logoURL` (downloaded once per run, no image library). */
@Composable
private fun ChurchLogo(profile: ChurchProfile) {
    val modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
    val bundled = bundledLogo(profile.logoImageName)
    if (bundled != null) {
        Image(painterResource(bundled), contentDescription = null, contentScale = ContentScale.FillWidth, modifier = modifier)
        return
    }
    val url = profile.logoURL ?: return
    val bitmap by produceState(RemoteLogos.cache[url], url) { if (value == null) value = RemoteLogos.load(url) }
    bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = modifier) }
}

private object RemoteLogos {
    val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also { cache[url] = it }
    }
}
