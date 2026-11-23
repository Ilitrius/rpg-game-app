package com.idlerpg.game.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consent (Google UMP) and one-time AdMob initialisation. Fail-safe: any error simply means "no ads";
 * the game never depends on this. Ads are requested only after [canRequestAds] became true.
 */
object AdConsent {
    private val initialized = AtomicBoolean(false)
    private val _canRequestAds = MutableStateFlow(false)
    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True once consent allows ad requests (or none is required) and the SDK is initialised. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    /** True if the user may reopen the consent form (shown as button in Optionen). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Call from `Activity.onCreate`: updates consent info, shows the form if needed, then starts ads. */
    fun gather(activity: Activity) {
        runCatching {
            val info = UserMessagingPlatform.getConsentInformation(activity)
            val params = ConsentRequestParameters.Builder().build()
            // Ads may already be allowed from an earlier session: start without waiting for the network.
            applyState(activity, info)
            info.requestConsentInfoUpdate(
                activity,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { applyState(activity, info) }
                },
                { applyState(activity, info) },
            )
        }
    }

    /** Reopens the privacy options form (EU/UK users can change their choice). */
    fun showPrivacyOptions(activity: Activity) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                applyState(activity, UserMessagingPlatform.getConsentInformation(activity))
            }
        }
    }

    private fun applyState(context: Context, info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds() && initialized.compareAndSet(false, true)) {
            runCatching { MobileAds.initialize(context.applicationContext) {} }
        }
        _canRequestAds.value = info.canRequestAds()
    }
}
