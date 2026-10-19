package com.smartatm.pretest.util

data class LocalRiskResult(
    val riskLevel: String,      // "SAFE", "CAUTION", "DANGER"
    val riskScore: Int,         // 0 ~ 100
    val summary: String,
    val reasons: List<String>,
    val warning: String?
)

/**
 * 서버 없이 바로 판정하는 규칙 엔진 — 통화 중 실시간 자막 경고와, 서버에 닿지 못했을 때의 참고용 판정에 쓴다.
 *
 * **서버(ATM 저장소 backend/analysis/keywords.py · rules.py)와 똑같이 판정해야 한다.**
 * ATM이 막을지 말지는 서버가 정한다. 앱이 다른 기준을 쓰면 통화 화면에는 "위험"이 떴는데
 * ATM은 그냥 돈을 내주거나, 그 반대가 된다. 키워드를 바꾸려면 서버 사전을 먼저 바꾸고
 * 여기를 그대로 맞춘 뒤, test/resources/server_judgement_cases.json을 다시 만든다
 * (tools/make_judgement_cases.py).
 *
 * 규칙 (PRD 5.2 — 임의로 바꾸지 않는다)
 *  - 7개 범주마다 키워드가 하나라도 있으면 그 범주의 가중치를 **한 번만** 더한다
 *  - 최대 100점, 0~29 SAFE / 30~69 CAUTION / 70~100 DANGER
 */
object LocalRiskAnalyzer {

    private class RiskCategory(val reason: String, val weight: Int, val keywords: List<String>)

    private val categories = listOf(
        RiskCategory(
            "기관 사칭 표현 감지", 35, listOf(
                "검찰", "검사입니다", "수사관", "경찰청", "경찰서", "금융감독원", "금감원",
                "국세청", "우체국입니다", "법원입니다", "공공기관", "수사기관",
                "은행 직원입니다", "금융기관입니다", "보건복지부", "질병관리청",
            )
        ),
        RiskCategory(
            "범죄 연루 언급 감지", 25, listOf(
                "범죄에 연루", "범죄 연루", "사건에 연루", "명의도용", "명의 도용",
                "대포통장", "자금세탁", "자금 세탁", "구속", "체포", "수사 중",
                "계좌가 도용", "피의자", "출석 요구", "압수",
            )
        ),
        RiskCategory(
            "현금 인출 요구 감지", 30, listOf(
                "현금을 인출", "현금 인출", "인출해", "인출하세요", "인출하여",
                "돈을 찾아", "돈을 인출", "출금하세요", "출금해", "현금으로 찾",
                "예금을 찾", "전액 인출",
            )
        ),
        RiskCategory(
            "송금·이체 요구 감지", 25, listOf(
                "송금하세요", "송금해", "송금 바랍니다", "이체하세요", "이체해",
                "계좌로 보내", "안전계좌", "안전 계좌", "입금하세요", "입금해 주세요",
                "상품권", "핀번호", "기프트카드", "선입금", "보증료",
            )
        ),
        RiskCategory(
            "긴급 행동 요구 감지", 15, listOf(
                "지금 바로", "즉시", "당장", "오늘 안에", "서둘러", "빨리",
                "시간이 없", "마감", "긴급", "늦으면", "급하게", "급히",
            )
        ),
        RiskCategory(
            "비밀 유지 요구 감지", 25, listOf(
                "말하지 마", "말씀하지 마", "알리지 마", "비밀로", "비밀 유지",
                "누구에게도", "가족에게 말", "은행 직원에게 말", "혼자 오",
                "발설하지", "함구",
            )
        ),
        RiskCategory(
            "특정 장소 현금 전달 요구 감지", 25, listOf(
                "지정 장소", "지정된 장소", "장소로 가져", "가지고 오세요", "가져오세요",
                "직접 전달", "만나서 전달", "수거", "직원이 찾아", "수령하러",
            )
        ),
    )

    private const val SCORE_MAX = 100
    private const val SAFE_MAX = 29
    private const val CAUTION_MAX = 69
    private const val SUMMARY_MAX_REASONS = 2
    private const val SAFE_SUMMARY = "위험 표현이 발견되지 않았습니다"

    fun analyze(text: String): LocalRiskResult {
        val normalized = text.trim().lowercase()

        var score = 0
        val reasons = mutableListOf<String>()
        if (normalized.isNotEmpty()) {
            for (category in categories) {
                if (category.keywords.any { normalized.contains(it.lowercase()) }) {
                    score += category.weight
                    reasons += category.reason
                }
            }
        }
        score = minOf(score, SCORE_MAX)

        val level = when {
            score <= SAFE_MAX -> "SAFE"
            score <= CAUTION_MAX -> "CAUTION"
            else -> "DANGER"
        }
        val summary = if (reasons.isEmpty()) {
            SAFE_SUMMARY
        } else {
            reasons.take(SUMMARY_MAX_REASONS).joinToString(" 및 ") { it.replace(" 감지", "") } + " 감지"
        }
        val warning = when (level) {
            "DANGER" -> "🚨 보이스피싱 위험(${score}점/100)! 즉시 전화를 끊으세요!"
            "CAUTION" -> "⚠️ 보이스피싱 의심 표현이 감지되었습니다. (주의 점수: $score/100)"
            else -> null
        }
        return LocalRiskResult(level, score, summary, reasons, warning)
    }
}
