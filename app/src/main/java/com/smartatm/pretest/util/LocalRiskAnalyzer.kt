package com.smartatm.pretest.util

data class LocalRiskResult(
    val riskLevel: String,      // "SAFE", "CAUTION", "DANGER"
    val riskScore: Int,         // 0 ~ 100
    val summary: String,
    val reasons: List<String>,
    val warning: String?
)

/**
 * 서버 로그인 및 네트워크 연결 없이도 실시간으로 통화 음성/문자 텍스트를 즉시 분석하는 온디바이스 지능형 규칙 엔진
 */
object LocalRiskAnalyzer {

    // 1. 핵심 고위험 단일 키워드 (가중치 30~50점)
    private val highRiskKeywords = listOf(
        "안전계좌" to 50,
        "대포통장" to 40,
        "전달책" to 45,
        "현금 전달" to 45,
        "직접 전달" to 40,
        "사물함" to 35,
        "체포영장" to 40,
        "구속영장" to 40,
        "계좌 동결" to 35,
        "불법 자금" to 35,
        "범죄 연루" to 35,
        "범죄에 연루" to 35,
        "사건 연루" to 35,
        "원격제어" to 45,
        "팀뷰어" to 45,
        "애니데스크" to 45,
        "퀵서포트" to 45,
        "보증료 선입금" to 45,
        "선입금" to 40,
        "기존 대출 상환" to 40,
        "비밀로 하라" to 35,
        "발설하지" to 35,
        "타인에게 말하지" to 35,
        "가족에게 말하지" to 35,
        "은행원에게 말하지" to 40,
        "전화 끊지 마" to 35
    )

    // 2. 수사/공공/금융기관 사칭 단일 키워드 (가중치 15~25점)
    private val institutionKeywords = listOf(
        "서울중앙지검" to 30,
        "중앙지검" to 25,
        "수사관" to 25,
        "검찰청" to 25,
        "국세청" to 25,
        "검찰" to 20,
        "검사" to 20,
        "금융감독원" to 25,
        "금감원" to 20,
        "경찰청" to 20,
        "사이버수사대" to 20,
        "형사" to 15,
        "보안카드" to 20,
        "비밀번호" to 20,
        "주민등록번호" to 15,
        "개인정보" to 15
    )

    // 3. 자금 이동 및 인출/송금 관련 기본 키워드 (단독 15~25점)
    private val financialActionKeywords = listOf(
        "ATM" to 30,
        "에이티엠" to 30,
        "현금인출기" to 30,
        "자동화기기" to 25,
        "CD기" to 25,
        "송금" to 25,
        "계좌이체" to 25,
        "이체" to 20,
        "인출" to 25,
        "출금" to 25,
        "전액 인출" to 40,
        "예금 인출" to 40,
        "현금 찾아서" to 35,
        "돈을 찾아서" to 35,
        "입금" to 20,
        "무통장입금" to 30,
        "코인" to 25,
        "상품권" to 25
    )

    // 단순 포함 검사로는 오탐이 나는 키워드의 정밀 패턴 ("건강검사 결과", "정기검사 받으러" 등 제외)
    private val keywordPatterns = mapOf(
        "검사" to Regex("(?<![가-힣])검사(?!\\s*(결과|받으|받았|하러|를\\s*받))")
    )

    private val urlPattern = Regex("https?://|www\\.|bit\\.ly/|han\\.gl/|me2\\.kr/", RegexOption.IGNORE_CASE)

    private val smishingLures = listOf(
        "택배", "배송", "반송", "주소", "환급", "결제", "승인", "미납", "과태료", "청첩장", "부고", "쿠폰", "당첨"
    )

    private fun String.hasKeyword(kw: String): Boolean =
        keywordPatterns[kw]?.containsMatchIn(this) ?: contains(kw, ignoreCase = true)

    fun analyze(text: String): LocalRiskResult {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return LocalRiskResult(
                riskLevel = "SAFE",
                riskScore = 0,
                summary = "대화 대기 중",
                reasons = emptyList(),
                warning = null
            )
        }

        var totalScore = 0
        val detectedReasons = mutableListOf<String>()

