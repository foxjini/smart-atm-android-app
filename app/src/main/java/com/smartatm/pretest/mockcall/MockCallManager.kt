package com.smartatm.pretest.mockcall

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class MockCallManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onTranscriptReceived: (newText: String, accumulatedText: String) -> Unit
) {
    companion object {
        private const val TAG = "MockCallManager"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val HEARTBEAT_INTERVAL_MS = 2000L   // 연결 유지 PING 주기
        private const val PEER_SILENCE_TIMEOUT_MS = 10000L // 이 시간 동안 아무 메시지도 없으면 연결 끊김으로 판단
    }

    private val audioStreamer = MockAudioStreamer(context)
    private val tonePlayer = CallTonePlayer(context)
    private val multicastLock = WifiMulticastLock(context)

    // 이 기기의 비콘을 식별하는 ID (자기가 만든 방이 탐색 목록에 뜨지 않도록)
    private val instanceId = java.util.UUID.randomUUID().toString()
    private val json = Json { ignoreUnknownKeys = true }

    // 제어 메시지는 단일 스레드에서 순서대로 전송 (자막 순서 뒤섞임 및 CALL_END 추월 방지)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val sendDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val _uiState = MutableStateFlow(MockCallUiState(localIp = NetworkUtils.getLocalIpAddress()))
    val uiState: StateFlow<MockCallUiState> = _uiState.asStateFlow()

    // Sockets & I/O
    private var serverSocket: ServerSocket? = null
    private var controlSocket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var socketReader: BufferedReader? = null

    @Volatile
    private var lastPeerActivityAt = 0L

    // Background Jobs
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var beaconSendJob: Job? = null
    private var beaconListenJob: Job? = null
    private var probeJob: Job? = null

    // 탐색 리스너 소켓 (포트 8990): 탐색 요청 송신과 응답 수신에 함께 사용
    @Volatile
    private var discoverySocket: DatagramSocket? = null

    // 이 기기가 방을 열고 상대를 기다리는 동안의 방 ID (탐색 요청에 응답할 때 사용)
    @Volatile
    private var advertisedRoomId: String? = null

    // Mock 통화 화면이 열려 있을 때만 서브넷 탐색 요청을 보냄
    @Volatile
    private var discoveryActive = false
    private var timerJob: Job? = null

    // Accumulated conversation for analysis
    private val accumulatedStringBuilder = java.lang.StringBuilder()

    private var toneJob: Job? = null

    init {
        startDiscoveryListener()
        startSubnetProbe()
        // 발신 중 연결음 / 수신 중 벨소리를 통화 상태에 맞춰 자동 전환
        toneJob = coroutineScope.launch {
            uiState.map { it.callStatus }
                .distinctUntilChanged()
                .collect { tonePlayer.onCallStatusChanged(it) }
        }
    }

    fun setRole(role: CallRole) {
        _uiState.update { it.copy(currentRole = role) }
    }

    fun updateRoomId(newId: String) {
        _uiState.update { it.copy(roomId = newId.trim()) }
    }

    /**
     * 방 만들기 (호스트 모드)
     */
    fun createRoom(roomId: String? = null) {
        val id = roomId ?: _uiState.value.roomId
        resetConnection()

        _uiState.update {
            it.copy(
                roomId = id,
                callStatus = CallStatus.WAITING_PEER,
                localIp = NetworkUtils.getLocalIpAddress()
            )
        }

        serverJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(NetworkUtils.PORT_CONTROL))
                }
                serverSocket = server
                Log.d(TAG, "Server socket opened on port ${NetworkUtils.PORT_CONTROL}")

                // 브로드캐스트 비콘 시작 (상대방 자동 탐색용)
                startBeaconSender(id)

                // 상대가 나가도 방을 유지하고 다음 참여자를 계속 받음
                // (예전에는 첫 접속만 받고 이후 접속은 대기열에 방치 → 참여자만 '연결됨'으로 보이는 불일치 발생)
                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    val busy = controlSocket?.isClosed == false ||
                        _uiState.value.callStatus == CallStatus.ENDED
                    if (busy) {
                        // 이미 상대가 있거나 통화 결과 화면이면 새 접속은 즉시 끊어 상대가 바로 실패를 알게 함
                        Log.d(TAG, "Rejecting extra client: ${client.inetAddress.hostAddress}")
                        client.close()
                        continue
                    }
                    Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}")
                    stopBeaconSender()
                    setupControlSocket(client)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Error in createRoom: ${e.message}")
                    _uiState.update { it.copy(callStatus = CallStatus.IDLE) }
                }
            }
        }
    }

    /**
     * 방 참여하기 (클라이언트 모드)
     */
    fun joinRoom(targetIp: String) {
        resetConnection()
        _uiState.update {
            it.copy(
                callStatus = CallStatus.WAITING_PEER,
                remotePeerIp = targetIp,
                localIp = NetworkUtils.getLocalIpAddress()
            )
        }

        clientJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to host $targetIp:${NetworkUtils.PORT_CONTROL}...")
                val socket = Socket().apply {
                    connect(InetSocketAddress(targetIp, NetworkUtils.PORT_CONTROL), CONNECT_TIMEOUT_MS)
                    soTimeout = 0
                }
                Log.d(TAG, "Connected to host $targetIp")
                setupControlSocket(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $targetIp", e)
                _uiState.update { it.copy(callStatus = CallStatus.IDLE) }
            }
        }
    }

    private fun setupControlSocket(socket: Socket) {
        controlSocket = socket
        val peerIp = socket.inetAddress.hostAddress ?: "unknown"

        socketWriter = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        socketReader = reader

        _uiState.update {
            it.copy(
                remotePeerIp = peerIp,
                callStatus = CallStatus.PEER_CONNECTED
            )
        }

        lastPeerActivityAt = System.currentTimeMillis()

        // 제어 소켓 수신 루프
        val readerJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    lastPeerActivityAt = System.currentTimeMillis()
                    handleIncomingControlMessage(line)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Control socket read closed: ${e.message}")
            } finally {
                // resetConnection()/방 재생성으로 이미 교체된 이전 소켓이면 새 연결 상태를 덮어쓰지 않음
                if (controlSocket === socket) {
                    handlePeerDisconnected()
                }
            }
        }

        // 연결 유지 PING 송신 + 상대 무응답 감지 (Wi-Fi 끊김 등으로 남은 '반쪽 연결' 정리)
        coroutineScope.launch(Dispatchers.IO) {
            while (isActive && readerJob.isActive && !socket.isClosed) {
                sendControlMessage(
                    ControlMessage(
                        type = MessageType.PING,
                        senderRole = _uiState.value.currentRole.name,
                        senderIp = _uiState.value.localIp
                    )
                )
                delay(HEARTBEAT_INTERVAL_MS)
                if (System.currentTimeMillis() - lastPeerActivityAt > PEER_SILENCE_TIMEOUT_MS) {
                    Log.w(TAG, "Peer silent for ${PEER_SILENCE_TIMEOUT_MS}ms, closing control socket")
                    try {
                        socket.close() // 수신 루프가 종료되며 handlePeerDisconnected() 호출
                    } catch (_: Exception) {
                    }
                    break
                }
            }
        }
    }

    /**
     * 전화 걸기 (CALL_OFFER)
     */
    fun makeCall() {
        if (_uiState.value.callStatus != CallStatus.PEER_CONNECTED) return

        _uiState.update { it.copy(callStatus = CallStatus.CALLING) }
        sendControlMessage(
            ControlMessage(
                type = MessageType.CALL_OFFER,
                senderRole = _uiState.value.currentRole.name,
                senderIp = _uiState.value.localIp,
                roomId = _uiState.value.roomId
            )
        )
    }

    /**
     * 전화 수락 (CALL_ANSWER)
     */
    fun acceptCall() {
        val socketIp = controlSocket?.inetAddress?.hostAddress
        val peerIp = if (!socketIp.isNullOrBlank() && socketIp != "127.0.0.1") {
            socketIp
        } else {
            _uiState.value.remotePeerIp ?: return
        }
        sendControlMessage(
            ControlMessage(
                type = MessageType.CALL_ANSWER,
                senderRole = _uiState.value.currentRole.name,
                senderIp = _uiState.value.localIp,
                roomId = _uiState.value.roomId
            )
        )
        startCallSession(peerIp)
    }

    /**
     * 전화 거절 (CALL_REJECT)
     */
    fun rejectCall() {
        sendControlMessage(
            ControlMessage(
                type = MessageType.CALL_REJECT,
                senderRole = _uiState.value.currentRole.name,
                senderIp = _uiState.value.localIp
            )
        )
        _uiState.update { it.copy(callStatus = CallStatus.PEER_CONNECTED) }
    }

    /**
     * 통화 종료 (CALL_END)
     */
    fun endCall() {
        sendControlMessage(
            ControlMessage(
                type = MessageType.CALL_END,
                senderRole = _uiState.value.currentRole.name,
                senderIp = _uiState.value.localIp
            )
        )
        stopCallSession()
    }

    private fun startCallSession(peerIp: String) {
        // 새 통화는 이전 통화의 자막/위험도 누적 없이 시작
        resetTranscript()
        audioStreamer.startStreaming(peerIp)
        _uiState.update {
            it.copy(
                callStatus = CallStatus.IN_CALL,
                callDurationSeconds = 0,
                isMuted = false
            )
        }
        startCallTimer()
    }

    private fun stopCallSession() {
        audioStreamer.stopStreaming()
        stopCallTimer()
        _uiState.update { it.copy(callStatus = CallStatus.ENDED) }
    }

    private fun handlePeerDisconnected() {
        audioStreamer.stopStreaming()
        stopCallTimer()
        closeControlSocket()

        // 방을 연 쪽(호스트)은 방을 유지하고 다시 참여자를 기다림
        val isHosting = serverSocket?.isClosed == false
        var resumedWaiting = false
        _uiState.update {
            val next = when {
                // 통화 종료 후 상대가 나가도 결과 확인(ENDED) 화면은 유지
                it.callStatus == CallStatus.ENDED -> CallStatus.ENDED
                isHosting -> CallStatus.WAITING_PEER
                else -> CallStatus.IDLE
            }
            resumedWaiting = next == CallStatus.WAITING_PEER
            it.copy(callStatus = next, remotePeerIp = null)
        }
        if (resumedWaiting) {
            startBeaconSender(_uiState.value.roomId)
        }
    }

    private fun closeControlSocket() {
        try {
            socketWriter?.close()
            socketReader?.close()
            controlSocket?.close()
        } catch (_: Exception) {
        }
        socketWriter = null
        socketReader = null
        controlSocket = null
    }

    /**
     * 실시간 STT 텍스트 또는 대본 자막 전송
     */
    fun sendTranscript(text: String, isLocalSender: Boolean = true) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        if (isLocalSender) {
            // 로컬 UI에도 기록
            addTranscriptItem(_uiState.value.currentRole, trimmed)
        }

        sendControlMessage(
            ControlMessage(
                type = MessageType.STT_TEXT,
                senderRole = _uiState.value.currentRole.name,
                senderIp = _uiState.value.localIp,
                payload = trimmed
            )
        )
    }

    private fun addTranscriptItem(role: CallRole, text: String) {
        // UI 스레드(로컬 발화)와 IO 스레드(상대 발화)에서 동시에 호출됨
        val fullAccumulated = synchronized(accumulatedStringBuilder) {
            if (accumulatedStringBuilder.isNotEmpty()) {
                accumulatedStringBuilder.append(" ")
            }
            accumulatedStringBuilder.append(text)
            _uiState.update { state ->
                val updated = state.liveTranscript + TranscriptItem(speakerRole = role, text = text)
                state.copy(liveTranscript = updated)
            }
            accumulatedStringBuilder.toString()
        }

        // 분석 트리거
        onTranscriptReceived(text, fullAccumulated)
    }

    fun updateRiskAnalysis(
        riskLevel: String,
        riskScore: Int,
        summary: String?,
        reasons: List<String>,
        warning: String?
    ) {
        _uiState.update {
            it.copy(
                realTimeRiskLevel = riskLevel,
                realTimeRiskScore = riskScore,
                realTimeSummary = summary,
                realTimeReasons = reasons,
                warningMessage = warning
            )
        }
    }

    fun toggleMute() {
        val newMute = !_uiState.value.isMuted
        audioStreamer.setMuted(newMute)
        _uiState.update { it.copy(isMuted = newMute) }
    }

    fun toggleSpeaker() {
        val newSpeaker = !_uiState.value.isSpeakerOn
        audioStreamer.setSpeakerphoneOn(newSpeaker)
        _uiState.update { it.copy(isSpeakerOn = newSpeaker) }
    }

    fun pauseAudioRecord() {
        audioStreamer.pauseRecording()
    }

    fun resumeAudioRecord() {
        audioStreamer.resumeRecording()
    }

    fun getAccumulatedTranscript(): String {
        return synchronized(accumulatedStringBuilder) { accumulatedStringBuilder.toString() }
    }

    fun resetTranscript() {
        synchronized(accumulatedStringBuilder) { accumulatedStringBuilder.setLength(0) }
        _uiState.update {
            it.copy(
                liveTranscript = emptyList(),
                realTimeRiskLevel = "SAFE",
                realTimeRiskScore = 0,
                realTimeSummary = null,
                realTimeReasons = emptyList(),
                warningMessage = null
            )
        }
    }

    // --- 수신 메시지 파싱 ---
    private fun handleIncomingControlMessage(raw: String) {
        try {
            val message = json.decodeFromString<ControlMessage>(raw)
            when (message.type) {
                MessageType.CALL_OFFER -> {
                    val socketIp = controlSocket?.inetAddress?.hostAddress
                    val effectivePeerIp = if (!socketIp.isNullOrBlank() && socketIp != "127.0.0.1") socketIp else message.senderIp
                    _uiState.update {
                        it.copy(
                            callStatus = CallStatus.INCOMING_CALL,
                            remotePeerIp = effectivePeerIp
                        )
                    }
                }
                MessageType.CALL_ANSWER -> {
                    val socketIp = controlSocket?.inetAddress?.hostAddress
                    val effectivePeerIp = if (!socketIp.isNullOrBlank() && socketIp != "127.0.0.1") {
                        socketIp
                    } else {
                        _uiState.value.remotePeerIp ?: message.senderIp
                    }
                    startCallSession(effectivePeerIp)
                }
                MessageType.CALL_REJECT -> {
                    _uiState.update { it.copy(callStatus = CallStatus.PEER_CONNECTED) }
                }
                MessageType.CALL_END -> {
                    stopCallSession()
                }
                MessageType.STT_TEXT -> {
                    val incomingText = message.payload ?: ""
                    if (incomingText.isNotBlank()) {
                        val senderRole = if (message.senderRole == CallRole.ATTACKER.name) {
                            CallRole.ATTACKER
                        } else {
                            CallRole.VICTIM
                        }
                        addTranscriptItem(senderRole, incomingText)
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse control message: $raw", e)
        }
    }

    private fun sendControlMessage(message: ControlMessage) {
        coroutineScope.launch(sendDispatcher) {
            try {
                val raw = json.encodeToString(message)
                socketWriter?.println(raw)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send control message", e)
            }
        }
    }

    // --- 통화 타이머 ---
    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            while (isActive) {
                delay(1000)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    private fun stopCallTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Mock 통화 화면 진입/이탈 시 호출: 화면이 열려 있을 때만 서브넷 탐색 요청 송신 */
    fun setDiscoveryActive(active: Boolean) {
        discoveryActive = active
    }

    private fun buildBeacon(roomId: String) = ControlMessage(
        type = MessageType.DISCOVERY_BEACON,
        senderRole = _uiState.value.currentRole.name,
        senderIp = _uiState.value.localIp,
        roomId = roomId,
        payload = instanceId
    )

    // --- 방 비콘 브로드캐스트 (방 생성자용) ---
    private fun startBeaconSender(roomId: String) {
        beaconSendJob?.cancel()
        advertisedRoomId = roomId
        beaconSendJob = coroutineScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }

                while (isActive) {
                    val bytes = json.encodeToString(buildBeacon(roomId)).toByteArray()
                    // Wi-Fi 서브넷 브로드캐스트 + 255.255.255.255 모두로 송신 (주소별 실패는 무시)
                    for (address in NetworkUtils.getBroadcastAddresses()) {
                        try {
                            socket.send(DatagramPacket(bytes, bytes.size, address, NetworkUtils.PORT_DISCOVERY))
                        } catch (e: Exception) {
                            Log.d(TAG, "Beacon send to $address failed: ${e.message}")
                        }
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                // Ignore cancel
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopBeaconSender() {
        advertisedRoomId = null
        beaconSendJob?.cancel()
        beaconSendJob = null
    }

    // --- 서브넷 유니캐스트 탐색 요청 (참여자용) ---
    // 핫스팟/공유기가 기기 간 브로드캐스트를 전달하지 않는 환경 대비: 같은 서브넷 주소마다 요청을 보내고
    // 방을 연 기기가 요청자에게 유니캐스트로 비콘을 응답한다.
    private fun startSubnetProbe() {
        probeJob?.cancel()
        probeJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = discoverySocket
                if (discoveryActive && socket != null && _uiState.value.callStatus == CallStatus.IDLE) {
                    val request = ControlMessage(
                        type = MessageType.DISCOVERY_REQUEST,
                        senderRole = _uiState.value.currentRole.name,
                        senderIp = _uiState.value.localIp,
                        payload = instanceId
                    )
                    val bytes = json.encodeToString(request).toByteArray()
                    for (address in NetworkUtils.getSubnetProbeAddresses()) {
                        if (!isActive) break
                        try {
                            socket.send(DatagramPacket(bytes, bytes.size, address, NetworkUtils.PORT_DISCOVERY))
                        } catch (e: Exception) {
                            // 개별 주소 실패 무시
                        }
                    }
                }
                delay(3000)
            }
        }
    }

    private fun replyToDiscoveryRequest(socket: DatagramSocket, requester: java.net.SocketAddress) {
        val roomId = advertisedRoomId ?: return
        try {
            val bytes = json.encodeToString(buildBeacon(roomId)).toByteArray()
            socket.send(DatagramPacket(bytes, bytes.size, requester))
        } catch (e: Exception) {
            Log.d(TAG, "Discovery reply failed: ${e.message}")
        }
    }

    // --- 방 비콘 수신 리스너 (참여자용) ---
    private fun startDiscoveryListener() {
        beaconListenJob?.cancel()
        beaconListenJob = coroutineScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            // 다른 기기의 브로드캐스트 비콘을 Wi-Fi 드라이버가 버리지 않도록 잠금 유지
            multicastLock.acquire()
            try {
                // 포트가 잠시 점유되어 있어도 탐색이 영구 중단되지 않도록 바인딩 재시도
                while (isActive && socket == null) {
                    socket = try {
                        DatagramSocket(null).apply {
                            reuseAddress = true
                            broadcast = true
                            bind(InetSocketAddress(NetworkUtils.PORT_DISCOVERY))
                            soTimeout = 2000
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Discovery socket bind failed, retrying: ${e.message}")
                        delay(2000)
                        null
                    }
                }
                val buffer = ByteArray(2048)
                discoverySocket = socket

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket!!.receive(packet)
                        val raw = String(packet.data, 0, packet.length)
                        val beacon = json.decodeFromString<ControlMessage>(raw)

                        if (beacon.type == MessageType.DISCOVERY_REQUEST && beacon.payload != instanceId) {
                            // 다른 기기의 탐색 요청: 방을 열고 있으면 요청자에게 직접 응답
                            replyToDiscoveryRequest(socket, packet.socketAddress)
                        } else if (beacon.type == MessageType.DISCOVERY_BEACON && beacon.roomId != null &&
                            beacon.payload != instanceId
                        ) {
                            val role = if (beacon.senderRole == CallRole.ATTACKER.name) CallRole.ATTACKER else CallRole.VICTIM
                            val room = DiscoveredRoom(
                                roomId = beacon.roomId,
                                // 비콘에 적힌 IP(127.0.0.1 등으로 잘못 잡힐 수 있음) 대신 실제 송신 주소 사용
                                hostIp = packet.address?.hostAddress ?: beacon.senderIp,
                                hostRole = role
                            )
                            updateDiscoveredRoom(room)
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout normal
                    } catch (e: Exception) {
                        // Ignore
                    }
                    cleanupExpiredRooms()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Discovery listener closed: ${e.message}")
            } finally {
                discoverySocket = null
                socket?.close()
                multicastLock.release()
            }
        }
    }

    private fun updateDiscoveredRoom(room: DiscoveredRoom) {
        _uiState.update { state ->
            val list = state.discoveredRooms.filter { it.hostIp != room.hostIp } + room
            state.copy(discoveredRooms = list)
        }
    }

    private fun cleanupExpiredRooms() {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            val active = state.discoveredRooms.filter { now - it.lastSeen < 6000 }
            if (active.size != state.discoveredRooms.size) {
                state.copy(discoveredRooms = active)
            } else {
                state
            }
        }
    }

    fun resetConnection() {
        stopBeaconSender()
        audioStreamer.stopStreaming()
        stopCallTimer()

        serverJob?.cancel()
        clientJob?.cancel()
        serverJob = null
        clientJob = null

        // 서버 소켓을 먼저 닫아, 제어 소켓 종료로 호출되는 handlePeerDisconnected()가
        // '호스트 대기'로 되돌리지 않게 함
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        closeControlSocket()

        _uiState.update {
            it.copy(
                callStatus = CallStatus.IDLE,
                remotePeerIp = null,
                callDurationSeconds = 0,
                isMuted = false
            )
        }
    }

    fun destroy() {
        resetConnection()
        beaconListenJob?.cancel()
        beaconListenJob = null
        probeJob?.cancel()
        probeJob = null
        toneJob?.cancel()
        toneJob = null
        tonePlayer.stopAll()
    }
}
