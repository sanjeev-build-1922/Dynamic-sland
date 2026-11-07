package com.example.dynamicisland

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

private enum class StudioSection(val label: String, val subtitle: String, val icon: ImageVector) {
    SETUP_TEST("Setup & Lab", "Permissions & Priority", Icons.Rounded.Science),
    FEATURES("Features Hub", "Enable / Disable All", Icons.Rounded.ToggleOn),
    STYLES("Wave & Styles", "Charging & Sliders", Icons.Rounded.AutoAwesome),
    MIST_GLASS("Mist & Glass", "Shaders & Themes", Icons.Rounded.Palette),
    SIZE_CUTOUT("Cutout & Size", "Position & Scale", Icons.Rounded.AspectRatio),
    MOTION_RULES("Motion & Fixes", "3-Stage & Guards", Icons.Rounded.Tune)
}

class MainActivity : ComponentActivity() {
    private var resumeCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandConfig.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF07080B)) {
                    AppControlPanel(
                        resumeTick = resumeCount,
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenNotificationSettings = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        IslandConfig.updateDetectedScreenWidth(this)
        IslandConfig.refreshBatteryOptimizationState(this)
        IslandConfig.syncInitialRingerAndDndState(this)
        IslandConfig.restoreBaselineState()
        resumeCount++
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppControlPanel(
    resumeTick: Int = 0,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(StudioSection.SETUP_TEST) }
    var showInAppDuplicatePreview by remember { mutableStateOf(false) }

    // Master & Individual Feature Toggles
    val masterEnabled by IslandConfig.masterIslandEnabled.collectAsState()
    val featMusic by IslandConfig.featureMusicEnabled.collectAsState()
    val featCall by IslandConfig.featureCallEnabled.collectAsState()
    val featNotif by IslandConfig.featureNotificationEnabled.collectAsState()
    val featBattery by IslandConfig.featureBatteryEnabled.collectAsState()
    val featNav by IslandConfig.featureNavigationEnabled.collectAsState()
    val featTimer by IslandConfig.featureTimerStopwatchEnabled.collectAsState()
    val featSilent by IslandConfig.featureSilentVibrateEnabled.collectAsState()
    val featEarbuds by IslandConfig.featureEarbudsEnabled.collectAsState()
    val featOtp by IslandConfig.featureOtpEnabled.collectAsState()
    val featTorch by IslandConfig.featureTorchEnabled.collectAsState()
    val featDownload by IslandConfig.featureDownloadEnabled.collectAsState()
    val featRecord by IslandConfig.featureRecordingEnabled.collectAsState()
    val featDnd by IslandConfig.featureDndEnabled.collectAsState()
    val featLock by IslandConfig.featureLockUnlockEnabled.collectAsState()

    // Pro Fixes & Extra Feature Toggles
    val ignoreFlashBlink by IslandConfig.ignoreNotificationFlashBlink.collectAsState()
    val keepSilentIdle by IslandConfig.keepSilentVibrateWhenIdle.collectAsState()
    val alwaysCallAccept by IslandConfig.alwaysShowCallAcceptButton.collectAsState()
    val reactiveMusicWave by IslandConfig.reactiveMusicMistWave.collectAsState()
    val musicShakeIntensity by IslandConfig.musicWaveShakeIntensity.collectAsState()
    val chargingStyleIdx by IslandConfig.chargingEffectStyleIndex.collectAsState()
    val musicSliderIdx by IslandConfig.musicSliderStyleIndex.collectAsState()
    val chargeAutoExpand by IslandConfig.chargingAutoExpandOnPlug.collectAsState()
    val hapticsEnabled by IslandConfig.hapticFeedbackEnabled.collectAsState()
    val autoCollapseApp by IslandConfig.autoCollapseOnAppOpen.collectAsState()
    val hideLandscape by IslandConfig.hideInLandscapeEnabled.collectAsState()
    val lowBatAlert by IslandConfig.batteryLowAlertEnabled.collectAsState()
    val edgeGlowNotif by IslandConfig.edgeGlowPulseOnNotification.collectAsState()

    // Size & Cutout
    val detectedScreenWidthDp by IslandConfig.detectedScreenWidthDp.collectAsState()
    val xOffset by IslandConfig.xOffset.collectAsState()
    val yOffset by IslandConfig.yOffset.collectAsState()
    val compactWidth by IslandConfig.compactWidthDp.collectAsState()
    val compactHeight by IslandConfig.compactHeightDp.collectAsState()
    val activeCompactW by IslandConfig.activeCompactWidthDp.collectAsState()
    val splitCircleSize by IslandConfig.splitCircleSizeDp.collectAsState()
    val splitGap by IslandConfig.splitGapDp.collectAsState()
    val dormantCircleSize by IslandConfig.dormantCircleSizeDp.collectAsState()
    val expandedPercent by IslandConfig.expandedWidthPercent.collectAsState()
    val expandedScale by IslandConfig.expandedHeightScale.collectAsState()
    val compactCorner by IslandConfig.compactCornerRadiusDp.collectAsState()
    val expandedCorner by IslandConfig.expandedCornerRadiusDp.collectAsState()
    val contentScale by IslandConfig.contentScale.collectAsState()
    val alwaysShowIdle by IslandConfig.alwaysShowIdlePill.collectAsState()
    val stage2WidthOff by IslandConfig.stage2WidthOffset.collectAsState()
    val stage2HeightOff by IslandConfig.stage2HeightOffset.collectAsState()
    val stage3WidthOff by IslandConfig.stage3WidthOffset.collectAsState()
    val stage3HeightOff by IslandConfig.stage3HeightOffset.collectAsState()

    // Mist & Glass
    val useFrostedGlass by IslandConfig.useFrostedGlassStyle.collectAsState()
    val syncArt by IslandConfig.syncMistWithAlbumArt.collectAsState()
    val mistPresetIdx by IslandConfig.mistThemePresetIndex.collectAsState()
    val bgAlpha by IslandConfig.islandBgAlpha.collectAsState()
    val smokeIntensity by IslandConfig.mistSmokeIntensity.collectAsState()
    val blurRadius by IslandConfig.mistBlurRadiusDp.collectAsState()
    val mistSpeed by IslandConfig.mistAnimSpeed.collectAsState()
    val borderGlow by IslandConfig.borderGlowAlpha.collectAsState()

    // Motion
    val bounceLevel by IslandConfig.animBounceLevel.collectAsState()
    val speedMult by IslandConfig.animSpeedMultiplier.collectAsState()
    val stretchScale by IslandConfig.stretchMaxScale.collectAsState()
    val dormantTimeout by IslandConfig.dormantTimeoutSeconds.collectAsState()
    val notifDurationMs by IslandConfig.notificationExpandDurationMs.collectAsState()
    val forceDormant by IslandConfig.forceDormantPreview.collectAsState()

    // Active State & 3-Stage Engine
    val activeState by IslandConfig.currentState.collectAsState()
    val secondaryState by IslandConfig.secondaryState.collectAsState()
    val currentStage by IslandConfig.currentStage.collectAsState()
    val isExpanded by IslandConfig.isExpanded.collectAsState()
    val isCharging by IslandConfig.isCharging.collectAsState()
    val isMediaPlaying by IslandConfig.isMediaPlaying.collectAsState()
    val isTorchOn by IslandConfig.isTorchOn.collectAsState()
    val isCallActive by IslandConfig.isCallActive.collectAsState()
    val isBatteryOptIgnored by IslandConfig.isBatteryOptimizationIgnored.collectAsState()

    val computedExpandedWidthDp = (detectedScreenWidthDp * expandedPercent).roundToInt()

    var isAccessibilityOn by remember(resumeTick) { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var isNotificationOn by remember(resumeTick) { mutableStateOf(isNotificationListenerEnabled(context)) }
    var isBluetoothOn by remember(resumeTick) { mutableStateOf(isBluetoothConnectGranted(context)) }
    var isCallPermOn by remember(resumeTick) { mutableStateOf(isAnswerCallsGranted(context)) }

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        isBluetoothOn = it
    }
    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        isCallPermOn = isAnswerCallsGranted(context)
    }

    val stageLabel = when (currentStage) {
        IslandStage.STAGE_1_CAMERA_CIRCLE -> "STAGE 1 • CAMERA CIRCLE"
        IslandStage.STAGE_2_CAPSULE -> "STAGE 2 • CAPSULE"
        IslandStage.STAGE_3_EXPANDED -> "STAGE 3 • EXPANDED"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =====================================================================
        // 1. PREMIUM HEADER WITH MINIMAL BLACK & WHITE CAPSULE BRAND LOGO
        // =====================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(14.dp)
                            .border(2.dp, Color.White, RoundedCornerShape(50))
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isAccessibilityOn && masterEnabled) Color(0xFF30D158) else Color(0xFFFF453A))
                        )
                        Text(
                            text = if (!masterEnabled) "MASTER PAUSED"
                            else if (isAccessibilityOn) "OVERLAY ON • $stageLabel"
                            else "STEP 1: ENABLE ACCESSIBILITY BELOW",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Dynamic Island Studio",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (masterEnabled) Color(0xFF30D158).copy(alpha = 0.18f)
                        else Color(0xFFFF453A).copy(alpha = 0.18f)
                    )
                    .border(
                        1.dp,
                        if (masterEnabled) Color(0xFF30D158).copy(alpha = 0.5f) else Color(0xFFFF453A).copy(alpha = 0.5f),
                        RoundedCornerShape(50)
                    )
                    .clickable { IslandConfig.setMasterIslandEnabled(!masterEnabled) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (masterEnabled) Color(0xFF30D158) else Color(0xFFFF453A),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (masterEnabled) "ON" else "OFF",
                        color = if (masterEnabled) Color(0xFF30D158) else Color(0xFFFF453A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // =====================================================================
        // 2. LIVE 3-STAGE & PRIORITY STATUS MONITOR CARD
        // =====================================================================
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF11131A)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stageLabel,
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildString {
                                append("Slot 1: ${activeState.name}")
                                if (secondaryState != null) append("  |  Slot 2 (Circle): ${secondaryState!!.name}")
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallPillActionButton(
                            label = if (isExpanded) "To Stage 2" else "To Stage 3",
                            color = Color(0xFF262936),
                            textColor = Color.White
                        ) {
                            if (activeState == IslandState.IDLE) {
                                IslandConfig.toggleTorchHardware(context)
                            } else {
                                IslandConfig.setExpandedState(!isExpanded)
                            }
                        }
                        if (secondaryState != null) {
                            SmallPillActionButton(
                                label = "Swap Slots",
                                color = Color(0xFF0A84FF),
                                textColor = Color.White
                            ) {
                                IslandConfig.swapSplitStates()
                            }
                        }
                    }
                }

                // Render in-app preview automatically when Accessibility Overlay is off, or when user toggles it on
                if (!isAccessibilityOn || showInAppDuplicatePreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        DynamicIslandUI()
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Real Status-Bar Overlay is active at the top of your screen.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Show In-Card Preview",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showInAppDuplicatePreview = true }
                        )
                    }
                }

                // Quick 3-Stage & Priority Test Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickStageChip("Stage 1 (Camera Circle)", activeState == IslandState.IDLE) {
                        IslandConfig.isCallActive.value = false
                        IslandConfig.isTorchOn.value = false
                        IslandConfig.isMediaPlaying.value = false
                        IslandConfig.hasActiveMediaSession.value = false
                        IslandConfig.isCharging.value = false
                        IslandConfig.isTimerActive.value = false
                        IslandConfig.isStopwatchActive.value = false
                        IslandConfig.isSilentMode.value = false
                        IslandConfig.isVibrateMode.value = false
                        IslandConfig.isDndEnabled.value = false
                        IslandConfig.setExpandedState(false)
                        IslandConfig.restoreBaselineState()
                    }
                    QuickStageChip(if (isTorchOn) "Flashlight ON (Continuous)" else "Turn On Flashlight", isTorchOn) {
                        IslandConfig.toggleTorchHardware(context)
                    }
                    QuickStageChip("3-Task Priority (Call+Torch+Song)", isCallActive && isTorchOn && isMediaPlaying) {
                        // Starts Song + Flashlight + Call simultaneously!
                        // Priority Engine puts Call (Slot 1) + Torch (Slot 2 Circle) and pushes Song out until Call or Torch ends!
                        IslandConfig.musicTitle.value = "Starboy • Live Bass"
                        IslandConfig.musicArtist.value = "The Weeknd"
                        IslandConfig.musicDuration.value = 180_000L
                        IslandConfig.musicProgress.value = 92_000L
                        IslandConfig.isMediaPlaying.value = true
                        IslandConfig.isTorchOn.value = true
                        IslandConfig.callerName.value = "Rohan Verma"
                        IslandConfig.callSubtitle.value = "00:18 • Connected"
                        IslandConfig.isCallIncoming.value = false
                        IslandConfig.isCallActive.value = true
                        IslandConfig.setExpandedState(false)
                        IslandConfig.restoreBaselineState()
                    }
                    QuickStageChip("Silent / DND Idle Capsule", activeState == IslandState.SOUND_MODE || activeState == IslandState.DND) {
                        IslandConfig.isCallActive.value = false
                        IslandConfig.isTorchOn.value = false
                        IslandConfig.isMediaPlaying.value = false
                        IslandConfig.hasActiveMediaSession.value = false
                        IslandConfig.isCharging.value = false
                        IslandConfig.isSilentMode.value = true
                        IslandConfig.soundModeText.value = "Silent"
                        IslandConfig.setExpandedState(false)
                        IslandConfig.restoreBaselineState()
                    }
                }
            }
        }

        // =====================================================================
        // PROMINENT STAGE 2 & STAGE 3 DIMENSION CONTROLS (-200 TO +200 DP)
        // =====================================================================
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141722)),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.28f), RoundedCornerShape(22.dp))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Stage 2 & Stage 3 Dimensions (-200 to +200)",
                            color = Color(0xFF38BDF8),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Fine-tune length (height) and width with -200 to 200 precision",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            IslandConfig.updateStage2WidthOffset(0f)
                            IslandConfig.updateStage2HeightOffset(0f)
                            IslandConfig.updateStage3WidthOffset(0f)
                            IslandConfig.updateStage3HeightOffset(0f)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222636)),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Reset 0", color = Color.White, fontSize = 11.sp)
                    }
                }

                Text(
                    text = "STAGE 2 CAPSULE (Length & Width)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                CalibrationSlider(
                    title = "Stage 2 Width [${(activeCompactW + stage2WidthOff).roundToInt()} dp]",
                    value = stage2WidthOff,
                    range = -200f..200f,
                    unit = "dp"
                ) { IslandConfig.updateStage2WidthOffset(it) }

                CalibrationSlider(
                    title = "Stage 2 Length / Height [${(compactHeight + stage2HeightOff).roundToInt()} dp]",
                    value = stage2HeightOff,
                    range = -200f..200f,
                    unit = "dp"
                ) { IslandConfig.updateStage2HeightOffset(it) }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                Text(
                    text = "STAGE 3 EXPANDED VIEW (Length & Width)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                CalibrationSlider(
                    title = "Stage 3 Width [${(computedExpandedWidthDp + stage3WidthOff).roundToInt()} dp]",
                    value = stage3WidthOff,
                    range = -200f..200f,
                    unit = "dp"
                ) { IslandConfig.updateStage3WidthOffset(it) }

                CalibrationSlider(
                    title = "Stage 3 Length / Height [${stage3HeightOff.roundToInt()} dp]",
                    value = stage3HeightOff,
                    range = -200f..200f,
                    unit = "dp"
                ) { IslandConfig.updateStage3HeightOffset(it) }
            }
        }

        // =====================================================================
        // 3. CATEGORIZED STUDIO NAVIGATION GRID (6 CLEAN CATEGORIES)
        // =====================================================================
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val rows = StudioSection.entries.chunked(3)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { section ->
                        CategoryNavCard(
                            section = section,
                            isSelected = currentTab == section,
                            modifier = Modifier.weight(1f),
                            onClick = { currentTab = section }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. CATEGORY CONTENT PANELS
        // =====================================================================
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn().togetherWith(fadeOut()) },
            label = "tabContent"
        ) { tab ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (tab) {
                    // ---------------------------------------------------------
                    // TAB 1: SETUP PERMISSIONS & 3-STAGE / PRIORITY TEST LAB
                    // ---------------------------------------------------------
                    StudioSection.SETUP_TEST -> {
                        StudioCard(
                            title = "1. Required System & Redmi / MIUI Permissions",
                            subtitle = "Enable Accessibility Overlay & Notification Listener first so the Island turns on over your status bar",
                            accentColor = Color(0xFFFF9F0A)
                        ) {
                            PermissionActionRow(
                                icon = Icons.Rounded.AccessibilityNew,
                                title = "Accessibility Overlay Service (Turn On Island)",
                                subtitle = if (isAccessibilityOn) "Active • Drawing 3-Stage Dynamic Island over status bar" else "Tap Enable -> Installed Apps -> Dynamic Island -> Turn ON",
                                isEnabled = isAccessibilityOn,
                                buttonLabel = if (isAccessibilityOn) "Settings" else "Enable",
                                accentColor = Color(0xFFDA5A36),
                                onClick = onOpenAccessibilitySettings
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            PermissionActionRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "Notification, Call, Clock & Media Sync",
                                subtitle = if (isNotificationOn) "Active • Syncing Calls, Music, Clock & Messages" else "Required for Calls, Music, Timer & Notifications",
                                isEnabled = isNotificationOn,
                                buttonLabel = if (isNotificationOn) "Settings" else "Allow",
                                accentColor = Color(0xFF3897FF),
                                onClick = onOpenNotificationSettings
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            PermissionActionRow(
                                icon = Icons.Rounded.Call,
                                title = "Direct Call Pick / Answer Permission",
                                subtitle = if (isCallPermOn) "Granted • Direct in-island call pick-up active" else "Allow so Pick/Accept button can answer hardware calls directly",
                                isEnabled = isCallPermOn,
                                buttonLabel = if (isCallPermOn) "Granted" else "Grant",
                                accentColor = Color(0xFF30D158),
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        callPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ANSWER_PHONE_CALLS,
                                                Manifest.permission.READ_PHONE_STATE
                                            )
                                        )
                                    }
                                }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            PermissionActionRow(
                                icon = Icons.Rounded.BatteryChargingFull,
                                title = "Unrestricted Background Battery (Anti-Kill)",
                                subtitle = if (isBatteryOptIgnored) "Granted • OS will not kill service in background" else "Tap to stop phone from disabling Accessibility Service",
                                isEnabled = isBatteryOptIgnored,
                                buttonLabel = if (isBatteryOptIgnored) "Granted" else "Allow",
                                accentColor = Color(0xFF30D158),
                                onClick = { IslandConfig.requestIgnoreBatteryOptimizations(context) }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            PermissionActionRow(
                                icon = Icons.Rounded.PhoneAndroid,
                                title = "Redmi / MIUI Autostart Permission",
                                subtitle = "Enable Autostart so MIUI keeps Dynamic Island alive",
                                isEnabled = false,
                                buttonLabel = "Autostart",
                                accentColor = Color(0xFFFF9F0A),
                                onClick = { IslandConfig.openMiuiAutostartSettings(context) }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            PermissionActionRow(
                                icon = Icons.Rounded.PowerSettingsNew,
                                title = "MIUI Battery Saver -> 'No Restrictions'",
                                subtitle = "Select 'No restrictions' inside MIUI Battery Saver screen",
                                isEnabled = false,
                                buttonLabel = "Open",
                                accentColor = Color(0xFF5E5CE6),
                                onClick = { IslandConfig.openMiuiBatterySaverSettings(context) }
                            )

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                PermissionActionRow(
                                    icon = Icons.Rounded.Bluetooth,
                                    title = "Bluetooth Earbud Name Access",
                                    subtitle = if (isBluetoothOn) "Granted • Detects AirPods / Earbuds" else "Allow to display connected earbud names",
                                    isEnabled = isBluetoothOn,
                                    buttonLabel = if (isBluetoothOn) "Granted" else "Grant",
                                    accentColor = Color(0xFF30D158),
                                    onClick = { btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                                )
                            }
                        }

                        StudioCard(
                            title = "2. Priority & 3-Stage Interactive Test Lab",
                            subtitle = "Test 2+ concurrent tasks (Call > Torch > Timer > Music > Charging) and Stage 1 -> 2 -> 3 camera expansion",
                            accentColor = Color(0xFF34D399)
                        ) {
                            ToggleSettingRow(
                                title = "Keep Charging Active (Tests Multi-Task Split Pill)",
                                subtitle = "Simulates charger plugged in—pairs with Music/Torch/Call or yields when 2 higher-priority tasks run",
                                checked = isCharging
                            ) {
                                IslandConfig.isCharging.value = it
                                IslandConfig.batteryLevel.value = 84
                                IslandConfig.chargingWatts.value = "67W Turbo"
                                IslandConfig.restoreBaselineState()
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            FlowRow(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TestStateChip(Icons.Rounded.FlashlightOn, if (isTorchOn) "Torch ON (Tap to Off)" else "Torch ON (Continuous)", isTorchOn) {
                                    IslandConfig.toggleTorchHardware(context)
                                }
                                TestStateChip(Icons.Rounded.Call, "Incoming Call (Stage 3)", activeState == IslandState.CALL && IslandConfig.isCallIncoming.value) {
                                    IslandConfig.triggerIncomingCall("Rohan Verma", "Incoming HD Voice Call", incoming = true)
                                }
                                TestStateChip(Icons.Rounded.PhoneInTalk, "Ongoing Call (Stage 2)", isCallActive && !IslandConfig.isCallIncoming.value) {
                                    IslandConfig.callerName.value = "Rohan Verma"
                                    IslandConfig.isCallActive.value = true
                                    IslandConfig.acceptCall(context)
                                    IslandConfig.restoreBaselineState()
                                }
                                TestStateChip(Icons.Rounded.MusicNote, "Music Playing Wave", isMediaPlaying) {
                                    if (IslandConfig.musicTitle.value == "No Media") {
                                        IslandConfig.musicTitle.value = "Starboy / Heat Waves"
                                        IslandConfig.musicArtist.value = "The Weeknd • Glass Animals"
                                        IslandConfig.musicDuration.value = 177_000L
                                        IslandConfig.musicProgress.value = 105_000L
                                    }
                                    IslandConfig.isMediaPlaying.value = !isMediaPlaying
                                    IslandConfig.hasActiveMediaSession.value = IslandConfig.isMediaPlaying.value
                                    IslandConfig.restoreBaselineState()
                                }
                                TestStateChip(Icons.Rounded.NotificationsActive, "Notification (1->3 Smooth)", activeState == IslandState.NOTIFICATION) {
                                    IslandConfig.triggerNotificationAlert(
                                        appName = "WhatsApp",
                                        title = "Aarav Sharma",
                                        text = "Camera cutout se dono taraf smooth expand ho raha hai bina kisi jhatke ke!",
                                        subText = "Just now",
                                        actionLabel = "Reply"
                                    )
                                }
                                TestStateChip(Icons.Rounded.LockOpen, "Unlock Animation (2.2s)", activeState == IslandState.LOCK) {
                                    IslandConfig.isDeviceLocked.value = false
                                    IslandConfig.showTemporaryState(IslandState.LOCK, 2200L, autoExpand = false)
                                }
                                TestStateChip(Icons.Rounded.NotificationsOff, "Silent Mode (Stage 2 Idle)", IslandConfig.isSilentMode.value) {
                                    val nextSilent = !IslandConfig.isSilentMode.value
                                    IslandConfig.onRingerModeUpdated(
                                        if (nextSilent) android.media.AudioManager.RINGER_MODE_SILENT
                                        else android.media.AudioManager.RINGER_MODE_NORMAL
                                    )
                                }
                                TestStateChip(Icons.Rounded.Vibration, "Vibrate Mode (Stage 2 Idle)", IslandConfig.isVibrateMode.value) {
                                    val nextVib = !IslandConfig.isVibrateMode.value
                                    IslandConfig.onRingerModeUpdated(
                                        if (nextVib) android.media.AudioManager.RINGER_MODE_VIBRATE
                                        else android.media.AudioManager.RINGER_MODE_NORMAL
                                    )
                                }
                                TestStateChip(Icons.Rounded.DarkMode, "DND Mode (Stage 2 Idle)", IslandConfig.isDndEnabled.value) {
                                    val nextDnd = !IslandConfig.isDndEnabled.value
                                    IslandConfig.triggerDndHud(nextDnd, if (nextDnd) "Focus / DND" else "Focus Off")
                                }
                                TestStateChip(Icons.Rounded.Bolt, "Liquid Charge Wave", activeState == IslandState.BATTERY) {
                                    IslandConfig.isCharging.value = true
                                    IslandConfig.batteryLevel.value = 82
                                    IslandConfig.chargingWatts.value = "67W Fast"
                                    IslandConfig.batteryTempCelsius.value = "36.2°C"
                                    IslandConfig.batteryTimeRemaining.value = "18m to full"
                                    IslandConfig.restoreBaselineState()
                                    IslandConfig.setExpandedState(true)
                                }
                                TestStateChip(Icons.Rounded.LocalCafe, "Coffee Timer (03:27)", IslandConfig.isTimerActive.value) {
                                    if (IslandConfig.isTimerActive.value) IslandConfig.stopTimer()
                                    else IslandConfig.startTimer(207, "Brewing Espresso...")
                                }
                                TestStateChip(Icons.Rounded.Timer, "Stopwatch", IslandConfig.isStopwatchActive.value) {
                                    if (IslandConfig.isStopwatchActive.value) IslandConfig.stopStopwatch()
                                    else IslandConfig.startStopwatch("Stopwatch Running ...")
                                }
                                TestStateChip(Icons.Rounded.TurnRight, "Maps Nav (90m)", IslandConfig.isNavigationActive.value) {
                                    if (IslandConfig.isNavigationActive.value) IslandConfig.stopNavigation()
                                    else IslandConfig.startNavigation("90m", "Turn Right • MG Road", "12 min • 4.2 km")
                                }
                                TestStateChip(Icons.Rounded.Key, "Smart OTP", activeState == IslandState.OTP) {
                                    IslandConfig.triggerOtpAlert("482910", "HDFC Bank Verification")
                                }
                                TestStateChip(Icons.Rounded.Download, "Download 68%", IslandConfig.isDownloadActive.value) {
                                    if (IslandConfig.isDownloadActive.value) IslandConfig.dismissDownload()
                                    else IslandConfig.updateDownloadProgress("NothingOS_3.2_OTA.zip", "Chrome", 68)
                                }
                                TestStateChip(Icons.Rounded.FiberManualRecord, "Screen Recording", IslandConfig.isRecordingActive.value) {
                                    if (IslandConfig.isRecordingActive.value) IslandConfig.stopScreenRecording()
                                    else IslandConfig.startScreenRecording()
                                }
                                TestStateChip(Icons.Rounded.Headphones, "Earbuds Connect", activeState == IslandState.EARBUDS) {
                                    IslandConfig.earbudsName.value = "Nothing Ear (2)"
                                    IslandConfig.earbudsBattery.value = 100
                                    IslandConfig.showTemporaryState(IslandState.EARBUDS, 4000L, autoExpand = true)
                                }
                                TestStateChip(Icons.Rounded.PowerSettingsNew, "Reset to Stage 1 Circle", currentStage == IslandStage.STAGE_1_CAMERA_CIRCLE, isDestructive = true) {
                                    IslandConfig.isCallActive.value = false
                                    IslandConfig.isTorchOn.value = false
                                    IslandConfig.isNavigationActive.value = false
                                    IslandConfig.isRecordingActive.value = false
                                    IslandConfig.isTimerActive.value = false
                                    IslandConfig.isStopwatchActive.value = false
                                    IslandConfig.isDownloadActive.value = false
                                    IslandConfig.isCharging.value = false
                                    IslandConfig.isMediaPlaying.value = false
                                    IslandConfig.hasActiveMediaSession.value = false
                                    IslandConfig.isSilentMode.value = false
                                    IslandConfig.isVibrateMode.value = false
                                    IslandConfig.isDndEnabled.value = false
                                    IslandConfig.setExpandedState(false)
                                    IslandConfig.restoreBaselineState()
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // TAB 2: FEATURES HUB (ENABLE / DISABLE EVERY FEATURE!)
                    // ---------------------------------------------------------
                    StudioSection.FEATURES -> {
                        StudioCard(
                            title = "Master Control & Quick Presets",
                            subtitle = "Enable or disable the entire Dynamic Island or individual modules anytime",
                            accentColor = Color(0xFF34D399)
                        ) {
                            ToggleSettingRow(
                                title = "Master Dynamic Island Overlay",
                                subtitle = "Turn OFF to completely hide Dynamic Island across all apps",
                                checked = masterEnabled,
                                onCheckedChange = { IslandConfig.setMasterIslandEnabled(it) }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { IslandConfig.setAllFeaturesEnabled(true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158).copy(alpha = 0.22f)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Enable All Features", color = Color(0xFF30D158), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { IslandConfig.setAllFeaturesEnabled(false) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A).copy(alpha = 0.20f)),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Disable All", color = Color(0xFFFF453A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        StudioCard(
                            title = "Core Priority Modules (Individual Toggles)",
                            subtitle = "All modules can be individually enabled or disabled",
                            accentColor = Color(0xFF38BDF8)
                        ) {
                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Call,
                                iconTint = Color(0xFF30D158),
                                title = "Incoming & Ongoing Call Island (Priority #1)",
                                subtitle = "Direct Pick (Accept), Decline, Speaker & Mute controls",
                                checked = featCall
                            ) { IslandConfig.setFeatureCallEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.FlashlightOn,
                                iconTint = Color(0xFFFACC15),
                                title = "Flashlight / Torch Continuous Guard (Priority #2)",
                                subtitle = "Stays continuously active on Island whenever Flashlight is ON",
                                checked = featTorch
                            ) { IslandConfig.setFeatureTorchEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Timer,
                                iconTint = Color(0xFFE85D1A),
                                title = "Timer & Stopwatch Live Countdown",
                                subtitle = "Syncs with Google Clock / Native Timer & Stopwatch",
                                checked = featTimer
                            ) { IslandConfig.setFeatureTimerStopwatchEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.MusicNote,
                                iconTint = Color(0xFFE866B7),
                                title = "Music Player & Audio Visualizer",
                                subtitle = "Live progress, reactive mist wave/shake & 6 seekbar styles",
                                checked = featMusic
                            ) { IslandConfig.setFeatureMusicEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.NotificationsActive,
                                iconTint = Color(0xFF38BDF8),
                                title = "Smart Notification Popup Island",
                                subtitle = "Camera-centered expanding notification card with app icon & actions",
                                checked = featNotif
                            ) { IslandConfig.setFeatureNotificationEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Bolt,
                                iconTint = Color(0xFF34D399),
                                title = "Charging & Liquid Wave Battery Island",
                                subtitle = "6 animated charging effects (Liquid Wave, Neon Pulse, Particles)",
                                checked = featBattery
                            ) { IslandConfig.setFeatureBatteryEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Vibration,
                                iconTint = Color(0xFFFF9F0A),
                                title = "Silent & Vibrate Stage-2 Display",
                                subtitle = "Displays Silent/Vibrate in Stage 2 Capsule when nothing else is running",
                                checked = featSilent
                            ) { IslandConfig.setFeatureSilentVibrateEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.DarkMode,
                                iconTint = Color(0xFF5E5CE6),
                                title = "Focus / DND Stage-2 Display",
                                subtitle = "Displays Focus/DND in Stage 2 Capsule when idle",
                                checked = featDnd
                            ) { IslandConfig.setFeatureDndEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.LockOpen,
                                iconTint = Color(0xFFE2E8F0),
                                title = "Screen Unlock Priority Animation",
                                subtitle = "Brief priority unlock capsule animation when waking device",
                                checked = featLock
                            ) { IslandConfig.setFeatureLockUnlockEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.TurnRight,
                                iconTint = Color(0xFF0A84FF),
                                title = "Google Maps Turn-by-Turn Navigation",
                                subtitle = "Live route distance, turn direction & ETA in island",
                                checked = featNav
                            ) { IslandConfig.setFeatureNavigationEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Key,
                                iconTint = Color(0xFFFF9F0A),
                                title = "Smart OTP Auto-Extractor",
                                subtitle = "Detects 4-8 digit verification codes with one-tap Copy button",
                                checked = featOtp
                            ) { IslandConfig.setFeatureOtpEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Headphones,
                                iconTint = Color(0xFF34D399),
                                title = "Bluetooth Earbuds / AirPods HUD",
                                subtitle = "Animated connection popup with device name & battery ring",
                                checked = featEarbuds
                            ) { IslandConfig.setFeatureEarbudsEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.Download,
                                iconTint = Color(0xFF0A84FF),
                                title = "File Download Progress Ring",
                                subtitle = "Live percentage ring & progress bar for ongoing downloads",
                                checked = featDownload
                            ) { IslandConfig.setFeatureDownloadEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            FeatureModuleToggleRow(
                                icon = Icons.Rounded.FiberManualRecord,
                                iconTint = Color(0xFFFF453A),
                                title = "Screen Recording Live Timer",
                                subtitle = "Elapsed recording counter and stop control",
                                checked = featRecord
                            ) { IslandConfig.setFeatureRecordingEnabled(it) }
                        }

                        StudioCard(
                            title = "Smart Fixes & Pro Enhancements",
                            subtitle = "All special hardware fixes and extra pro features (each toggleable)",
                            accentColor = Color(0xFFFF9F0A)
                        ) {
                            ToggleSettingRow(
                                title = "Display Silent / Vibrate / DND When Stage 1 Idle",
                                subtitle = "When no task is running on Stage 1, automatically show Silent, Vibrate, or DND in the Stage 2 Capsule",
                                checked = keepSilentIdle
                            ) { IslandConfig.setKeepSilentVibrateWhenIdle(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Anti-Flash Notification Blink Guard",
                                subtitle = "Ignores brief notification camera-flash blinks while keeping manual Flashlight instant & continuous",
                                checked = ignoreFlashBlink
                            ) { IslandConfig.setIgnoreNotificationFlashBlink(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Always Show Call Pick / Accept Button",
                                subtitle = "Guarantees the green Pick/Accept button is always visible on Call Island in every situation",
                                checked = alwaysCallAccept
                            ) { IslandConfig.setAlwaysShowCallAcceptButton(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Audio-Reactive Mist Wave & Shake on Play",
                                subtitle = "Mist waves and shakes dynamically when music plays, and calms down when paused",
                                checked = reactiveMusicWave
                            ) { IslandConfig.setReactiveMusicMistWave(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Auto-Expand Liquid Wave on Charger Plug-In",
                                subtitle = "Automatically expands the charging wave card when power is connected",
                                checked = chargeAutoExpand
                            ) { IslandConfig.setChargingAutoExpandOnPlug(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Tactile Haptic Feedback",
                                subtitle = "Subtle vibration tick when expanding, swapping, or controlling the island",
                                checked = hapticsEnabled
                            ) { IslandConfig.setHapticFeedbackEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Notification Edge Halo Glow",
                                subtitle = "Boosts neon rim glow when notifications or calls arrive",
                                checked = edgeGlowNotif
                            ) { IslandConfig.setEdgeGlowPulseOnNotification(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Low Battery Warning Pulse (20% / 15% / 10%)",
                                subtitle = "Flashes battery alert island when battery drops to critical thresholds",
                                checked = lowBatAlert
                            ) { IslandConfig.setBatteryLowAlertEnabled(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Auto-Collapse on App Launch",
                                subtitle = "Smoothly collapses expanded card after opening a notification or clock app",
                                checked = autoCollapseApp
                            ) { IslandConfig.setAutoCollapseOnAppOpen(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                            ToggleSettingRow(
                                title = "Auto-Hide in Fullscreen Landscape",
                                subtitle = "Hides the island during landscape games and fullscreen videos",
                                checked = hideLandscape
                            ) { IslandConfig.setHideInLandscapeEnabled(it) }
                        }
                    }

                    // ---------------------------------------------------------
                    // TAB 3: CHARGING WAVE EFFECTS & MUSIC SLIDER DESIGNS
                    // ---------------------------------------------------------
                    StudioSection.STYLES -> {
                        StudioCard(
                            title = "Charging Wave & Liquid Fill Effects (6 Styles)",
                            subtitle = "Select how the Dynamic Island animates while your phone is charging",
                            accentColor = Color(0xFF30D158)
                        ) {
                            ChargingEffectStyle.entries.forEachIndexed { idx, style ->
                                StyleOptionRow(
                                    title = style.label,
                                    subtitle = style.subtitle,
                                    isSelected = chargingStyleIdx == idx,
                                    accentColor = Color(0xFF30D158),
                                    onClick = {
                                        IslandConfig.setChargingEffectStyle(idx)
                                        IslandConfig.isCharging.value = true
                                        IslandConfig.batteryLevel.value = 78
                                        IslandConfig.chargingWatts.value = "67W HyperCharge"
                                        IslandConfig.restoreBaselineState()
                                        IslandConfig.setExpandedState(true)
                                    }
                                )
                            }
                        }

                        StudioCard(
                            title = "Music Seekbar / Slider Designs (6 Styles)",
                            subtitle = "Choose your favorite interactive music progress bar design",
                            accentColor = Color(0xFFE866B7)
                        ) {
                            MusicSliderStyle.entries.forEachIndexed { idx, style ->
                                StyleOptionRow(
                                    title = style.label,
                                    subtitle = style.subtitle,
                                    isSelected = musicSliderIdx == idx,
                                    accentColor = Color(0xFFE866B7),
                                    onClick = {
                                        IslandConfig.setMusicSliderStyle(idx)
                                        if (IslandConfig.musicTitle.value == "No Media") {
                                            IslandConfig.musicTitle.value = "Starboy • Live Bass"
                                            IslandConfig.musicArtist.value = "The Weeknd"
                                            IslandConfig.musicDuration.value = 180_000L
                                            IslandConfig.musicProgress.value = 96_000L
                                        }
                                        IslandConfig.isMediaPlaying.value = true
                                        IslandConfig.hasActiveMediaSession.value = true
                                        IslandConfig.restoreBaselineState()
                                        IslandConfig.setExpandedState(true)
                                    }
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            ToggleSettingRow(
                                title = "Audio-Reactive Mist Wave & Shake",
                                subtitle = "Mist waves & shakes when music plays and calms down when paused",
                                checked = reactiveMusicWave
                            ) { IslandConfig.setReactiveMusicMistWave(it) }

                            CalibrationSlider(
                                title = "Music Play Wave / Shake Intensity",
                                value = musicShakeIntensity * 100f,
                                range = 20f..200f,
                                unit = "%"
                            ) {
                                IslandConfig.updateMusicWaveShakeIntensity(it / 100f)
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // TAB 4: MIST & GLASS SHADER
                    // ---------------------------------------------------------
                    StudioSection.MIST_GLASS -> {
                        StudioCard(
                            title = "Fluid Frosted Mist & Smoke Shader",
                            subtitle = "Customize glass opacity, smoke plumes, album art tinting & themes",
                            accentColor = Color(0xFF38BDF8)
                        ) {
                            ToggleSettingRow(
                                title = "Enable Fluid Frosted Mist Glass Effect",
                                subtitle = "Turn OFF to switch Island to classic pure pitch-black AMOLED mode",
                                checked = useFrostedGlass
                            ) { IslandConfig.setFrostedGlassStyle(it) }

                            ToggleSettingRow(
                                title = "Sync Smoke Plume Colors with Song / Task",
                                subtitle = "Automatically tints moving mist with song album art & active state",
                                checked = syncArt
                            ) { IslandConfig.setSyncMistWithAlbumArt(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text("Manual Mist Color Themes", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MistThemePreset.entries.forEachIndexed { idx, preset ->
                                    TestStateChip(
                                        icon = Icons.Rounded.Palette,
                                        label = preset.label,
                                        isSelected = mistPresetIdx == idx,
                                        onClick = { IslandConfig.setMistThemePreset(idx) }
                                    )
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            CalibrationSlider("Base Glass Opacity / Darkness", bgAlpha * 100f, 35f..100f, "%") {
                                IslandConfig.updateIslandBgAlpha(it / 100f)
                            }
                            CalibrationSlider("Smoke Mist Plume Intensity", smokeIntensity * 100f, 0f..100f, "%") {
                                IslandConfig.updateMistSmokeIntensity(it / 100f)
                            }
                            CalibrationSlider("Mist Softness / Diffusion Spread", blurRadius, 8f..54f, "dp") {
                                IslandConfig.updateMistBlurRadius(it)
                            }
                            CalibrationSlider("Fluid Smoke Drift Speed", mistSpeed * 100f, 20f..250f, "%") {
                                IslandConfig.updateMistAnimSpeed(it / 100f)
                            }
                            CalibrationSlider("Glass Border Rim Glow", borderGlow * 100f, 0f..100f, "%") {
                                IslandConfig.updateBorderGlowAlpha(it / 100f)
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // TAB 5: CUTOUT & SIZE CALIBRATION
                    // ---------------------------------------------------------
                    StudioSection.SIZE_CUTOUT -> {
                        StudioCard(
                            title = "Phone Hardware Presets",
                            subtitle = "One-tap alignment presets or reset to default",
                            accentColor = Color(0xFF34D399)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DevicePreset.entries.forEach { preset ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1E2230))
                                            .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                                            .clickable { IslandConfig.applyPreset(preset) }
                                            .padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Rounded.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text(preset.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = { IslandConfig.resetAllCustomizationsToDefault() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF453A).copy(alpha = 0.20f)),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset All Customizations to Default", color = Color(0xFFFF453A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        StudioCard(
                            title = "3-Stage Cutout Position & Dimensions",
                            subtitle = "Screen: ${detectedScreenWidthDp.roundToInt()} dp • Stage 3 Expanded: $computedExpandedWidthDp dp",
                            accentColor = Color(0xFF38BDF8)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { IslandConfig.autoFitToScreenWidth(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158).copy(alpha = 0.22f)),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("Auto-Fit Screen", color = Color(0xFF30D158), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            CalibrationSlider("Camera Cutout Left / Right (X Offset)", xOffset, -240f..240f, "px") { IslandConfig.updateXOffset(it) }
                            CalibrationSlider("Camera Cutout Up / Down (Y Offset)", yOffset, -60f..200f, "px") { IslandConfig.updateYOffset(it) }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text("Stage 2 Capsule Length & Width Sliders (-200 to +200)", color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            CalibrationSlider("Stage 2 Width [${(activeCompactW + stage2WidthOff).roundToInt()} dp]", stage2WidthOff, -200f..200f, "dp") {
                                IslandConfig.updateStage2WidthOffset(it)
                            }
                            CalibrationSlider("Stage 2 Length / Height [${(compactHeight + stage2HeightOff).roundToInt()} dp]", stage2HeightOff, -200f..200f, "dp") {
                                IslandConfig.updateStage2HeightOffset(it)
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text("Stage 3 Expanded Length & Width Sliders (-200 to +200)", color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            CalibrationSlider("Stage 3 Width [${(computedExpandedWidthDp + stage3WidthOff).roundToInt()} dp]", stage3WidthOff, -200f..200f, "dp") {
                                IslandConfig.updateStage3WidthOffset(it)
                            }
                            CalibrationSlider("Stage 3 Length / Height [${stage3HeightOff.roundToInt()} dp]", stage3HeightOff, -200f..200f, "dp") {
                                IslandConfig.updateStage3HeightOffset(it)
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            Text("Base Stage 1 (Camera Circle) & Split Circle ('Tukda')", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            CalibrationSlider("Stage 1 Camera Circle Size", dormantCircleSize, 16f..44f, "dp") { IslandConfig.updateDormantCircleSize(it) }
                            CalibrationSlider("Split Detached Circle ('Tukda') Size", splitCircleSize, 24f..52f, "dp") { IslandConfig.updateSplitCircleSize(it) }
                            CalibrationSlider("Split Circle Gap", splitGap, 2f..16f, "dp") { IslandConfig.updateSplitGap(it) }
                            CalibrationSlider("Base Stage 2 Capsule Width", activeCompactW, 140f..280f, "dp") { IslandConfig.updateActiveCompactWidth(it) }
                            CalibrationSlider("Base Stage 2 Capsule Height", compactHeight, 22f..56f, "dp") { IslandConfig.updateCompactHeight(it) }
                            CalibrationSlider("Stage 2 Capsule Corner Radius", compactCorner, 10f..32f, "dp") {
                                IslandConfig.updateCompactCornerRadius(it)
                            }
                            CalibrationSlider("Stage 3 Expanded Corner Radius", expandedCorner, 18f..52f, "dp") {
                                IslandConfig.updateExpandedCornerRadius(it)
                            }
                            CalibrationSlider("Inner Icon & Element Scale", contentScale * 100f, 80f..130f, "%") {
                                IslandConfig.updateContentScale(it / 100f)
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // TAB 6: MOTION PHYSICS & 3-STAGE RULES
                    // ---------------------------------------------------------
                    StudioSection.MOTION_RULES -> {
                        StudioCard(
                            title = "Camera-Centered Smooth Expansion (Zero Jhatka)",
                            subtitle = "Single unified shell morphs symmetrically left & right from the camera cutout across Stage 1, 2, and 3",
                            accentColor = Color(0xFFA855F7)
                        ) {
                            CalibrationSlider("Spring Smoothness / Damping", bounceLevel * 100f, 40f..100f, "%") {
                                IslandConfig.updateAnimBounceLevel(it / 100f)
                            }
                            CalibrationSlider("Stage 1 <-> 2 <-> 3 Morph Speed", speedMult * 100f, 50f..200f, "%") {
                                IslandConfig.updateAnimSpeedMultiplier(it / 100f)
                            }
                            CalibrationSlider("Horizontal Stretch Elasticity", stretchScale * 100f, 108f..142f, "%") {
                                IslandConfig.updateStretchMaxScale(it / 100f)
                            }
                            CalibrationSlider("Idle Timeout", dormantTimeout.toFloat(), 15f..300f, "sec") {
                                IslandConfig.updateDormantTimeoutSeconds(it.roundToInt())
                            }
                            CalibrationSlider("Notification Auto-Expand Duration", (notifDurationMs / 1000f), 2f..10f, "sec") {
                                IslandConfig.updateNotificationExpandDurationMs((it * 1000f).toLong())
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                            ToggleSettingRow(
                                title = "Show Stage 1 Micro-Circle Behind Camera",
                                subtitle = "Keeps the small Stage 1 circle anchored behind the camera cutout ready to expand",
                                checked = alwaysShowIdle
                            ) { IslandConfig.setAlwaysShowIdle(it) }

                            ToggleSettingRow(
                                title = "Force Stage 1 Camera Circle Preview",
                                subtitle = "Temporarily force Stage 1 micro-circle to calibrate its position behind your camera",
                                checked = forceDormant
                            ) {
                                IslandConfig.forceDormantPreview.value = it
                                IslandConfig.isDormantCircleMode.value = it
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(36.dp))
    }
}

@Composable
private fun CategoryNavCard(
    section: StudioSection,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgBrush = if (isSelected) {
        Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF12141C), Color(0xFF12141C)))
    }
    val borderColor = if (isSelected) Color(0xFF34D399) else Color.White.copy(alpha = 0.08f)
    val iconColor = if (isSelected) Color(0xFF34D399) else Color(0xFF94A3B8)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(if (isSelected) 1.2.dp else 0.8.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(section.icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Text(
                text = section.label,
                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = section.subtitle,
                color = if (isSelected) Color(0xFF34D399) else Color(0xFF64748B),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StudioCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12141C)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.8.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            content()
        }
    }
}

@Composable
private fun FeatureModuleToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = if (checked) 0.20f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (checked) iconTint else Color(0xFF64748B), modifier = Modifier.size(20.dp))
            }
            Column {
                Text(title, color = if (checked) Color.White else Color(0xFF94A3B8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF64748B), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF30D158),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF222634)
            )
        )
    }
}

@Composable
private fun StyleOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.14f) else Color(0xFF191C26))
            .border(
                width = if (isSelected) 1.2.dp else 0.8.dp,
                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) accentColor else Color(0xFF262A38)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun QuickStageChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isActive) Color(0xFF34D399).copy(alpha = 0.22f) else Color(0xFF1D202B))
            .border(
                0.8.dp,
                if (isActive) Color(0xFF34D399) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isActive) Color(0xFF34D399) else Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SmallPillActionButton(
    label: String,
    color: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    buttonLabel: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (isEnabled) Icons.Rounded.CheckCircle else icon, null, tint = if (isEnabled) Color(0xFF30D158) else accentColor, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF8E8E93), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = if (isEnabled) Color(0xFF25252C) else accentColor),
            shape = RoundedCornerShape(50)
        ) {
            Text(buttonLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TestStateChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isDestructive -> Color(0xFFFF453A).copy(alpha = 0.20f)
        isSelected -> Color.White
        else -> Color(0xFF1D202B)
    }
    val contentColor = when {
        isDestructive -> Color(0xFFFF453A)
        isSelected -> Color.Black
        else -> Color.White
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(0.8.dp, if (isDestructive) Color(0xFFFF453A) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(label, color = contentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CalibrationSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.LightGray, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF26262E))
                        .clickable { onChange((value - 1f).coerceIn(range.start, range.endInclusive)) },
                    contentAlignment = Alignment.Center
                ) { Text("-", color = Color.White, fontWeight = FontWeight.Bold) }
                Text("${value.roundToInt()} $unit", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF26262E))
                        .clickable { onChange((value + 1f).coerceIn(range.start, range.endInclusive)) },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF34D399),
                inactiveTrackColor = Color(0xFF262A38)
            )
        )
    }
}

@Composable
private fun ToggleSettingRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF30D158),
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF222634)
            )
        )
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                it.resolveInfo?.serviceInfo?.name?.contains("IslandAccessibilityService") == true
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

private fun isBluetoothConnectGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else true
}

private fun isAnswerCallsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
    } else true
}