        // 1. 단일 키워드 매칭
        for ((kw, weight) in highRiskKeywords) {
            if (trimmed.hasKeyword(kw)) {
                totalScore += weight
                detectedReasons.add("자금 탈취/비밀유지 고위험 키워드: '$kw'")
            }
        }

        for ((kw, weight) in institutionKeywords) {
            if (trimmed.hasKeyword(kw)) {
                totalScore += weight
                detectedReasons.add("기관 사칭 의심 키워드: '$kw'")
            }
        }

        for ((kw, weight) in financialActionKeywords) {
            if (trimmed.hasKeyword(kw)) {
                totalScore += weight
                detectedReasons.add("금융 거래/기기 이동 언급: '$kw'")
            }
        }

        // 2. 핵심 복합 조합 규칙 (보이스피싱 전형적 행동 패턴 -> 가산점 대폭 부여)

        // 규칙 A: ATM/기기 이동 + 송금/인출/이체 요구 (전형적인 대면/기기 편취 수법)
        // "기기 충전", "은행 가서 찾아올게" 같은 일상 표현이 DANGER로 오탐되지 않도록 ATM 기기와 은행 방문을 구분
        val hasAtmDevice = trimmed.contains("ATM", ignoreCase = true) ||
                           trimmed.contains("에이티엠", ignoreCase = true) ||
                           trimmed.contains("인출기", ignoreCase = true) ||
                           trimmed.contains("자동화기기", ignoreCase = true) ||
                           trimmed.contains("CD기", ignoreCase = true)

        val hasBankVisit = trimmed.contains("창구", ignoreCase = true) ||
                           trimmed.contains("은행", ignoreCase = true)

        // 송금/인출을 직접 지칭하는 강한 표현 ("보내", "찾아" 등 일상 동사 제외)
        val hasExplicitMoneyMove = trimmed.contains("송금", ignoreCase = true) ||
                                   trimmed.contains("이체", ignoreCase = true) ||
                                   trimmed.contains("인출", ignoreCase = true) ||
                                   trimmed.contains("출금", ignoreCase = true) ||
                                   trimmed.contains("입금", ignoreCase = true)

        val hasTransferOrWithdraw = trimmed.contains("송금", ignoreCase = true) ||
                                    trimmed.contains("이체", ignoreCase = true) ||
                                    trimmed.contains("인출", ignoreCase = true) ||
                                    trimmed.contains("출금", ignoreCase = true) ||
                                    trimmed.contains("입금", ignoreCase = true) ||
                                    trimmed.contains("보내", ignoreCase = true) ||
                                    trimmed.contains("찾아", ignoreCase = true) ||
                                    trimmed.contains("뽑아", ignoreCase = true)

        if (hasAtmDevice && hasTransferOrWithdraw) {
            totalScore += 50
            detectedReasons.add("🚨 [핵심 피싱 패턴] ATM/기기 이동 및 송금/인출 유도 복합 감지")
        } else if (hasBankVisit && hasExplicitMoneyMove) {
            totalScore += 30
            detectedReasons.add("⚠️ 은행 방문 및 예금 인출/송금 언급")
        }

        // 규칙 B: 기관 사칭 + 자금 보호/안전/동결/조사 요구
        val hasInstitution = institutionKeywords.any { trimmed.hasKeyword(it.first) }
        val hasInvestigationOrSafety = trimmed.contains("안전", ignoreCase = true) ||
                                      trimmed.contains("보호", ignoreCase = true) ||
                                      trimmed.contains("동결", ignoreCase = true) ||
                                      trimmed.contains("조사", ignoreCase = true) ||
                                      trimmed.contains("확인", ignoreCase = true) ||
                                      trimmed.contains("연루", ignoreCase = true) ||
                                      trimmed.contains("피해자", ignoreCase = true)

        if (hasInstitution && (hasInvestigationOrSafety || hasTransferOrWithdraw)) {
            totalScore += 45
            detectedReasons.add("🚨 [핵심 피싱 패턴] 수사/금융기관 사칭 및 계좌/자금 조작 유도 복합 감지")
        }

