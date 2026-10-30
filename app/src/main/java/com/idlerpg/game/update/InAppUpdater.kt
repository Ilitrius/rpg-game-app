package com.idlerpg.game.update

import android.app.Activity
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the UI should offer for the Play in-app update. */
enum class UpdateUiState {
    /** Nothing to show (no update, check failed, no Play Store, or the user declined). */
    IDLE,

    /** A newer version is available: offer "Update". */
    AVAILABLE,

    /** The flexible update is downloading in the background (no UI). */
    DOWNLOADING,

    /** Downloaded: offer "Restart" to install. */
    READY,
}

/**
 * Play In-App Updates (flexible flow). Checks while the app is visible (at most every
 * [CHECK_INTERVAL_MILLIS]), never blocks the game and is completely fail-safe: without Play
 * services / Play Store (sideload, emulator, offline) every call fails silently and the state
 * stays [UpdateUiState.IDLE]. Only the app's own update status is queried from Google Play; no
 * personal data is sent by the app.
 *
 * Must be created while the activity is initialising (it registers an activity-result launcher).
 */
class InAppUpdater(private val activity: ComponentActivity) : DefaultLifecycleObserver {

    // Lazy: the activity has no base context yet while it is being constructed; first used in onStart.
    private val manager: AppUpdateManager? by lazy { runCatching { AppUpdateManagerFactory.create(activity) }.getOrNull() }
    private val _state = MutableStateFlow(UpdateUiState.IDLE)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var info: AppUpdateInfo? = null
    private var lastCheckAt = 0L
    private var declined = false

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (_state.value != UpdateUiState.READY) _state.value = UpdateUiState.DOWNLOADING
        } else {
            declined = true
            _state.value = UpdateUiState.IDLE
        }
    }

    private val listener = InstallStateUpdatedListener { st ->
        when (st.installStatus()) {
            InstallStatus.DOWNLOADED -> _state.value = UpdateUiState.READY
            InstallStatus.PENDING, InstallStatus.DOWNLOADING -> _state.value = UpdateUiState.DOWNLOADING
            InstallStatus.FAILED, InstallStatus.CANCELED -> _state.value = UpdateUiState.IDLE
            else -> Unit
        }
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val m = manager ?: return
        runCatching { m.registerListener(listener) }
        check(m)
    }

    override fun onStop(owner: LifecycleOwner) {
        val m = manager ?: return
        runCatching { m.unregisterListener(listener) }
    }

    private fun check(m: AppUpdateManager) {
        val now = SystemClock.elapsedRealtime()
        val ready = _state.value == UpdateUiState.READY
        // A downloaded update is re-checked on every start (the user may have ignored the prompt).
        if (!ready && lastCheckAt != 0L && now - lastCheckAt < CHECK_INTERVAL_MILLIS) return
        lastCheckAt = now
        runCatching {
            m.appUpdateInfo
                .addOnSuccessListener { i -> runCatching { onInfo(i) } }
                .addOnFailureListener { /* no Play / offline: stay silent */ }
        }
    }

    private fun onInfo(i: AppUpdateInfo) {
        when {
            i.installStatus() == InstallStatus.DOWNLOADED -> _state.value = UpdateUiState.READY
            i.installStatus() == InstallStatus.DOWNLOADING || i.installStatus() == InstallStatus.PENDING ->
                _state.value = UpdateUiState.DOWNLOADING
            i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                i.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                info = i
                if (!declined && _state.value == UpdateUiState.IDLE) _state.value = UpdateUiState.AVAILABLE
            }
            else -> Unit
        }
    }

    /** User tapped "Update": starts the flexible download via the Play dialog. */
    fun startUpdate() {
        val m = manager
        val i = info
        if (m == null || i == null) {
            _state.value = UpdateUiState.IDLE
            return
        }
        runCatching {
            m.startUpdateFlowForResult(i, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
        }.onFailure { _state.value = UpdateUiState.IDLE }
    }

    /** User tapped "Restart": installs the downloaded update (the app restarts). */
    fun completeUpdate() {
        runCatching { manager?.completeUpdate() }
    }

    /** User dismissed the hint; not offered again until the next app start. */
    fun dismiss() {
        declined = true
        if (_state.value == UpdateUiState.AVAILABLE) _state.value = UpdateUiState.IDLE
    }

    private companion object {
        const val CHECK_INTERVAL_MILLIS = 6L * 60 * 60 * 1000
    }
}
