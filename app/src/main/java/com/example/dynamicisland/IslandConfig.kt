@file:Suppress("DEPRECATION")

package com.example.dynamicisland

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaController
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.milliseconds

enum class IslandState {
    IDLE, MUSIC, TIMER, STOPWATCH, RECORDING, SOUND_MODE, EARBUDS,
    LOCK, BATTERY, CALL, NAVIGATION, OTP, TORCH, DOWNLOAD, DND, NOTIFICATION
}

/**
 * Explicit 3-Stage Architecture:
 * - STAGE_1_CAMERA_CIRCLE: Tiny circle hidden behind the front camera cutout when truly idle
 * - STAGE_2_CAPSULE: Compact capsule island (+ optional detached split circle when 2+ tasks run)
 * - STAGE_3_EXPANDED: Full interactive expanded card
 */
enum class IslandStage {
    STAGE_1_CAMERA_CIRCLE,
    STAGE_2_CAPSULE,
    STAGE_3_EXPANDED
}

enum class DevicePreset(
    val title: String,
    val xPx: Float,
    val yPx: Float,
    val widthDp: Float,
    val heightDp: Float,
    val activeWidthDp: Float,
    val expandedPercent: Float
) {
    CMF_PHONE_2_PRO("CMF Phone 2 Pro", 0f, 22f, 126f, 34f, 212f, 0.95f),
    REDMI_NOTE_10S("Redmi Note 10S", 0f, 18f, 120f, 33f, 206f, 0.94f),
    DEFAULT_CENTER("Auto Screen-Fit", 0f, 24f, 122f, 34f, 210f, 0.94f)
}

enum class MistThemePreset(
    val label: String,
    val primaryArgb: Long,
    val secondaryArgb: Long
) {
    EMERALD_BOTANICAL("Emerald Mist", 0xFF34D399L, 0xFF10B981L),
    CYBER_OCEAN("Cyber Cyan", 0xFF38BDF8L, 0xFF6366F1L),
    SUNSET_EMBER("Sunset Gold", 0xFFFBBF24L, 0xFFF97316L),
    COSMIC_VIOLET("Neon Violet", 0xFFA855F7L, 0xFFEC4899L),
    ROSE_QUARTZ("Crimson Rose", 0xFFF43F5EL, 0xFFFB7185L),
    FROSTED_ICE("Arctic Silver", 0xFFE2E8F0L, 0xFF64748BL)
}

enum class ChargingEffectStyle(
    val label: String,
    val subtitle: String
) {
    LIQUID_WAVE("Liquid Wave Fill", "Sloshing dual-sine liquid fill & rising bubbles"),
    NEON_PULSE("Neon Energy Pulse", "Shockwave energy rings radiating from camera cutout"),
    PARTICLE_FLOW("Quantum Particles", "Glowing energy particles streaming into battery core"),
    AURORA_GLOW("Aurora Liquid Morph", "Shifting emerald-cyan-violet aurora waves"),
    HYPER_TURBO("HyperCharge Warp", "Segmented neon plasma bar with dual lightning"),
    MINIMAL_RING("Classic Minimal Ring", "Clean Apple-style glowing ring & pill fill")
}

enum class MusicSliderStyle(
    val label: String,
    val subtitle: String
) {
    WAVEFORM_SEEK("Live Soundwave", "Sine-wave scrub bar that dances when music plays"),
    LIQUID_CAPSULE("Liquid Glass Tube", "Thick frosted glass capsule with sloshing liquid tip"),
    EQUALIZER_BARS("Beat Equalizer", "28 vertical spectrum bars synced to playback"),
    NEON_LASER("Cyber Neon Laser", "Glowing laser beam with pulsing diamond playhead"),
    DOT_MATRIX("Nothing Dot-Matrix", "Retro-futuristic LED dot matrix progress track"),
    CLASSIC_MINIMAL("Classic iOS Pill", "Clean minimal scrub bar with crisp white thumb")
}

object IslandConfig {
    private const val PREFS_NAME = "dynamic_island_prefs"
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transientJob: Job? = null
    private var stretchFeedbackJob: Job? = null
    private var torchDebounceJob: Job? = null

    val isAccessibilityServiceRunning = MutableStateFlow(false)
    val isNotificationServiceRunning = MutableStateFlow(false)
    val isBatteryOptimizationIgnored = MutableStateFlow(false)
    val detectedScreenWidthDp = MutableStateFlow(392f)
    val isLandscapeOrientation = MutableStateFlow(false)

    // =========================================================================
    // MASTER & INDIVIDUAL FEATURE ENABLE / DISABLE TOGGLES
    // =========================================================================
    val masterIslandEnabled = MutableStateFlow(true)
    val featureMusicEnabled = MutableStateFlow(true)
    val featureCallEnabled = MutableStateFlow(true)
    val featureNotificationEnabled = MutableStateFlow(true)
    val featureBatteryEnabled = MutableStateFlow(true)
    val featureNavigationEnabled = MutableStateFlow(true)
    val featureTimerStopwatchEnabled = MutableStateFlow(true)
    val featureSilentVibrateEnabled = MutableStateFlow(true)
    val featureEarbudsEnabled = MutableStateFlow(true)
    val featureOtpEnabled = MutableStateFlow(true)
    val featureTorchEnabled = MutableStateFlow(true)
    val featureDownloadEnabled = MutableStateFlow(true)
    val featureRecordingEnabled = MutableStateFlow(true)
    val featureDndEnabled = MutableStateFlow(true)
    val featureLockUnlockEnabled = MutableStateFlow(true)

    // =========================================================================
    // EXTRA PRO ENHANCEMENTS & FIX TOGGLES (ALL USER-TOGGLEABLE)
    // =========================================================================
    val ignoreNotificationFlashBlink = MutableStateFlow(true)
    val keepSilentVibrateWhenIdle = MutableStateFlow(true)
    val alwaysShowCallAcceptButton = MutableStateFlow(true)
    val reactiveMusicMistWave = MutableStateFlow(true)
    val musicWaveShakeIntensity = MutableStateFlow(1.15f)
    val chargingEffectStyleIndex = MutableStateFlow(0) // Default: LIQUID_WAVE
    val musicSliderStyleIndex = MutableStateFlow(0)    // Default: WAVEFORM_SEEK
    val chargingAutoExpandOnPlug = MutableStateFlow(true)
    val hapticFeedbackEnabled = MutableStateFlow(true)
    val autoCollapseOnAppOpen = MutableStateFlow(true)
    val hideInLandscapeEnabled = MutableStateFlow(false)
    val batteryLowAlertEnabled = MutableStateFlow(true)
    val edgeGlowPulseOnNotification = MutableStateFlow(true)

    // Granular Size, Cutout & Curvature Controls
    val xOffset = MutableStateFlow(0f)
    val yOffset = MutableStateFlow(24f)
    val compactWidthDp = MutableStateFlow(122f)
    val compactHeightDp = MutableStateFlow(34f)
    val activeCompactWidthDp = MutableStateFlow(210f)
    val splitCircleSizeDp = MutableStateFlow(34f)
    val splitGapDp = MutableStateFlow(6f)
        val dormantCircleSizeDp = MutableStateFlow(26f)

    // User-Requested Stage 2 & Stage 3 Dimension Sliders (-200 to +200 dp)
    val stage2WidthOffset = MutableStateFlow(0f)
    val stage2HeightOffset = MutableStateFlow(0f)
    val stage3WidthOffset = MutableStateFlow(0f)
    val stage3HeightOffset = MutableStateFlow(0f)
    val expandedWidthPercent = MutableStateFlow(0.94f)
    val expandedHeightScale = MutableStateFlow(1.05f)
    val compactCornerRadiusDp = MutableStateFlow(26f)
    val expandedCornerRadiusDp = MutableStateFlow(38f)
    val contentScale = MutableStateFlow(1.0f)
    val alwaysShowIdlePill = MutableStateFlow(true)
    val forceDormantPreview = MutableStateFlow(false)

    // Fluid Frosted Mist, Smoke & Glass Shader Controls
    val useFrostedGlassStyle = MutableStateFlow(true)
    val mistEffectEnabled = MutableStateFlow(true)
    val syncMistWithAlbumArt = MutableStateFlow(true)
    val mistThemePresetIndex = MutableStateFlow(0)
    val islandBgAlpha = MutableStateFlow(0.88f)
    val mistSmokeIntensity = MutableStateFlow(0.76f)
    val mistBlurRadiusDp = MutableStateFlow(28f)
    val mistAnimSpeed = MutableStateFlow(1.0f)
    val borderGlowAlpha = MutableStateFlow(0.48f)

