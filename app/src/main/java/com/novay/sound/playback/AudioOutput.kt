package com.novay.sound.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ses nereden çıkıyor: telefon hoparlörü, kablolu kulaklık ya da hangi Bluetooth cihazı. */
data class OutputDevice(val key: String, val label: String)

@Singleton
class AudioOutput @Inject constructor(@ApplicationContext context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val _current = MutableStateFlow(OutputDevice("speaker", "Telefon hoparlörü"))
    val current = _current.asStateFlow()

    init {
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refresh()
        }, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun refresh() {
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bt = outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        val wired = outs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        _current.value = when {
            bt != null -> OutputDevice("bt_" + bt.productName.toString().filter { it.isLetterOrDigit() }.take(30),
                "Bluetooth: ${bt.productName}")
            wired != null -> OutputDevice("wired", "Kablolu kulaklık")
            else -> OutputDevice("speaker", "Telefon hoparlörü")
        }
    }
}
