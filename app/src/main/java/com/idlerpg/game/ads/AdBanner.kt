package com.idlerpg.game.ads

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.idlerpg.game.R

/** Google's public test banner unit: debug builds must never request real ads. */
private const val TEST_BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"

/**
 * Adaptive banner above the menu bar. Takes no space until an ad is loaded (so the game is never
 * squeezed by an empty strip) and nothing at all without consent or network.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val canRequest by AdConsent.canRequestAds.collectAsStateWithLifecycle()
    if (!canRequest) return
    val context = LocalContext.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val unitId = if (debuggable) TEST_BANNER_UNIT else context.getString(R.string.admob_banner_unit_id)
    var loaded by remember { mutableStateOf(false) }

    val adView = remember(widthDp, unitId) {
        runCatching {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                adUnitId = unitId
                adListener = object : AdListener() {
                    override fun onAdLoaded() { loaded = true }
                    override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
                }
                loadAd(AdRequest.Builder().build())
            }
        }.getOrNull()
    }
    DisposableEffect(adView) { onDispose { adView?.destroy() } }

    if (adView != null) {
        AndroidView(
            factory = { adView },
            modifier = modifier.fillMaxWidth().then(if (loaded) Modifier else Modifier.height0()),
        )
    }
}

private fun Modifier.height0(): Modifier = this.then(Modifier.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, 0) { }
})
