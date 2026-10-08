package com.smartatm.pretest.mockcall

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 실제 전화처럼 발신 측에는 통화 연결음(링백톤), 수신 측에는 벨소리/진동을 재생한다.
 * 통화 상태가 바뀔 때마다 [onCallStatusChanged]로 알려주면 알맞은 소리로 전환/정지한다.
 */
class CallTonePlayer(private val context: Context) {

    companion object {
        private const val TAG = "CallTonePlayer"
        private const val SAMPLE_RATE = 16000
        private const val RINGBACK_ON_MS = 1000   // 한국 표준 연결음: 1초 울림
        private const val RINGBACK_OFF_MS = 2000  //                  2초 쉼
        private const val FADE_MS = 10            // 시작/끝 딸깍 소리 방지
        private val VIBRATION_PATTERN = longArrayOf(0, 1000, 1000) // 1초 진동 / 1초 쉼 반복
    }

    private var ringbackTrack: AudioTrack? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    @Synchronized
    fun onCallStatusChanged(status: CallStatus) {
        when (status) {
            CallStatus.CALLING -> {
                stopRingtone()
                startRingback()
            }
            CallStatus.INCOMING_CALL -> {
                stopRingback()
                startRingtone()
            }
            else -> stopAll()
        }
    }

    @Synchronized
    fun stopAll() {
        stopRingback()
        stopRingtone()
    }

    // --- 발신 측 통화 연결음 (뚜르르~) ---
    private fun startRingback() {
        if (ringbackTrack != null) return
        try {
            val samples = buildRingbackPcm()
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    // 통화 음성과 같은 경로(스피커, 미디어 볼륨)로 재생
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.setLoopPoints(0, samples.size, -1) // 무한 반복
            track.play()
            ringbackTrack = track
            Log.d(TAG, "Ringback tone started")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start ringback tone", e)
            stopRingback()
        }
    }

    /** 440Hz + 480Hz 합성음 1초 + 무음 2초 한 주기 (16kHz mono PCM) */
    private fun buildRingbackPcm(): ShortArray {
        val onSamples = SAMPLE_RATE * RINGBACK_ON_MS / 1000
        val total = SAMPLE_RATE * (RINGBACK_ON_MS + RINGBACK_OFF_MS) / 1000
        val fadeSamples = SAMPLE_RATE * FADE_MS / 1000
        val samples = ShortArray(total)
        for (i in 0 until onSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val fade = min(1.0, min(i, onSamples - 1 - i).toDouble() / fadeSamples)
            val value = (sin(2 * PI * 440 * t) + sin(2 * PI * 480 * t)) * 0.25 * fade
            samples[i] = (value * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun stopRingback() {
        ringbackTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
            Log.d(TAG, "Ringback tone stopped")
        }
        ringbackTrack = null
    }

    // --- 수신 측 벨소리 + 진동 ---
    private fun startRingtone() {
        if (ringtone == null) {
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    play()
                }
                Log.d(TAG, "Ringtone started (${ringtone != null})")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play ringtone", e)
            }
        }

        if (vibrator == null) {
            try {
                @Suppress("DEPRECATION")
                vibrator = (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.takeIf { it.hasVibrator() }
                vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to vibrate", e)
                vibrator = null
            }
        }
    }

    private fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (_: Exception) {
        }
        ringtone = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }
}
