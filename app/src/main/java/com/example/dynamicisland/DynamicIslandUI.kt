@file:Suppress("DEPRECATION")

package com.example.dynamicisland

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

private val ObsidianBlack = Color(0xFF000000)
private val DeepMistBase = Color(0xFF06090E)
private val CrispWhite = Color(0xFFFFFFFF)
private val AlabasterText = Color(0xFFEBEBF5)
private val SubtleSilver = Color(0xFF94A3B8)
private val VibrantEmerald = Color(0xFF30D158)
private val WarmAmber = Color(0xFFFF9F0A)
private val FlameOrange = Color(0xFFE85D1A)
private val AmberActionBg = Color(0xFF6B4C23)
private val DarkControlBg = Color(0xFF2C2C34)
private val CrimsonAlert = Color(0xFFFF453A)
private val CobaltBlue = Color(0xFF0A84FF)
private val IndigoFocus = Color(0xFF5E5CE6)
private val RoseWaveform = Color(0xFFE866B7)
private val CyberCyan = Color(0xFF38BDF8)

@Composable
private fun rememberMistPlumeColors(state: IslandState): Pair<Color, Color> {
    val syncArt by IslandConfig.syncMistWithAlbumArt.collectAsState()
    val presetIdx by IslandConfig.mistThemePresetIndex.collectAsState()
    val extractedPri by IslandConfig.extractedAccentColor.collectAsState()
    val extractedSec by IslandConfig.extractedSecondaryColor.collectAsState()

    val preset = MistThemePreset.entries.getOrElse(presetIdx) { MistThemePreset.EMERALD_BOTANICAL }

    val (targetPri, targetSec) = remember(state, syncArt, preset, extractedPri, extractedSec) {
        if (!syncArt) {
            Color(preset.primaryArgb) to Color(preset.secondaryArgb)
        } else {
            when (state) {
                IslandState.MUSIC -> Color(extractedPri) to Color(extractedSec)
                IslandState.TIMER -> FlameOrange to WarmAmber
                IslandState.STOPWATCH -> VibrantEmerald to Color(0xFF14B8A6)
                IslandState.CALL -> VibrantEmerald to Color(0xFF0EA5E9)
                IslandState.NAVIGATION -> CobaltBlue to VibrantEmerald
                IslandState.BATTERY -> VibrantEmerald to CyberCyan
                IslandState.RECORDING -> CrimsonAlert to FlameOrange
                IslandState.TORCH -> WarmAmber to Color(0xFFFACC15)
                IslandState.OTP -> WarmAmber to VibrantEmerald
                IslandState.DOWNLOAD -> CobaltBlue to IndigoFocus
                IslandState.DND -> IndigoFocus to Color(0xFFC084FC)
                IslandState.SOUND_MODE -> WarmAmber to CrimsonAlert
                IslandState.NOTIFICATION -> CyberCyan to Color(0xFF818CF8)
                else -> Color(preset.primaryArgb) to Color(preset.secondaryArgb)
            }
        }
    }

    val animPri by animateColorAsState(targetPri, tween(550, easing = FastOutSlowInEasing), label = "mistPri")
    val animSec by animateColorAsState(targetSec, tween(550, easing = FastOutSlowInEasing), label = "mistSec")
    return animPri to animSec
}

/**
 * Fluid Frosted Mist Backdrop with:
 * 1. Audio-Reactive Wave & Shake when Music is Playing (and smooth calm-down when Paused!)
 * 2. Liquid Wave Fill & Charging Aura when Battery is Charging!
 */
@Composable
fun FluidFrostedMistBackdrop(
    state: IslandState,
    modifier: Modifier = Modifier
) {
    val useFrosted by IslandConfig.useFrostedGlassStyle.collectAsState()
    val bgAlpha by IslandConfig.islandBgAlpha.collectAsState()
    val smokeIntensity by IslandConfig.mistSmokeIntensity.collectAsState()
    val blurSpreadDp by IslandConfig.mistBlurRadiusDp.collectAsState()
    val animSpeed by IslandConfig.mistAnimSpeed.collectAsState()

    val isMediaPlaying by IslandConfig.isMediaPlaying.collectAsState()
    val reactiveMusicWave by IslandConfig.reactiveMusicMistWave.collectAsState()
    val musicShakeIntensity by IslandConfig.musicWaveShakeIntensity.collectAsState()

    val isCharging by IslandConfig.isCharging.collectAsState()
    val batteryPct by IslandConfig.batteryLevel.collectAsState()
    val chargingStyleIdx by IslandConfig.chargingEffectStyleIndex.collectAsState()

    val (primaryTint, secondaryTint) = rememberMistPlumeColors(state)

    val isMusicActiveHere = (state == IslandState.MUSIC) && isMediaPlaying && reactiveMusicWave
    val musicWaveFactor by animateFloatAsState(
        targetValue = if (isMusicActiveHere) musicShakeIntensity.coerceIn(0.2f, 2.0f) else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "musicWaveFactor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "fluidSmokeEngine")
    val durationMs = (9000f / animSpeed.coerceIn(0.2f, 2.5f)).toInt().coerceAtLeast(1800)

    val phaseA by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "phaseA"
    )
    val phaseB by infiniteTransition.animateFloat(
        initialValue = (2f * PI).toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween((durationMs * 1.32f).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "phaseB"
    )
    val fastWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(720, easing = LinearEasing), RepeatMode.Restart),
        label = "fastWavePhase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val maxDim = maxOf(w, h)

        if (!useFrosted || state == IslandState.IDLE) {
            drawRect(color = ObsidianBlack)
            return@Canvas
        }

        drawRect(color = DeepMistBase.copy(alpha = bgAlpha.coerceIn(0.35f, 1f)))

        val spreadFactor = (blurSpreadDp / 26f).coerceIn(0.45f, 2.1f)
        val effectiveSmoke = (smokeIntensity * 0.58f).coerceIn(0f, 0.88f)

        val beatShakeX = if (musicWaveFactor > 0.01f) {
            w * 0.065f * musicWaveFactor * sin(fastWavePhase * 2f)
        } else 0f
        val beatShakeY = if (musicWaveFactor > 0.01f) {
            h * 0.11f * musicWaveFactor * cos(fastWavePhase * 3f)
        } else 0f
        val beatPulseScale = if (musicWaveFactor > 0.01f) {
            1f + 0.18f * musicWaveFactor * ((sin(fastWavePhase * 2f) + 1f) * 0.5f)
        } else 1f

        if (effectiveSmoke > 0.01f) {
            val plume1 = Offset(
                x = (w * (0.24f + 0.22f * cos(phaseA)) + beatShakeX).coerceIn(0f, w),
                y = (h * (0.36f + 0.24f * sin(phaseA * 1.15f)) + beatShakeY).coerceIn(0f, h)
            )
            val plume2 = Offset(
                x = (w * (0.76f + 0.20f * sin(phaseB)) - beatShakeX * 0.9f).coerceIn(0f, w),
                y = (h * (0.64f + 0.24f * cos(phaseB * 1.1f)) - beatShakeY * 0.8f).coerceIn(0f, h)
            )
            val plume3 = Offset(
                x = (w * (0.50f + 0.27f * sin(phaseA + 1.3f)) + beatShakeX * 0.6f).coerceIn(0f, w),
                y = (h * (0.26f + 0.22f * cos(phaseB)) - beatShakeY * 0.6f).coerceIn(0f, h)
            )
            val plume4 = Offset(
                x = (w * (0.38f + 0.24f * cos(phaseB + 0.9f)) - beatShakeX * 0.5f).coerceIn(0f, w),
                y = (h * (0.76f + 0.18f * sin(phaseA)) + beatShakeY * 0.7f).coerceIn(0f, h)
            )

            val r1 = maxDim * 0.72f * spreadFactor * beatPulseScale
            val r2 = maxDim * 0.68f * spreadFactor * beatPulseScale

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryTint.copy(alpha = (effectiveSmoke * (1f + 0.22f * musicWaveFactor)).coerceAtMost(0.95f)),
                        primaryTint.copy(alpha = effectiveSmoke * 0.42f),
                        Color.Transparent
                    ),
                    center = plume1,
                    radius = r1
                ),
                radius = r1,
                center = plume1
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryTint.copy(alpha = (effectiveSmoke * 0.90f * (1f + 0.20f * musicWaveFactor)).coerceAtMost(0.92f)),
                        secondaryTint.copy(alpha = effectiveSmoke * 0.35f),
                        Color.Transparent
                    ),
                    center = plume2,
                    radius = r2
                ),
                radius = r2,
                center = plume2
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrispWhite.copy(alpha = effectiveSmoke * 0.24f),
                        primaryTint.copy(alpha = effectiveSmoke * 0.16f),
                        Color.Transparent
                    ),
                    center = plume3,
                    radius = maxDim * 0.54f * spreadFactor
                ),
                radius = maxDim * 0.54f * spreadFactor,
                center = plume3
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryTint.copy(alpha = effectiveSmoke * 0.50f),
                        Color.Transparent
                    ),
                    center = plume4,
                    radius = maxDim * 0.48f * spreadFactor
                ),
                radius = maxDim * 0.48f * spreadFactor,
                center = plume4
            )
        }

        if (musicWaveFactor > 0.02f) {
            val wavePath = Path()
            val steps = 28
            val stepW = w / steps
            val waveAmp = (h * 0.22f * musicWaveFactor).coerceAtMost(26f)
            val baseLineY = h * 0.56f

            wavePath.moveTo(0f, h)
            wavePath.lineTo(0f, baseLineY)
            for (i in 0..steps) {
                val x = i * stepW
                val angle = (i.toFloat() / steps) * (2.5f * PI).toFloat() + fastWavePhase * 2f
                val y = baseLineY + sin(angle) * waveAmp + cos(angle * 1.5f - fastWavePhase) * (waveAmp * 0.45f)
                wavePath.lineTo(x, y)
            }
            wavePath.lineTo(w, h)
            wavePath.close()

            drawPath(
                path = wavePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryTint.copy(alpha = (0.34f * musicWaveFactor).coerceIn(0f, 0.55f)),
                        secondaryTint.copy(alpha = (0.18f * musicWaveFactor).coerceIn(0f, 0.35f)),
                        Color.Transparent
                    )
                )
            )

            val crestPath = Path()
            for (i in 0..steps) {
                val x = i * stepW
                val angle = (i.toFloat() / steps) * (2.5f * PI).toFloat() + fastWavePhase * 2f
                val y = baseLineY + sin(angle) * waveAmp + cos(angle * 1.5f - fastWavePhase) * (waveAmp * 0.45f)
                if (i == 0) crestPath.moveTo(x, y) else crestPath.lineTo(x, y)
            }
            drawPath(
                path = crestPath,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        primaryTint.copy(alpha = (0.55f * musicWaveFactor).coerceIn(0f, 0.78f)),
                        CrispWhite.copy(alpha = (0.45f * musicWaveFactor).coerceIn(0f, 0.65f)),
                        secondaryTint.copy(alpha = (0.55f * musicWaveFactor).coerceIn(0f, 0.78f)),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        if (state == IslandState.BATTERY && isCharging) {
            val style = ChargingEffectStyle.entries.getOrElse(chargingStyleIdx) { ChargingEffectStyle.LIQUID_WAVE }
            if (style == ChargingEffectStyle.LIQUID_WAVE || style == ChargingEffectStyle.AURORA_GLOW) {
                val fillFraction = (batteryPct / 100f).coerceIn(0.12f, 0.96f)
                val fillWidth = w * fillFraction
                val liqPath = Path()
                val steps = 20
                val stepH = h / steps
                liqPath.moveTo(0f, 0f)
                for (i in 0..steps) {
                    val y = i * stepH
                    val waveX = fillWidth + sin((i.toFloat() / steps) * 2f * PI.toFloat() + fastWavePhase * 2f) * (w * 0.025f)
                    liqPath.lineTo(waveX.coerceIn(0f, w), y)
                }
                liqPath.lineTo(0f, h)
                liqPath.close()
                drawPath(
                    path = liqPath,
                    brush = Brush.horizontalGradient(
                        listOf(
                            VibrantEmerald.copy(alpha = 0.14f),
                            CyberCyan.copy(alpha = 0.26f)
                        )
                    )
                )
            }
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    CrispWhite.copy(alpha = 0.14f * smokeIntensity.coerceAtLeast(0.25f)),
                    CrispWhite.copy(alpha = 0.03f),
                    Color.Transparent,
                    ObsidianBlack.copy(alpha = 0.35f)
                )
            )
        )
    }
}

