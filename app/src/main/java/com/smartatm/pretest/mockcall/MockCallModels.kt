package com.smartatm.pretest.mockcall

import kotlinx.serialization.Serializable

/**
 * Mock 통화 역할 (공격자 vs 피해자)
 */
enum class CallRole(val title: String, val desc: String) {
    ATTACKER("공격자 (보이스피싱 범죄자)", "대본을 읽거나 통화를 걸어 피해자를 유도합니다."),
    VICTIM("피해자 (어르신 / 일반 시민)", "통화를 수신하고 실시간 위험도를 분석받습니다.")
}

/**
 * 통화 세션 상태 머신
 */
enum class CallStatus {
    IDLE,              // 초기 상태 (방 생성 또는 참여 대기)
    WAITING_PEER,      // 방 생성 후 상대방 접속 대기 중
    PEER_CONNECTED,    // 양쪽 스마트폰이 TCP로 연결됨 (전화 걸기 가능)
    CALLING,           // 발신 중 (상대방 수락 대기)
    INCOMING_CALL,     // 수신 중 (전화벨 울림, 수락/거절 대기)
    IN_CALL,           // 통화 진행 중 (음성 및 실시간 STT 분석 활성)
    ENDED              // 통화 종료 (최종 분석 확인 및 QR 연계 가능)
}

/**
 * TCP/UDP 제어 메시지 종류
 */
enum class MessageType {
    DISCOVERY_BEACON,  // 방 존재 브로드캐스트 (또는 탐색 요청에 대한 유니캐스트 응답)
    DISCOVERY_REQUEST, // 방 탐색 요청 (서브넷 주소에 유니캐스트로 송신)
    CALL_OFFER,        // 전화 걸기 요청
    CALL_ANSWER,       // 전화 수락
    CALL_REJECT,       // 전화 거절
    CALL_END,          // 통화 종료
    STT_TEXT,          // 실시간 발화 자막 텍스트
    MUTE_CHANGE,       // 마이크 음소거 상태 알림
    PING               // 연결 유지용
}

/**
 * 제어 소켓 통신 페이로드
 */
@Serializable
data class ControlMessage(
    val type: MessageType,
    val senderRole: String,       // "ATTACKER" or "VICTIM"
    val senderIp: String,
    val roomId: String? = null,
    val payload: String? = null,  // 텍스트 자막, 대본, 거절 사유 등
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 로컬 Wi-Fi에서 자동 탐색된 방 정보
 */
data class DiscoveredRoom(
    val roomId: String,
    val hostIp: String,
    val hostRole: CallRole,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Mock 통화 화면 전체 UI 상태
 */
data class MockCallUiState(
    val currentRole: CallRole = CallRole.VICTIM,
    val callStatus: CallStatus = CallStatus.IDLE,
    val localIp: String = "127.0.0.1",
    val roomId: String = "VP-CALL-001",
    val remotePeerIp: String? = null,
    val callDurationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val liveTranscript: List<TranscriptItem> = emptyList(),
    val partialText: String = "",
    val realTimeRiskLevel: String = "SAFE",  // SAFE, CAUTION, DANGER
    val realTimeRiskScore: Int = 0,
    val realTimeSummary: String? = null,
    val realTimeReasons: List<String> = emptyList(),
    val warningMessage: String? = null,
    val discoveredRooms: List<DiscoveredRoom> = emptyList()
)

/**
 * 통화 자막 항목
 */
data class TranscriptItem(
    val speakerRole: CallRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
