package com.smartatm.pretest.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartatm.pretest.data.api.RetrofitProvider
import com.smartatm.pretest.data.local.PreferencesManager
import com.smartatm.pretest.data.model.AnalysisRequest
import com.smartatm.pretest.data.model.AnalysisResponse
import com.smartatm.pretest.data.model.ErrorResponse
import com.smartatm.pretest.data.model.LoginRequest
import com.smartatm.pretest.util.LocalRiskAnalyzer
import com.smartatm.pretest.util.QrCodeGenerator
import com.smartatm.pretest.util.SpeechManager
import com.smartatm.pretest.mockcall.CallRole
import com.smartatm.pretest.mockcall.CallStatus
import com.smartatm.pretest.mockcall.MockCallManager
import com.smartatm.pretest.mockcall.MockCallUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        const val MAX_TEXT_LENGTH = 2000

        // PRD Appendix Demo Scripts
        const val SCRIPT_VOICE_PHISHING_CALL =
            "여보세요, 서울중앙지검 수사관입니다. 선생님 명의로 개설된 계좌가 대포통장 사건에 연루되어 수사 중입니다. " +
            "지금 바로 은행에 가셔서 예금을 전액 인출하신 다음에, 저희가 알려드리는 안전계좌로 입금해 주셔야 합니다. " +
            "이건 수사 중인 사안이라 가족에게 말씀하지 마시고 혼자 오셔야 합니다."

        const val SCRIPT_VOICE_PHISHING_SMS =
            "[국세청] 귀하의 계좌가 범죄에 연루되어 있습니다. 즉시 500만원을 인출해 강남역 3번 출구에서 전달하세요. 타인에게 발설하지 마십시오."

        const val SCRIPT_NORMAL_CALL =
            "엄마, 나야. 오늘 저녁에 집에 갈 건데 뭐 사갈까? 병원 예약은 목요일 두 시로 해놨어. 아버지 약도 타와야 하니까 처방전 챙겨놔. 끊을게, 이따 봐."

        const val SCRIPT_NORMAL_SMS =
            "어머니, 목요일 병원 예약 2시입니다. 처방전 잊지 마세요."
    }

    private val preferencesManager = PreferencesManager(application)
    private val retrofitProvider = RetrofitProvider(preferencesManager)

    // Speech manager
    private var speechManager: SpeechManager? = null

    // Mock Call Manager (PRD v2.0)
    val mockCallManager = MockCallManager(
        context = application,
        coroutineScope = viewModelScope,
        onTranscriptReceived = { _, fullText ->
            analyzeMockCallRealtime(fullText)
        }
    )
    val mockCallUiState: StateFlow<MockCallUiState> = mockCallManager.uiState

    // UI State
    private val _serverUrl = MutableStateFlow(preferencesManager.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _username = MutableStateFlow(preferencesManager.username)
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(preferencesManager.password)
    val password: StateFlow<String> = _password.asStateFlow()

    private val _authToken = MutableStateFlow(preferencesManager.authToken)
    val authToken: StateFlow<String> = _authToken.asStateFlow()

    private val _healthStatus = MutableStateFlow<String?>("미확인")
    val healthStatus: StateFlow<String?> = _healthStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _partialSttText = MutableStateFlow("")
    val partialSttText: StateFlow<String> = _partialSttText.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _analysisResult = MutableStateFlow<AnalysisResponse?>(null)
    val analysisResult: StateFlow<AnalysisResponse?> = _analysisResult.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    // 서버 확인을 받지 못한 결과일 때 그 이유 (null이면 서버가 판정한 결과)
    private val _resultNotice = MutableStateFlow<String?>(null)
    val resultNotice: StateFlow<String?> = _resultNotice.asStateFlow()

    private val _rmsVolume = MutableStateFlow(0f)
    val rmsVolume: StateFlow<Float> = _rmsVolume.asStateFlow()

    private val _speechStatusText = MutableStateFlow("대기 중")
    val speechStatusText: StateFlow<String> = _speechStatusText.asStateFlow()

    // 문자 검사 목록: 메시지 id → 검사 결과 등급 (SAFE/CAUTION/DANGER)
    private val _smsCheckResults = MutableStateFlow<Map<Int, String>>(emptyMap())
    val smsCheckResults: StateFlow<Map<Int, String>> = _smsCheckResults.asStateFlow()

    // 다른 앱에서 [공유]로 문자를 받으면 true → 화면이 직접 입력 검사 화면으로 이동 후 소비
    private val _hasPendingSharedText = MutableStateFlow(false)
    val hasPendingSharedText: StateFlow<Boolean> = _hasPendingSharedText.asStateFlow()

    init {
        initSpeechManager()
    }

    fun attachActivityContext(activityContext: android.content.Context) {
        speechManager?.updateContext(activityContext)
    }

    /** Activity 파괴 시 호출: ViewModel이 파괴된 Activity를 붙잡아 누수되지 않도록 Application Context로 복귀 */
    fun detachActivityContext() {
        speechManager?.updateContext(getApplication())
    }

    private fun initSpeechManager() {
        speechManager = SpeechManager(
            context = getApplication(),
            onPartialResult = { partial ->
                _partialSttText.value = partial
            },
            onFinalResult = { final ->
                _partialSttText.value = ""
                appendInputText(final)
                // 통화 중일 경우 (공격자 또는 피해자) 음성인식 텍스트를 실시간 자막 및 분석에 반영
                if (mockCallUiState.value.callStatus == CallStatus.IN_CALL) {
                    mockCallManager.sendTranscript(final, isLocalSender = true)
                }
            },
            onErrorOccurred = { error ->
                _errorMessage.value = error
                _rmsVolume.value = 0f
            },
            onRmsChanged = { rms ->
                // rmsdB is typically between -2.0f and 10.0f; normalize to 0.0 ~ 1.0f
                val normalized = ((rms + 2f) / 12f).coerceIn(0f, 1f)
                _rmsVolume.value = normalized
            },
            onStateChanged = { status ->
                _speechStatusText.value = status
            }
        )
    }

    /**
     * Google 음성 인식 시스템 다이얼로그나 외부 인식기에서 반환된 텍스트 처리
     */
    fun handleVoiceRecognitionResult(recognizedText: String) {
        val trimmed = recognizedText.trim()
        if (trimmed.isNotBlank()) {
            appendInputText(trimmed)
            _speechStatusText.value = "✅ 음성 입력 완료: \"$trimmed\""
            if (mockCallUiState.value.callStatus == CallStatus.IN_CALL) {
                mockCallManager.sendTranscript(trimmed, isLocalSender = true)
            }
        }
    }

    fun updateServerUrl(url: String) {
        val trimmed = url.trim()
        preferencesManager.serverUrl = trimmed
        _serverUrl.value = trimmed
        retrofitProvider.invalidate()
    }

    fun updateUsername(name: String) {
        preferencesManager.username = name
        _username.value = name
    }

    fun updatePassword(pw: String) {
        preferencesManager.password = pw
        _password.value = pw
    }

    fun checkHealth() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _healthStatus.value = "확인 중..."
            try {
                val service = retrofitProvider.getApiService()
                val response = withContext(Dispatchers.IO) { service.checkHealth() }
                if (response.isSuccessful) {
                    _healthStatus.value = "연결 성공 (200 OK)"
                } else {
                    _healthStatus.value = "서버 응답 오류 (HTTP ${response.code()})"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Health check failed", e)
                _healthStatus.value = "서버에 연결할 수 없습니다 (${_serverUrl.value})"
                _errorMessage.value = "서버에 연결할 수 없습니다: ${_serverUrl.value}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val service = retrofitProvider.getApiService()
                val request = LoginRequest(username = _username.value.trim(), password = _password.value)
                val response = withContext(Dispatchers.IO) { service.login(request) }

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.data.accessToken
                    preferencesManager.authToken = token
                    _authToken.value = token
                    _errorMessage.value = null
                    onSuccess?.invoke()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = parseErrorMessage(errorBody, "로그인 실패 (HTTP ${response.code()})")
                    _errorMessage.value = msg
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login failed", e)
                _errorMessage.value = "서버에 연결할 수 없습니다: ${_serverUrl.value}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSharedText(text: String) {
        setInputText(text)
        _hasPendingSharedText.value = true
    }

    fun consumeSharedText() {
        _hasPendingSharedText.value = false
    }

    /** 문자 목록에서 선택한 메시지를 검사하고 목록에 표시할 결과 등급을 기록 */
    fun analyzeSms(messageId: Int, body: String, onSuccess: () -> Unit) {
        setInputText(body)
        analyze(onSuccess = {
            _analysisResult.value?.riskLevel?.let { level ->
                _smsCheckResults.value = _smsCheckResults.value + (messageId to level)
            }
            onSuccess()
        })
    }

    fun setInputText(text: String) {
        _inputText.value = if (text.length > MAX_TEXT_LENGTH) {
            text.substring(0, MAX_TEXT_LENGTH)
        } else {
            text
        }
    }

    fun appendInputText(text: String) {
        val current = _inputText.value
        val combined = if (current.isBlank()) {
            text
        } else {
            "$current $text"
        }
        setInputText(combined)
    }

    fun clearInputText() {
        _inputText.value = ""
        _partialSttText.value = ""
    }

    fun toggleListening() {
        if (_isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun startListening() {
        _isListening.value = true
        _partialSttText.value = ""
        // 통화 중이면 오디오 스트리머의 마이크를 잠시 정지하여 SpeechRecognizer에 마이크 독점 권한 부여
        if (mockCallUiState.value.callStatus == CallStatus.IN_CALL) {
            mockCallManager.pauseAudioRecord()
        }
        speechManager?.startListening()
    }

    fun stopListening() {
        _isListening.value = false
        _rmsVolume.value = 0f
        speechManager?.stopListening()
        // 통화 중이면 오디오 스트리머 마이크 복구
        if (mockCallUiState.value.callStatus == CallStatus.IN_CALL) {
            mockCallManager.resumeAudioRecord()
        }
        if (_partialSttText.value.isNotBlank()) {
            appendInputText(_partialSttText.value)
            _partialSttText.value = ""
        }
    }

    fun analyze(onSuccess: (() -> Unit)? = null) {
        val message = _inputText.value.trim()
        if (message.isBlank()) {
            _errorMessage.value = "검사할 텍스트를 입력해 주세요."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val token = _authToken.value
            if (token.isBlank()) {
                showLocalResult(message, "로그인하지 않아 서버에서 확인하지 못했습니다.")
                _isLoading.value = false
                onSuccess?.invoke()
                return@launch
            }

            try {
                val service = retrofitProvider.getApiService()
                val authHeader = "Bearer $token"
                val request = AnalysisRequest(message = message)
                val response = withContext(Dispatchers.IO) { service.analyzeMessage(authHeader, request) }

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!.data
                    _analysisResult.value = result
                    _resultNotice.value = null

                    // Generate QR if risk is not SAFE and sessionId is present
                    if (result.riskLevel != "SAFE" && !result.sessionId.isNullOrBlank()) {
                        val qrPayload = "{\"session_id\": \"${result.sessionId}\"}"
                        val bitmap = withContext(Dispatchers.Default) {
                            QrCodeGenerator.generateQrBitmap(qrPayload, 800)
                        }
                        _qrBitmap.value = bitmap
                    } else {
                        _qrBitmap.value = null
                    }

                    onSuccess?.invoke()
                } else if (response.code() == 401) {
                    // 토큰 만료(12시간)·손상 · 서버 DB를 새로 만든 뒤의 옛 토큰.
                    // 여기서 조용히 앱 판정으로 넘어가면, ATM이 모르는 결과를 들고 ATM 앞에 서게 된다.
                    preferencesManager.clearAuth()
                    _authToken.value = ""
                    val msg = parseErrorMessage(response.errorBody()?.string(), "로그인이 만료되었습니다.")
                    _errorMessage.value = "$msg (설정 화면 → 로그인)"
                } else {
                    Log.w(TAG, "Backend analysis failed (HTTP ${response.code()}), showing local result")
                    val msg = parseErrorMessage(response.errorBody()?.string(), "HTTP ${response.code()}")
                    showLocalResult(message, "서버 오류로 확인하지 못했습니다: $msg")
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis network failed, showing local result", e)
                showLocalResult(message, "서버에 연결할 수 없습니다: ${_serverUrl.value}")
                onSuccess?.invoke()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 서버 확인 없이 앱 규칙으로만 판정한 결과를 보여 준다 — 참고용이고 **QR은 만들지 않는다.**
     *
     * ATM은 QR의 session_id를 서버에 물어 본다. 서버에 없는 번호(예전의 "local-…")를 비추면
     * "등록되지 않은 QR"로 보고 막지 않는다 — 앱 화면은 "위험"인데 ATM은 돈을 내준다.
     */
    private fun showLocalResult(message: String, reason: String) {
        val localResult = LocalRiskAnalyzer.analyze(message)
        _analysisResult.value = AnalysisResponse(
            riskScore = localResult.riskScore,
            riskLevel = localResult.riskLevel,
            summary = localResult.summary,
            reasons = localResult.reasons
        )
        _resultNotice.value = reason
        _qrBitmap.value = null
    }

    fun resetAnalysis() {
        _analysisResult.value = null
        _qrBitmap.value = null
        _resultNotice.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun parseErrorMessage(errorJson: String?, fallback: String): String {
        return ErrorResponse.parse(errorJson)?.message ?: fallback
    }

    fun analyzeMockCallRealtime(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        // 공격자 모드는 통화만 수행하며 보이스피싱 위험도 분석은 피해자 기기에서만 진행
        if (mockCallUiState.value.currentRole == CallRole.ATTACKER) return

        // 통화 중 자막 경고는 앱 규칙으로만 낸다 (서버와 같은 규칙이라 결과도 같다).
        // 문장마다 서버에 보내면 POST /analysis 한 번마다 세션이 새로 생겨, 통화 한 번에
        // 콜센터 '확인 필요' 목록이 같은 통화로 여러 줄 찬다. 서버에는 통화가 끝난 뒤
        // confirmMockCallResult()에서 한 번만 보낸다 — 그 세션이 QR에 들어간다.
        val localResult = LocalRiskAnalyzer.analyze(trimmed)
        mockCallManager.updateRiskAnalysis(
            riskLevel = localResult.riskLevel,
            riskScore = localResult.riskScore,
            summary = localResult.summary,
            reasons = localResult.reasons,
            warning = localResult.warning
        )
    }

    fun confirmMockCallResult(onSuccess: () -> Unit) {
        val fullTranscript = mockCallManager.getAccumulatedTranscript()
        if (fullTranscript.isNotBlank()) {
            setInputText(fullTranscript)
        }

        val token = _authToken.value
        if (token.isBlank()) {
            // 로그인하지 않았으면 결과는 보여 주되 QR은 만들지 않는다 (showLocalResult 참고)
            showLocalResult(fullTranscript, "로그인하지 않아 서버에서 확인하지 못했습니다.")
            onSuccess.invoke()
        } else {
            analyze(onSuccess = onSuccess)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
        mockCallManager.destroy()
    }
}
