@file:Suppress("DEPRECATION")

package com.example.dynamicisland

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class IslandAccessibilityService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var cameraManager: CameraManager? = null
    private var primaryFlashCameraId: String? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    private val batteryReceiver = IslandBatteryReceiver()
    private var systemEventsReceiver: BroadcastReceiver? = null
    private var lastRingerMode: Int? = null
    private var torchCallbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            // Ignore callbacks from non-flash cameras (e.g. front selfie camera) so they never reset the rear flashlight state!
            val targetId = primaryFlashCameraId
            if (targetId != null && cameraId != targetId) return
            IslandConfig.setTorchStateFromCallback(cameraId, enabled)
        }
    }

    override fun onCreate() {
        super.onCreate()
        IslandConfig.init(applicationContext)
        IslandConfig.updateDetectedScreenWidth(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        lastRingerMode = am?.ringerMode

        val batteryFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        ContextCompat.registerReceiver(this, batteryReceiver, batteryFilter, ContextCompat.RECEIVER_EXPORTED)

        registerContextualReceivers()
        registerTorchCallback()
        rebindNotificationServiceIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        rebindNotificationServiceIfNeeded()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        rebindNotificationServiceIfNeeded()
    }

    private fun rebindNotificationServiceIfNeeded() {
        if (IslandConfig.isNotificationServiceRunning.value) return
        try {
            val cn = ComponentName(applicationContext, IslandNotificationService::class.java)
            NotificationListenerService.requestRebind(cn)
        } catch (_: Exception) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        IslandConfig.updateDetectedScreenWidth(this)
    }

    private fun registerTorchCallback() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager = cm
            primaryFlashCameraId = cm.cameraIdList.firstOrNull { id ->
                try {
                    cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) {
                    false
                }
            }
            cm.registerTorchCallback(torchCallback, null)
            torchCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onServiceConnected() {
        super.onServiceConnected()
        IslandConfig.init(applicationContext)
        IslandConfig.updateDetectedScreenWidth(this)
        IslandConfig.refreshBatteryOptimizationState(this)
        IslandConfig.syncInitialRingerAndDndState(this)
        IslandConfig.isAccessibilityServiceRunning.value = true
        IslandConfig.restoreBaselineState()
        rebindNotificationServiceIfNeeded()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (composeView == null) {
            composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@IslandAccessibilityService)
                setViewTreeViewModelStoreOwner(this@IslandAccessibilityService)
                setViewTreeSavedStateRegistryOwner(this@IslandAccessibilityService)
                setOnTouchListener { _, event ->
                    if (event?.action == MotionEvent.ACTION_OUTSIDE && IslandConfig.isExpanded.value) {
                        IslandConfig.setExpandedState(false)
                        true
                    } else {
                        false
                    }
                }
                setContent { DynamicIslandUI() }
            }

            // Keep WRAP_CONTENT + TOP|CENTER_HORIZONTAL + FLAG_WATCH_OUTSIDE_TOUCH permanently!
            // Never resize the WindowManager window to MATCH_PARENT on expand—this completely eliminates
            // the WindowManager surface relayout jump ("jhatka") when transitioning between Stage 1, 2, and 3!
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = IslandConfig.yOffset.value.toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }

            try {
                windowManager?.addView(composeView, layoutParams)
            } catch (_: Exception) {
            }

            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            serviceScope.launch {
                launch {
                    IslandConfig.xOffset.collect {
                        // xOffset is handled inside Compose via translationX or offset
                    }
                }
                launch {
                    IslandConfig.yOffset.collect { yValue ->
                        layoutParams.y = yValue.toInt()
                        if (composeView?.isAttachedToWindow == true) {
                            try {
                                windowManager?.updateViewLayout(composeView, layoutParams)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                launch {
                    var tickCounter = 0
                    while (isActive) {
                        delay(1000.milliseconds)
                        IslandConfig.tickOneSecond()
                        tickCounter++
                        if (tickCounter % 45 == 0) {
                            rebindNotificationServiceIfNeeded()
                        }
                    }
                }
            }
        }
    }

    private fun registerContextualReceivers() {
        val receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                        val mode = intent.getIntExtra(
                            AudioManager.EXTRA_RINGER_MODE,
                            AudioManager.RINGER_MODE_NORMAL
                        )
                        if (lastRingerMode == null || lastRingerMode != mode) {
                            IslandConfig.onRingerModeUpdated(mode)
                        }
                        lastRingerMode = mode
                    }

                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val filterMode = nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                        val dndOn = filterMode != NotificationManager.INTERRUPTION_FILTER_ALL &&
                                filterMode != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                        IslandConfig.triggerDndHud(
                            enabled = dndOn,
                            label = if (dndOn) "Focus / DND" else "Focus Off"
                        )
                    }

                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        if (!IslandConfig.isStateFeatureEnabled(IslandState.EARBUDS)) return
                        val isConnectedEvent = if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                            true
                        } else {
                            intent.getIntExtra(
                                BluetoothA2dp.EXTRA_STATE,
                                BluetoothA2dp.STATE_DISCONNECTED
                            ) == BluetoothA2dp.STATE_CONNECTED
                        }

                        if (isConnectedEvent) {
                            val device: BluetoothDevice? =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }

                            val canReadBtName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            } else true

                            val name = if (canReadBtName && device != null) {
                                try {
                                    device.name?.takeIf { it.isNotBlank() } ?: "Wireless Audio"
                                } catch (_: SecurityException) {
                                    "Wireless Audio"
                                }
                            } else "Wireless Audio"

                            IslandConfig.earbudsName.value = name
                            IslandConfig.isEarbudsConnected.value = true
                            IslandConfig.showTemporaryState(
                                state = IslandState.EARBUDS,
                                durationMs = 3600L,
                                autoExpand = true
                            )
                        }
                    }

                    Intent.ACTION_USER_PRESENT -> {
                        IslandConfig.isDeviceLocked.value = false
                        if (IslandConfig.isStateFeatureEnabled(IslandState.LOCK)) {
                            // High-priority brief Unlock animation on the Island!
                            IslandConfig.showTemporaryState(IslandState.LOCK, durationMs = 2200L, autoExpand = false)
                        }
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        IslandConfig.isDeviceLocked.value = true
                        IslandConfig.setExpandedState(false)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        systemEventsReceiver = receiver
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanupServiceOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupServiceOverlay()
        super.onDestroy()
    }

    private fun cleanupServiceOverlay() {
        IslandConfig.isAccessibilityServiceRunning.value = false
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        } catch (_: Exception) {}
        serviceJob.cancel()
        if (torchCallbackRegistered) {
            try {
                cameraManager?.unregisterTorchCallback(torchCallback)
                torchCallbackRegistered = false
            } catch (_: Exception) {
            }
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        systemEventsReceiver?.let {
            try {
                unregisterReceiver(it)
                systemEventsReceiver = null
            } catch (_: Exception) {
            }
        }
        composeView?.let { view ->
            try {
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                }
            } catch (_: Exception) {
            }
        }
        composeView = null
        try {
            store.clear()
        } catch (_: Exception) {}
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}
