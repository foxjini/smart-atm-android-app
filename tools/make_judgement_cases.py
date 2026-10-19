"""서버 규칙 엔진의 판정을 그대로 받아 적어, 앱 단위테스트의 기준 데이터로 쓴다.

앱의 LocalRiskAnalyzer는 서버(ATM 저장소 backend/analysis)와 똑같이 판정해야 한다.
서버 키워드 사전을 바꿨다면 이 스크립트를 다시 돌리고, LocalRiskAnalyzer.kt도 맞춘다.

    python tools/make_judgement_cases.py <ATM 저장소 경로>

결과: app/src/test/resources/server_judgement_cases.json
"""
import json
import re
import sys
from pathlib import Path

ATM_REPO = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(ATM_REPO / "backend"))
from analysis.keywords import RISK_CATEGORIES  # noqa: E402
from analysis.rules import analyze_message  # noqa: E402

APP = Path(__file__).resolve().parent.parent
OUT = APP / "app/src/test/resources/server_judgement_cases.json"
SAMPLES = APP / "app/src/main/java/com/smartatm/pretest/data/model/SampleMessages.kt"

SENTENCES = [
    # 앱의 시연 대본 (MainViewModel)
    "여보세요, 서울중앙지검 수사관입니다. 선생님 명의로 개설된 계좌가 대포통장 사건에 연루되어 수사 중입니다. "
    "지금 바로 은행에 가셔서 예금을 전액 인출하신 다음에, 저희가 알려드리는 안전계좌로 입금해 주셔야 합니다. "
    "이건 수사 중인 사안이라 가족에게 말씀하지 마시고 혼자 오셔야 합니다.",
    "[국세청] 귀하의 계좌가 범죄에 연루되어 있습니다. 즉시 500만원을 인출해 강남역 3번 출구에서 전달하세요. 타인에게 발설하지 마십시오.",
    "엄마, 나야. 오늘 저녁에 집에 갈 건데 뭐 사갈까? 병원 예약은 목요일 두 시로 해놨어. 아버지 약도 타와야 하니까 처방전 챙겨놔. 끊을게, 이따 봐.",
    "어머니, 목요일 병원 예약 2시입니다. 처방전 잊지 마세요.",
    # 통화를 문장마다 이어 붙일 때의 중간 단계 (모의 통화 실시간 자막)
    "여보세요, 서울중앙지검 수사관입니다.",
    "여보세요, 서울중앙지검 수사관입니다. 선생님 명의로 개설된 계좌가 대포통장 사건에 연루되어 수사 중입니다.",
    # 평범한 말 (예전 앱 규칙은 이 문장들을 DANGER/CAUTION으로 잘못 봤다)
    "ATM에 가서 송금",
    "엄마, 병원 검사 결과 확인했어. 괜찮대.",
    "은행 들러서 용돈 보내 드릴게요.",
    "아버지, 이번 달 대출 이자 납부했어요.",
    "",
    "   ",
    # 대소문자 · 띄어쓰기
    "ATM 앞에서 즉시 인출하세요",
    "금감원 직원이 찾아갑니다. 비밀로 하세요.",
]
# 문자 검사 화면의 시연 문자 (SampleMessages.kt에서 그대로 읽는다 — 문자를 바꾸면 이 스크립트를 다시 돌린다)
SENTENCES += re.findall(r'body = "([^"]*)"', SAMPLES.read_text(encoding="utf-8"))
# 키워드 하나하나가 서버와 같은 범주에 걸리는지
SENTENCES += [f"고객님, {kw} 관련 안내입니다." for c in RISK_CATEGORIES for kw in c.keywords]

cases = []
for text in SENTENCES:
    r = analyze_message(text)
    cases.append({"text": text, "risk_level": r.risk_level, "risk_score": r.risk_score,
                  "reasons": r.reasons, "summary": r.summary})
OUT.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
print(f"{len(cases)}개 문장 → {OUT}")
