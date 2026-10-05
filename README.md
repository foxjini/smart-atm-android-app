# Smart ATM 안드로이드 사전 테스트 앱 (SmartATMPretest)

> **보이스피싱 실시간 사전 검사 및 라즈베리파이 ATM 출금 차단 연동 검증용 안드로이드 네이티브 앱**

---

## 📖 설치 및 사용 매뉴얼 (HTML)

상세한 설치 및 사용 설명서는 아래 HTML 문서를 웹 브라우저로 열어 확인하실 수 있습니다.

- **[manual.html (루트 매뉴얼)](./manual.html)**
- **[docs/manual.html (문서함 매뉴얼)](./docs/manual.html)**

---

## 🎯 주요 기능 및 특징

1. **3가지 의심 내용 입력 방식**:
   - 문자/메시지 앱에서 **[공유]** 로 본문 자동 수신 (`ACTION_SEND` Intent)
   - 통화 중 상대방 음성을 실시간 수집하는 **스피커폰 통화 받아쓰기 (STT)** 및 침묵 자동 재시작 루프
   - 원클릭 시연 대본 칩 (100점 보이스피싱 / 0점 정상 대화)
2. **실시간 위험도 분석 엔진 연동 (`POST /api/v1/analysis`)**:
   - 위험 점수(0~100점), 위험 등급(`SAFE`, `CAUTION`, `DANGER`), 세부 탐지 근거 제공
3. **ATM 카메라 인식 최적화 고대비 QR 표출**:
   - 라즈베리파이 저해상도(640×480) 카메라에 최적화된 고대비 흑백 QR 코드 자동 생성
   - QR 표출 시 **화면 밝기 100% 강제 고정** 및 **화면 꺼짐 방지 (`FLAG_KEEP_SCREEN_ON`)**
   - ATM 태깅 시 즉각 **출금 전면 차단 (`BLOCK`)** 동작 검증
4. **전시장 유동 IP 대응 동적 설정**:
   - 앱 내 설정(⚙️) 화면에서 백엔드 서버 URL(`http://192.168.0.x:8000`) 실시간 변경 및 헬스체크(`GET /health`)

---

## 🛠️ 개발 및 빌드 환경

- **언어 / UI**: Kotlin, Jetpack Compose Material 3
- **최소 / 타깃 SDK**: Min SDK 26 (Android 8.0) / Target SDK 34 (Android 14)
- **빌드 도구**: Gradle 8.6, Android Gradle Plugin 8.4.1, JDK 17
- **필수 권한**: `INTERNET`, `RECORD_AUDIO`

```bash
# APK 빌드 (Debug)
./gradlew assembleDebug

# 연결된 스마트폰에 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📂 주요 문서 목록

- [manual.html](./manual.html) : 설치 및 사용 방법 종합 매뉴얼 (웹 브라우저용)
- [07_안드로이드-사전테스트앱-PRD.md](./07_안드로이드-사전테스트앱-PRD.md) : 제품 요구사항 정의서
- [docs/08_Flutter-인수인계-가이드.md](./docs/08_Flutter-인수인계-가이드.md) : 팀 Flutter 개발자를 위한 기술 인수인계서
