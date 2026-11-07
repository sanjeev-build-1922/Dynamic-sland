package com.example.dynamicisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import kotlin.math.abs

class IslandBatteryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null) {
            IslandConfig.init(context.applicationContext)
        }
        val action = intent?.action ?: return
        if (action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_BATTERY_CHANGED
        ) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val isChargingNow = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged ||
                    action == Intent.ACTION_POWER_CONNECTED) &&
                    action != Intent.ACTION_POWER_DISCONNECTED

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            var currentPct = IslandConfig.batteryLevel.value
            if (level != -1 && scale > 0) {
                currentPct = (level * 100 / scale.toFloat()).toInt().coerceIn(0, 100)
                IslandConfig.batteryLevel.value = currentPct
            }

            val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 350)
            if (tempTenths > 0) {
                IslandConfig.batteryTempCelsius.value = "%.1f°C".format(tempTenths / 10f)
            }

            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000).coerceAtLeast(3500)
            val bm = context?.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null && isChargingNow) {
                val currentNowUa = abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
                val currentMa = if (currentNowUa > 50_000) currentNowUa / 1000f else currentNowUa.toFloat()
                val watts = ((voltageMv / 1000f) * (currentMa / 1000f)).coerceIn(5f, 120f)
                IslandConfig.chargingWatts.value = if (watts >= 18f) {
                    "%.0fW Fast".format(watts)
                } else {
                    "%.0fW Charging".format(watts)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val remMs = bm.computeChargeTimeRemaining()
                    if (remMs > 0L) {
                        val mins = (remMs / 60_000L).coerceAtLeast(1L)
                        IslandConfig.batteryTimeRemaining.value = "${mins}m to full"
                    } else {
                        val estMins = ((100 - currentPct) * 0.7f).toInt().coerceAtLeast(1)
                        IslandConfig.batteryTimeRemaining.value = "${estMins}m to full"
                    }
                }
            } else {
                IslandConfig.batteryTimeRemaining.value =
                    if (isChargingNow) "Charging..." else "$currentPct% remaining"
            }

            val wasCharging = IslandConfig.isCharging.value
            if (action == Intent.ACTION_POWER_DISCONNECTED || (!isChargingNow && wasCharging)) {
                IslandConfig.isCharging.value = false
                if (IslandConfig.currentState.value == IslandState.BATTERY) {
                    IslandConfig.setExpandedState(false)
                }
                IslandConfig.restoreBaselineState()
            } else if (isChargingNow && !wasCharging) {
                IslandConfig.onChargerConnected()
            } else if (isChargingNow) {
                IslandConfig.isCharging.value = true
                IslandConfig.restoreBaselineState()
            } else {
                IslandConfig.checkLowBatteryWarning(currentPct, charging = false)
            }
        }
    }
}
