package com.novay.sound.bt

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.novay.sound.data.settings.SettingsRepository
import com.novay.sound.data.settings.Toggle
import com.novay.sound.playback.PlaybackService
import com.novay.sound.util.CrashLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Araba / kulaklık Bluetooth'u bağlanınca kaldığın yerden çal.
 * Arka planda servis başlatabilmek için pil optimizasyonundan muaf olmak gerekir (Ayarlar'dan istenir).
 */
class BluetoothAutoPlayReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun settings(): SettingsRepository }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        val device: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return

        val pending = goAsync()
        val settings = EntryPointAccessors.fromApplication(context.applicationContext, Deps::class.java).settings()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = settings.current()
                if (!s.on(Toggle.AUTO_PLAY_BT)) return@launch
                val address = runCatching { device.address }.getOrNull() ?: return@launch
                val isAudio = runCatching { device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO }
                    .getOrDefault(true)
                val allowed = if (s.btDevices.isEmpty()) isAudio else address in s.btDevices
                if (!allowed) return@launch
                delay(2500) // ses yolunun Bluetooth'a geçmesini bekle
                runCatching {
                    ContextCompat.startForegroundService(
                        context, Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_AUTOPLAY)
                    )
                }.onFailure { CrashLogger.log("Bluetooth otomatik çalma başlatılamadı (pil optimizasyonu?)", it) }
            } finally {
                pending.finish()
            }
        }
    }
}
