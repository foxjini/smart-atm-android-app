package com.smartatm.pretest.mockcall

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    const val PORT_CONTROL = 8988     // TCP control socket
    const val PORT_AUDIO = 8989       // UDP audio stream
    const val PORT_DISCOVERY = 8990   // UDP discovery beacon

    // 수동 연결 기본 상대방 IP (Windows 모바일 핫스팟의 호스트 주소)
    const val DEFAULT_PEER_IP = "192.168.137.1"

    private const val MAX_PROBE_HOSTS = 1024

    /**
     * 현재 기기의 로컬 Wi-Fi 또는 활성화된 사설 IPv4 주소(192.168.x.x 또는 10.x.x.x 등)를 반환합니다.
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // wlan0 (Wi-Fi) 우선 탐색
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                // wlan0: 일반 Wi-Fi, swlan0/ap0/softap0: 휴대폰 핫스팟(테더링) 인터페이스
                val isWifi = isWifiInterfaceName(intf.name)
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

    private fun isWifiInterfaceName(name: String): Boolean {
        val n = name.lowercase()
        return n.contains("wlan") || n.startsWith("ap") || n.startsWith("softap")
    }

    /**
     * 방 탐색 요청을 유니캐스트로 보낼 같은 Wi-Fi 서브넷의 상대 주소 목록 (자기 주소 제외).
     * 핫스팟/공유기가 기기 간 브로드캐스트를 전달하지 않아도, IP 직접 연결이 되는 환경이면 탐색된다.
     * 서브넷이 /22 보다 넓으면 자기 주소가 속한 /24 만 탐색한다.
     */
    fun getSubnetProbeAddresses(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { !it.isLoopback && it.isUp && isWifiInterfaceName(it.name) }
            for (intf in interfaces) {
                for (ifAddr in intf.interfaceAddresses) {
                    val addr = ifAddr.address as? Inet4Address ?: continue
                    if (!addr.isSiteLocalAddress) continue
                    val prefix = ifAddr.networkPrefixLength.toInt().coerceIn(22, 30)
                    val ip = addr.address.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
                    val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
                    val network = ip and mask
                    val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
                    var host = network + 1
                    while (host < broadcast && result.size < MAX_PROBE_HOSTS) {
                        if (host != ip) {
                            result += InetAddress.getByAddress(
                                byteArrayOf(
                                    (host shr 24).toByte(), (host shr 16).toByte(),
                                    (host shr 8).toByte(), host.toByte()
                                )
                            )
                        }
                        host++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.toList()
    }

    /**
     * 방 탐색 비콘을 보낼 브로드캐스트 주소 목록.
     * 255.255.255.255 는 모바일 데이터가 켜져 있거나 인터넷 없는 Wi-Fi일 때 Wi-Fi가 아닌 경로로
     * 나가 버릴 수 있으므로, Wi-Fi 서브넷 브로드캐스트(예: 192.168.0.255)를 먼저 보낸다.
     */
    fun getBroadcastAddresses(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                .filter { !it.isLoopback && it.isUp }
                .sortedByDescending { isWifiInterfaceName(it.name) }
            for (intf in interfaces) {
                for (ifAddr in intf.interfaceAddresses) {
                    val broadcast = ifAddr.broadcast ?: continue
                    if (ifAddr.address is Inet4Address && ifAddr.address.isSiteLocalAddress) {
                        result += broadcast
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result += InetAddress.getByName("255.255.255.255")
        return result.toList()
    }
}