        // 규칙 C: 대출/신용 + 선입금/수수료/기존대출 상환
        val hasLoan = trimmed.contains("대출", ignoreCase = true) ||
                      trimmed.contains("대환", ignoreCase = true) ||
                      trimmed.contains("저금리", ignoreCase = true) ||
                      trimmed.contains("신용등급", ignoreCase = true)

        val hasFeeOrRepay = trimmed.contains("보증료", ignoreCase = true) ||
                            trimmed.contains("수수료", ignoreCase = true) ||
                            trimmed.contains("선입금", ignoreCase = true) ||
                            trimmed.contains("상환", ignoreCase = true) ||
                            trimmed.contains("예치금", ignoreCase = true) ||
                            trimmed.contains("납부", ignoreCase = true)

        if (hasLoan && hasFeeOrRepay) {
            totalScore += 50
            detectedReasons.add("🚨 [핵심 피싱 패턴] 대출 빙자 선입금/기존대출 상환 요구 복합 감지")
        }

        // 규칙 D: 통화 유지 강요 및 발설 금지 ("끊지 마라", "비밀로", "직원에게 말하지")
        val hasSecrecy = trimmed.contains("끊지", ignoreCase = true) ||
                         trimmed.contains("비밀", ignoreCase = true) ||
                         trimmed.contains("말하지", ignoreCase = true) ||
                         trimmed.contains("발설", ignoreCase = true) ||
                         trimmed.contains("혼자", ignoreCase = true)

        if (hasSecrecy && (hasTransferOrWithdraw || hasAtmDevice || hasBankVisit || hasInstitution)) {
            totalScore += 30
            detectedReasons.add("🚨 [심리적 압박 패턴] 주변 통보 차단 및 통화 유지 강요 감지")
        }

        // 규칙 E: 문자 속 출처 불명 링크(URL) + 택배/환급/결제 등 클릭 유도 문구 (스미싱)
        val hasUrl = urlPattern.containsMatchIn(trimmed)
        if (hasUrl) {
            totalScore += 30
            detectedReasons.add("출처 불명 인터넷 주소(URL) 포함")
            if (smishingLures.any { trimmed.contains(it) }) {
                totalScore += 30
                detectedReasons.add("🚨 [스미싱 패턴] 택배·환급·결제 등을 빙자한 링크 클릭 유도 감지")
            }
        }

        val clampedScore = totalScore.coerceIn(0, 100)

        return when {
            clampedScore >= 60 || (hasAtmDevice && hasTransferOrWithdraw) -> {
                // ATM 송금/인출 복합 유도 또는 60점 이상은 무조건 DANGER 및 최소 85점 이상 보정
                val finalScore = maxOf(clampedScore, 85)
                LocalRiskResult(
                    riskLevel = "DANGER",
                    riskScore = finalScore,
                    summary = "ATM 이동 및 자금 송금/인출 요구 등 전형적인 보이스피싱 수법이 명백하게 감지되었습니다.",
                    reasons = detectedReasons.distinct(),
                    warning = "🚨 보이스피싱 위험도 최고(${finalScore}점/100)! ATM 출금 및 송금이 차단됩니다. 즉시 전화를 끊으세요!"
                )
            }
            clampedScore >= 25 -> {
                LocalRiskResult(
                    riskLevel = "CAUTION",
                    riskScore = clampedScore,
                    summary = "기관 언급, 금융 거래 또는 의심스러운 단어가 포함된 주의 대상 대화입니다.",
                    reasons = detectedReasons.distinct(),
                    warning = "⚠️ 보이스피싱 의심 단어가 감지되었습니다. (주의 점수: $clampedScore/100)"
                )
            }
            else -> {
                LocalRiskResult(
                    riskLevel = "SAFE",
                    riskScore = clampedScore,
                    summary = "보이스피싱 의심 키워드가 감지되지 않은 정상적인 대화입니다.",
                    reasons = if (detectedReasons.isEmpty()) listOf("정상적인 금융/일상 거래 대화") else detectedReasons.distinct(),
                    warning = null
                )
            }
        }
    }
}