    val extractedAccentColor = MutableStateFlow(0xFF34D399L)
    val extractedSecondaryColor = MutableStateFlow(0xFF10B981L)

    // Spring Physics, Stretch Gesture & Timeout Controls
    val animBounceLevel = MutableStateFlow(0.68f)
    val animSpeedMultiplier = MutableStateFlow(1.0f)
    val stretchMaxScale = MutableStateFlow(1.26f)
    val dormantTimeoutSeconds = MutableStateFlow(180)
    val notificationExpandDurationMs = MutableStateFlow(4500L)

    // Master State, 3-Stage Engine & Persistent Split-Pill
    val currentState = MutableStateFlow(IslandState.IDLE)
    val secondaryState = MutableStateFlow<IslandState?>(null)
    val isExpanded = MutableStateFlow(false)
    val currentStage = MutableStateFlow(IslandStage.STAGE_1_CAMERA_CIRCLE)
    val idleSecondsCounter = MutableStateFlow(0)
    val isDormantCircleMode = MutableStateFlow(true) // Stage 1 default when IDLE
    val stretchFeedbackText = MutableStateFlow("")
    private var isTransientStateActive = false
    private var userSwappedPrimary: IslandState? = null

    // Feature State Flows
    val isMediaPlaying = MutableStateFlow(false)
    val hasActiveMediaSession = MutableStateFlow(false)
    val musicTitle = MutableStateFlow("No Media")
    val musicArtist = MutableStateFlow("Unknown")
    val musicProgress = MutableStateFlow(0L)
    val musicDuration = MutableStateFlow(1L)
    val musicArtwork = MutableStateFlow<Bitmap?>(null)
    val isFavoriteTrack = MutableStateFlow(false)
    var activeMediaController: MediaController? = null
    var activeMediaPackage: String? = null

    val batteryLevel = MutableStateFlow(100)
    val isCharging = MutableStateFlow(false)
    val chargingWatts = MutableStateFlow("33W Fast")
    val batteryTempCelsius = MutableStateFlow("35.8°C")
    val batteryTimeRemaining = MutableStateFlow("24m to full")
    private var lastLowBatteryWarnLevel = 100

    val isTimerActive = MutableStateFlow(false)
    val isTimerPaused = MutableStateFlow(false)
    val isTimerRinging = MutableStateFlow(false)
    val timerTotalSeconds = MutableStateFlow(207)
    val timerRemainingSeconds = MutableStateFlow(207)
    val timerSubtitle = MutableStateFlow("Brewing Espresso...")
    var timerPauseActionIntent: PendingIntent? = null
    var timerResetActionIntent: PendingIntent? = null
    var timerOpenAppIntent: PendingIntent? = null
    var timerStopSoundIntent: PendingIntent? = null

    val isStopwatchActive = MutableStateFlow(false)
    val isStopwatchPaused = MutableStateFlow(false)
    val stopwatchElapsedSeconds = MutableStateFlow(0)
    val stopwatchLapCount = MutableStateFlow(1)
    val stopwatchSubtitle = MutableStateFlow("Stopwatch Running")
    var stopwatchPauseActionIntent: PendingIntent? = null
    var stopwatchResetActionIntent: PendingIntent? = null
    var stopwatchOpenAppIntent: PendingIntent? = null
    var stopwatchPauseIntent: PendingIntent?
        get() = stopwatchPauseActionIntent
        set(value) { stopwatchPauseActionIntent = value }
    var stopwatchResetIntent: PendingIntent?
        get() = stopwatchResetActionIntent
        set(value) { stopwatchResetActionIntent = value }
    var stopwatchLapIntent: PendingIntent? = null

    val isRecordingActive = MutableStateFlow(false)
    val recordingElapsedSeconds = MutableStateFlow(24)

    val soundModeText = MutableStateFlow("Ring")
    val isSilentMode = MutableStateFlow(false)
    val isVibrateMode = MutableStateFlow(false)

    val earbudsName = MutableStateFlow("Nothing Ear (2)")
    val earbudsBattery = MutableStateFlow(100)
    val isEarbudsConnected = MutableStateFlow(false)

    val isDeviceLocked = MutableStateFlow(false)

    val isCallActive = MutableStateFlow(false)
    val isCallIncoming = MutableStateFlow(true)
    val callDurationSeconds = MutableStateFlow(0)
    val callerName = MutableStateFlow("Rohan Verma")
    val callSubtitle = MutableStateFlow("Incoming HD Voice Call")
    val isSpeakerEnabled = MutableStateFlow(false)
    val isMicMuted = MutableStateFlow(false)
    var callAcceptIntent: PendingIntent? = null
    var callDeclineIntent: PendingIntent? = null
    var callContentIntent: PendingIntent? = null

    val isNavigationActive = MutableStateFlow(false)
    val navDistance = MutableStateFlow("90m")
    val navDirection = MutableStateFlow("Turn Right • MG Road")
    val navEta = MutableStateFlow("12 min • 4.2 km")
    var navOpenIntent: PendingIntent? = null

    val otpCode = MutableStateFlow("482910")
    val otpSender = MutableStateFlow("Bank Verification")

    val isTorchOn = MutableStateFlow(false)
    private var torchCameraId: String? = null
    @Volatile
    var lastNotificationOrCallTimestampMs: Long = 0L
    @Volatile
    private var manualTorchChangeTimestampMs: Long = 0L

    val isDownloadActive = MutableStateFlow(false)
    val downloadTitle = MutableStateFlow("NothingOS_Update.zip")
    val downloadAppName = MutableStateFlow("Chrome")
    val downloadProgress = MutableStateFlow(68)

    val isDndEnabled = MutableStateFlow(false)
    val dndModeLabel = MutableStateFlow("Focus / DND")

    val notificationAppName = MutableStateFlow("WhatsApp")
    val notificationTitle = MutableStateFlow("Aarav Sharma")
    val notificationText = MutableStateFlow("Bro, Dynamic Island ka 3-stage camera-expand UI, priority split-pill aur flashlight guard kamaal lag raha hai!")
    val notificationBody = notificationText
    val notificationSubtitle = notificationText
    val notificationSubText = MutableStateFlow("Just now")
    val notificationIcon = MutableStateFlow<Bitmap?>(null)
    val notificationActionLabel = MutableStateFlow("")
    var notificationActionIntent: PendingIntent? = null
    var notificationContentIntent: PendingIntent? = null
    var notificationOpenIntent: PendingIntent?
        get() = notificationContentIntent
        set(value) { notificationContentIntent = value }

