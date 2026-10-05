package com.smartatm.pretest.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechManager(
    private var context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit,
    private val onRmsChanged: ((Float) -> Unit)? = null,
    private val onStateChanged: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SpeechManager"
        private const val RESTART_DELAY_MS = 700L
        private val GOOGLE_SERVICE_COMPONENT = ComponentName(
            "com.google.android.googlequicksearchbox",
            "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListeningRequested = false

    fun updateContext(newContext: Context) {
        this.context = newContext
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech: Speech recognizer ready")
            onStateChanged?.invoke("🎙️ 음성을 듣고 있습니다 (말씀하세요)...")
            onPartialResult("🎙️ 듣고 있습니다 (말씀하세요)...")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech: User started speaking")
            onStateChanged?.invoke("🗣️ 음성 감지됨 (인식 중...)")
            onPartialResult("🗣️ 음성 인식 중...")
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRmsChanged?.invoke(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech: Silence detected")
            onStateChanged?.invoke("⏳ 음성 처리 및 변환 중...")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "마이크 오류 (하드웨어 점유 또는 권한 확인)"
                SpeechRecognizer.ERROR_CLIENT -> "음성인식 클라이언트 일시 오류"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크/인터넷 연결 오류"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 음성 없음 (계속 듣는 중...)"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성 인식기 준비 중"
                SpeechRecognizer.ERROR_SERVER -> "음성인식 서버 오류"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성이 감지되지 않음 (계속 듣는 중...)"
                else -> "음성 인식 오류 ($error)"
            }
            Log.w(TAG, "onError: $errorMsg ($error)")

            val isFatal = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            val isSilenceOrNoMatch = error == SpeechRecognizer.ERROR_NO_MATCH || 
                                     error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isFatal) {
                isListeningRequested = false
                onErrorOccurred(errorMsg)
                onStateChanged?.invoke("❌ $errorMsg")
            } else if (isListeningRequested) {
                if (!isSilenceOrNoMatch) {
                    onErrorOccurred(errorMsg)
                    onStateChanged?.invoke("⚠️ $errorMsg")
                } else {
                    onStateChanged?.invoke("🎙️ 음성을 기다리는 중...")
                }
                val delayMs = if (isSilenceOrNoMatch) 300L else RESTART_DELAY_MS
                scheduleRestart(delayMs)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
            if (recognizedText.isNotEmpty()) {
                Log.d(TAG, "onResults: $recognizedText")
                onFinalResult(recognizedText)
                onStateChanged?.invoke("✅ 인식 성공: \"$recognizedText\"")
            }
            if (isListeningRequested) {
                scheduleRestart(RESTART_DELAY_MS)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = matches?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotEmpty()) {
                Log.d(TAG, "onPartialResults: $partialText")
                onPartialResult(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun buildRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        speechRecognizer?.let { return it }

        // SpeechRecognizer 가용성 사전 검사
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is reported as NOT available on this device!")
        }

        // Google 음성인식 서비스가 있으면 우선 연결 (삼성 음성인식기보다 호환성이 월등함)
        val recognizer = try {
            SpeechRecognizer.createSpeechRecognizer(context, GOOGLE_SERVICE_COMPONENT)
        } catch (e: Exception) {
            Log.d(TAG, "Google speech service not found, falling back to default", e)
            SpeechRecognizer.createSpeechRecognizer(context)
        }

        recognizer.setRecognitionListener(recognitionListener)
        speechRecognizer = recognizer
        return recognizer
    }

    private fun recreateRecognizer(): SpeechRecognizer {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying recognizer", e)
        }
        speechRecognizer = null
        return getOrCreateRecognizer()
    }

    fun startListening() {
        handler.removeCallbacksAndMessages(null)
        handler.post {
            isListeningRequested = true
            onStateChanged?.invoke("🎙️ 음성 인식기 준비 중...")
            try {
                val recognizer = recreateRecognizer()
                recognizer.startListening(buildRecognizerIntent())
                Log.d(TAG, "Started listening successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                onErrorOccurred("음성 인식 시작 실패: ${e.localizedMessage}")
                scheduleRestart(1200L)
            }
        }
    }

    fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        isListeningRequested = false
        handler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
                onStateChanged?.invoke("⏹️ 음성 인식 중지됨")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognition", e)
            }
        }
    }

    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!isListeningRequested) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isListeningRequested) {
                try {
                    val recognizer = getOrCreateRecognizer()
                    recognizer.cancel()
                    recognizer.startListening(buildRecognizerIntent())
                    Log.d(TAG, "Restarted listening successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "Restart failed, recreating recognizer", e)
                    try {
                        val recognizer = recreateRecognizer()
                        recognizer.startListening(buildRecognizerIntent())
                    } catch (e2: Exception) {
                        Log.e(TAG, "Recreate and start also failed", e2)
                        scheduleRestart(1500L)
                    }
                }
            }
        }, delayMs)
    }

    fun destroy() {
        stopListening()
    }
}
