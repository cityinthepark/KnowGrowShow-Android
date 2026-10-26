package com.stoneofhope.stoneofhope.ui.welcome

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.stoneofhope.stoneofhope.R

private data class WelcomeSlide(@param:StringRes val title: Int, @param:StringRes val body: Int)

private val slides = listOf(
    WelcomeSlide(R.string.welcome_slide_1_title, R.string.welcome_slide_1_body),
    WelcomeSlide(R.string.welcome_slide_2_title, R.string.welcome_slide_2_body),
    WelcomeSlide(R.string.welcome_slide_3_title, R.string.welcome_slide_3_body),
)

private object LegalUrls {
    const val Privacy = "https://stoneofhope.app/privacy.html"
    const val Terms = "https://stoneofhope.app/terms.html"
}

/**
 * First-launch carousel (iOS `WelcomeOnboardingView`): three feature pages, then the choice
 * between cross-device sync and this device only. Shown until a choice is made.
 */
@Composable
fun WelcomeScreen(
    onContinueLocal: () -> Unit,
    onContinueRegister: () -> Unit,
) {
    val pageCount = slides.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            if (page < slides.size) {
                IntroSlide(slides[page])
            } else {
                SyncChoiceSlide(
                    onContinueLocal = onContinueLocal,
                    onContinueRegister = onContinueRegister,
                    onOpenTerms = { open(LegalUrls.Terms) },
                    onOpenPrivacy = { open(LegalUrls.Privacy) },
                )
            }
        }
        PageDots(count = pageCount, current = pagerState.currentPage)
    }
}

@Composable
private fun IntroSlide(slide: WelcomeSlide) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(slide.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(slide.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.welcome_swipe_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncChoiceSlide(
    onContinueLocal: () -> Unit,
    onContinueRegister: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.welcome_sync_choice_headline),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onContinueRegister, modifier = Modifier.fillMaxWidth().testTag("welcomeRegister")) {
            Text(stringResource(R.string.welcome_sync_choice_register), textAlign = TextAlign.Center)
        }
        Text(
            text = stringResource(R.string.welcome_sync_choice_or),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        OutlinedButton(onClick = onContinueLocal, modifier = Modifier.fillMaxWidth().testTag("welcomeLocal")) {
            Text(stringResource(R.string.welcome_sync_choice_local), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.legal_consent_prefix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.terms_of_use)) }
            Text(
                text = stringResource(R.string.legal_consent_middle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.privacy_policy)) }
        }
    }
}

/** Page indicator, like the iOS `TabView` `.page(indexDisplayMode: .always)` dots. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        color = if (index == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
            )
        }
    }
}
