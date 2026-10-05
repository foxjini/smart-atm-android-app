package com.smartatm.pretest.mockcall

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    const val PORT_CONTROL = 8988     // TCP control socket
    const val PORT_AUDIO = 8989       // UDP audio stream
    const val PORT_DISCOVERY = 8990   // UDP discovery beacon

    /**
     * 현재 기기의 로컬 Wi-Fi 또는 활성화된 사설 IPv4 주소(192.168.x.x 또는 10.x.x.x 등)를 반환합니다.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // wlan0 (Wi-Fi) 우선 탐색
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val isWifi = intf.name.contains("wlan", ignoreCase = true)
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (isWifi) {
                            return hostAddress
                        }
                    }
                }
            }

            // Wi-Fi 명칭이 아닐 경우 다른 비-루프백 IPv4 반환
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    /**
     * 브로드캐스트 주소 (255.255.255.255)
     */
    fun getBroadcastAddress(): InetAddress {
        return InetAddress.getByName("255.255.255.255")
    }
}
