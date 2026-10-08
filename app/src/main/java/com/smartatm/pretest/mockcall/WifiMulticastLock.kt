package com.smartatm.pretest.mockcall

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Android Wi-Fi 드라이버는 절전을 위해 자기 주소가 아닌 브로드캐스트/멀티캐스트 패킷을 걸러낸다.
 * 이 잠금을 잡고 있어야 다른 스마트폰이 보낸 방 탐색 비콘(UDP 브로드캐스트)을 수신할 수 있다.
 */
class WifiMulticastLock(context: Context) {

    private val lock: WifiManager.MulticastLock? = try {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("SmartATM-MockCall-Discovery")
            ?.apply { setReferenceCounted(false) }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to create multicast lock", e)
        null
    }

    fun acquire() {
        try {
            lock?.acquire()
            Log.d(TAG, "Wi-Fi multicast lock acquired (${lock?.isHeld})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock", e)
        }
    }

    fun release() {
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release multicast lock", e)
        }
    }

    companion object {
        private const val TAG = "WifiMulticastLock"
    }
}