    fun init(context: Context) {
        appContext = context.applicationContext
        updateDetectedScreenWidth(context)
        refreshBatteryOptimizationState(context)
        syncInitialRingerAndDndState(context)
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp

        // Load Master & Feature Toggles (Force masterIslandEnabled = true on init so island is never stuck off)
        masterIslandEnabled.value = sp.getBoolean("masterIslandEnabled", true)
        featureMusicEnabled.value = sp.getBoolean("featureMusicEnabled", true)
        featureCallEnabled.value = sp.getBoolean("featureCallEnabled", true)
        featureNotificationEnabled.value = sp.getBoolean("featureNotificationEnabled", true)
        featureBatteryEnabled.value = sp.getBoolean("featureBatteryEnabled", true)
        featureNavigationEnabled.value = sp.getBoolean("featureNavigationEnabled", true)
        featureTimerStopwatchEnabled.value = sp.getBoolean("featureTimerStopwatchEnabled", true)
        featureSilentVibrateEnabled.value = sp.getBoolean("featureSilentVibrateEnabled", true)
        featureEarbudsEnabled.value = sp.getBoolean("featureEarbudsEnabled", true)
        featureOtpEnabled.value = sp.getBoolean("featureOtpEnabled", true)
        featureTorchEnabled.value = sp.getBoolean("featureTorchEnabled", true)
        featureDownloadEnabled.value = sp.getBoolean("featureDownloadEnabled", true)
        featureRecordingEnabled.value = sp.getBoolean("featureRecordingEnabled", true)
        featureDndEnabled.value = sp.getBoolean("featureDndEnabled", true)
        featureLockUnlockEnabled.value = sp.getBoolean("featureLockUnlockEnabled", true)

        // Load Extra Pro Enhancements & Style Selections
        ignoreNotificationFlashBlink.value = sp.getBoolean("ignoreNotificationFlashBlink", true)
        keepSilentVibrateWhenIdle.value = sp.getBoolean("keepSilentVibrateWhenIdle", true)
        alwaysShowCallAcceptButton.value = sp.getBoolean("alwaysShowCallAcceptButton", true)
        reactiveMusicMistWave.value = sp.getBoolean("reactiveMusicMistWave", true)
        musicWaveShakeIntensity.value = sp.getFloat("musicWaveShakeIntensity", 1.15f).coerceIn(0.2f, 2.0f)
        chargingEffectStyleIndex.value = sp.getInt("chargingEffectStyleIndex", 0).coerceIn(0, ChargingEffectStyle.entries.lastIndex)
        musicSliderStyleIndex.value = sp.getInt("musicSliderStyleIndex", 0).coerceIn(0, MusicSliderStyle.entries.lastIndex)
        chargingAutoExpandOnPlug.value = sp.getBoolean("chargingAutoExpandOnPlug", true)
        hapticFeedbackEnabled.value = sp.getBoolean("hapticFeedbackEnabled", true)
        autoCollapseOnAppOpen.value = sp.getBoolean("autoCollapseOnAppOpen", true)
        hideInLandscapeEnabled.value = sp.getBoolean("hideInLandscapeEnabled", false)
        batteryLowAlertEnabled.value = sp.getBoolean("batteryLowAlertEnabled", true)
        edgeGlowPulseOnNotification.value = sp.getBoolean("edgeGlowPulseOnNotification", true)

        // Load Size & Cutout
        xOffset.value = sp.getFloat("xOffset", 0f)
        yOffset.value = sp.getFloat("yOffset", 24f)
        compactWidthDp.value = sp.getFloat("compactWidthDp", 122f).coerceIn(70f, 220f)
        compactHeightDp.value = sp.getFloat("compactHeightDp", 34f).coerceIn(22f, 56f)
        activeCompactWidthDp.value = sp.getFloat("activeCompactWidthDp", 210f).coerceIn(140f, 280f)
        splitCircleSizeDp.value = sp.getFloat("splitCircleSizeDp", 34f).coerceIn(24f, 52f)
        splitGapDp.value = sp.getFloat("splitGapDp", 6f).coerceIn(2f, 16f)
        dormantCircleSizeDp.value = sp.getFloat("dormantCircleSizeDp", 26f).coerceIn(16f, 44f)
        stage2WidthOffset.value = sp.getFloat("stage2WidthOffset", 0f).coerceIn(-200f, 200f)
        stage2HeightOffset.value = sp.getFloat("stage2HeightOffset", 0f).coerceIn(-200f, 200f)
        stage3WidthOffset.value = sp.getFloat("stage3WidthOffset", 0f).coerceIn(-200f, 200f)
        stage3HeightOffset.value = sp.getFloat("stage3HeightOffset", 0f).coerceIn(-200f, 200f)
        expandedWidthPercent.value = sp.getFloat("expandedWidthPercent", 0.94f).coerceIn(0.70f, 0.99f)
        expandedHeightScale.value = sp.getFloat("expandedHeightScale", 1.05f).coerceIn(0.80f, 1.40f)
        compactCornerRadiusDp.value = sp.getFloat("compactCornerRadiusDp", 26f).coerceIn(10f, 32f)
        expandedCornerRadiusDp.value = sp.getFloat("expandedCornerRadiusDp", 38f).coerceIn(18f, 52f)
        contentScale.value = sp.getFloat("contentScale", 1.0f).coerceIn(0.80f, 1.30f)
        alwaysShowIdlePill.value = sp.getBoolean("alwaysShowIdlePill", true)

        val frosted = sp.getBoolean("useFrostedGlassStyle", true)
        useFrostedGlassStyle.value = frosted
        mistEffectEnabled.value = frosted
        syncMistWithAlbumArt.value = sp.getBoolean("syncMistWithAlbumArt", true)
        mistThemePresetIndex.value = sp.getInt("mistThemePresetIndex", 0).coerceIn(0, MistThemePreset.entries.lastIndex)
        islandBgAlpha.value = sp.getFloat("islandBgAlpha", 0.88f).coerceIn(0.35f, 1.0f)
        mistSmokeIntensity.value = sp.getFloat("mistSmokeIntensity", 0.76f).coerceIn(0.0f, 1.0f)
        mistBlurRadiusDp.value = sp.getFloat("mistBlurRadiusDp", 28f).coerceIn(8f, 54f)
        mistAnimSpeed.value = sp.getFloat("mistAnimSpeed", 1.0f).coerceIn(0.2f, 2.5f)
        borderGlowAlpha.value = sp.getFloat("borderGlowAlpha", 0.48f).coerceIn(0.0f, 1.0f)

        animBounceLevel.value = sp.getFloat("animBounceLevel", 0.68f).coerceIn(0.40f, 1.0f)
        animSpeedMultiplier.value = sp.getFloat("animSpeedMultiplier", 1.0f).coerceIn(0.50f, 2.0f)
        stretchMaxScale.value = sp.getFloat("stretchMaxScale", 1.26f).coerceIn(1.08f, 1.42f)
        dormantTimeoutSeconds.value = sp.getInt("dormantTimeoutSeconds", 180).coerceIn(15, 300)
        notificationExpandDurationMs.value = sp.getLong("notificationExpandDurationMs", 4500L).coerceIn(2000L, 10000L)

        val preset = MistThemePreset.entries[mistThemePresetIndex.value]
        extractedAccentColor.value = preset.primaryArgb
        extractedSecondaryColor.value = preset.secondaryArgb

        restoreBaselineState()
    }