@Composable
private fun CameraCutoutSafeGap() {
    Spacer(modifier = Modifier.width(34.dp))
}

@Composable
private fun Modifier.elasticIslandStretchGesture(
    state: IslandState,
    onStretchScaleChange: (Float, Float) -> Unit
): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val maxScale by IslandConfig.stretchMaxScale.collectAsState()

    return this.pointerInput(state, maxScale) {
        var totalDragX = 0f
        var holdJob: Job? = null
        var holdTriggered = false

        detectHorizontalDragGestures(
            onDragStart = {
                totalDragX = 0f
                holdTriggered = false
                holdJob?.cancel()
            },
            onDragCancel = {
                holdJob?.cancel()
                onStretchScaleChange(1f, 0f)
            },
            onDragEnd = {
                holdJob?.cancel()
                val threshold = 42f
                if (!holdTriggered && abs(totalDragX) >= threshold) {
                    val isRight = totalDragX > 0f
                    triggerContextualStretchAction(context, state, isRight)
                }
                onStretchScaleChange(1f, 0f)
                totalDragX = 0f
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                totalDragX += dragAmount
                val norm = (abs(totalDragX) / 170f).coerceIn(0f, 1f)
                val targetScaleX = 1f + norm * (maxScale - 1f)
                val directionalShift = (totalDragX / 18f).coerceIn(-10f, 10f)
                onStretchScaleChange(targetScaleX, directionalShift)

                if (state == IslandState.CALL && totalDragX < -48f && holdJob == null && !holdTriggered) {
                    IslandConfig.showStretchFeedback("Hold 2s to Cut Call...")
                    holdJob = scope.launch {
                        delay(2000.milliseconds)
                        holdTriggered = true
                        IslandConfig.showStretchFeedback("Call Ended")
                        IslandConfig.endCall(context)
                        onStretchScaleChange(1f, 0f)
                    }
                } else if (totalDragX > -35f) {
                    holdJob?.cancel()
                    holdJob = null
                }
            }
        )
    }
}

private fun triggerContextualStretchAction(
    context: android.content.Context,
    state: IslandState,
    isRight: Boolean
) {
    when (state) {
        IslandState.MUSIC -> {
            if (isRight) IslandConfig.skipToNextTrack(context) else IslandConfig.skipToPreviousTrack(context)
        }
        IslandState.TIMER -> {
            if (isRight) {
                if (IslandConfig.isTimerRinging.value) {
                    IslandConfig.resetOrSilenceTimer()
                    IslandConfig.showStretchFeedback("Alarm Stopped")
                } else {
                    IslandConfig.toggleTimerPause()
                    IslandConfig.showStretchFeedback(if (IslandConfig.isTimerPaused.value) "Timer Paused" else "Timer Resumed")
                }
            } else {
                IslandConfig.resetOrSilenceTimer()
                IslandConfig.showStretchFeedback("Timer Reset")
            }
        }
        IslandState.STOPWATCH -> {
            if (isRight) {
                IslandConfig.toggleStopwatchPause()
                IslandConfig.showStretchFeedback(if (IslandConfig.isStopwatchPaused.value) "Stopwatch Paused" else "Stopwatch Running")
            } else {
                IslandConfig.resetStopwatch()
                IslandConfig.showStretchFeedback("Stopwatch Reset")
            }
        }
        IslandState.CALL -> {
            if (isRight) {
                if (IslandConfig.isCallIncoming.value) {
                    IslandConfig.acceptCall(context)
                } else {
                    IslandConfig.toggleCallSpeaker(context)
                }
            } else {
                IslandConfig.showStretchFeedback("Hold Left 2s to Cut Call")
            }
        }
        IslandState.NAVIGATION -> {
            if (isRight) {
                IslandConfig.setExpandedState(true)
                IslandConfig.showStretchFeedback("Expanded Route")
            } else {
                IslandConfig.stopNavigation()
                IslandConfig.showStretchFeedback("Route Closed")
            }
        }
        IslandState.OTP -> {
            if (isRight) {
                IslandConfig.copyOtpToClipboard(context)
                IslandConfig.showStretchFeedback("OTP Copied!")
            } else {
                IslandConfig.setExpandedState(false)
                IslandConfig.restoreBaselineState()
            }
        }
        IslandState.TORCH -> {
            IslandConfig.toggleTorchHardware(context)
            IslandConfig.showStretchFeedback(if (IslandConfig.isTorchOn.value) "Torch On" else "Torch Off")
        }
        IslandState.NOTIFICATION -> {
            if (isRight) {
                IslandConfig.sendPendingIntentSafely(IslandConfig.notificationContentIntent)
            }
            IslandConfig.setExpandedState(false)
            IslandConfig.restoreBaselineState()
        }
        else -> {
            IslandConfig.setExpandedState(!IslandConfig.isExpanded.value)
        }
    }
}

/**
 * Inner Layout Wrappers for Stage 2 (Compact Capsule) and Stage 3 (Expanded Card).
 * Notice that the outer shell (width, corner radius, border, shadow, and backdrop) is handled
 * by the single unified container in `DynamicIslandUI()` so that Stage 1 <-> Stage 2 <-> Stage 3
 * morphs continuously from the camera cutout toward both sides without any duplicate-box jhatka!
 */
@Composable
fun IslandSurface(
    state: IslandState,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    heightScale: Float = 1f,
    cornerRadius: Dp = 38.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val stage3HeightOff by IslandConfig.stage3HeightOffset.collectAsState()

    // Differentiation between Full-Height Interactive Tasks vs Compact/Reduced Minor Tasks:
    // Full/Major: MUSIC, NOTIFICATION, CALL, TIMER, STOPWATCH, NAVIGATION, BATTERY
    // Compact/Reduced: TORCH, EARBUDS, DND, LOCK, OTP, DOWNLOAD, SOUND_MODE, RECORDING
    val isMajorFullTask = when (state) {
        IslandState.MUSIC,
        IslandState.NOTIFICATION,
        IslandState.CALL,
        IslandState.TIMER,
        IslandState.STOPWATCH,
        IslandState.NAVIGATION,
        IslandState.BATTERY -> true
        else -> false
    }

    val horizPad = if (expanded) (18f * heightScale).dp else 12.dp
    val vertPad = if (expanded) {
        if (isMajorFullTask) {
            ((16f * heightScale) + (stage3HeightOff * 0.45f)).coerceAtLeast(4f).dp
        } else {
            // Minor tasks remain compact and sleek with controlled vertical responsiveness
            ((8f * heightScale) + (stage3HeightOff * 0.20f)).coerceAtLeast(3f).dp
        }
    } else 6.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizPad, vertical = vertPad),
        content = content
    )
}

@Composable
private fun CompactPillBox(
    state: IslandState,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    onClick: (() -> Unit)? = null,
    backgroundOverlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val contentScale by IslandConfig.contentScale.collectAsState()

    val baseMod = Modifier
        .fillMaxWidth()
        .height(height)

    val clickableMod = if (onClick != null) {
        baseMod.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                IslandConfig.performHapticTick()
                onClick()
            }
        )
    } else baseMod

    Box(modifier = clickableMod) {
        backgroundOverlay?.invoke(this)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp)
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}

@Composable
private fun DetachedSplitCircle(
    secondaryState: IslandState,
    circleSize: Dp,
    onClick: () -> Unit
) {
    val useFrosted by IslandConfig.useFrostedGlassStyle.collectAsState()
    val borderGlow by IslandConfig.borderGlowAlpha.collectAsState()
    val artwork by IslandConfig.musicArtwork.collectAsState()
    val isPlaying by IslandConfig.isMediaPlaying.collectAsState()
    val batteryPct by IslandConfig.batteryLevel.collectAsState()
    val (priColor, _) = rememberMistPlumeColors(secondaryState)

    val infiniteTransition = rememberInfiniteTransition(label = "discRotation")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart),
        label = "spinDeg"
    )

    Box(
        modifier = Modifier
            .size(circleSize)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .border(
                width = 0.9.dp,
                color = if (useFrosted) priColor.copy(alpha = (0.55f * borderGlow).coerceIn(0.15f, 0.78f))
                else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        FluidFrostedMistBackdrop(state = secondaryState, modifier = Modifier.matchParentSize())

        when (secondaryState) {
            IslandState.MUSIC -> {
                Box(
                    modifier = Modifier
                        .size(circleSize - 6.dp)
                        .graphicsLayer { rotationZ = if (isPlaying) spinAngle else 0f }
                ) {
                    ArtworkThumbnail(
                        bitmap = artwork,
                        size = circleSize - 6.dp,
                        cornerRadius = circleSize / 2,
                        isCircular = true
                    )
                }
            }
            IslandState.CALL -> {
                Icon(Icons.Rounded.Call, null, tint = VibrantEmerald, modifier = Modifier.size(circleSize * 0.52f))
            }
            IslandState.TORCH -> {
                Icon(Icons.Rounded.FlashlightOn, null, tint = WarmAmber, modifier = Modifier.size(circleSize * 0.52f))
            }
            IslandState.BATTERY -> {
                CircularRingIndicator(
                    progress = (batteryPct / 100f).coerceIn(0.08f, 1f),
                    color = VibrantEmerald,
                    size = circleSize * 0.68f,
                    strokeWidth = 2.2.dp
                ) {
                    Icon(Icons.Rounded.BatteryChargingFull, null, tint = VibrantEmerald, modifier = Modifier.size(circleSize * 0.36f))
                }
            }
            IslandState.NAVIGATION -> {
                Icon(Icons.Rounded.TurnRight, null, tint = CobaltBlue, modifier = Modifier.size(circleSize * 0.52f))
            }
            IslandState.TIMER -> {
                CircularRingIndicator(progress = 0.72f, color = FlameOrange, size = circleSize * 0.62f, strokeWidth = 2.2.dp)
            }
            IslandState.STOPWATCH -> {
                CircularRingIndicator(progress = 0.65f, color = VibrantEmerald, size = circleSize * 0.62f, strokeWidth = 2.2.dp)
            }
            IslandState.RECORDING -> {
                Box(Modifier.size(circleSize * 0.34f).clip(CircleShape).background(CrimsonAlert))
            }
            IslandState.DOWNLOAD -> {
                Icon(Icons.Rounded.Download, null, tint = CobaltBlue, modifier = Modifier.size(circleSize * 0.50f))
            }
            IslandState.SOUND_MODE -> {
                val isSilent by IslandConfig.isSilentMode.collectAsState()
                Icon(
                    imageVector = if (isSilent) Icons.Rounded.NotificationsOff else Icons.Rounded.Vibration,
                    contentDescription = null,
                    tint = if (isSilent) CrimsonAlert else WarmAmber,
                    modifier = Modifier.size(circleSize * 0.50f)
                )
            }
            IslandState.DND -> {
                Icon(Icons.Rounded.DarkMode, null, tint = IndigoFocus, modifier = Modifier.size(circleSize * 0.50f))
            }
            else -> {
                Icon(Icons.Rounded.NotificationsActive, null, tint = CrispWhite, modifier = Modifier.size(circleSize * 0.48f))
            }
        }
    }
}

