package com.smartatm.pretest.util

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
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) {
    companion object {
        private const val TAG = "SpeechManager"
        private const val RESTART_DELAY_MS = 250L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isListeningRequested = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            // SpeechRecognizer stops on silence; restart if listening is still requested
            scheduleRestart()
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
                SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한 필요"
                SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                SpeechRecognizer.ERROR_NO_MATCH -> "일치하는 음성 없음 (재시도 중)"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "음성인식기 사용 중 (재시도 중)"
                SpeechRecognizer.ERROR_SERVER -> "서버 에러"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말이 없어 대기 중 (재시도 중)"
                else -> "음성 인식 에러 ($error)"
            }
            Log.w(TAG, "onError: $errorMsg ($error)")

            // For recoverable silence/timeout errors, trigger safe restart
            if (isListeningRequested) {
                scheduleRestart()
            } else {
                onErrorOccurred(errorMsg)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: ""
            if (recognizedText.isNotEmpty()) {
                Log.d(TAG, "onResults: $recognizedText")
                onFinalResult(recognizedText)
            }
            if (isListeningRequested) {
                scheduleRestart()
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

    private fun createRecognizer(): SpeechRecognizer {
        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(recognitionListener)
        speechRecognizer = recognizer
        return recognizer
    }

    fun startListening() {
        handler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onErrorOccurred("이 기기에서 음성 인식을 지원하지 않습니다.")
                return@post
            }

            isListeningRequested = true
            val recognizer = speechRecognizer ?: createRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            try {
                recognizer.startListening(intent)
                Log.d(TAG, "Started listening")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                scheduleRestart()
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
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognition", e)
            }
        }
    }

    private fun scheduleRestart() {
        if (!isListeningRequested) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isListeningRequested) {
                try {
                    val recognizer = speechRecognizer ?: createRecognizer()
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                    recognizer.cancel()
                    recognizer.startListening(intent)
                    Log.d(TAG, "Restarted listening")
                } catch (e: Exception) {
                    Log.w(TAG, "Restart failed, re-creating recognizer", e)
                    createRecognizer()
                    startListening()
                }
            }
        }, RESTART_DELAY_MS)
    }

    fun destroy() {
        stopListening()
        handler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer", e)
            }
        }
    }
}