    fun syncInitialRingerAndDndState(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                when (am.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> {
                        soundModeText.value = "Silent"
                        isSilentMode.value = true
                        isVibrateMode.value = false
                    }
                    AudioManager.RINGER_MODE_VIBRATE -> {
                        soundModeText.value = "Vibrate"
                        isSilentMode.value = false
                        isVibrateMode.value = true
                    }
                    else -> {
                        soundModeText.value = "Ring"
                        isSilentMode.value = false
                        isVibrateMode.value = false
                    }
                }
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                val filterMode = nm.currentInterruptionFilter
                val dndOn = filterMode != NotificationManager.INTERRUPTION_FILTER_ALL &&
                        filterMode != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                isDndEnabled.value = dndOn
                if (dndOn) dndModeLabel.value = "Focus / DND"
            }
        } catch (_: Exception) {
        }
    }

    fun performHapticTick(context: Context? = appContext) {
        if (!hapticFeedbackEnabled.value) return
        val ctx = context ?: appContext ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(VibrationEffect.createOneShot(14L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vib?.vibrate(14L)
                }
            }
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // FEATURE TOGGLE SETTERS
    // =========================================================================
    private fun saveBool(key: String, flow: MutableStateFlow<Boolean>, enabled: Boolean) {
        flow.value = enabled
        prefs?.edit()?.putBoolean(key, enabled)?.apply()
        restoreBaselineState()
    }

    fun setMasterIslandEnabled(enabled: Boolean) {
        masterIslandEnabled.value = enabled
        prefs?.edit()?.putBoolean("masterIslandEnabled", enabled)?.apply()
        if (!enabled) isExpanded.value = false
        restoreBaselineState()
    }

    fun setFeatureMusicEnabled(enabled: Boolean) = saveBool("featureMusicEnabled", featureMusicEnabled, enabled)
    fun setFeatureCallEnabled(enabled: Boolean) = saveBool("featureCallEnabled", featureCallEnabled, enabled)
    fun setFeatureNotificationEnabled(enabled: Boolean) = saveBool("featureNotificationEnabled", featureNotificationEnabled, enabled)
    fun setFeatureBatteryEnabled(enabled: Boolean) = saveBool("featureBatteryEnabled", featureBatteryEnabled, enabled)
    fun setFeatureNavigationEnabled(enabled: Boolean) = saveBool("featureNavigationEnabled", featureNavigationEnabled, enabled)
    fun setFeatureTimerStopwatchEnabled(enabled: Boolean) = saveBool("featureTimerStopwatchEnabled", featureTimerStopwatchEnabled, enabled)
    fun setFeatureSilentVibrateEnabled(enabled: Boolean) = saveBool("featureSilentVibrateEnabled", featureSilentVibrateEnabled, enabled)
    fun setFeatureEarbudsEnabled(enabled: Boolean) = saveBool("featureEarbudsEnabled", featureEarbudsEnabled, enabled)
    fun setFeatureOtpEnabled(enabled: Boolean) = saveBool("featureOtpEnabled", featureOtpEnabled, enabled)
    fun setFeatureTorchEnabled(enabled: Boolean) = saveBool("featureTorchEnabled", featureTorchEnabled, enabled)
    fun setFeatureDownloadEnabled(enabled: Boolean) = saveBool("featureDownloadEnabled", featureDownloadEnabled, enabled)
    fun setFeatureRecordingEnabled(enabled: Boolean) = saveBool("featureRecordingEnabled", featureRecordingEnabled, enabled)
    fun setFeatureDndEnabled(enabled: Boolean) = saveBool("featureDndEnabled", featureDndEnabled, enabled)
    fun setFeatureLockUnlockEnabled(enabled: Boolean) = saveBool("featureLockUnlockEnabled", featureLockUnlockEnabled, enabled)

    fun setIgnoreNotificationFlashBlink(enabled: Boolean) = saveBool("ignoreNotificationFlashBlink", ignoreNotificationFlashBlink, enabled)
    fun setKeepSilentVibrateWhenIdle(enabled: Boolean) = saveBool("keepSilentVibrateWhenIdle", keepSilentVibrateWhenIdle, enabled)
    fun setAlwaysShowCallAcceptButton(enabled: Boolean) = saveBool("alwaysShowCallAcceptButton", alwaysShowCallAcceptButton, enabled)
    fun setReactiveMusicMistWave(enabled: Boolean) = saveBool("reactiveMusicMistWave", reactiveMusicMistWave, enabled)
    fun setChargingAutoExpandOnPlug(enabled: Boolean) = saveBool("chargingAutoExpandOnPlug", chargingAutoExpandOnPlug, enabled)
    fun setHapticFeedbackEnabled(enabled: Boolean) = saveBool("hapticFeedbackEnabled", hapticFeedbackEnabled, enabled)
    fun setAutoCollapseOnAppOpen(enabled: Boolean) = saveBool("autoCollapseOnAppOpen", autoCollapseOnAppOpen, enabled)
    fun setHideInLandscapeEnabled(enabled: Boolean) = saveBool("hideInLandscapeEnabled", hideInLandscapeEnabled, enabled)
    fun setBatteryLowAlertEnabled(enabled: Boolean) = saveBool("batteryLowAlertEnabled", batteryLowAlertEnabled, enabled)
    fun setEdgeGlowPulseOnNotification(enabled: Boolean) = saveBool("edgeGlowPulseOnNotification", edgeGlowPulseOnNotification, enabled)

    fun setAllFeaturesEnabled(enabled: Boolean) {
        setMasterIslandEnabled(enabled)
        setFeatureMusicEnabled(enabled)
        setFeatureCallEnabled(enabled)
        setFeatureNotificationEnabled(enabled)
        setFeatureBatteryEnabled(enabled)
        setFeatureNavigationEnabled(enabled)
        setFeatureTimerStopwatchEnabled(enabled)
        setFeatureSilentVibrateEnabled(enabled)
        setFeatureEarbudsEnabled(enabled)
        setFeatureOtpEnabled(enabled)
        setFeatureTorchEnabled(enabled)
        setFeatureDownloadEnabled(enabled)
        setFeatureRecordingEnabled(enabled)
        setFeatureDndEnabled(enabled)
        setFeatureLockUnlockEnabled(enabled)
    }

    fun updateMusicWaveShakeIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.2f, 2.0f)
        musicWaveShakeIntensity.value = clamped
        prefs?.edit()?.putFloat("musicWaveShakeIntensity", clamped)?.apply()
    }

    fun setChargingEffectStyle(index: Int) {
        val clamped = index.coerceIn(0, ChargingEffectStyle.entries.lastIndex)
        chargingEffectStyleIndex.value = clamped
        prefs?.edit()?.putInt("chargingEffectStyleIndex", clamped)?.apply()
    }

    fun setMusicSliderStyle(index: Int) {
        val clamped = index.coerceIn(0, MusicSliderStyle.entries.lastIndex)
        musicSliderStyleIndex.value = clamped
        prefs?.edit()?.putInt("musicSliderStyleIndex", clamped)?.apply()
    }

    fun refreshBatteryOptimizationState(context: Context? = appContext) {
        val ctx = context ?: appContext ?: return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryOptimizationIgnored.value = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    fun openMiuiAutostartSettings(context: Context) {
        val candidates = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun openMiuiBatterySaverSettings(context: Context) {
        val candidates = listOf(
            Intent("miui.intent.action.POWER_HIDE_MODE_ACTIVITY").apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Dynamic Island")
            },
            Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).apply {
                putExtra("package_name", context.packageName)
                putExtra("package_label", "Dynamic Island")
            },
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
        )
        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    fun updateDetectedScreenWidth(context: Context) {
        val dm = context.resources.displayMetrics
        if (dm.density > 0f && dm.widthPixels > 0) {
            detectedScreenWidthDp.value = (dm.widthPixels / dm.density).coerceAtLeast(320f)
        }
        isLandscapeOrientation.value = dm.widthPixels > dm.heightPixels
    }

    fun computeExpandedWidthDp(screenWidthDp: Float = detectedScreenWidthDp.value): Float {
        return (screenWidthDp * expandedWidthPercent.value).coerceIn(260f, screenWidthDp - 8f)
    }

    fun autoFitToScreenWidth(context: Context? = appContext) {
        context?.let { updateDetectedScreenWidth(it) }
        updateExpandedWidthPercent(0.94f)
        updateExpandedHeightScale(1.05f)
    }

    fun updateXOffset(value: Float) {
        val clamped = value.coerceIn(-240f, 240f)
        xOffset.value = clamped
        prefs?.edit()?.putFloat("xOffset", clamped)?.apply()
    }

    fun updateYOffset(value: Float) {
        val clamped = value.coerceIn(-60f, 200f)
        yOffset.value = clamped
        prefs?.edit()?.putFloat("yOffset", clamped)?.apply()
    }

    fun updateCompactWidth(value: Float) {
        val clamped = value.coerceIn(70f, 220f)
        compactWidthDp.value = clamped
        prefs?.edit()?.putFloat("compactWidthDp", clamped)?.apply()
    }

    fun updateCompactHeight(value: Float) {
        val clamped = value.coerceIn(22f, 56f)
        compactHeightDp.value = clamped
        prefs?.edit()?.putFloat("compactHeightDp", clamped)?.apply()
    }

    fun updateActiveCompactWidth(value: Float) {
        val clamped = value.coerceIn(140f, 280f)
        activeCompactWidthDp.value = clamped
        prefs?.edit()?.putFloat("activeCompactWidthDp", clamped)?.apply()
    }

    fun updateSplitCircleSize(value: Float) {
        val clamped = value.coerceIn(24f, 52f)
        splitCircleSizeDp.value = clamped
        prefs?.edit()?.putFloat("splitCircleSizeDp", clamped)?.apply()
    }

    fun updateSplitGap(value: Float) {
        val clamped = value.coerceIn(2f, 16f)
        splitGapDp.value = clamped
        prefs?.edit()?.putFloat("splitGapDp", clamped)?.apply()
    }

    fun updateDormantCircleSize(value: Float) {
        val clamped = value.coerceIn(16f, 44f)
        dormantCircleSizeDp.value = clamped
        prefs?.edit()?.putFloat("dormantCircleSizeDp", clamped)?.apply()
    }

    fun updateExpandedWidthPercent(percent: Float) {
        val clamped = percent.coerceIn(0.70f, 0.99f)
        expandedWidthPercent.value = clamped
        prefs?.edit()?.putFloat("expandedWidthPercent", clamped)?.apply()
    }

    fun updateExpandedHeightScale(scale: Float) {
        val clamped = scale.coerceIn(0.80f, 1.40f)
        expandedHeightScale.value = clamped
        prefs?.edit()?.putFloat("expandedHeightScale", clamped)?.apply()
    }

    fun updateCompactCornerRadius(radiusDp: Float) {
        val clamped = radiusDp.coerceIn(10f, 32f)
        compactCornerRadiusDp.value = clamped
        prefs?.edit()?.putFloat("compactCornerRadiusDp", clamped)?.apply()
    }

    fun updateStage2WidthOffset(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(-200f, 200f)
        stage2WidthOffset.value = clamped
        prefs?.edit()?.putFloat("stage2WidthOffset", clamped)?.apply()
    }

    fun updateStage2HeightOffset(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(-200f, 200f)
        stage2HeightOffset.value = clamped
        prefs?.edit()?.putFloat("stage2HeightOffset", clamped)?.apply()
    }

    fun updateStage3WidthOffset(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(-200f, 200f)
        stage3WidthOffset.value = clamped
        prefs?.edit()?.putFloat("stage3WidthOffset", clamped)?.apply()
    }

    fun updateStage3HeightOffset(offsetDp: Float) {
        val clamped = offsetDp.coerceIn(-200f, 200f)
        stage3HeightOffset.value = clamped
        prefs?.edit()?.putFloat("stage3HeightOffset", clamped)?.apply()
    }

    fun updateExpandedCornerRadius(radiusDp: Float) {
        val clamped = radiusDp.coerceIn(18f, 52f)
        expandedCornerRadiusDp.value = clamped
        prefs?.edit()?.putFloat("expandedCornerRadiusDp", clamped)?.apply()
    }

    fun updateContentScale(scale: Float) {
        val clamped = scale.coerceIn(0.80f, 1.30f)
        contentScale.value = clamped
        prefs?.edit()?.putFloat("contentScale", clamped)?.apply()
    }

    fun setAlwaysShowIdle(enabled: Boolean) {
        alwaysShowIdlePill.value = enabled
        prefs?.edit()?.putBoolean("alwaysShowIdlePill", enabled)?.apply()
    }

    fun setFrostedGlassStyle(enabled: Boolean) {
        useFrostedGlassStyle.value = enabled
        mistEffectEnabled.value = enabled
        prefs?.edit()?.putBoolean("useFrostedGlassStyle", enabled)?.apply()
    }

    fun setSyncMistWithAlbumArt(enabled: Boolean) {
        syncMistWithAlbumArt.value = enabled
        prefs?.edit()?.putBoolean("syncMistWithAlbumArt", enabled)?.apply()
    }

    fun setMistThemePreset(index: Int) {
        val clamped = index.coerceIn(0, MistThemePreset.entries.lastIndex)
        mistThemePresetIndex.value = clamped
        val preset = MistThemePreset.entries[clamped]
        extractedAccentColor.value = preset.primaryArgb
        extractedSecondaryColor.value = preset.secondaryArgb
        prefs?.edit()?.putInt("mistThemePresetIndex", clamped)?.apply()
    }

    fun updateIslandBgAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.35f, 1.0f)
        islandBgAlpha.value = clamped
        prefs?.edit()?.putFloat("islandBgAlpha", clamped)?.apply()
    }

    fun updateMistSmokeIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        mistSmokeIntensity.value = clamped
        prefs?.edit()?.putFloat("mistSmokeIntensity", clamped)?.apply()
    }

    fun updateMistBlurRadius(radiusDp: Float) {
        val clamped = radiusDp.coerceIn(8f, 54f)
        mistBlurRadiusDp.value = clamped
        prefs?.edit()?.putFloat("mistBlurRadiusDp", clamped)?.apply()
    }

    fun updateMistAnimSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.2f, 2.5f)
        mistAnimSpeed.value = clamped
        prefs?.edit()?.putFloat("mistAnimSpeed", clamped)?.apply()
    }

    fun updateBorderGlowAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.0f, 1.0f)
        borderGlowAlpha.value = clamped
        prefs?.edit()?.putFloat("borderGlowAlpha", clamped)?.apply()
    }

    fun updateAnimBounceLevel(level: Float) {
        val clamped = level.coerceIn(0.40f, 1.0f)
        animBounceLevel.value = clamped
        prefs?.edit()?.putFloat("animBounceLevel", clamped)?.apply()
    }

    fun updateAnimSpeedMultiplier(speed: Float) {
        val clamped = speed.coerceIn(0.50f, 2.0f)
        animSpeedMultiplier.value = clamped
        prefs?.edit()?.putFloat("animSpeedMultiplier", clamped)?.apply()
    }

    fun updateStretchMaxScale(scale: Float) {
        val clamped = scale.coerceIn(1.08f, 1.42f)
        stretchMaxScale.value = clamped
        prefs?.edit()?.putFloat("stretchMaxScale", clamped)?.apply()
    }

    fun updateDormantTimeoutSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(15, 300)
        dormantTimeoutSeconds.value = clamped
        prefs?.edit()?.putInt("dormantTimeoutSeconds", clamped)?.apply()
    }

    fun updateNotificationExpandDurationMs(durationMs: Long) {
        val clamped = durationMs.coerceIn(2000L, 10000L)
        notificationExpandDurationMs.value = clamped
        prefs?.edit()?.putLong("notificationExpandDurationMs", clamped)?.apply()
    }

    fun applyPreset(preset: DevicePreset) {
        updateXOffset(preset.xPx)
        updateYOffset(preset.yPx)
        updateCompactWidth(preset.widthDp)
        updateCompactHeight(preset.heightDp)
        updateActiveCompactWidth(preset.activeWidthDp)
        updateExpandedWidthPercent(preset.expandedPercent)
        updateExpandedHeightScale(1.05f)
    }

    fun resetAllCustomizationsToDefault() {
        applyPreset(DevicePreset.DEFAULT_CENTER)
        updateSplitCircleSize(34f)
        updateSplitGap(6f)
        updateDormantCircleSize(26f)
        updateStage2WidthOffset(0f)
        updateStage2HeightOffset(0f)
        updateStage3WidthOffset(0f)
        updateStage3HeightOffset(0f)
        updateCompactCornerRadius(26f)
        updateExpandedCornerRadius(38f)
        updateContentScale(1.0f)
        setAlwaysShowIdle(true)
        forceDormantPreview.value = false
        setFrostedGlassStyle(true)
        setSyncMistWithAlbumArt(true)
        setMistThemePreset(0)
        updateIslandBgAlpha(0.88f)
        updateMistSmokeIntensity(0.76f)
        updateMistBlurRadius(28f)
        updateMistAnimSpeed(1.0f)
        updateBorderGlowAlpha(0.48f)
        updateAnimBounceLevel(0.68f)
        updateAnimSpeedMultiplier(1.0f)
        updateStretchMaxScale(1.26f)
        updateDormantTimeoutSeconds(180)
        updateNotificationExpandDurationMs(4500L)
        setChargingEffectStyle(0)
        setMusicSliderStyle(0)
        setReactiveMusicMistWave(true)
        updateMusicWaveShakeIntensity(1.15f)
        setIgnoreNotificationFlashBlink(true)
        setKeepSilentVibrateWhenIdle(true)
        setAlwaysShowCallAcceptButton(true)
        setAllFeaturesEnabled(true)
    }

    fun updateArtworkAndExtractColors(bitmap: Bitmap?) {
        musicArtwork.value = bitmap
        extractAccentColorFromBitmap(bitmap)
    }

    fun extractAccentColorFromBitmap(bitmap: Bitmap?) {
        if (!syncMistWithAlbumArt.value || bitmap == null || bitmap.isRecycled) return
        try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return

            var rSum1 = 0L; var gSum1 = 0L; var bSum1 = 0L; var count1 = 0L
            var rSum2 = 0L; var gSum2 = 0L; var bSum2 = 0L; var count2 = 0L
            val stepX = (w / 8).coerceAtLeast(1)
            val stepY = (h / 8).coerceAtLeast(1)

            for (y in 0 until h step stepY) {
                for (x in 0 until w step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val luma = (r * 299 + g * 587 + b * 114) / 1000
                    if (luma in 30..235) {
                        if (x < w / 2) {
                            rSum1 += r; gSum1 += g; bSum1 += b; count1++
                        } else {
                            rSum2 += r; gSum2 += g; bSum2 += b; count2++
                        }
                    }
                }
            }

            if (count1 > 0L) {
                val r1 = ((rSum1 / count1).toInt() + 40).coerceIn(50, 250)
                val g1 = ((gSum1 / count1).toInt() + 40).coerceIn(50, 250)
                val b1 = ((bSum1 / count1).toInt() + 40).coerceIn(50, 250)
                extractedAccentColor.value = (0xFFL shl 24) or (r1.toLong() shl 16) or (g1.toLong() shl 8) or b1.toLong()
            }
            if (count2 > 0L) {
                val r2 = ((rSum2 / count2).toInt() + 35).coerceIn(45, 250)
                val g2 = ((gSum2 / count2).toInt() + 35).coerceIn(45, 250)
                val b2 = ((bSum2 / count2).toInt() + 35).coerceIn(45, 250)
                extractedSecondaryColor.value = (0xFFL shl 24) or (r2.toLong() shl 16) or (g2.toLong() shl 8) or b2.toLong()
            }
        } catch (_: Exception) {
        }
    }

    fun showStretchFeedback(label: String) {
        stretchFeedbackJob?.cancel()
        stretchFeedbackText.value = label
        stretchFeedbackJob = scope.launch {
            delay(1300.milliseconds)
            stretchFeedbackText.value = ""
        }
    }

    fun swapSplitStates() {
        val sec = secondaryState.value ?: return
        val pri = currentState.value
        if (pri != IslandState.IDLE && pri != sec) {
            userSwappedPrimary = sec
            currentState.value = sec
            secondaryState.value = pri
            idleSecondsCounter.value = 0
            isDormantCircleMode.value = false
            syncStage()
            performHapticTick()
        }
    }

    fun isStateFeatureEnabled(state: IslandState): Boolean {
        if (!masterIslandEnabled.value) return false
        return when (state) {
            IslandState.IDLE -> true
            IslandState.MUSIC -> featureMusicEnabled.value
            IslandState.CALL -> featureCallEnabled.value
            IslandState.NOTIFICATION -> featureNotificationEnabled.value
            IslandState.BATTERY -> featureBatteryEnabled.value
            IslandState.NAVIGATION -> featureNavigationEnabled.value
            IslandState.TIMER, IslandState.STOPWATCH -> featureTimerStopwatchEnabled.value
            IslandState.SOUND_MODE -> featureSilentVibrateEnabled.value
            IslandState.EARBUDS -> featureEarbudsEnabled.value
            IslandState.OTP -> featureOtpEnabled.value
            IslandState.TORCH -> featureTorchEnabled.value
            IslandState.DOWNLOAD -> featureDownloadEnabled.value
            IslandState.RECORDING -> featureRecordingEnabled.value
            IslandState.DND -> featureDndEnabled.value
            IslandState.LOCK -> featureLockUnlockEnabled.value
        }
    }

    /**
     * PRIORITY ENGINE FOR 2+ CONCURRENT TASKS (MAX 2 SLOTS: MAIN CAPSULE + SMALL SPLIT CIRCLE)
     *
     * Priority Hierarchy (Highest -> Lowest):
     * 1. CALL (Incoming or Ongoing Call)
     * 2. TORCH (Flashlight ON — stays continuously active while flashlight is ON!)
     * 3. TIMER (Active Countdown)
     * 4. STOPWATCH (Active Stopwatch)
     * 5. RECORDING (Screen Recording)
     * 6. NAVIGATION (Maps Turn-by-Turn)
     * 7. DOWNLOAD (File Download)
     * 8. MUSIC (Playing Song / Active Media Session)
     * 9. BATTERY (Charging)
     *
     * Example: If CALL + TORCH + MUSIC are all running (3 tasks), the top 2 priority tasks are
     * [CALL, TORCH] — Music is pushed out of the 2 slots until either Call or Torch finishes!
     *
     * Fallback when Stage 1 (0 active tasks from 1..9):
     * If DND or Silent/Vibrate is enabled on the phone, display DND / SOUND_MODE in Stage 2 Capsule
     * so the user knows Silent/Vibrate/DND is active!
     */
    fun collectActivePersistentStates(): List<IslandState> {
        if (!masterIslandEnabled.value) return emptyList()
        val prioritizedTasks = mutableListOf<IslandState>()

        // 1. Incoming / Ongoing Call (Top Priority)
        if (isCallActive.value && featureCallEnabled.value) {
            prioritizedTasks.add(IslandState.CALL)
        }
        // 2. Flashlight / Torch (Continuous High Priority whenever Torch is ON!)
        if (isTorchOn.value && featureTorchEnabled.value) {
            prioritizedTasks.add(IslandState.TORCH)
        }
        // 3. Timer
        if (isTimerActive.value && featureTimerStopwatchEnabled.value) {
            prioritizedTasks.add(IslandState.TIMER)
        }
        // 4. Stopwatch
        if (isStopwatchActive.value && featureTimerStopwatchEnabled.value) {
            prioritizedTasks.add(IslandState.STOPWATCH)
        }
        // 5. Screen Recording
        if (isRecordingActive.value && featureRecordingEnabled.value) {
            prioritizedTasks.add(IslandState.RECORDING)
        }
        // 6. Maps Navigation
        if (isNavigationActive.value && featureNavigationEnabled.value) {
            prioritizedTasks.add(IslandState.NAVIGATION)
        }
        // 7. File Download
        if (isDownloadActive.value && featureDownloadEnabled.value) {
            prioritizedTasks.add(IslandState.DOWNLOAD)
        }
        // 8. Music / Song Playback
        if ((isMediaPlaying.value || activeMediaController != null || hasActiveMediaSession.value) && featureMusicEnabled.value) {
            prioritizedTasks.add(IslandState.MUSIC)
        }
        // 9. Battery Charging
        if (isCharging.value && featureBatteryEnabled.value) {
            prioritizedTasks.add(IslandState.BATTERY)
        }

        // Fallback when NOTHING is running on the Island (Stage 1 -> Stage 2 Status Display):
        // Show DND / Focus and/or Silent / Vibrate so the user always sees that Silent/Vibrate/DND is active!
        if (prioritizedTasks.isEmpty() && keepSilentVibrateWhenIdle.value) {
            if (isDndEnabled.value && featureDndEnabled.value) {
                prioritizedTasks.add(IslandState.DND)
            }
            if ((isSilentMode.value || isVibrateMode.value) && featureSilentVibrateEnabled.value) {
                prioritizedTasks.add(IslandState.SOUND_MODE)
            }
        }

        return prioritizedTasks
    }

    private fun syncStage() {
        val state = currentState.value
        val expanded = isExpanded.value
        currentStage.value = when {
            expanded && state != IslandState.IDLE -> IslandStage.STAGE_3_EXPANDED
            state != IslandState.IDLE -> IslandStage.STAGE_2_CAPSULE
            else -> IslandStage.STAGE_1_CAMERA_CIRCLE
        }
        isDormantCircleMode.value = (currentStage.value == IslandStage.STAGE_1_CAMERA_CIRCLE) || forceDormantPreview.value
    }

    fun setExpandedState(expanded: Boolean) {
        isExpanded.value = expanded
        syncStage()
    }

    /**
     * Shows a temporary priority alert (Unlock animation, Notification, OTP, Earbuds, DND/Ringer HUD).
     * While the temporary alert is in Slot #1 (Main Capsule / Expanded), the #1 persistent task
     * (like Call or Torch or Music) sits in Slot #2 (Small Split Circle)!
     */
    fun showTemporaryState(state: IslandState, durationMs: Long = 3600L, autoExpand: Boolean = false) {
        if (!isStateFeatureEnabled(state)) return
        transientJob?.cancel()
        isTransientStateActive = true
        idleSecondsCounter.value = 0

        val persistent = collectActivePersistentStates()
        secondaryState.value = persistent.firstOrNull { it != state }
        currentState.value = state
        if (autoExpand) {
            isExpanded.value = true
        }
        syncStage()

        val effectiveDuration = if (state == IslandState.NOTIFICATION) notificationExpandDurationMs.value else durationMs
        transientJob = scope.launch {
            delay(effectiveDuration.milliseconds)
            isTransientStateActive = false
            if (autoExpand) {
                isExpanded.value = false
            }
            restoreBaselineState()
        }
    }

    fun restoreBaselineState() {
        if (!masterIslandEnabled.value) {
            currentState.value = IslandState.IDLE
            secondaryState.value = null
            isExpanded.value = false
            syncStage()
            return
        }

        // Only the top 2 highest-priority tasks occupy the 2 Island slots (Main Capsule + Small Split Circle)
        val allActive = collectActivePersistentStates()
        val topTwo = allActive.take(2)

        if (topTwo.isEmpty()) {
            userSwappedPrimary = null
            currentState.value = IslandState.IDLE
            secondaryState.value = null
            isExpanded.value = false
            syncStage()
            return
        }

        if (topTwo.size == 1) {
            userSwappedPrimary = null
            currentState.value = topTwo[0]
            secondaryState.value = null
            syncStage()
            return
        }

        // Exactly 2 top-priority tasks (e.g., CALL + TORCH when Call, Torch, and Music are all running):
        val swapped = userSwappedPrimary
        if (swapped != null && swapped in topTwo) {
            currentState.value = swapped
            secondaryState.value = topTwo.first { it != swapped }
        } else {
            userSwappedPrimary = null
            currentState.value = topTwo[0]
            secondaryState.value = topTwo[1]
        }
        syncStage()
    }

    fun sendPendingIntentSafely(intent: PendingIntent?): Boolean {
        if (intent == null) return false
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                intent.send(appContext, 0, null, null, null, null, options.toBundle())
            } else {
                intent.send()
            }
            true
        } catch (_: Exception) {
            try {
                intent.send()
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun PendingIntent.sendSafelyWithBackgroundPermission() {
        sendPendingIntentSafely(this)
    }

    fun toggleMediaPlayPause(context: Context? = appContext) {
        idleSecondsCounter.value = 0
        performHapticTick(context)
        val controller = activeMediaController
        if (controller != null) {
            val state = controller.playbackState?.state
            if (state == 3) {
                controller.transportControls.pause()
                isMediaPlaying.value = false
            } else {
                controller.transportControls.play()
                isMediaPlaying.value = true
            }
        } else {
            dispatchMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            isMediaPlaying.value = !isMediaPlaying.value
        }
        restoreBaselineState()
    }

    fun skipToNextTrack(context: Context? = appContext) {
        idleSecondsCounter.value = 0
        performHapticTick(context)
        val controller = activeMediaController
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            dispatchMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            musicProgress.value = 0L
        }
        showStretchFeedback("Next Track >>")
    }

    fun skipToPreviousTrack(context: Context? = appContext) {
        idleSecondsCounter.value = 0
        performHapticTick(context)
        val controller = activeMediaController
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            dispatchMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            musicProgress.value = 0L
        }
        showStretchFeedback("<< Previous Track")
    }

    private fun dispatchMediaKeyEvent(context: Context?, keyCode: Int) {
        val ctx = context ?: appContext ?: return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        } catch (_: Exception) {
        }
    }

    fun startTimer(seconds: Int = 207, subtitle: String = "Brewing Espresso...") {
        if (!isStateFeatureEnabled(IslandState.TIMER)) return
        val total = seconds.coerceAtLeast(1)
        timerTotalSeconds.value = total
        timerRemainingSeconds.value = total
        timerSubtitle.value = subtitle
        isTimerPaused.value = false
        isTimerRinging.value = false
        isTimerActive.value = true
        idleSecondsCounter.value = 0
        restoreBaselineState()
    }

    fun toggleTimerPause() {
        performHapticTick()
        sendPendingIntentSafely(timerPauseActionIntent)
        if (isTimerRinging.value) {
            stopTimer()
            return
        }
        if (isTimerActive.value) {
            isTimerPaused.value = !isTimerPaused.value
        }
    }

    fun resetOrSilenceTimer() {
        performHapticTick()
        if (isTimerRinging.value) {
            sendPendingIntentSafely(timerResetActionIntent)
            sendPendingIntentSafely(timerPauseActionIntent)
            isTimerRinging.value = false
            stopTimer()
        } else {
            sendPendingIntentSafely(timerResetActionIntent)
            timerRemainingSeconds.value = timerTotalSeconds.value.coerceAtLeast(10)
            isTimerPaused.value = false
        }
    }

    fun resetOrRestartTimer() {
        resetOrSilenceTimer()
    }

    fun stopTimer() {
        sendPendingIntentSafely(timerResetActionIntent)
        timerPauseActionIntent = null
        timerResetActionIntent = null
        timerOpenAppIntent = null
        isTimerActive.value = false
        isTimerPaused.value = false
        isTimerRinging.value = false
        isExpanded.value = false
        restoreBaselineState()
    }

    fun startStopwatch(subtitle: String = "Stopwatch Running") {
        if (!isStateFeatureEnabled(IslandState.STOPWATCH)) return
        stopwatchElapsedSeconds.value = 0
        stopwatchLapCount.value = 1
        stopwatchSubtitle.value = subtitle
        isStopwatchPaused.value = false
        isStopwatchActive.value = true
        idleSecondsCounter.value = 0
        restoreBaselineState()
    }

    fun toggleStopwatchPause() {
        performHapticTick()
        sendPendingIntentSafely(stopwatchPauseActionIntent)
        if (isStopwatchActive.value) {
            isStopwatchPaused.value = !isStopwatchPaused.value
        }
    }

    fun resetStopwatch() {
        performHapticTick()
        sendPendingIntentSafely(stopwatchResetActionIntent)
        stopwatchElapsedSeconds.value = 0
        stopwatchLapCount.value = 1
        isStopwatchPaused.value = true
    }

    fun stopStopwatch() {
        sendPendingIntentSafely(stopwatchResetActionIntent)
        stopwatchPauseActionIntent = null
        stopwatchResetActionIntent = null
        stopwatchOpenAppIntent = null
        isStopwatchActive.value = false
        isStopwatchPaused.value = false
        isExpanded.value = false
        restoreBaselineState()
    }

    fun startScreenRecording() {
        if (!isStateFeatureEnabled(IslandState.RECORDING)) return
        recordingElapsedSeconds.value = 24
        isRecordingActive.value = true
        idleSecondsCounter.value = 0
        restoreBaselineState()
    }

    fun stopScreenRecording() {
        isRecordingActive.value = false
        isExpanded.value = false
        restoreBaselineState()
    }

    // =========================================================================
    // CALL HANDLING: DIRECT PICK / ACCEPT, DECLINE / HANG UP, SPEAKER & MUTE
    // =========================================================================
    fun triggerIncomingCall(
        name: String = "Rohan Verma",
        subtitle: String = "Incoming HD Voice Call",
        incoming: Boolean = true
    ) {
        if (!isStateFeatureEnabled(IslandState.CALL)) return
        lastNotificationOrCallTimestampMs = System.currentTimeMillis()
        callerName.value = name
        callSubtitle.value = subtitle
        isCallIncoming.value = incoming
        if (incoming) {
            callDurationSeconds.value = 0
        }
        isCallActive.value = true
        idleSecondsCounter.value = 0
        restoreBaselineState()
        if (incoming) {
            setExpandedState(true)
        }
    }

    @SuppressLint("MissingPermission")
    fun acceptCall(context: Context? = appContext) {
        val ctx = context ?: appContext
        performHapticTick(ctx)
        var handled = false

        if (callAcceptIntent != null) {
            handled = sendPendingIntentSafely(callAcceptIntent)
        }

        if (!handled && ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    tm?.acceptRingingCall()
                    handled = true
                }
            } catch (_: Exception) {
            }
        }

        if (!handled) {
            dispatchMediaKeyEvent(ctx, KeyEvent.KEYCODE_HEADSETHOOK)
            if (callContentIntent != null) {
                sendPendingIntentSafely(callContentIntent)
            }
        }

        isCallIncoming.value = false
        callDurationSeconds.value = 0
        callSubtitle.value = "00:00 • Connected"
        setExpandedState(false)
        showStretchFeedback("Call Connected")
    }

    @SuppressLint("MissingPermission")
    fun endCall(context: Context? = appContext) {
        val ctx = context ?: appContext
        performHapticTick(ctx)
        var handled = false

        if (callDeclineIntent != null) {
            handled = sendPendingIntentSafely(callDeclineIntent)
        }

        if (!handled && ctx != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                    val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    tm?.endCall()
                }
            } catch (_: Exception) {
            }
        }

        isCallActive.value = false
        isCallIncoming.value = false
        callDurationSeconds.value = 0
        callAcceptIntent = null
        callDeclineIntent = null
        callContentIntent = null
        setExpandedState(false)
        restoreBaselineState()
    }

    fun toggleCallSpeaker(context: Context? = appContext) {
        val ctx = context ?: appContext
        performHapticTick(ctx)
        val next = !isSpeakerEnabled.value
        isSpeakerEnabled.value = next
        try {
            val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isSpeakerphoneOn = next
        } catch (_: Exception) {
        }
        showStretchFeedback(if (next) "Speaker On" else "Speaker Off")
    }

    fun toggleCallMute(context: Context? = appContext) {
        val ctx = context ?: appContext
        performHapticTick(ctx)
        val next = !isMicMuted.value
        isMicMuted.value = next
        try {
            val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.isMicrophoneMute = next
        } catch (_: Exception) {
        }
        showStretchFeedback(if (next) "Mic Muted" else "Mic Active")
    }

    fun startNavigation(
        distance: String = "90m",
        direction: String = "Turn Right • MG Road",
        eta: String = "12 min • 4.2 km"
    ) {
        if (!isStateFeatureEnabled(IslandState.NAVIGATION)) return
        navDistance.value = distance
        navDirection.value = direction
        navEta.value = eta
        isNavigationActive.value = true
        idleSecondsCounter.value = 0
        restoreBaselineState()
    }

    fun stopNavigation() {
        isNavigationActive.value = false
        navOpenIntent = null
        setExpandedState(false)
        restoreBaselineState()
    }

    fun triggerOtpAlert(code: String, sender: String = "Verification Code") {
        if (!isStateFeatureEnabled(IslandState.OTP)) return
        lastNotificationOrCallTimestampMs = System.currentTimeMillis()
        otpCode.value = code
        otpSender.value = sender
        showTemporaryState(IslandState.OTP, durationMs = 10000L, autoExpand = true)
    }

    fun copyOtpToClipboard(context: Context? = appContext) {
        val ctx = context ?: appContext ?: return
        performHapticTick(ctx)
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("OTP", otpCode.value))
        } catch (_: Exception) {
        }
        setExpandedState(false)
        restoreBaselineState()
    }

    // =========================================================================
    // CONTINUOUS FLASHLIGHT / TORCH ENGINE + SMART NOTIFICATION BLINK FILTER
    // =========================================================================
    fun setTorchStateFromCallback(cameraId: String?, enabled: Boolean) {
        if (cameraId != null) torchCameraId = cameraId
        val now = System.currentTimeMillis()
        val isManualAction = (now - manualTorchChangeTimestampMs) < 1500L

        if (!enabled) {
            torchDebounceJob?.cancel()
            torchDebounceJob = null
            val wasOn = isTorchOn.value
            isTorchOn.value = false
            if (wasOn) {
                if (currentState.value == IslandState.TORCH) {
                    setExpandedState(false)
                }
                restoreBaselineState()
            }
            return
        }

        // Torch turned ON:
        if (!isStateFeatureEnabled(IslandState.TORCH)) return

        // Only debounce if a brand-new notification/call was posted in the last 650ms (camera flash blink on notification).
        // Otherwise activate Torch Island IMMEDIATELY (0ms delay) and keep it continuously running while Torch is ON!
        val timeSinceNotif = now - lastNotificationOrCallTimestampMs
        if (!isManualAction && ignoreNotificationFlashBlink.value && timeSinceNotif in 0..650L) {
            torchDebounceJob?.cancel()
            torchDebounceJob = scope.launch {
                delay(380.milliseconds)
                activateTorchStateNow()
            }
        } else {
            torchDebounceJob?.cancel()
            activateTorchStateNow()
        }
    }

    private fun activateTorchStateNow() {
        isTorchOn.value = true
        idleSecondsCounter.value = 0
        restoreBaselineState()
    }

    fun toggleTorchHardware(context: Context? = appContext) {
        if (!isStateFeatureEnabled(IslandState.TORCH)) return
        val ctx = context ?: appContext
        performHapticTick(ctx)
        val targetState = !isTorchOn.value
        manualTorchChangeTimestampMs = System.currentTimeMillis()
        torchDebounceJob?.cancel()
        if (ctx != null) {
            try {
                val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val camId = torchCameraId ?: cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (camId != null) {
                    torchCameraId = camId
                    cm.setTorchMode(camId, targetState)
                }
            } catch (_: Exception) {
            }
        }
        setTorchStateFromCallback(torchCameraId, targetState)
    }

    fun onChargerConnected() {
        if (!isStateFeatureEnabled(IslandState.BATTERY)) return
        isCharging.value = true
        idleSecondsCounter.value = 0
        if (chargingAutoExpandOnPlug.value) {
            showTemporaryState(IslandState.BATTERY, durationMs = 4200L, autoExpand = true)
        } else {
            restoreBaselineState()
        }
    }

    fun checkLowBatteryWarning(level: Int, charging: Boolean) {
        if (charging) {
            lastLowBatteryWarnLevel = 100
            return
        }
        if (!batteryLowAlertEnabled.value || !isStateFeatureEnabled(IslandState.BATTERY)) return
        if ((level == 20 || level == 15 || level == 10) && level < lastLowBatteryWarnLevel) {
            lastLowBatteryWarnLevel = level
            showTemporaryState(IslandState.BATTERY, durationMs = 4000L, autoExpand = true)
        }
    }

    fun updateDownloadProgress(title: String, appName: String, progress: Int) {
        if (!isStateFeatureEnabled(IslandState.DOWNLOAD)) return
        downloadTitle.value = title
        downloadAppName.value = appName
        val clamped = progress.coerceIn(0, 100)
        downloadProgress.value = clamped
        isDownloadActive.value = clamped < 100
        if (clamped < 100) {
            idleSecondsCounter.value = 0
            restoreBaselineState()
        } else {
            showTemporaryState(IslandState.DOWNLOAD, durationMs = 2500L, autoExpand = false)
        }
    }

    fun dismissDownload() {
        isDownloadActive.value = false
        setExpandedState(false)
        restoreBaselineState()
    }

    fun triggerDndHud(enabled: Boolean, label: String = if (enabled) "Focus / DND" else "DND Off") {
        isDndEnabled.value = enabled
        dndModeLabel.value = label
        if (!isStateFeatureEnabled(IslandState.DND)) {
            restoreBaselineState()
            return
        }
        showTemporaryState(IslandState.DND, durationMs = 2800L, autoExpand = false)
    }

    fun onRingerModeUpdated(mode: Int) {
        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> {
                soundModeText.value = "Silent"
                isSilentMode.value = true
                isVibrateMode.value = false
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                soundModeText.value = "Vibrate"
                isSilentMode.value = false
                isVibrateMode.value = true
            }
            else -> {
                soundModeText.value = "Ring"
                isSilentMode.value = false
                isVibrateMode.value = false
            }
        }
        if (!isStateFeatureEnabled(IslandState.SOUND_MODE)) {
            restoreBaselineState()
            return
        }
        showTemporaryState(IslandState.SOUND_MODE, durationMs = 2800L, autoExpand = false)
    }

    fun refreshSecondarySplitState() {
        if (!isTransientStateActive) {
            restoreBaselineState()
        } else {
            val persistent = collectActivePersistentStates()
            secondaryState.value = persistent.firstOrNull { it != currentState.value }
            syncStage()
        }
    }

    fun triggerNotificationAlert(
        appName: String = "WhatsApp",
        title: String = "Aarav Sharma",
        text: String = "Bro, Dynamic Island ka 3-stage camera-expand UI, priority split-pill aur flashlight guard kamaal lag raha hai!",
        message: String? = null,
        subText: String = "Just now",
        icon: Bitmap? = null,
        actionLabel: String = "",
        actionIntent: PendingIntent? = null,
        contentIntent: PendingIntent? = null
    ) {
        if (!isStateFeatureEnabled(IslandState.NOTIFICATION)) return
        lastNotificationOrCallTimestampMs = System.currentTimeMillis()
        // Cancel only an unconfirmed 380ms blink-check job; NEVER turn off an already-active Torch!
        if (ignoreNotificationFlashBlink.value) {
            torchDebounceJob?.cancel()
            torchDebounceJob = null
        }

        notificationAppName.value = appName
        notificationTitle.value = title
        notificationText.value = message ?: text
        notificationSubText.value = subText
        notificationIcon.value = icon
        notificationActionLabel.value = actionLabel
        notificationActionIntent = actionIntent
        notificationContentIntent = contentIntent
        showTemporaryState(
            state = IslandState.NOTIFICATION,
            durationMs = notificationExpandDurationMs.value,
            autoExpand = true
        )
    }

    fun tickOneSecond() {
        if (isTimerActive.value && !isTimerPaused.value) {
            val next = (timerRemainingSeconds.value - 1).coerceAtLeast(0)
            timerRemainingSeconds.value = next
            if (next == 0) {
                isTimerPaused.value = true
                isTimerRinging.value = true
                timerSubtitle.value = "Timer Finished! Swipe Right to Stop"
                setExpandedState(true)
            }
        }
        if (isStopwatchActive.value && !isStopwatchPaused.value) {
            stopwatchElapsedSeconds.value += 1
        }
        if (isRecordingActive.value) {
            recordingElapsedSeconds.value += 1
        }
        if (isCallActive.value && !isCallIncoming.value) {
            callDurationSeconds.value += 1
            val mins = callDurationSeconds.value / 60
            val secs = callDurationSeconds.value % 60
            callSubtitle.value = "%02d:%02d • Connected".format(mins, secs)
        }
        syncStage()
    }
}