@Composable
fun IslandLabel(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 13.sp,
    color: Color = AlabasterText,
    weight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.Default
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = size,
        color = color,
        fontWeight = weight,
        fontFamily = fontFamily,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun LiveMusicWaveform(
    isPlaying: Boolean,
    primaryColor: Color = RoseWaveform,
    secondaryColor: Color = CrispWhite,
    barWidth: Dp = 2.6.dp,
    maxHeight: Dp = 18.dp
) {
    val transition = rememberInfiniteTransition(label = "waveformBars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.18f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "playWaveScale"
    )
    val phaseOffsets = remember { listOf(0.0f, 1.15f, 2.35f, 3.55f, 4.75f) }

    Row(
        modifier = Modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(2.2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        phaseOffsets.forEachIndexed { idx, offset ->
            val dynamicPart = ((sin(phase + offset) + 1f) / 2f) * playScale
            val fraction = (0.20f + 0.80f * dynamicPart).coerceIn(0.20f, 1f)
            val barColor = if (isPlaying) {
                if (idx % 2 == 0) primaryColor else secondaryColor
            } else {
                SubtleSilver.copy(alpha = 0.55f)
            }
            Canvas(Modifier.width(barWidth).height(maxHeight)) {
                val barH = (size.height * fraction).coerceAtLeast(size.width)
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(0f, (size.height - barH) / 2f),
                    size = Size(size.width, barH),
                    cornerRadius = CornerRadius(size.width / 2f, size.width / 2f)
                )
            }
        }
    }
}

@Composable
fun CustomMusicSeekBar(
    progressFraction: Float,
    isPlaying: Boolean,
    style: MusicSliderStyle,
    primaryColor: Color,
    secondaryColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val clampedProgress = progressFraction.coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "sliderWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(780, easing = LinearEasing), RepeatMode.Restart),
        label = "sliderWavePhase"
    )
    val activeAmp by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.08f,
        animationSpec = tween(420),
        label = "sliderActiveAmp"
    )

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width.coerceAtLeast(1)
                    onSeek((offset.x / w.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.coerceAtLeast(1)
                    onSeek((change.position.x / w.toFloat()).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val cy = h / 2f
            val activeX = (w * clampedProgress).coerceIn(0f, w)

            when (style) {
                MusicSliderStyle.WAVEFORM_SEEK -> {
                    drawLine(
                        color = CrispWhite.copy(alpha = 0.22f),
                        start = Offset(activeX, cy),
                        end = Offset(w, cy),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val wavePath = Path()
                    val steps = 42
                    val maxAmp = 5.5.dp.toPx() * activeAmp
                    for (i in 0..steps) {
                        val x = (activeX * i) / steps.toFloat()
                        val angle = (x / w) * 8f * PI.toFloat() - wavePhase * 2f
                        val y = cy + sin(angle) * maxAmp
                        if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    drawPath(
                        path = wavePath,
                        brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, CrispWhite)),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawRoundRect(
                        color = CrispWhite,
                        topLeft = Offset((activeX - 3.dp.toPx()).coerceIn(0f, w - 6.dp.toPx()), cy - 8.dp.toPx()),
                        size = Size(6.dp.toPx(), 16.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }

                MusicSliderStyle.LIQUID_CAPSULE -> {
                    val tubeH = 12.dp.toPx()
                    val topY = cy - tubeH / 2f
                    drawRoundRect(
                        color = CrispWhite.copy(alpha = 0.14f),
                        topLeft = Offset(0f, topY),
                        size = Size(w, tubeH),
                        cornerRadius = CornerRadius(tubeH / 2f, tubeH / 2f)
                    )
                    val fillW = activeX.coerceAtLeast(tubeH)
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.85f), secondaryColor, CrispWhite.copy(alpha = 0.95f)),
                            startX = 0f,
                            endX = fillW
                        ),
                        topLeft = Offset(0f, topY),
                        size = Size(fillW.coerceAtMost(w), tubeH),
                        cornerRadius = CornerRadius(tubeH / 2f, tubeH / 2f)
                    )
                    drawRoundRect(
                        color = CrispWhite.copy(alpha = 0.38f),
                        topLeft = Offset(4.dp.toPx(), topY + 2.dp.toPx()),
                        size = Size((fillW - 8.dp.toPx()).coerceAtLeast(2f), 2.5.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                MusicSliderStyle.EQUALIZER_BARS -> {
                    val barCount = 28
                    val gap = 3.dp.toPx()
                    val barW = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)
                    for (i in 0 until barCount) {
                        val frac = i.toFloat() / (barCount - 1).toFloat()
                        val isPassed = frac <= clampedProgress
                        val beatWave = if (isPlaying) {
                            0.35f + 0.65f * ((sin(wavePhase * 2f + i * 0.55f) + 1f) * 0.5f)
                        } else {
                            0.30f + 0.25f * sin(i * 0.7f)
                        }
                        val barH = (h * 0.78f * beatWave).coerceIn(4.dp.toPx(), h * 0.85f)
                        val x = i * (barW + gap)
                        drawRoundRect(
                            color = if (isPassed) {
                                if (i % 2 == 0) primaryColor else secondaryColor
                            } else {
                                CrispWhite.copy(alpha = 0.20f)
                            },
                            topLeft = Offset(x, cy - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }

                MusicSliderStyle.NEON_LASER -> {
                    drawLine(
                        color = CrispWhite.copy(alpha = 0.16f),
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.45f), secondaryColor.copy(alpha = 0.55f))),
                        start = Offset(0f, cy),
                        end = Offset(activeX, cy),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        brush = Brush.horizontalGradient(listOf(primaryColor, CrispWhite)),
                        start = Offset(0f, cy),
                        end = Offset(activeX, cy),
                        strokeWidth = 2.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    val orbRadius = (5.5f + 1.8f * activeAmp * sin(wavePhase * 2f)).dp.toPx()
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.45f),
                        radius = orbRadius * 1.6f,
                        center = Offset(activeX, cy)
                    )
                    drawCircle(
                        color = CrispWhite,
                        radius = 4.5.dp.toPx(),
                        center = Offset(activeX, cy)
                    )
                }

                MusicSliderStyle.DOT_MATRIX -> {
                    val dotCount = 24
                    val spacing = w / (dotCount - 1).coerceAtLeast(1)
                    for (i in 0 until dotCount) {
                        val x = i * spacing
                        val isLit = x <= activeX
                        val isHead = abs(x - activeX) < spacing * 0.6f
                        val r = when {
                            isHead -> 4.6.dp.toPx()
                            isLit -> 2.8.dp.toPx()
                            else -> 2.0.dp.toPx()
                        }
                        drawCircle(
                            color = when {
                                isHead -> CrispWhite
                                isLit -> primaryColor
                                else -> CrispWhite.copy(alpha = 0.22f)
                            },
                            radius = r,
                            center = Offset(x, cy)
                        )
                    }
                }

                MusicSliderStyle.CLASSIC_MINIMAL -> {
                    val trackH = 4.5.dp.toPx()
                    drawRoundRect(
                        color = CrispWhite.copy(alpha = 0.24f),
                        topLeft = Offset(0f, cy - trackH / 2f),
                        size = Size(w, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(0f, cy - trackH / 2f),
                        size = Size(activeX, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )
                    drawCircle(
                        color = CrispWhite,
                        radius = 6.dp.toPx(),
                        center = Offset(activeX.coerceIn(6.dp.toPx(), w - 6.dp.toPx()), cy)
                    )
                }
            }
        }
    }
}

@Composable
fun CircularRingIndicator(
    progress: Float,
    color: Color,
    size: Dp = 22.dp,
    strokeWidth: Dp = 2.6.dp,
    centerContent: (@Composable () -> Unit)? = null
) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            drawCircle(color = color.copy(alpha = 0.24f), style = Stroke(width = strokePx))
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        centerContent?.invoke()
    }
}

@Composable
fun ArtworkThumbnail(bitmap: Bitmap?, size: Dp, cornerRadius: Dp, isCircular: Boolean = false) {
    val shape = if (isCircular) CircleShape else RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3E5879), Color(0xFF1B263B))))
            .border(0.6.dp, Color.White.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Rounded.Album, null, tint = CrispWhite.copy(alpha = 0.9f), modifier = Modifier.size(size * 0.56f))
        }
    }
}

