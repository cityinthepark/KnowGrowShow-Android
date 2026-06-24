package com.stoneofhope.stoneofhope.ui.welcome

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class WelcomeSlide(
    val title: String,
    val body: String
)

private val slides = listOf(
    WelcomeSlide(
        title = "Welcome",
        body = "Read Scripture, take notes, and grow in one place — on your device first."
    ),
    WelcomeSlide(
        title = "Notes & prayer",
        body = "Capture thoughts, prayers, flashcards, and moments tied to God's Word."
    ),
    WelcomeSlide(
        title = "Sermons & rhythm",
        body = "Follow series, log sermon notes, and keep a gentle daily rhythm."
    )
)

private object LegalUrls {
    const val Privacy = "https://stoneofhope.app/privacy.html"
    const val Terms = "https://stoneofhope.app/terms.html"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    onContinueLocal: () -> Unit,
    onContinueRegister: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { slides.size + 1 })
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            if (page < slides.size) {
                IntroSlide(slide = slides[page])
            } else {
                SyncChoiceSlide(
                    onContinueLocal = onContinueLocal,
                    onContinueRegister = onContinueRegister,
                    onOpenPrivacy = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalUrls.Privacy)))
                    },
                    onOpenTerms = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LegalUrls.Terms)))
                    }
                )
            }
        }

        if (pagerState.currentPage < slides.size) {
            Text(
                text = "Swipe to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun IntroSlide(slide: WelcomeSlide) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = slide.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = slide.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SyncChoiceSlide(
    onContinueLocal: () -> Unit,
    onContinueRegister: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose an option",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onContinueRegister,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register to share data across devices")
        }

        Text(
            text = "or",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        OutlinedButton(
            onClick = onContinueLocal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use on this device only")
        }

        Spacer(modifier = Modifier.height(24.dp))

        LegalConsentFooter(
            onOpenPrivacy = onOpenPrivacy,
            onOpenTerms = onOpenTerms
        )
    }
}

@Composable
private fun LegalConsentFooter(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "By continuing, you agree to our",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onOpenTerms) {
                Text("Terms of Use")
            }
            TextButton(onClick = onOpenPrivacy) {
                Text("Privacy Policy")
            }
        }
    }
}
