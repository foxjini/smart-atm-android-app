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
import com.smartatm.pretest.data.model.LoginRequest
import com.smartatm.pretest.util.QrCodeGenerator
import com.smartatm.pretest.util.SpeechManager
import com.smartatm.pretest.mockcall.CallRole
import com.smartatm.pretest.mockcall.CallStatus
import com.smartatm.pretest.mockcall.MockCallManager
import com.smartatm.pretest.mockcall.MockCallUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    private var mockAnalysisJob: Job? = null

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

    init {
        initSpeechManager()
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
                // 통화 중 공격자 모드일 경우 음성인식 텍스트를 상대방에게 자동 전송
                if (mockCallUiState.value.callStatus == CallStatus.IN_CALL &&
                    mockCallUiState.value.currentRole == CallRole.ATTACKER) {
                    mockCallManager.sendTranscript(final, isLocalSender = true)
                }
            },
            onErrorOccurred = { error ->
                _errorMessage.value = error
            }
        )
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
        speechManager?.startListening()
    }

    fun stopListening() {
        _isListening.value = false
        speechManager?.stopListening()
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

        val token = _authToken.value
        if (token.isBlank()) {
            _errorMessage.value = "로그인이 필요합니다. 설정 화면에서 로그인해 주세요."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val service = retrofitProvider.getApiService()
                val authHeader = "Bearer $token"
                val request = AnalysisRequest(message = message)
                val response = withContext(Dispatchers.IO) { service.analyzeMessage(authHeader, request) }

                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!.data
                    _analysisResult.value = result

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
                } else {
                    val errorBody = response.errorBody()?.string()
                    val msg = parseErrorMessage(errorBody, "분석 요청 실패 (HTTP ${response.code()})")
                    _errorMessage.value = msg
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis request failed", e)
                _errorMessage.value = "서버에 연결할 수 없습니다: ${_serverUrl.value}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetAnalysis() {
        _analysisResult.value = null
        _qrBitmap.value = null
        _errorMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun parseErrorMessage(errorJson: String?, fallback: String): String {
        if (errorJson.isNullOrBlank()) return fallback
        return try {
            val jsonElement = Json.parseToJsonElement(errorJson).jsonObject
            val detail = jsonElement["detail"]
            if (detail != null) {
                if (detail is kotlinx.serialization.json.JsonObject) {
                    val message = detail["message"]?.jsonPrimitive?.content
                    message ?: fallback
                } else {
                    detail.jsonPrimitive.content
                }
            } else {
                fallback
            }
        } catch (e: Exception) {
            fallback
        }
    }

    fun analyzeMockCallRealtime(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val token = _authToken.value
        if (token.isBlank()) return

        mockAnalysisJob?.cancel()
        mockAnalysisJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(300)
                val service = retrofitProvider.getApiService()
                val authHeader = "Bearer $token"
                val request = AnalysisRequest(message = trimmed)
                val response = service.analyzeMessage(authHeader, request)

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!.data
                    val warning = when (data.riskLevel) {
                        "DANGER" -> "⚠️ 보이스피싱 강력 의심! (위험 점수: ${data.riskScore}/100) 즉시 통화를 종료하세요."
                        "CAUTION" -> "⚠️ 의심스러운 금융/기관 언급이 감지되었습니다. (주의 점수: ${data.riskScore}/100)"
                        else -> null
                    }
                    mockCallManager.updateRiskAnalysis(
                        riskLevel = data.riskLevel,
                        riskScore = data.riskScore,
                        summary = data.summary,
                        reasons = data.reasons,
                        warning = warning
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mock call real-time analysis error: ${e.message}")
            }
        }
    }

    fun confirmMockCallResult(onSuccess: () -> Unit) {
        val fullTranscript = mockCallManager.getAccumulatedTranscript()
        if (fullTranscript.isNotBlank()) {
            setInputText(fullTranscript)
        }
        analyze(onSuccess = onSuccess)
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
        mockCallManager.destroy()
    }
}