@Composable
fun MusicIsland(
    title: String,
    artist: String,
    artwork: Bitmap?,
    playing: Boolean,
    isFavorite: Boolean,
    progressFraction: Float,
    currentStr: String,
    durationStr: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    onToggleFavorite: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onOutputClick: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val (priAccent, secAccent) = rememberMistPlumeColors(IslandState.MUSIC)
    val sliderStyleIdx by IslandConfig.musicSliderStyleIndex.collectAsState()
    val sliderStyle = MusicSliderStyle.entries.getOrElse(sliderStyleIdx) { MusicSliderStyle.WAVEFORM_SEEK }

    val artScale by animateFloatAsState(
        targetValue = if (playing) 1.0f else 0.91f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "artPlayScale"
    )

    if (!expanded) {
        CompactPillBox(
            state = IslandState.MUSIC,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Box(modifier = Modifier.graphicsLayer {
                scaleX = artScale
                scaleY = artScale
            }) {
                ArtworkThumbnail(
                    bitmap = artwork,
                    size = (compactHeight - 10.dp).coerceAtLeast(18.dp),
                    cornerRadius = 6.dp,
                    isCircular = true
                )
            }
            CameraCutoutSafeGap()
            LiveMusicWaveform(
                isPlaying = playing,
                primaryColor = priAccent,
                secondaryColor = CrispWhite,
                barWidth = 2.4.dp,
                maxHeight = 14.dp
            )
        }
    } else {
        IslandSurface(
            state = IslandState.MUSIC,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }) {
                    ArtworkThumbnail(bitmap = artwork, size = (64f * heightScale).dp, cornerRadius = 16.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (playing) priAccent.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.12f))
                                .clickable {
                                    val nextIdx = (sliderStyleIdx + 1) % MusicSliderStyle.entries.size
                                    IslandConfig.setMusicSliderStyle(nextIdx)
                                }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (playing) "PLAYING • ${sliderStyle.label.uppercase()}" else "PAUSED • ${sliderStyle.label.uppercase()}",
                                color = if (playing) priAccent else SubtleSilver,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    IslandLabel(title, size = 18.sp, weight = FontWeight.Bold, color = CrispWhite)
                    Spacer(Modifier.height(2.dp))
                    IslandLabel(artist, size = 13.sp, color = AlabasterText.copy(alpha = 0.80f))
                }
                Spacer(Modifier.width(10.dp))
                LiveMusicWaveform(
                    isPlaying = playing,
                    primaryColor = priAccent,
                    secondaryColor = CrispWhite.copy(alpha = 0.88f),
                    barWidth = 3.2.dp,
                    maxHeight = 24.dp
                )
            }
            Spacer(Modifier.height((12f * heightScale).dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IslandLabel(currentStr, color = AlabasterText.copy(alpha = 0.82f), size = 11.sp, weight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                CustomMusicSeekBar(
                    progressFraction = progressFraction,
                    isPlaying = playing,
                    style = sliderStyle,
                    primaryColor = priAccent,
                    secondaryColor = secAccent,
                    onSeek = onSeek,
                    modifier = Modifier.weight(1f)
                )
                IslandLabel(durationStr, color = AlabasterText.copy(alpha = 0.82f), size = 11.sp, weight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height((10f * heightScale).dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) CrimsonAlert else CrispWhite,
                    modifier = Modifier.size(25.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleFavorite
                    )
                )
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = CrispWhite,
                    modifier = Modifier.size(34.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPrevious
                    )
                )
                Box(
                    modifier = Modifier
                        .size((50f * heightScale).dp)
                        .clip(CircleShape)
                        .background(
                            if (playing) Brush.linearGradient(listOf(priAccent.copy(alpha = 0.42f), secAccent.copy(alpha = 0.28f)))
                            else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.10f)))
                        )
                        .border(1.dp, CrispWhite.copy(alpha = 0.35f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = CrispWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = CrispWhite,
                    modifier = Modifier.size(34.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNext
                    )
                )
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = "Switch Seekbar Style",
                    tint = priAccent,
                    modifier = Modifier.size(23.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            val nextIdx = (sliderStyleIdx + 1) % MusicSliderStyle.entries.size
                            IslandConfig.setMusicSliderStyle(nextIdx)
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun TimerOrStopwatchIsland(
    isStopwatch: Boolean,
    secondsValue: Int,
    totalSeconds: Int,
    isPaused: Boolean,
    isRinging: Boolean,
    subtitle: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit
) {
    val state = if (isStopwatch) IslandState.STOPWATCH else IslandState.TIMER
    val progress = if (isStopwatch) {
        (secondsValue % 60) / 60f
    } else if (totalSeconds > 0) {
        secondsValue.toFloat() / totalSeconds.toFloat()
    } else 0f

    val hours = secondsValue / 3600
    val mins = (secondsValue % 3600) / 60
    val secs = secondsValue % 60
    val compactDigits = if (hours > 0) "%d:%02d".format(hours, mins) else "%02d:%02d".format(mins, secs)
    val fullFormatted = if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs) else "%02d:%02d".format(mins, secs)

    val accentRingColor = when {
        isRinging -> CrimsonAlert
        isStopwatch -> VibrantEmerald
        else -> FlameOrange
    }

    if (!expanded) {
        CompactPillBox(
            state = state,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            CircularRingIndicator(
                progress = if (isRinging) 1f else progress.coerceIn(0.10f, 1f),
                color = accentRingColor,
                size = (compactHeight - 12.dp).coerceAtLeast(19.dp),
                strokeWidth = 2.3.dp
            ) {
                Icon(
                    imageVector = if (isStopwatch) Icons.Rounded.Timer else Icons.Rounded.LocalCafe,
                    contentDescription = null,
                    tint = accentRingColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            CameraCutoutSafeGap()
            Text(
                text = compactDigits,
                color = accentRingColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    } else {
        IslandSurface(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IslandLabel(
                    text = subtitle.ifBlank { if (isStopwatch) "Stopwatch Running ..." else "Brewing Espresso ..." },
                    color = if (isRinging) WarmAmber else CrispWhite,
                    size = 14.sp,
                    weight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height((12f * heightScale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isStopwatch) "Stopwatch" else "Timer",
                        color = AlabasterText.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = fullFormatted,
                        color = CrispWhite,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                CircularRingIndicator(
                    progress = if (isRinging) 1f else progress.coerceIn(0.1f, 1f),
                    color = accentRingColor,
                    size = (60f * heightScale).dp,
                    strokeWidth = 4.2.dp
                ) {
                    Box(
                        modifier = Modifier
                            .size((46f * heightScale).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isStopwatch) Icons.Rounded.Timer else Icons.Rounded.CoffeeMaker,
                            contentDescription = null,
                            tint = CrispWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height((12f * heightScale).dp))
            Box(Modifier.fillMaxWidth().height(0.8.dp).background(Color.White.copy(alpha = 0.14f)))
            Spacer(Modifier.height((10f * heightScale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AmberActionBg)
                            .clickable { onTogglePause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = "Pause/Resume",
                            tint = WarmAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DarkControlBg)
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = CrispWhite,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.clickable { onOpenApp() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Open Clock",
                        color = CrispWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = CrispWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GeneralNotificationIsland(
    appName: String,
    title: String,
    bodyText: String,
    subText: String,
    iconBitmap: Bitmap?,
    actionLabel: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenNotification: () -> Unit,
    onTriggerAction: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) {
        CompactPillBox(
            state = IslandState.NOTIFICATION,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Box(
                modifier = Modifier
                    .size((compactHeight - 10.dp).coerceAtLeast(20.dp))
                    .clip(CircleShape)
                    .background(CyberCyan.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Text(
                        text = appName.firstOrNull()?.uppercase() ?: "N",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            CameraCutoutSafeGap()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(CyberCyan))
                Text(
                    text = appName.take(4).uppercase(),
                    color = AlabasterText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    } else {
        IslandSurface(
            state = IslandState.NOTIFICATION,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onOpenNotification() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    ArtworkThumbnail(bitmap = iconBitmap, size = 46.dp, cornerRadius = 14.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(CobaltBlue, IndigoFocus))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.firstOrNull()?.uppercase() ?: "N",
                            color = CrispWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = appName,
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "• ${subText.ifBlank { "Just now" }}",
                                color = SubtleSilver,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    IslandLabel(title, size = 15.sp, weight = FontWeight.Bold, color = CrispWhite)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = bodyText,
                        color = AlabasterText.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, "Dismiss", tint = AlabasterText, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.height((12f * heightScale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CobaltBlue.copy(alpha = 0.85f))
                        .clickable { onOpenNotification() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Open", color = CrispWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Rounded.ArrowOutward, null, tint = CrispWhite, modifier = Modifier.size(14.dp))
                    }
                }
                if (actionLabel.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(VibrantEmerald.copy(alpha = 0.24f))
                            .border(0.8.dp, VibrantEmerald.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { onTriggerAction() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionLabel,
                            color = VibrantEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Dismiss", color = AlabasterText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ScreenRecordingIsland(
    elapsedSeconds: Int,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onStopRecording: () -> Unit
) {
    val timeStr = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    if (!expanded) {
        CompactPillBox(
            state = IslandState.RECORDING,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Box(
                modifier = Modifier.size(18.dp).clip(CircleShape).background(CrimsonAlert.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(CrimsonAlert))
            }
            CameraCutoutSafeGap()
            Text(timeStr, color = CrimsonAlert, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    } else {
        IslandSurface(
            state = IslandState.RECORDING,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(CrimsonAlert))
                        Text(timeStr, color = CrimsonAlert, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(3.dp))
                    IslandLabel("Screen Recording Active", color = CrispWhite, size = 17.sp, weight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.size(46.dp).clip(CircleShape).border(2.2.dp, CrispWhite, CircleShape).clickable { onStopRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(CrimsonAlert))
                }
            }
        }
    }
}

@Composable
fun SoundModeIsland(
    modeText: String,
    isSilent: Boolean,
    isVibrate: Boolean,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val accent = when {
        isSilent -> CrimsonAlert
        isVibrate -> WarmAmber
        else -> VibrantEmerald
    }
    val circleBg = accent.copy(alpha = 0.22f)

    if (!expanded) {
        CompactPillBox(
            state = IslandState.SOUND_MODE,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Box(Modifier.size(21.dp).clip(CircleShape).background(circleBg), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        isSilent -> Icons.Rounded.NotificationsOff
                        isVibrate -> Icons.Rounded.Vibration
                        else -> Icons.Rounded.NotificationsActive
                    },
                    contentDescription = modeText,
                    tint = accent,
                    modifier = Modifier.size(13.dp)
                )
            }
            CameraCutoutSafeGap()
            Text(
                text = modeText,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        IslandSurface(
            state = IslandState.SOUND_MODE,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(circleBg), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isSilent -> Icons.Rounded.NotificationsOff
                                isVibrate -> Icons.Rounded.Vibration
                                else -> Icons.Rounded.NotificationsActive
                            },
                            contentDescription = modeText,
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        IslandLabel("Sound Profile: $modeText", size = 16.sp, weight = FontWeight.Bold, color = CrispWhite)
                        IslandLabel(
                            text = if (isSilent || isVibrate) "Active in Stage 2 Capsule while idle" else "Standard Ringer Active",
                            size = 12.sp,
                            color = AlabasterText.copy(alpha = 0.78f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.22f))
                        .clickable { onExpandedChange(false) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(modeText.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun BatteryIsland(
    batteryPercent: Int,
    charging: Boolean,
    wattsLabel: String,
    tempLabel: String,
    timeRemainingLabel: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val level = batteryPercent.coerceIn(0, 100)
    val styleIdx by IslandConfig.chargingEffectStyleIndex.collectAsState()
    val effectStyle = ChargingEffectStyle.entries.getOrElse(styleIdx) { ChargingEffectStyle.LIQUID_WAVE }

    val batteryColor = when {
        level <= 15 && !charging -> CrimsonAlert
        level <= 30 && !charging -> WarmAmber
        else -> VibrantEmerald
    }
    val secondaryChargeColor = when (effectStyle) {
        ChargingEffectStyle.AURORA_GLOW -> Color(0xFFA855F7)
        ChargingEffectStyle.HYPER_TURBO -> WarmAmber
        else -> CyberCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "chargingEffects")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "chargeWavePhase"
    )
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1350, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "chargePulse"
    )

    if (!expanded) {
        CompactPillBox(
            state = IslandState.BATTERY,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) },
            backgroundOverlay = {
                if (charging) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val w = size.width
                        val h = size.height
                        val fillW = w * (level / 100f).coerceIn(0.12f, 1f)

                        when (effectStyle) {
                            ChargingEffectStyle.LIQUID_WAVE, ChargingEffectStyle.AURORA_GLOW -> {
                                val wavePath = Path()
                                val steps = 24
                                val stepW = w / steps
                                val baseY = h * (1f - (level / 100f).coerceIn(0.25f, 0.78f))
                                wavePath.moveTo(0f, h)
                                for (i in 0..steps) {
                                    val x = i * stepW
                                    val y = baseY + sin((i.toFloat() / steps) * 2.5f * PI.toFloat() + wavePhase) * (h * 0.14f)
                                    wavePath.lineTo(x, y)
                                }
                                wavePath.lineTo(w, h)
                                wavePath.close()
                                drawPath(
                                    path = wavePath,
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            batteryColor.copy(alpha = 0.28f),
                                            secondaryChargeColor.copy(alpha = 0.32f),
                                            batteryColor.copy(alpha = 0.28f)
                                        )
                                    )
                                )
                            }
                            ChargingEffectStyle.NEON_PULSE -> {
                                val r = (w * 0.55f) * pulseProgress
                                drawCircle(
                                    color = batteryColor.copy(alpha = (1f - pulseProgress) * 0.40f),
                                    radius = r,
                                    center = Offset(w / 2f, h / 2f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            ChargingEffectStyle.PARTICLE_FLOW -> {
                                for (p in 0 until 8) {
                                    val px = ((pulseProgress + p * 0.125f) % 1f) * fillW
                                    val py = h * 0.5f + sin(wavePhase + p) * (h * 0.25f)
                                    drawCircle(
                                        color = batteryColor.copy(alpha = 0.55f),
                                        radius = 2.2.dp.toPx(),
                                        center = Offset(px, py)
                                    )
                                }
                            }
                            ChargingEffectStyle.HYPER_TURBO -> {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            batteryColor.copy(alpha = 0.30f),
                                            WarmAmber.copy(alpha = 0.35f * (0.5f + 0.5f * sin(wavePhase))),
                                            Color.Transparent
                                        ),
                                        startX = 0f,
                                        endX = fillW
                                    )
                                )
                            }
                            ChargingEffectStyle.MINIMAL_RING -> {}
                        }
                    }
                }
            }
        ) {
            CircularRingIndicator(
                progress = (level / 100f).coerceIn(0.08f, 1f),
                color = batteryColor,
                size = (compactHeight - 12.dp).coerceAtLeast(19.dp),
                strokeWidth = 2.2.dp
            ) {
                Icon(
                    imageVector = if (charging) Icons.Rounded.Bolt else Icons.Rounded.BatteryStd,
                    contentDescription = null,
                    tint = batteryColor,
                    modifier = Modifier.size(12.dp)
                )
            }
            CameraCutoutSafeGap()
            Text(
                text = "$level%",
                color = batteryColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    } else {
        IslandSurface(
            state = IslandState.BATTERY,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(batteryColor.copy(alpha = 0.20f))
                            .border(1.dp, batteryColor.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (charging) Icons.Rounded.Bolt else Icons.Rounded.BatteryChargingFull,
                            contentDescription = null,
                            tint = batteryColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        IslandLabel(
                            text = if (charging) "HyperCharge Active" else "Battery Status",
                            size = 16.sp,
                            weight = FontWeight.Bold,
                            color = CrispWhite
                        )
                        IslandLabel("$level% • $timeRemainingLabel", size = 12.sp, color = AlabasterText.copy(alpha = 0.80f))
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(batteryColor.copy(alpha = 0.18f))
                        .border(0.8.dp, batteryColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .clickable {
                            val nextIdx = (styleIdx + 1) % ChargingEffectStyle.entries.size
                            IslandConfig.setChargingEffectStyle(nextIdx)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = batteryColor, modifier = Modifier.size(12.dp))
                        Text(
                            text = effectStyle.label,
                            color = batteryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height((12f * heightScale).dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val fillW = (w * (level / 100f)).coerceIn(w * 0.08f, w)

                    when (effectStyle) {
                        ChargingEffectStyle.LIQUID_WAVE -> {
                            val path1 = Path()
                            val path2 = Path()
                            val steps = 36
                            val stepH = h / steps

                            path1.moveTo(0f, 0f)
                            path2.moveTo(0f, 0f)
                            for (i in 0..steps) {
                                val y = i * stepH
                                val angle = (i.toFloat() / steps) * (2.2f * PI).toFloat()
                                val wave1X = (fillW + sin(angle + wavePhase * 2f) * 9.dp.toPx()).coerceIn(0f, w)
                                val wave2X = (fillW + cos(angle - wavePhase * 1.6f) * 11.dp.toPx()).coerceIn(0f, w)
                                path1.lineTo(wave1X, y)
                                path2.lineTo(wave2X, y)
                            }
                            path1.lineTo(0f, h)
                            path1.close()
                            path2.lineTo(0f, h)
                            path2.close()

                            drawPath(
                                path = path2,
                                brush = Brush.horizontalGradient(
                                    listOf(batteryColor.copy(alpha = 0.35f), secondaryChargeColor.copy(alpha = 0.45f))
                                )
                            )
                            drawPath(
                                path = path1,
                                brush = Brush.horizontalGradient(
                                    listOf(batteryColor.copy(alpha = 0.72f), secondaryChargeColor.copy(alpha = 0.85f))
                                )
                            )

                            if (charging) {
                                for (b in 0 until 10) {
                                    val bx = (((pulseProgress + b * 0.1f) % 1f) * (fillW - 8.dp.toPx())).coerceAtLeast(4f)
                                    val by = h * (0.2f + 0.6f * ((sin(wavePhase + b * 1.3f) + 1f) * 0.5f))
                                    val br = (2.2f + (b % 3) * 1.1f).dp.toPx()
                                    drawCircle(
                                        color = CrispWhite.copy(alpha = 0.45f),
                                        radius = br,
                                        center = Offset(bx, by)
                                    )
                                }
                            }
                        }

                        ChargingEffectStyle.NEON_PULSE -> {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    listOf(batteryColor.copy(alpha = 0.35f), secondaryChargeColor.copy(alpha = 0.65f))
                                ),
                                size = Size(fillW, h)
                            )
                            for (ring in 0 until 4) {
                                val p = (pulseProgress + ring * 0.25f) % 1f
                                val rx = p * fillW
                                drawLine(
                                    brush = Brush.verticalGradient(
                                        listOf(Color.Transparent, CrispWhite.copy(alpha = 1f - p), Color.Transparent)
                                    ),
                                    start = Offset(rx, 0f),
                                    end = Offset(rx, h),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                        }

                        ChargingEffectStyle.PARTICLE_FLOW -> {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(batteryColor.copy(alpha = 0.28f), secondaryChargeColor.copy(alpha = 0.55f))
                                ),
                                size = Size(fillW, h)
                            )
                            for (p in 0 until 16) {
                                val prog = (pulseProgress + p * (1f / 16f)) % 1f
                                val px = prog * w
                                val py = h / 2f + sin(prog * 4f * PI.toFloat() + wavePhase + p) * (h * 0.32f)
                                val isInside = px <= fillW
                                drawCircle(
                                    color = if (isInside) CrispWhite else batteryColor.copy(alpha = 0.75f),
                                    radius = (if (isInside) 3.2f else 2.4f).dp.toPx(),
                                    center = Offset(px, py)
                                )
                            }
                        }

                        ChargingEffectStyle.AURORA_GLOW -> {
                            val auroraPath = Path()
                            val steps = 32
                            val stepW = w / steps
                            auroraPath.moveTo(0f, h)
                            for (i in 0..steps) {
                                val x = i * stepW
                                val y = h * 0.35f + sin((i.toFloat() / steps) * 3f * PI.toFloat() + wavePhase) * (h * 0.28f)
                                auroraPath.lineTo(x, y)
                            }
                            auroraPath.lineTo(w, h)
                            auroraPath.close()
                            drawPath(
                                path = auroraPath,
                                brush = Brush.horizontalGradient(
                                    listOf(VibrantEmerald.copy(alpha = 0.70f), CyberCyan.copy(alpha = 0.70f), Color(0xFFA855F7).copy(alpha = 0.70f))
                                )
                            )
                        }

                        ChargingEffectStyle.HYPER_TURBO -> {
                            val segments = 20
                            val gap = 4.dp.toPx()
                            val segW = (w - gap * (segments + 1)) / segments
                            val activeSegs = ((level / 100f) * segments).toInt()
                            for (s in 0 until segments) {
                                val sx = gap + s * (segW + gap)
                                val isLit = s < activeSegs
                                val isPulseSeg = charging && s == (activeSegs - 1).coerceAtLeast(0)
                                drawRoundRect(
                                    color = when {
                                        isPulseSeg -> CrispWhite
                                        isLit -> if (s > segments * 0.75) CyberCyan else batteryColor
                                        else -> CrispWhite.copy(alpha = 0.12f)
                                    },
                                    topLeft = Offset(sx, 6.dp.toPx()),
                                    size = Size(segW, h - 12.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }

                        ChargingEffectStyle.MINIMAL_RING -> {
                            drawRoundRect(
                                color = batteryColor.copy(alpha = 0.75f),
                                topLeft = Offset(0f, 0f),
                                size = Size(fillW, h),
                                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (charging) "⚡ $wattsLabel" else "BATTERY LEVEL",
                        color = CrispWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$level%",
                        color = CrispWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height((12f * heightScale).dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TelemetryStatCard(label = "POWER", value = if (charging) wattsLabel else "Discharging", accent = batteryColor, modifier = Modifier.weight(1f))
                TelemetryStatCard(label = "TEMP", value = tempLabel, accent = CrispWhite, modifier = Modifier.weight(1f))
                TelemetryStatCard(label = "ESTIMATE", value = timeRemainingLabel, accent = AlabasterText, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TelemetryStatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = SubtleSilver, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun NavigationIsland(
    distance: String,
    direction: String,
    eta: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenMaps: () -> Unit,
    onEndRoute: () -> Unit
) {
    val shortDistSymbol = remember(distance) { distance.replace(" ", "").take(5).ifBlank { "90m" } }

    if (!expanded) {
        CompactPillBox(
            state = IslandState.NAVIGATION,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Box(
                modifier = Modifier.size(21.dp).clip(CircleShape).background(CobaltBlue.copy(alpha = 0.24f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.TurnRight, null, tint = CobaltBlue, modifier = Modifier.size(15.dp))
            }
            CameraCutoutSafeGap()
            Text(
                text = shortDistSymbol,
                color = VibrantEmerald,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    } else {
        IslandSurface(
            state = IslandState.NAVIGATION,
            modifier = Modifier.fillMaxWidth().clickable { onOpenMaps() },
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TurnLeft, null, tint = SubtleSilver, modifier = Modifier.size(26.dp))
                Icon(Icons.Rounded.Straight, null, tint = CrispWhite, modifier = Modifier.size(28.dp))
                Icon(Icons.Rounded.Straight, null, tint = CrispWhite, modifier = Modifier.size(28.dp))
                Icon(Icons.Rounded.TurnRight, null, tint = CobaltBlue, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height((10f * heightScale).dp))
            Text(distance, color = CrispWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(CobaltBlue).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("MAP", color = CrispWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        IslandLabel(direction, size = 16.sp, weight = FontWeight.Bold, color = CrispWhite)
                        IslandLabel(eta, size = 12.sp, color = AlabasterText.copy(alpha = 0.78f))
                    }
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(CrimsonAlert.copy(alpha = 0.24f))
                        .clickable { onEndRoute() }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("End Route", color = CrimsonAlert, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OtpIsland(
    code: String,
    sender: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCopyOtp: () -> Unit
) {
    if (!expanded) {
        CompactPillBox(
            state = IslandState.OTP,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Icon(Icons.Rounded.Key, null, tint = WarmAmber, modifier = Modifier.size(16.dp))
            CameraCutoutSafeGap()
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy OTP",
                tint = VibrantEmerald,
                modifier = Modifier.size(15.dp).clickable { onCopyOtp() }
            )
        }
    } else {
        IslandSurface(
            state = IslandState.OTP,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    IslandLabel(sender, size = 13.sp, color = AlabasterText.copy(alpha = 0.78f))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = code,
                        color = CrispWhite,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(VibrantEmerald)
                        .clickable { onCopyOtp() }.padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.ContentCopy, null, tint = ObsidianBlack, modifier = Modifier.size(16.dp))
                        Text("Copy OTP", color = ObsidianBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TorchIsland(
    isOn: Boolean,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleTorch: () -> Unit
) {
    if (!expanded) {
        CompactPillBox(
            state = IslandState.TORCH,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(WarmAmber.copy(alpha = 0.24f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.FlashlightOn, null, tint = WarmAmber, modifier = Modifier.size(13.dp))
                }
                Text("ON", color = WarmAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            CameraCutoutSafeGap()
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isOn) WarmAmber else SubtleSilver.copy(alpha = 0.3f))
                    .clickable { onToggleTorch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PowerSettingsNew, "Turn Off", tint = ObsidianBlack, modifier = Modifier.size(12.dp))
            }
        }
    } else {
        IslandSurface(
            state = IslandState.TORCH,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(WarmAmber.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.FlashlightOn, null, tint = WarmAmber, modifier = Modifier.size(26.dp))
                    }
                    Column {
                        IslandLabel("Flashlight Active", size = 17.sp, weight = FontWeight.Bold, color = CrispWhite)
                        IslandLabel("Continuous Island Guard • Tap to Turn Off", size = 12.sp, color = AlabasterText.copy(alpha = 0.78f))
                    }
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(WarmAmber)
                        .clickable { onToggleTorch() }.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text("Turn Off", color = ObsidianBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DownloadIsland(
    title: String,
    appName: String,
    progressPercent: Int,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val pct = progressPercent.coerceIn(0, 100)
    if (!expanded) {
        CompactPillBox(
            state = IslandState.DOWNLOAD,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            CircularRingIndicator(progress = pct / 100f, color = CobaltBlue, size = 19.dp, strokeWidth = 2.3.dp) {
                Icon(Icons.Rounded.Download, null, tint = CobaltBlue, modifier = Modifier.size(11.dp))
            }
            CameraCutoutSafeGap()
            Text("$pct%", color = CrispWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    } else {
        IslandSurface(
            state = IslandState.DOWNLOAD,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(CobaltBlue.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Download, null, tint = CobaltBlue, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        IslandLabel(title, size = 16.sp, weight = FontWeight.Bold, color = CrispWhite)
                        IslandLabel("$appName • $pct% completed", size = 13.sp, color = AlabasterText.copy(alpha = 0.78f))
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = AlabasterText,
                    modifier = Modifier.size(22.dp).clickable { onDismiss() }
                )
            }
            Spacer(Modifier.height((14f * heightScale).dp))
            LinearProgressIndicator(
                progress = { pct / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = CobaltBlue,
                trackColor = Color.White.copy(alpha = 0.16f)
            )
        }
    }
}

@Composable
fun DndIsland(
    isEnabled: Boolean,
    label: String,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expanded: Boolean,
    heightScale: Float,
    expandedCornerRadius: Dp,
    onExpandedChange: (Boolean) -> Unit
) {
    if (!expanded) {
        CompactPillBox(
            state = IslandState.DND,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Box(Modifier.size(21.dp).clip(CircleShape).background(IndigoFocus.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DarkMode, null, tint = IndigoFocus, modifier = Modifier.size(13.dp))
            }
            CameraCutoutSafeGap()
            Text(
                text = if (isEnabled) "DND" else "Off",
                color = IndigoFocus,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        IslandSurface(
            state = IslandState.DND,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(IndigoFocus.copy(alpha = 0.24f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.DarkMode, null, tint = IndigoFocus, modifier = Modifier.size(24.dp))
                    }
                    Column {
                        IslandLabel(label, size = 16.sp, weight = FontWeight.Bold, color = CrispWhite)
                        IslandLabel(if (isEnabled) "Do Not Disturb is active" else "Notifications allowed", size = 12.sp, color = AlabasterText.copy(alpha = 0.78f))
                    }
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(IndigoFocus.copy(alpha = 0.24f))
                        .clickable { onExpandedChange(false) }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(if (isEnabled) "ACTIVE" else "OFF", color = IndigoFocus, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun EarbudsIsland(
    deviceName: String,
    batteryPercent: Int,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val pct = batteryPercent.coerceIn(0, 100)
    if (!expanded) {
        CompactPillBox(
            state = IslandState.EARBUDS,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Icon(Icons.Rounded.Headphones, null, tint = CrispWhite, modifier = Modifier.size(17.dp))
            CameraCutoutSafeGap()
            CircularRingIndicator(progress = pct / 100f, color = VibrantEmerald, size = 18.dp, strokeWidth = 2.4.dp)
        }
    } else {
        IslandSurface(
            state = IslandState.EARBUDS,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Headphones, null, tint = CrispWhite, modifier = Modifier.size(26.dp))
                    }
                    Column {
                        IslandLabel("Connected", color = AlabasterText.copy(alpha = 0.78f), size = 12.sp)
                        IslandLabel(deviceName, color = CrispWhite, size = 17.sp, weight = FontWeight.Bold)
                    }
                }
                CircularRingIndicator(progress = pct / 100f, color = VibrantEmerald, size = 48.dp, strokeWidth = 4.dp) {
                    Text("$pct", color = VibrantEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LockIsland(
    isLocked: Boolean,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp
) {
    CompactPillBox(
        state = IslandState.LOCK,
        width = compactWidth,
        height = compactHeight,
        cornerRadius = compactCornerRadius
    ) {
        Icon(if (isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, tint = CrispWhite, modifier = Modifier.size(16.dp))
        CameraCutoutSafeGap()
        Text(
            text = if (isLocked) "Locked" else "Unlocked",
            color = if (isLocked) SubtleSilver else VibrantEmerald,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CallIsland(
    callerName: String,
    subtitle: String,
    isIncoming: Boolean,
    alwaysShowAcceptBtn: Boolean,
    callDurationSec: Int,
    isSpeakerOn: Boolean,
    isMuted: Boolean,
    compactWidth: Dp,
    compactHeight: Dp,
    compactCornerRadius: Dp,
    expandedWidth: Dp,
    expandedCornerRadius: Dp,
    heightScale: Float,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleMute: () -> Unit,
    onEndCall: () -> Unit,
    onAcceptCall: () -> Unit
) {
    val showAcceptButton = isIncoming || alwaysShowAcceptBtn
    val formattedDur = "%02d:%02d".format(callDurationSec / 60, callDurationSec % 60)

    if (!expanded) {
        CompactPillBox(
            state = IslandState.CALL,
            width = compactWidth,
            height = compactHeight,
            cornerRadius = compactCornerRadius,
            onClick = { onExpandedChange(true) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier.size(21.dp).clip(CircleShape).background(VibrantEmerald.copy(alpha = 0.24f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PhoneInTalk, null, tint = VibrantEmerald, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = if (isIncoming) "Call" else formattedDur,
                    color = VibrantEmerald,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            CameraCutoutSafeGap()

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showAcceptButton) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(VibrantEmerald)
                            .clickable { onAcceptCall() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Call, "Accept Call", tint = ObsidianBlack, modifier = Modifier.size(14.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(CrimsonAlert)
                        .clickable { onEndCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CallEnd, "End Call", tint = CrispWhite, modifier = Modifier.size(14.dp))
                }
            }
        }
    } else {
        IslandSurface(
            state = IslandState.CALL,
            modifier = Modifier.fillMaxWidth(),
            expanded = true,
            heightScale = heightScale,
            cornerRadius = expandedCornerRadius
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(VibrantEmerald, Color(0xFF0EA5E9)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = ObsidianBlack, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isIncoming) VibrantEmerald.copy(alpha = 0.24f) else CobaltBlue.copy(alpha = 0.24f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isIncoming) "INCOMING CALL" else "ACTIVE CALL • $formattedDur",
                                    color = if (isIncoming) VibrantEmerald else CyberCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        IslandLabel(callerName, size = 18.sp, weight = FontWeight.Bold, color = CrispWhite)
                        IslandLabel(subtitle, size = 12.sp, color = AlabasterText.copy(alpha = 0.80f))
                    }
                }
                LiveMusicWaveform(
                    isPlaying = true,
                    primaryColor = VibrantEmerald,
                    secondaryColor = CyberCyan,
                    barWidth = 2.6.dp,
                    maxHeight = 20.dp
                )
            }

            Spacer(Modifier.height((14f * heightScale).dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallCircleButton(
                    icon = if (isSpeakerOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    isActive = isSpeakerOn,
                    onClick = onToggleSpeaker
                )
                CallCircleButton(
                    icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    isActive = isMuted,
                    onClick = onToggleMute
                )

                if (showAcceptButton) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(VibrantEmerald)
                            .clickable { onAcceptCall() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Call, "Accept", tint = ObsidianBlack, modifier = Modifier.size(20.dp))
                            Text(
                                text = if (isIncoming) "Accept" else "Answer",
                                color = ObsidianBlack,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CrimsonAlert)
                        .clickable { onEndCall() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.CallEnd, "End Call", tint = CrispWhite, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (isIncoming) "Decline" else "End Call",
                            color = CrispWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallCircleButton(icon: ImageVector, isActive: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (isActive) CrispWhite else DarkControlBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (isActive) ObsidianBlack else CrispWhite, modifier = Modifier.size(21.dp))
    }
}

/**
 * MASTER 3-STAGE DYNAMIC ISLAND ENGINE WITH UNIFIED CAMERA-CENTERED MORPHING:
 *
 * - Stage 1 (STAGE_1_CAMERA_CIRCLE): Tiny micro-circle (`dormantCircleDp`) hidden neatly behind the camera cutout when IDLE.
 * - Stage 2 (STAGE_2_CAPSULE): Capsule island (`activeCompactW`) + optional Detached Split Circle (`DetachedSplitCircle`) when 2+ tasks run.
 * - Stage 3 (STAGE_3_EXPANDED): Full expanded view (`targetExpandedWidthDp`).
 *
 * Uses ONE single continuous outer container (`UnifiedMorphingIslandShell`) centered at `Alignment.TopCenter`
 * so Stage 1 <-> Stage 2 <-> Stage 3 expands and contracts symmetrically in both directions from the camera
 * cutout with ZERO "jhatka" (no duplicate-box crossfading or 0px collapse)!
 */
@Composable
fun DynamicIslandUI() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val currentScreenWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(320f)
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    LaunchedEffect(currentScreenWidthDp, isLandscape) {
        IslandConfig.detectedScreenWidthDp.value = currentScreenWidthDp
        IslandConfig.isLandscapeOrientation.value = isLandscape
    }

    val masterEnabled by IslandConfig.masterIslandEnabled.collectAsState()
    val hideInLandscape by IslandConfig.hideInLandscapeEnabled.collectAsState()
    if (!masterEnabled || (hideInLandscape && isLandscape)) {
        Spacer(Modifier.size(1.dp))
        return
    }

    val activeState by IslandConfig.currentState.collectAsState()
    val secondaryState by IslandConfig.secondaryState.collectAsState()
    val isExpanded by IslandConfig.isExpanded.collectAsState()
    val forceDormant by IslandConfig.forceDormantPreview.collectAsState()
    val stretchFeedback by IslandConfig.stretchFeedbackText.collectAsState()

    val compactH by IslandConfig.compactHeightDp.collectAsState()
    val activeCompactW by IslandConfig.activeCompactWidthDp.collectAsState()
    val splitCircleDp by IslandConfig.splitCircleSizeDp.collectAsState()
    val splitGap by IslandConfig.splitGapDp.collectAsState()
    val dormantCircleDp by IslandConfig.dormantCircleSizeDp.collectAsState()
    val expandedPercent by IslandConfig.expandedWidthPercent.collectAsState()
    val heightScale by IslandConfig.expandedHeightScale.collectAsState()
    val compactCorner by IslandConfig.compactCornerRadiusDp.collectAsState()
    val expandedCorner by IslandConfig.expandedCornerRadiusDp.collectAsState()
    val alwaysShowIdle by IslandConfig.alwaysShowIdlePill.collectAsState()
    val stage2WidthOff by IslandConfig.stage2WidthOffset.collectAsState()
    val stage2HeightOff by IslandConfig.stage2HeightOffset.collectAsState()
    val stage3WidthOff by IslandConfig.stage3WidthOffset.collectAsState()
    val stage3HeightOff by IslandConfig.stage3HeightOffset.collectAsState()

    val useFrosted by IslandConfig.useFrostedGlassStyle.collectAsState()
    val borderGlow by IslandConfig.borderGlowAlpha.collectAsState()
    val edgeGlowPulse by IslandConfig.edgeGlowPulseOnNotification.collectAsState()
    val bounceLevel by IslandConfig.animBounceLevel.collectAsState()
    val speedMult by IslandConfig.animSpeedMultiplier.collectAsState()

    // Determine exact current stage (1, 2, or 3):
    val stage = when {
        forceDormant && !isExpanded -> IslandStage.STAGE_1_CAMERA_CIRCLE
        isExpanded && activeState != IslandState.IDLE -> IslandStage.STAGE_3_EXPANDED
        activeState != IslandState.IDLE -> IslandStage.STAGE_2_CAPSULE
        else -> IslandStage.STAGE_1_CAMERA_CIRCLE
    }

    // Critically damped, silky spring so expansion from camera cutout toward left & right has zero jhatka
    val smoothDamping = (0.92f - (bounceLevel * 0.12f)).coerceIn(0.78f, 0.94f)
    val dynamicStiffness = (360f * speedMult).coerceIn(180f, 800f)

    // Stage 3 Expanded Width with direct responsive slider adjustment:
    // Allows expanding from 160dp up to 100% full screen width (currentScreenWidthDp)
    val targetExpandedWidthDp = (currentScreenWidthDp * expandedPercent + stage3WidthOff).coerceIn(160f, currentScreenWidthDp)

    val hasSplitSecondary = (stage == IslandStage.STAGE_2_CAPSULE) &&
            secondaryState != null &&
            secondaryState != activeState

    val stage2CapsuleWidthDp = when {
        hasSplitSecondary -> (activeCompactW + stage2WidthOff - splitCircleDp - splitGap).coerceAtLeast(90f)
        activeState == IslandState.CALL -> (activeCompactW + stage2WidthOff + 18f).coerceAtLeast(150f)
        else -> (activeCompactW + stage2WidthOff).coerceAtLeast(90f)
    }

    // Target width & corner radius of the ONE unified island shell across Stage 1, Stage 2, and Stage 3:
    val targetShellWidthDp = when (stage) {
        IslandStage.STAGE_1_CAMERA_CIRCLE -> if (alwaysShowIdle) dormantCircleDp else 1f
        IslandStage.STAGE_2_CAPSULE -> stage2CapsuleWidthDp
        IslandStage.STAGE_3_EXPANDED -> targetExpandedWidthDp
    }

    val targetShellCornerDp = when (stage) {
        IslandStage.STAGE_1_CAMERA_CIRCLE -> (dormantCircleDp / 2f).coerceAtLeast(10f)
        IslandStage.STAGE_2_CAPSULE -> compactCorner
        IslandStage.STAGE_3_EXPANDED -> expandedCorner
    }

    val animatedShellWidth by animateDpAsState(
        targetValue = targetShellWidthDp.dp,
        animationSpec = spring(dampingRatio = smoothDamping, stiffness = dynamicStiffness),
        label = "unifiedShellWidth"
    )
    val animatedShellCorner by animateDpAsState(
        targetValue = targetShellCornerDp.dp,
        animationSpec = spring(dampingRatio = smoothDamping, stiffness = dynamicStiffness),
        label = "unifiedShellCorner"
    )
    val animatedCompactHeight by animateDpAsState(
        targetValue = if (stage == IslandStage.STAGE_1_CAMERA_CIRCLE) dormantCircleDp.dp else (compactH + stage2HeightOff).coerceAtLeast(18f).dp,
        animationSpec = spring(dampingRatio = smoothDamping, stiffness = dynamicStiffness),
        label = "unifiedCompactH"
    )

    var rawStretchScaleX by remember { mutableFloatStateOf(1f) }
    var rawStretchShiftX by remember { mutableFloatStateOf(0f) }

    val smoothStretchScaleX by animateFloatAsState(
        targetValue = rawStretchScaleX,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "stretchScaleX"
    )
    val smoothStretchShiftX by animateFloatAsState(
        targetValue = rawStretchShiftX,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "stretchShiftX"
    )

    val (priColor, secColor) = rememberMistPlumeColors(activeState)
    val pulseBoost = if (edgeGlowPulse && (activeState == IslandState.NOTIFICATION || activeState == IslandState.CALL)) 0.25f else 0f
    val effGlow = (borderGlow + pulseBoost).coerceIn(0f, 1f)

    val shellShape = RoundedCornerShape(animatedShellCorner)
    val shellBorderBrush = if (stage == IslandStage.STAGE_1_CAMERA_CIRCLE) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f)))
    } else if (useFrosted) {
        Brush.linearGradient(
            listOf(
                CrispWhite.copy(alpha = (0.46f * effGlow).coerceIn(0.08f, 0.82f)),
                priColor.copy(alpha = (0.58f * effGlow).coerceIn(0.08f, 0.82f)),
                secColor.copy(alpha = (0.38f * effGlow).coerceIn(0.06f, 0.68f)),
                CrispWhite.copy(alpha = (0.18f * effGlow).coerceIn(0.04f, 0.38f))
            )
        )
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)))
    }

    Box(
        modifier = Modifier.wrapContentSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = smoothStretchScaleX
                    scaleY = 1f - ((smoothStretchScaleX - 1f) * 0.14f).coerceIn(0f, 0.07f)
                    translationX = smoothStretchShiftX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .elasticIslandStretchGesture(activeState) { sX, shiftX ->
                    rawStretchScaleX = sX
                    rawStretchShiftX = shiftX
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(splitGap.dp, Alignment.CenterHorizontally)
            ) {
                // =============================================================
                // UNIFIED MORPHING ISLAND SHELL (STAGE 1 <-> STAGE 2 <-> STAGE 3)
                // Single persistent Box centered at TopCenter: expands left & right
                // symmetrically from the camera cutout with ZERO jhatka!
                // =============================================================
                Box(
                    modifier = Modifier
                        .width(animatedShellWidth)
                        .shadow(
                            elevation = if (stage == IslandStage.STAGE_3_EXPANDED) 24.dp else if (stage == IslandStage.STAGE_2_CAPSULE) 8.dp else 2.dp,
                            shape = shellShape,
                            ambientColor = Color.Black,
                            spotColor = Color.Black
                        )
                        .clip(shellShape)
                        .border(
                            width = if (pulseBoost > 0f) 1.25.dp else 0.9.dp,
                            brush = shellBorderBrush,
                            shape = shellShape
                        )
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = smoothDamping, stiffness = dynamicStiffness),
                            alignment = Alignment.TopCenter
                        ),
                    contentAlignment = Alignment.TopCenter
                ) {
                    FluidFrostedMistBackdrop(state = activeState, modifier = Modifier.matchParentSize())

                    Crossfade(
                        targetState = Triple(stage, activeState, isExpanded),
                        animationSpec = tween(durationMillis = (165f / speedMult).toInt().coerceIn(80, 260), easing = FastOutSlowInEasing),
                        label = "innerContentCrossfade"
                    ) { (targetStage, state, expanded) ->
                        when {
                            targetStage == IslandStage.STAGE_1_CAMERA_CIRCLE || state == IslandState.IDLE -> {
                                // STAGE 1: Small Circle hidden neatly behind the front camera cutout!
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(animatedCompactHeight)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            IslandConfig.performHapticTick(context)
                                            if (IslandConfig.forceDormantPreview.value) {
                                                IslandConfig.forceDormantPreview.value = false
                                                IslandConfig.restoreBaselineState()
                                            }
                                        }
                                )
                            }

                            state == IslandState.CALL -> {
                                val caller by IslandConfig.callerName.collectAsState()
                                val sub by IslandConfig.callSubtitle.collectAsState()
                                val incoming by IslandConfig.isCallIncoming.collectAsState()
                                val alwaysAccept by IslandConfig.alwaysShowCallAcceptButton.collectAsState()
                                val callDur by IslandConfig.callDurationSeconds.collectAsState()
                                val speaker by IslandConfig.isSpeakerEnabled.collectAsState()
                                val muted by IslandConfig.isMicMuted.collectAsState()
                                CallIsland(
                                    callerName = caller,
                                    subtitle = sub,
                                    isIncoming = incoming,
                                    alwaysShowAcceptBtn = alwaysAccept,
                                    callDurationSec = callDur,
                                    isSpeakerOn = speaker,
                                    isMuted = muted,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onToggleSpeaker = { IslandConfig.toggleCallSpeaker(context) },
                                    onToggleMute = { IslandConfig.toggleCallMute(context) },
                                    onEndCall = { IslandConfig.endCall(context) },
                                    onAcceptCall = { IslandConfig.acceptCall(context) }
                                )
                            }

                            state == IslandState.MUSIC -> {
                                val isPlaying by IslandConfig.isMediaPlaying.collectAsState()
                                val title by IslandConfig.musicTitle.collectAsState()
                                val artist by IslandConfig.musicArtist.collectAsState()
                                val artwork by IslandConfig.musicArtwork.collectAsState()
                                val isFavorite by IslandConfig.isFavoriteTrack.collectAsState()
                                val currentMs by IslandConfig.musicProgress.collectAsState()
                                val durationMs by IslandConfig.musicDuration.collectAsState()

                                val safeDuration = durationMs.coerceAtLeast(1L)
                                val fraction = (currentMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                                val currentStr = "%d:%02d".format(currentMs / 1000 / 60, currentMs / 1000 % 60)
                                val durationStr = "%d:%02d".format(safeDuration / 1000 / 60, safeDuration / 1000 % 60)

                                MusicIsland(
                                    title = title,
                                    artist = artist,
                                    artwork = artwork,
                                    playing = isPlaying,
                                    isFavorite = isFavorite,
                                    progressFraction = fraction,
                                    currentStr = currentStr,
                                    durationStr = durationStr,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    onToggleFavorite = { IslandConfig.isFavoriteTrack.value = !isFavorite },
                                    onPlayPause = { IslandConfig.toggleMediaPlayPause(context) },
                                    onNext = { IslandConfig.skipToNextTrack(context) },
                                    onPrevious = { IslandConfig.skipToPreviousTrack(context) },
                                    onSeek = { newFraction ->
                                        val newTimeMs = (newFraction * safeDuration).toLong()
                                        IslandConfig.musicProgress.value = newTimeMs
                                        IslandConfig.activeMediaController?.transportControls?.seekTo(newTimeMs)
                                    },
                                    onOutputClick = { IslandConfig.setExpandedState(false) },
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) }
                                )
                            }

                            state == IslandState.TIMER -> {
                                val remaining by IslandConfig.timerRemainingSeconds.collectAsState()
                                val total by IslandConfig.timerTotalSeconds.collectAsState()
                                val paused by IslandConfig.isTimerPaused.collectAsState()
                                val ringing by IslandConfig.isTimerRinging.collectAsState()
                                val subtitle by IslandConfig.timerSubtitle.collectAsState()
                                TimerOrStopwatchIsland(
                                    isStopwatch = false,
                                    secondsValue = remaining,
                                    totalSeconds = total,
                                    isPaused = paused,
                                    isRinging = ringing,
                                    subtitle = subtitle,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onTogglePause = { IslandConfig.toggleTimerPause() },
                                    onCancel = { IslandConfig.stopTimer() },
                                    onOpenApp = {
                                        IslandConfig.sendPendingIntentSafely(IslandConfig.timerOpenAppIntent)
                                        if (IslandConfig.autoCollapseOnAppOpen.value) {
                                            IslandConfig.setExpandedState(false)
                                        }
                                    }
                                )
                            }

                            state == IslandState.STOPWATCH -> {
                                val elapsed by IslandConfig.stopwatchElapsedSeconds.collectAsState()
                                val paused by IslandConfig.isStopwatchPaused.collectAsState()
                                val subtitle by IslandConfig.stopwatchSubtitle.collectAsState()
                                TimerOrStopwatchIsland(
                                    isStopwatch = true,
                                    secondsValue = elapsed,
                                    totalSeconds = 60,
                                    isPaused = paused,
                                    isRinging = false,
                                    subtitle = subtitle,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onTogglePause = { IslandConfig.toggleStopwatchPause() },
                                    onCancel = { IslandConfig.stopStopwatch() },
                                    onOpenApp = {
                                        IslandConfig.sendPendingIntentSafely(IslandConfig.stopwatchOpenAppIntent)
                                        if (IslandConfig.autoCollapseOnAppOpen.value) {
                                            IslandConfig.setExpandedState(false)
                                        }
                                    }
                                )
                            }

                            state == IslandState.NOTIFICATION -> {
                                val appName by IslandConfig.notificationAppName.collectAsState()
                                val nTitle by IslandConfig.notificationTitle.collectAsState()
                                val nBody by IslandConfig.notificationText.collectAsState()
                                val nSub by IslandConfig.notificationSubText.collectAsState()
                                val nIcon by IslandConfig.notificationIcon.collectAsState()
                                val nActionLabel by IslandConfig.notificationActionLabel.collectAsState()
                                GeneralNotificationIsland(
                                    appName = appName,
                                    title = nTitle,
                                    bodyText = nBody,
                                    subText = nSub,
                                    iconBitmap = nIcon,
                                    actionLabel = nActionLabel,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onOpenNotification = {
                                        IslandConfig.sendPendingIntentSafely(IslandConfig.notificationContentIntent)
                                        IslandConfig.setExpandedState(false)
                                        IslandConfig.restoreBaselineState()
                                    },
                                    onTriggerAction = {
                                        IslandConfig.sendPendingIntentSafely(IslandConfig.notificationActionIntent)
                                        IslandConfig.setExpandedState(false)
                                        IslandConfig.restoreBaselineState()
                                    },
                                    onDismiss = {
                                        IslandConfig.setExpandedState(false)
                                        IslandConfig.restoreBaselineState()
                                    }
                                )
                            }

                            state == IslandState.EARBUDS -> {
                                val name by IslandConfig.earbudsName.collectAsState()
                                val bat by IslandConfig.earbudsBattery.collectAsState()
                                EarbudsIsland(
                                    deviceName = name,
                                    batteryPercent = bat,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) }
                                )
                            }

                            state == IslandState.SOUND_MODE -> {
                                val modeText by IslandConfig.soundModeText.collectAsState()
                                val isSilent by IslandConfig.isSilentMode.collectAsState()
                                val isVibrate by IslandConfig.isVibrateMode.collectAsState()
                                SoundModeIsland(
                                    modeText = modeText,
                                    isSilent = isSilent,
                                    isVibrate = isVibrate,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) }
                                )
                            }

                            state == IslandState.RECORDING -> {
                                val elapsed by IslandConfig.recordingElapsedSeconds.collectAsState()
                                ScreenRecordingIsland(
                                    elapsedSeconds = elapsed,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onStopRecording = { IslandConfig.stopScreenRecording() }
                                )
                            }

                            state == IslandState.LOCK -> {
                                val locked by IslandConfig.isDeviceLocked.collectAsState()
                                LockIsland(locked, animatedShellWidth, animatedCompactHeight, compactCorner.dp)
                            }

                            state == IslandState.BATTERY -> {
                                val level by IslandConfig.batteryLevel.collectAsState()
                                val charging by IslandConfig.isCharging.collectAsState()
                                val watts by IslandConfig.chargingWatts.collectAsState()
                                val temp by IslandConfig.batteryTempCelsius.collectAsState()
                                val rem by IslandConfig.batteryTimeRemaining.collectAsState()
                                BatteryIsland(
                                    batteryPercent = level,
                                    charging = charging,
                                    wattsLabel = watts,
                                    tempLabel = temp,
                                    timeRemainingLabel = rem,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) }
                                )
                            }

                            state == IslandState.NAVIGATION -> {
                                val dist by IslandConfig.navDistance.collectAsState()
                                val dir by IslandConfig.navDirection.collectAsState()
                                val eta by IslandConfig.navEta.collectAsState()
                                NavigationIsland(
                                    distance = dist,
                                    direction = dir,
                                    eta = eta,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onOpenMaps = {
                                        IslandConfig.sendPendingIntentSafely(IslandConfig.navOpenIntent)
                                        IslandConfig.setExpandedState(false)
                                    },
                                    onEndRoute = { IslandConfig.stopNavigation() }
                                )
                            }

                            state == IslandState.OTP -> {
                                val code by IslandConfig.otpCode.collectAsState()
                                val sender by IslandConfig.otpSender.collectAsState()
                                OtpIsland(
                                    code = code,
                                    sender = sender,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onCopyOtp = { IslandConfig.copyOtpToClipboard(context) }
                                )
                            }

                            state == IslandState.TORCH -> {
                                val isOn by IslandConfig.isTorchOn.collectAsState()
                                TorchIsland(
                                    isOn = isOn,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onToggleTorch = { IslandConfig.toggleTorchHardware(context) }
                                )
                            }

                            state == IslandState.DOWNLOAD -> {
                                val title by IslandConfig.downloadTitle.collectAsState()
                                val app by IslandConfig.downloadAppName.collectAsState()
                                val pct by IslandConfig.downloadProgress.collectAsState()
                                DownloadIsland(
                                    title = title,
                                    appName = app,
                                    progressPercent = pct,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expandedWidth = targetExpandedWidthDp.dp,
                                    expandedCornerRadius = expandedCorner.dp,
                                    heightScale = heightScale,
                                    expanded = expanded,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) },
                                    onDismiss = { IslandConfig.dismissDownload() }
                                )
                            }

                            state == IslandState.DND -> {
                                val dndOn by IslandConfig.isDndEnabled.collectAsState()
                                val label by IslandConfig.dndModeLabel.collectAsState()
                                DndIsland(
                                    isEnabled = dndOn,
                                    label = label,
                                    compactWidth = animatedShellWidth,
                                    compactHeight = animatedCompactHeight,
                                    compactCornerRadius = compactCorner.dp,
                                    expanded = expanded,
                                    heightScale = heightScale,
                                    expandedCornerRadius = expandedCorner.dp,
                                    onExpandedChange = { IslandConfig.setExpandedState(it) }
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // DETACHED SMALL SPLIT CIRCLE ("CHOTA SA CIRCLE TUKDA") FOR 2ND PRIORITY TASK
                // =============================================================
                AnimatedVisibility(
                    visible = hasSplitSecondary && secondaryState != null,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.65f, transformOrigin = TransformOrigin(0.5f, 0.5f)),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.65f, transformOrigin = TransformOrigin(0.5f, 0.5f))
                ) {
                    if (secondaryState != null) {
                        DetachedSplitCircle(
                            secondaryState = secondaryState!!,
                            circleSize = splitCircleDp.dp,
                            onClick = { IslandConfig.swapSplitStates() }
                        )
                    }
                }
            }

            if (stretchFeedback.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xEE12161F))
                        .border(0.7.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stretchFeedback,
                        color = CrispWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
