@file:Suppress("DEPRECATION", "InlinedApi")

package com.example.dynamicisland

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class IslandNotificationService : NotificationListenerService() {

    private var currentController: MediaController? = null
    private var mediaSessionManager: MediaSessionManager? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)
    private var progressTrackerJob: Job? = null

    private val otpRegex = Regex("\\b(\\d{4,8})\\b")
    private val timeRegex = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    private var lastNotificationSignature: String = ""
    private var lastNotificationPostMs: Long = 0L

    private val ignoredSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.miui.securitycenter",
        "com.miui.powerkeeper",
        "com.google.android.inputmethod.latin"
    )

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            if (controllers.isNullOrEmpty()) {
                clearMediaSession()
                return@OnActiveSessionsChangedListener
            }
            val playingController = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()

            if (playingController != null) {
                if (currentController?.sessionToken != playingController.sessionToken) {
                    registerController(playingController)
                }
            } else {
                clearMediaSession()
            }
        }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handlePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadataFromController(metadata)
        }

        override fun onSessionDestroyed() {
            clearMediaSession()
        }
    }

    private fun updateMetadataFromController(metadata: MediaMetadata?) {
        if (metadata == null || !IslandConfig.isStateFeatureEnabled(IslandState.MUSIC)) return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "Unknown Artist"
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(1L)
        val art: Bitmap? = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        if (title.isBlank()) {
            clearMediaSession()
            return
        }

        IslandConfig.hasActiveMediaSession.value = true
        IslandConfig.musicTitle.value = title
        IslandConfig.musicArtist.value = artist
        IslandConfig.musicDuration.value = duration
        if (art != null) {
            IslandConfig.updateArtworkAndExtractColors(art)
        }
        IslandConfig.refreshSecondarySplitState()
    }

    private fun handlePlaybackState(state: PlaybackState?) {
        if (!IslandConfig.isStateFeatureEnabled(IslandState.MUSIC)) {
            progressTrackerJob?.cancel()
            return
        }
        val code = state?.state ?: PlaybackState.STATE_NONE
        if (code == PlaybackState.STATE_NONE || code == PlaybackState.STATE_STOPPED || code == PlaybackState.STATE_ERROR) {
            val mediaPkg = currentController?.packageName
            val stillHasNotif = try {
                activeNotifications?.any { it.packageName == mediaPkg } == true
            } catch (_: Exception) {
                true
            }
            if (!stillHasNotif) {
                clearMediaSession()
                return
            }
        }

        val isPlaying = code == PlaybackState.STATE_PLAYING || code == PlaybackState.STATE_BUFFERING
        IslandConfig.isMediaPlaying.value = isPlaying
        IslandConfig.hasActiveMediaSession.value = true
        IslandConfig.musicProgress.value = (state?.position ?: 0L).coerceAtLeast(0L)
        IslandConfig.refreshSecondarySplitState()

        if (isPlaying) {
            startTrackingProgress()
        } else {
            progressTrackerJob?.cancel()
        }
    }

    private fun startTrackingProgress() {
        progressTrackerJob?.cancel()
        progressTrackerJob = serviceScope.launch {
            while (isActive) {
                val controller = currentController
                if (controller == null) {
                    clearMediaSession()
                    break
                }
                val pb = controller.playbackState
                if (pb == null || pb.state == PlaybackState.STATE_STOPPED || pb.state == PlaybackState.STATE_NONE) {
                    val hasNotif = try {
                        activeNotifications?.any { it.packageName == controller.packageName } == true
                    } catch (_: Exception) {
                        false
                    }
                    if (!hasNotif) {
                        clearMediaSession()
                        break
                    }
                }
                IslandConfig.musicProgress.value = (pb?.position ?: 0L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        IslandConfig.init(applicationContext)
        IslandConfig.isNotificationServiceRunning.value = true

        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            mediaSessionManager = mgr
            val comp = ComponentName(this, IslandNotificationService::class.java)
            mgr.addOnActiveSessionsChangedListener(activeSessionsListener, comp)
            val active = mgr.getActiveSessions(comp)
            if (active.isNotEmpty()) {
                registerController(active.first())
            }
        } catch (_: Exception) {
        }

        try {
            activeNotifications?.forEach { sbn ->
                processStatusBarNotification(sbn, isInitialScan = true)
            }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        IslandConfig.isNotificationServiceRunning.value = false
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        processStatusBarNotification(sbn, isInitialScan = false)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName.lowercase()

        val token = extractMediaToken(notification)
        if ((token != null && currentController?.sessionToken == token) ||
            sbn.packageName == IslandConfig.activeMediaPackage
        ) {
            clearMediaSession()
            return
        }

        if ((notification.category == Notification.CATEGORY_CALL || isDialerOrCallPackage(pkg)) &&
            IslandConfig.isCallActive.value
        ) {
            val stillHasCall = try {
                activeNotifications?.any {
                    it.key != sbn.key && it.notification?.category == Notification.CATEGORY_CALL
                } == true
            } catch (_: Exception) {
                false
            }
            if (!stillHasCall) {
                IslandConfig.endCall()
            }
        }

        if (notification.category == Notification.CATEGORY_NAVIGATION || pkg.contains("maps")) {
            IslandConfig.stopNavigation()
        }

        if (isClockPackage(pkg) || notification.category == Notification.CATEGORY_STOPWATCH) {
            val stillHasClock = try {
                activeNotifications?.any {
                    it.key != sbn.key && (isClockPackage(it.packageName.lowercase()) ||
                            it.notification?.category == Notification.CATEGORY_STOPWATCH)
                } == true
            } catch (_: Exception) {
                false
            }
            if (!stillHasClock) {
                IslandConfig.stopTimer()
                IslandConfig.stopStopwatch()
            }
        }
    }

    private fun isClockPackage(pkg: String): Boolean {
        return pkg.contains("deskclock") || pkg.contains("clock") || pkg.contains("timer") || pkg.contains("alarm")
    }

    private fun isDialerOrCallPackage(pkg: String): Boolean {
        return pkg.contains("dialer") || pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("phone")
    }

    private fun extractClockRemoteViewsData(notification: Notification): Pair<Int?, String> {
        var chronometerSeconds: Int? = null
        val texts = StringBuilder()
        val rv = notification.bigContentView ?: notification.contentView ?: return null to ""
        try {
            val container = FrameLayout(applicationContext)
            val inflated = rv.apply(applicationContext, container)
            fun traverse(v: View) {
                when (v) {
                    is Chronometer -> {
                        val deltaMs = abs(SystemClock.elapsedRealtime() - v.base)
                        if (deltaMs < 86_400_000L) {
                            chronometerSeconds = (deltaMs / 1000L).toInt()
                        }
                        if (!v.text.isNullOrBlank()) {
                            texts.append(v.text).append(" ")
                        }
                    }
                    is TextView -> {
                        if (!v.text.isNullOrBlank()) {
                            texts.append(v.text).append(" ")
                        }
                    }
                    is ViewGroup -> {
                        for (i in 0 until v.childCount) {
                            traverse(v.getChildAt(i))
                        }
                    }
                }
            }
            traverse(inflated)
        } catch (_: Exception) {
        }
        return chronometerSeconds to texts.toString()
    }

    private fun extractParcelablePendingIntent(extras: android.os.Bundle, key: String): PendingIntent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelable(key, PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelable(key) as? PendingIntent
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun processStatusBarNotification(sbn: StatusBarNotification?, isInitialScan: Boolean) {
        if (sbn == null || sbn.packageName == packageName) return
        if (!IslandConfig.masterIslandEnabled.value) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val pkg = sbn.packageName.lowercase()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val ticker = notification.tickerText?.toString()?.trim().orEmpty()

        val allExtrasText = buildString {
            append("$text $subText $bigText $ticker $title ")
            for (k in extras.keySet()) {
                val v = extras.get(k)
                if (v is CharSequence) append("$v ")
            }
        }
        val combinedLower = allExtrasText.lowercase()
        val actions = notification.actions.orEmpty()
        val actionTitlesLower = actions.mapNotNull { it.title?.toString()?.lowercase() }.joinToString(" ")

        // =====================================================================
        // 1. INCOMING & ONGOING CALL DETECTION (Full CallStyle + Multi-Dialer Support)
        // =====================================================================
        val callType = extras.getInt("android.callType", -1) // 1 = incoming, 2 = ongoing, 3 = screening
        val answerIntentFromStyle = extractParcelablePendingIntent(extras, "android.callAnswerIntent")
        val declineIntentFromStyle = extractParcelablePendingIntent(extras, "android.callDeclineIntent")
        val hangUpIntentFromStyle = extractParcelablePendingIntent(extras, "android.callHangUpIntent")

        val isCallNotif = notification.category == Notification.CATEGORY_CALL ||
                callType in 1..3 ||
                answerIntentFromStyle != null ||
                declineIntentFromStyle != null ||
                (isDialerOrCallPackage(pkg) && sbn.isOngoing && (actionTitlesLower.contains("answer") || actionTitlesLower.contains("decline") || actionTitlesLower.contains("hang") || actionTitlesLower.contains("end")))

        if (isCallNotif && IslandConfig.isStateFeatureEnabled(IslandState.CALL)) {
            val matchedAcceptAction = actions.firstOrNull {
                val t = it.title?.toString()?.lowercase().orEmpty()
                t.contains("answer") || t.contains("accept") || t.contains("pick") ||
                        t.contains("receive") || t.contains("connect") || t.contains("join") ||
                        t.contains("उठाएं") || t.contains("स्वीकार")
            }?.actionIntent

            val matchedDeclineAction = actions.firstOrNull {
                val t = it.title?.toString()?.lowercase().orEmpty()
                t.contains("decline") || t.contains("reject") || t.contains("end") ||
                        t.contains("hang") || t.contains("cut") || t.contains("dismiss") ||
                        t.contains("काटें") || t.contains("अस्वीकार")
            }?.actionIntent

            val fallbackAccept = if (actions.size >= 2) {
                actions.firstOrNull { it.actionIntent != matchedDeclineAction }?.actionIntent ?: actions.last().actionIntent
            } else null

            val fallbackDecline = actions.firstOrNull()?.actionIntent

            val finalAcceptIntent = answerIntentFromStyle ?: matchedAcceptAction ?: fallbackAccept
            val finalDeclineIntent = declineIntentFromStyle ?: hangUpIntentFromStyle ?: matchedDeclineAction ?: fallbackDecline

            IslandConfig.callAcceptIntent = finalAcceptIntent
            IslandConfig.callDeclineIntent = finalDeclineIntent
            IslandConfig.callContentIntent = notification.contentIntent ?: notification.fullScreenIntent

            val isOngoingByText = combinedLower.contains("ongoing") ||
                    combinedLower.contains("in progress") ||
                    timeRegex.containsMatchIn("$title $text")
            val isIncomingCall = when {
                callType == 1 -> true
                callType == 2 -> false
                finalAcceptIntent != null && !isOngoingByText -> true
                actions.size >= 2 && !isOngoingByText -> true
                combinedLower.contains("incoming") || combinedLower.contains("ringing") -> true
                else -> !isOngoingByText
            }

            val resolvedCaller = title.ifBlank { "Incoming Call" }
            val resolvedSubtitle = when {
                text.isNotBlank() -> text
                isIncomingCall -> "Incoming Voice Call"
                else -> "Ongoing Call"
            }

            IslandConfig.triggerIncomingCall(
                name = resolvedCaller,
                subtitle = resolvedSubtitle,
                incoming = isIncomingCall
            )
            return
        }

        // =====================================================================
        // 2. GOOGLE CLOCK / NATIVE TIMER & STOPWATCH DETECTION
        // =====================================================================
        val usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val isChronometerCountDown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        } else {
            false
        }

        val isClockOrTimerNotif = isClockPackage(pkg) ||
                notification.category == Notification.CATEGORY_STOPWATCH ||
                (notification.category == Notification.CATEGORY_ALARM && (sbn.isOngoing || actionTitlesLower.contains("stop") || actionTitlesLower.contains("dismiss"))) ||
                (usesChronometer && (combinedLower.contains("timer") || combinedLower.contains("stopwatch")))

        if (isClockOrTimerNotif && IslandConfig.isStateFeatureEnabled(IslandState.TIMER)) {
            val pauseResumeIntent = actions.firstOrNull {
                val l = it.title?.toString()?.lowercase().orEmpty()
                l.contains("pause") || l.contains("resume") || l.contains("start")
            }?.actionIntent ?: actions.getOrNull(0)?.actionIntent

            val resetCancelIntent = actions.firstOrNull {
                val l = it.title?.toString()?.lowercase().orEmpty()
                l.contains("reset") || l.contains("delete") || l.contains("cancel")
            }?.actionIntent ?: actions.getOrNull(1)?.actionIntent

            val lapIntent = actions.firstOrNull {
                val l = it.title?.toString()?.lowercase().orEmpty()
                l.contains("lap")
            }?.actionIntent

            val stopSoundIntent = actions.firstOrNull {
                val l = it.title?.toString()?.lowercase().orEmpty()
                l.contains("stop") || l.contains("dismiss")
            }?.actionIntent ?: pauseResumeIntent

            val (remoteChronoSec, remoteViewsText) = extractClockRemoteViewsData(notification)
            val nowMs = System.currentTimeMillis()
            val whenMs = notification.`when`
            val whenChronoSec = if (whenMs > 0L && abs(whenMs - nowMs) < 86_400_000L) {
                (abs(whenMs - nowMs) / 1000L).toInt()
            } else null

            val parsedLiveSec = remoteChronoSec
                ?: parseSecondsFromText("$text $subText $remoteViewsText")
                ?: whenChronoSec
                ?: parseSecondsFromText(title)

            val fullTextLower = "$combinedLower ${remoteViewsText.lowercase()}"
            val isStopwatch = notification.category == Notification.CATEGORY_STOPWATCH ||
                    fullTextLower.contains("stopwatch") || actionTitlesLower.contains("lap") ||
                    (usesChronometer && !isChronometerCountDown && whenMs in 1 until nowMs && !fullTextLower.contains("timer"))

            val isPaused = fullTextLower.contains("paused") ||
                    actionTitlesLower.contains("resume") || actionTitlesLower.contains("start")

            val isRinging = fullTextLower.contains("time's up") || fullTextLower.contains("firing") ||
                    (actionTitlesLower.contains("stop") && !isStopwatch)

            if (isStopwatch) {
                IslandConfig.stopwatchPauseIntent = pauseResumeIntent
                IslandConfig.stopwatchResetIntent = resetCancelIntent
                IslandConfig.stopwatchLapIntent = lapIntent
                IslandConfig.stopwatchOpenAppIntent = notification.contentIntent
                IslandConfig.isStopwatchPaused.value = isPaused
                if (parsedLiveSec != null && (!IslandConfig.isStopwatchActive.value || isPaused || abs(parsedLiveSec - IslandConfig.stopwatchElapsedSeconds.value) > 2)) {
                    IslandConfig.stopwatchElapsedSeconds.value = parsedLiveSec
                }
                IslandConfig.stopwatchSubtitle.value = title.ifBlank { "Google Clock • Stopwatch" }
                IslandConfig.isStopwatchActive.value = true
                IslandConfig.refreshSecondarySplitState()
            } else {
                IslandConfig.timerPauseActionIntent = pauseResumeIntent
                IslandConfig.timerResetActionIntent = resetCancelIntent
                IslandConfig.timerStopSoundIntent = stopSoundIntent
                IslandConfig.timerOpenAppIntent = notification.contentIntent
                IslandConfig.isTimerPaused.value = isPaused || isRinging
                IslandConfig.isTimerRinging.value = isRinging

                if (isRinging) {
                    IslandConfig.timerRemainingSeconds.value = 0
                } else if (parsedLiveSec != null) {
                    if (!IslandConfig.isTimerActive.value || parsedLiveSec > IslandConfig.timerTotalSeconds.value) {
                        IslandConfig.timerTotalSeconds.value = parsedLiveSec.coerceAtLeast(10)
                    }
                    if (!IslandConfig.isTimerActive.value || isPaused || abs(parsedLiveSec - IslandConfig.timerRemainingSeconds.value) > 2) {
                        IslandConfig.timerRemainingSeconds.value = parsedLiveSec
                    }
                } else if (!IslandConfig.isTimerActive.value) {
                    IslandConfig.timerRemainingSeconds.value = 60
                    IslandConfig.timerTotalSeconds.value = 60
                }

                IslandConfig.timerSubtitle.value = when {
                    isRinging -> "Timer Finished • Stretch Right to Stop Sound"
                    title.isNotBlank() -> title
                    else -> "Phone Timer Running"
                }
                IslandConfig.isTimerActive.value = true
                IslandConfig.refreshSecondarySplitState()
            }
            return
        }

        // =====================================================================
        // 3. TURN-BY-TURN GOOGLE MAPS NAVIGATION DETECTION
        // =====================================================================
        if ((notification.category == Notification.CATEGORY_NAVIGATION ||
                    (pkg.contains("maps") && sbn.isOngoing)) &&
            IslandConfig.isStateFeatureEnabled(IslandState.NAVIGATION)
        ) {
            IslandConfig.navOpenIntent = notification.contentIntent
            IslandConfig.startNavigation(
                distance = title.ifBlank { "90 ft" },
                direction = text.ifBlank { "Continue on current route" },
                eta = subText.ifBlank { "Live Navigation" }
            )
            return
        }

        // =====================================================================
        // 4. SMART OTP EXTRACTOR
        // =====================================================================
        if (!sbn.isOngoing &&
            IslandConfig.isStateFeatureEnabled(IslandState.OTP) &&
            (combinedLower.contains("otp") ||
                    combinedLower.contains("verification") ||
                    combinedLower.contains("code") ||
                    combinedLower.contains("passcode"))
        ) {
            val match = otpRegex.find("$title $text")
            if (match != null) {
                IslandConfig.triggerOtpAlert(
                    code = match.groupValues[1],
                    sender = title.ifBlank { resolveAppLabel(sbn.packageName) }
                )
                return
            }
        }

        // =====================================================================
        // 5. FILE DOWNLOAD PROGRESS RING DETECTION
        // =====================================================================
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progressCur = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (sbn.isOngoing && !isIndeterminate && progressMax > 0 && progressCur >= 0 &&
            !extras.containsKey(Notification.EXTRA_MEDIA_SESSION) &&
            IslandConfig.isStateFeatureEnabled(IslandState.DOWNLOAD)
        ) {
            val pct = ((progressCur.toFloat() / progressMax.toFloat()) * 100f).toInt().coerceIn(0, 100)
            IslandConfig.updateDownloadProgress(
                title = title.ifBlank { "Downloading File..." },
                appName = resolveAppLabel(sbn.packageName),
                progress = pct
            )
            return
        }

        // =====================================================================
        // 6. DIRECT NOTIFICATION MEDIA SESSION DETECTION
        // =====================================================================
        val token = extractMediaToken(notification)
        if (token != null && IslandConfig.isStateFeatureEnabled(IslandState.MUSIC)) {
            IslandConfig.activeMediaPackage = sbn.packageName
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                try {
                    val drawable = largeIcon.loadDrawable(this)
                    val bmp = drawableToBitmap(drawable)
                    if (bmp != null) {
                        IslandConfig.updateArtworkAndExtractColors(bmp)
                    }
                } catch (_: Exception) {
                }
            }

            val controller = MediaController(this, token)
            if (currentController?.sessionToken != token) {
                registerController(controller)
            } else {
                updateMetadataFromController(controller.metadata)
                handlePlaybackState(controller.playbackState)
            }
            return
        }

        // =====================================================================
        // 7. CLEANED & ENHANCED GENERAL INCOMING NOTIFICATION
        // =====================================================================
        if (!isInitialScan &&
            !sbn.isOngoing &&
            pkg !in ignoredSystemPackages &&
            (notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
            (notification.flags and Notification.FLAG_ONGOING_EVENT) == 0 &&
            (title.isNotBlank() || text.isNotBlank()) &&
            IslandConfig.isStateFeatureEnabled(IslandState.NOTIFICATION)
        ) {
            val cleanBody = bigText.ifBlank { text.ifBlank { subText } }
            val sig = "$pkg|$title|$cleanBody"
            val now = System.currentTimeMillis()
            if (sig == lastNotificationSignature && (now - lastNotificationPostMs) < 1800L) {
                return
            }
            lastNotificationSignature = sig
            lastNotificationPostMs = now

            val appName = resolveAppLabel(sbn.packageName)
            val iconBmp = extractNotificationBitmap(notification, sbn.packageName)
            val firstAction = actions.firstOrNull { !it.title.isNullOrBlank() && it.actionIntent != null }

            IslandConfig.triggerNotificationAlert(
                appName = appName,
                title = title.ifBlank { appName },
                message = cleanBody.ifBlank { "New notification received" },
                subText = subText.ifBlank { "Just now" },
                icon = iconBmp,
                actionLabel = firstAction?.title?.toString()?.trim().orEmpty(),
                actionIntent = firstAction?.actionIntent,
                contentIntent = notification.contentIntent
            )
        }
    }

    private fun extractNotificationBitmap(notification: Notification, pkg: String): Bitmap? {
        try {
            val largeIcon = notification.getLargeIcon()
            if (largeIcon != null) {
                val d = largeIcon.loadDrawable(this)
                val b = drawableToBitmap(d)
                if (b != null) return b
            }
        } catch (_: Exception) {
        }
        return try {
            val appDrawable = packageManager.getApplicationIcon(pkg)
            drawableToBitmap(appDrawable)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        return try {
            val w = drawable.intrinsicWidth.coerceIn(48, 192)
            val h = drawable.intrinsicHeight.coerceIn(48, 192)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveAppLabel(pkg: String): String {
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun parseSecondsFromText(input: String): Int? {
        val m = timeRegex.find(input) ?: return null
        val p1 = m.groupValues[1].toIntOrNull() ?: return null
        val p2 = m.groupValues[2].toIntOrNull() ?: return null
        val p3 = m.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull()
        return if (p3 != null) {
            p1 * 3600 + p2 * 60 + p3
        } else {
            p1 * 60 + p2
        }
    }

    private fun extractMediaToken(notification: Notification): MediaSession.Token? {
        val extras = notification.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable<android.os.Parcelable>(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    private fun registerController(controller: MediaController) {
        currentController?.unregisterCallback(mediaCallback)
        currentController = controller
        IslandConfig.activeMediaController = controller
        IslandConfig.activeMediaPackage = controller.packageName
        IslandConfig.hasActiveMediaSession.value = true
        controller.registerCallback(mediaCallback)

        updateMetadataFromController(controller.metadata)
        handlePlaybackState(controller.playbackState)
    }

    private fun clearMediaSession() {
        currentController?.unregisterCallback(mediaCallback)
        currentController = null
        IslandConfig.activeMediaController = null
        IslandConfig.activeMediaPackage = null
        IslandConfig.hasActiveMediaSession.value = false
        IslandConfig.isMediaPlaying.value = false
        IslandConfig.musicArtwork.value = null
        progressTrackerJob?.cancel()
        IslandConfig.restoreBaselineState()
    }

    override fun onDestroy() {
        IslandConfig.isNotificationServiceRunning.value = false
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (_: Exception) {
        }
        serviceJob.cancel()
        currentController?.unregisterCallback(mediaCallback)
        super.onDestroy()
    }
}
