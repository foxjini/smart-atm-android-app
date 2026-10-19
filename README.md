# Smart ATM 안드로이드 사전 테스트 앱 (SmartATMPretest)

> **보이스피싱 실시간 사전 검사 및 라즈베리파이 ATM 출금 차단 연동 검증용 안드로이드 네이티브 앱**

---

## 📖 설치 및 사용 매뉴얼 (HTML)

상세한 설치 및 사용 설명서는 아래 HTML 문서를 웹 브라우저로 열어 확인하실 수 있습니다.

- **[manual.html (루트 매뉴얼)](./manual.html)**
- **[docs/manual.html (문서함 매뉴얼)](./docs/manual.html)**

---

## 🎯 주요 기능 및 특징

1. **Wi-Fi 2대 스마트폰 Mock 통화 시뮬레이션 (PRD v2.0 확장)**:
   - 안드로이드 OS의 통화 직접 녹음 제약을 우회하기 위해 **동일 Wi-Fi 환경에서 2대 스마트폰(공격자 vs 피해자) 간 실시간 음성 통화** 지원
   - UDP 16kHz PCM 저지연 오디오 스트리밍 (`AudioRecord` ➔ `AudioTrack`)
   - 통화 중 상대방 음성 실시간 STT 자막 스트리밍 및 백엔드 누적 위험도 분석
   - 기관 사칭/안전계좌 이체 감지 시 **실시간 위험 경고 배너** 및 통화 종료 후 즉각 **ATM 출금 차단 QR 연동**
   - 전시장 네트워크 대응 **UDP 비콘 자동 방 탐색** 및 수동 IP 직접 연결 동시 지원
   - 공격자 모드 **원클릭 시연 대본 퀵 전송** (전시장 소음 환경 대비)
2. **문자 검사 (직접 입력 / 문자 앱 공유)**:
   - 문자/메시지 앱에서 **[공유]** 로 본문 자동 수신 (`ACTION_SEND` Intent)
   - 다른 기기에서 틀어 준 통화 녹음·대본 음성을 받아쓰는 **실시간 받아쓰기 (STT)** 및 침묵 자동 재시작 루프
     (실제 전화가 연결된 같은 폰에서는 안드로이드가 마이크를 무음으로 막아 받아쓸 수 없다)
   - 원클릭 시연 대본 칩 (100점 보이스피싱 / 0점 정상 대화)
3. **실시간 위험도 분석 엔진 연동 (`POST /api/v1/analysis`)**:
   - 위험 점수(0~100점), 위험 등급(`SAFE`, `CAUTION`, `DANGER`), 세부 탐지 근거 제공
4. **ATM 카메라 인식 최적화 고대비 QR 표출**:
   - 라즈베리파이 저해상도(640×480) 카메라에 최적화된 고대비 흑백 QR 코드 자동 생성
   - QR 표출 시 **화면 밝기 100% 강제 고정** 및 **화면 꺼짐 방지 (`FLAG_KEEP_SCREEN_ON`)**
   - ATM 태깅 시 즉각 **출금 전면 차단 (`BLOCK`)** 동작 검증
5. **전시장 유동 IP 대응 동적 설정**:
   - 앱 내 설정(⚙️) 화면에서 백엔드 서버 URL(기본값 `http://192.168.137.1:8000`, 모바일 핫스팟 기준) 실시간 변경 및 헬스체크(`GET /health`)

---

## 🛠️ 개발 및 빌드 환경

- **언어 / UI**: Kotlin, Jetpack Compose Material 3
- **최소 / 타깃 SDK**: Min SDK 26 (Android 8.0) / Target SDK 34 (Android 14)
- **빌드 도구**: Gradle 9.6.0, Android Gradle Plugin 9.4.1, Kotlin 2.2.10, JDK 17
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
- [docs/07_안드로이드-사전테스트앱-PRD_v2.0_Mock통화.md](./docs/07_안드로이드-사전테스트앱-PRD_v2.0_Mock통화.md) : 제품 요구사항 정의서 v2.0 (Mock 통화 연동 확장판)
- [07_안드로이드-사전테스트앱-PRD.md](./07_안드로이드-사전테스트앱-PRD.md) : 제품 요구사항 정의서 v1.0
- [docs/08_Flutter-인수인계-가이드.md](./docs/08_Flutter-인수인계-가이드.md) : 팀 Flutter 개발자를 위한 기술 인수인계서
