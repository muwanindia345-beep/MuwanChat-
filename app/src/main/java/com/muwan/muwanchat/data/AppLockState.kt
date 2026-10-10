package com.muwan.muwanchat.data

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory "is the app currently locked?" flag that MainActivity draws the
 * unlock screen from.
 *
 * Locks when:
 *  - the process starts fresh (cold start / process death) and a lock is set
 *  - the app was in the background for at least GRACE_MS
 *
 * Rotation does NOT lock. GRACE_MS exists so that system pickers (gallery,
 * camera, permission dialogs) don't lock the app the moment you come back from
 * them. A configurable auto-lock timer comes in a later step.
 */
object AppLockState {

    private const val GRACE_MS = 30_000L

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    // True while the in-progress call screen is the visible destination. The unlock
    // gate steps aside then, so a locked app can still answer / run a call. It does
    // NOT unlock the app: `locked` stays true and the gate returns the moment the
    // user leaves the call screen (back / call ended).
    private val _callUiVisible = MutableStateFlow(false)
    val callUiVisible: StateFlow<Boolean> = _callUiVisible.asStateFlow()

    fun setCallUiVisible(visible: Boolean) {
        _callUiVisible.value = visible
    }

    private var initialised = false
    private var stoppedAt = 0L

    private fun shouldLock(context: Context): Boolean =
        AppLockStore.isLockEnabled(context) &&
            AuthDataStore.getTokenBlocking(context).isNotEmpty()

    /** Call from MainActivity.onCreate. Only the first call per process locks. */
    fun onActivityCreated(context: Context) {
        if (initialised) return
        initialised = true
        if (shouldLock(context)) _locked.value = true
    }

    fun onActivityStopped(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onActivityStarted(context: Context) {
        if (stoppedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - stoppedAt
        stoppedAt = 0L
        if (away >= GRACE_MS && shouldLock(context)) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }
}
