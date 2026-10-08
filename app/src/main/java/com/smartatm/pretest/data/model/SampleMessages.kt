package com.smartatm.pretest.data.model

/**
 * 문자 검사 화면(갤럭시 메시지 앱 스타일 목록)에 표시할 시연용 문자 메시지.
 * [isPhishing]은 화면에는 표시하지 않고, 시연/테스트에서 기대 결과 확인용으로만 사용한다.
 */
data class SmsMessage(
    val id: Int,
    val sender: String,          // 연락처 이름 또는 발신 번호
    val phoneNumber: String,
    val isContact: Boolean,      // 저장된 연락처 여부 (아바타 표시 방식)
    val body: String,
    val timeLabel: String,       // 목록에 표시할 시각 ("오후 2:31", "어제", "10월 6일")
    val dateLabel: String,       // 대화 화면 날짜 구분선
    val unreadCount: Int = 0,
    val isPhishing: Boolean
)

object SampleMessages {

    val all: List<SmsMessage> = listOf(
        SmsMessage(
            id = 1,
            sender = "02-3480-2000",
            phoneNumber = "02-3480-2000",
            isContact = false,
            body = "[국세청] 귀하의 계좌가 범죄에 연루되어 있습니다. 즉시 500만원을 인출해 강남역 3번 출구에서 전달하세요. 타인에게 발설하지 마십시오.",
            timeLabel = "오후 2:31",
            dateLabel = "오늘",
            unreadCount = 1,
            isPhishing = true
        ),
        SmsMessage(
            id = 2,
            sender = "엄마",
            phoneNumber = "010-2345-6789",
            isContact = true,
            body = "어머니, 목요일 병원 예약 2시입니다. 처방전 잊지 마세요.",
            timeLabel = "오후 1:05",
            dateLabel = "오늘",
            isPhishing = false
        ),
        SmsMessage(
            id = 3,
            sender = "010-9182-7731",
            phoneNumber = "010-9182-7731",
            isContact = false,
            body = "엄마 나 폰 액정이 깨져서 수리 맡기고 임시폰으로 연락해. 급하게 결제할 게 있는데 문화상품권 10만원권 5장만 사서 핀번호 보내줄 수 있어? 아빠한테는 비밀로 해줘.",
            timeLabel = "오전 11:47",
            dateLabel = "오늘",
            unreadCount = 2,
            isPhishing = true
        ),
        SmsMessage(
            id = 4,
            sender = "김민수",
            phoneNumber = "010-5512-3344",
            isContact = true,
            body = "내일 저녁 7시에 학교 앞에서 보자! 늦지 마~",
            timeLabel = "오전 10:20",
            dateLabel = "오늘",
            isPhishing = false
        ),
        SmsMessage(
            id = 5,
            sender = "1588-1255",
            phoneNumber = "1588-1255",
            isContact = false,
            body = "[CJ대한통운] 고객님의 택배가 주소지 불명으로 반송 예정입니다. 아래 링크에서 주소를 확인해 주세요. http://cj-delivery.kr-track.xyz/a8",
            timeLabel = "어제",
            dateLabel = "어제",
            unreadCount = 1,
            isPhishing = true
        ),
        SmsMessage(
            id = 6,
            sender = "인천전자마이스터고",
            phoneNumber = "032-000-0000",
            isContact = true,
            body = "[인천전자마이스터고] 10월 정보통신과 현장실습 설명회가 금요일 오후 3시 본관 강당에서 진행됩니다. 많은 참석 바랍니다.",
            timeLabel = "어제",
            dateLabel = "어제",
            isPhishing = false
        ),
        SmsMessage(
            id = 7,
            sender = "+82 70-4821-0093",
            phoneNumber = "+82 70-4821-0093",
            isContact = false,
            body = "[국제발신] 고객님은 정부지원 저금리 대환대출 대상자입니다. 기존 대출 상환 후 승인되며, 보증료 선입금 확인 즉시 3,000만원 지급됩니다.",
            timeLabel = "10월 6일",
            dateLabel = "10월 6일 (화)",
            isPhishing = true
        ),
        SmsMessage(
            id = 8,
            sender = "아빠",
            phoneNumber = "010-8765-4321",
            isContact = true,
            body = "오늘 비 온다더라. 우산 꼭 챙겨가고 저녁은 집에서 먹자.",
            timeLabel = "10월 6일",
            dateLabel = "10월 6일 (화)",
            isPhishing = false
        ),
        SmsMessage(
            id = 9,
            sender = "010-4402-1187",
            phoneNumber = "010-4402-1187",
            isContact = false,
            body = "서울중앙지검 수사관입니다. 선생님 명의 계좌가 대포통장 사건에 연루되어 계좌 동결 예정입니다. 자산 보호를 위해 안전계좌로 이체하셔야 합니다.",
            timeLabel = "10월 5일",
            dateLabel = "10월 5일 (월)",
            isPhishing = true
        ),
        SmsMessage(
            id = 10,
            sender = "박지영 선생님",
            phoneNumber = "010-3321-7788",
            isContact = true,
            body = "민준 어머님, 다음 주 수요일 학부모 상담 시간 확인 부탁드립니다. 편하신 시간 알려주세요.",
            timeLabel = "10월 4일",
            dateLabel = "10월 4일 (일)",
            isPhishing = false
        )
    )

    fun findById(id: Int): SmsMessage? = all.firstOrNull { it.id == id }
}
