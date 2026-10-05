package com.smartatm.pretest.mockcall

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
    }

    private val audioStreamer = MockAudioStreamer(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(MockCallUiState(localIp = NetworkUtils.getLocalIpAddress()))
    val uiState: StateFlow<MockCallUiState> = _uiState.asStateFlow()

    // Sockets & I/O
    private var serverSocket: ServerSocket? = null
    private var controlSocket: Socket? = null
    private var socketWriter: PrintWriter? = null
    private var socketReader: BufferedReader? = null

    // Background Jobs
    private var serverJob: Job? = null
    private var clientJob: Job? = null
    private var beaconSendJob: Job? = null
    private var beaconListenJob: Job? = null
    private var timerJob: Job? = null

    // Accumulated conversation for analysis
    private val accumulatedStringBuilder = java.lang.StringBuilder()

    init {
        startDiscoveryListener()
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
                serverSocket = ServerSocket(NetworkUtils.PORT_CONTROL).apply {
                    reuseAddress = true
                }
                Log.d(TAG, "Server socket opened on port ${NetworkUtils.PORT_CONTROL}")

                // 브로드캐스트 비콘 시작 (상대방 자동 탐색용)
                startBeaconSender(id)

                val client = serverSocket!!.accept()
                Log.d(TAG, "Client connected: ${client.inetAddress.hostAddress}")
                stopBeaconSender()

                setupControlSocket(client)
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
                val socket = Socket(targetIp, NetworkUtils.PORT_CONTROL).apply {
                    soTimeout = 0
                }
                Log.d(TAG, "Connected to host $targetIp")
                setupControlSocket(socket)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $targetIp", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(callStatus = CallStatus.IDLE) }
                }
            }
        }
    }

    private fun setupControlSocket(socket: Socket) {
        controlSocket = socket
        val peerIp = socket.inetAddress.hostAddress ?: "unknown"

        socketWriter = PrintWriter(socket.getOutputStream(), true)
        socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))

        _uiState.update {
            it.copy(
                remotePeerIp = peerIp,
                callStatus = CallStatus.PEER_CONNECTED
            )
        }

        // 제어 소켓 수신 루프
        coroutineScope.launch(Dispatchers.IO) {
            try {
                while (isActive && controlSocket?.isConnected == true) {
                    val line = socketReader?.readLine() ?: break
                    handleIncomingControlMessage(line)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Control socket read closed: ${e.message}")
            } finally {
                handlePeerDisconnected()
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
        _uiState.update {
            it.copy(
                callStatus = CallStatus.IDLE,
                remotePeerIp = null
            )
        }
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
        if (accumulatedStringBuilder.isNotEmpty()) {
            accumulatedStringBuilder.append(" ")
        }
        accumulatedStringBuilder.append(text)
        val fullAccumulated = accumulatedStringBuilder.toString()

        _uiState.update { state ->
            val updated = state.liveTranscript + TranscriptItem(speakerRole = role, text = text)
            state.copy(liveTranscript = updated)
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
        return accumulatedStringBuilder.toString()
    }

    fun resetTranscript() {
        accumulatedStringBuilder.setLength(0)
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
        coroutineScope.launch(Dispatchers.IO) {
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

    // --- 방 비콘 브로드캐스트 (방 생성자용) ---
    private fun startBeaconSender(roomId: String) {
        beaconSendJob?.cancel()
        beaconSendJob = coroutineScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { broadcast = true }
                val broadcastAddr = NetworkUtils.getBroadcastAddress()

                while (isActive) {
                    val beacon = ControlMessage(
                        type = MessageType.DISCOVERY_BEACON,
                        senderRole = _uiState.value.currentRole.name,
                        senderIp = _uiState.value.localIp,
                        roomId = roomId
                    )
                    val bytes = json.encodeToString(beacon).toByteArray()
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, NetworkUtils.PORT_DISCOVERY)
                    socket.send(packet)
                    delay(1500)
                }
            } catch (e: Exception) {
                // Ignore cancel
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopBeaconSender() {
        beaconSendJob?.cancel()
        beaconSendJob = null
    }

    // --- 방 비콘 수신 리스너 (참여자용) ---
    private fun startDiscoveryListener() {
        beaconListenJob?.cancel()
        beaconListenJob = coroutineScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(NetworkUtils.PORT_DISCOVERY).apply {
                    broadcast = true
                    reuseAddress = true
                    soTimeout = 2000
                }
                val buffer = ByteArray(1024)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val raw = String(packet.data, 0, packet.length)
                        val beacon = json.decodeFromString<ControlMessage>(raw)

                        if (beacon.type == MessageType.DISCOVERY_BEACON && beacon.roomId != null) {
                            val role = if (beacon.senderRole == CallRole.ATTACKER.name) CallRole.ATTACKER else CallRole.VICTIM
                            val room = DiscoveredRoom(
                                roomId = beacon.roomId,
                                hostIp = beacon.senderIp,
                                hostRole = role
                            )
                            updateDiscoveredRoom(room)
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout normal
                        cleanupExpiredRooms()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Discovery listener closed: ${e.message}")
            } finally {
                socket?.close()
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

        try {
            socketWriter?.close()
            socketReader?.close()
            controlSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }

        socketWriter = null
        socketReader = null
        controlSocket = null
        serverSocket = null

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
    }
}
