package com.smartatm.pretest.mockcall

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class MockAudioStreamer(private val context: Context) {

    companion object {
        private const val TAG = "MockAudioStreamer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_CHUNK_SIZE = 1280 // 40ms @ 16kHz 16-bit mono
    }

    private val isRunning = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)

    private var recordThread: Thread? = null
    private var playbackThread: Thread? = null

    private var sendSocket: DatagramSocket? = null
    private var receiveSocket: DatagramSocket? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private val isRecordingPaused = AtomicBoolean(false)

    // 사용자가 원하는 스피커폰 상태 (STT 일시정지 중에는 통신 라우팅을 해제했다가 재개 시 복원)
    @Volatile
    private var speakerRequested = true

    @SuppressLint("MissingPermission")
    fun startStreaming(remoteIp: String) {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Audio streamer is already running")
            return
        }

        try {
            // 1. AudioTrack (수신 및 재생) 설정 - 라우드스피커로 선명하게 출력되도록 USAGE_MEDIA 사용
            val minPlayBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val playBufSize = maxOf(minPlayBuf, BUFFER_CHUNK_SIZE * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .setEncoding(ENCODING)
                        .build()
                )
                .setBufferSizeInBytes(playBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(1.0f)
            audioTrack?.play()

            // 2. AudioRecord (송신) 설정
            // 이전 통화에서 SpeechRecognizer용으로 일시정지된 상태가 남아 있으면 송신 스레드가 영원히 대기하므로 초기화
            isRecordingPaused.set(false)
            createAndStartAudioRecord()

            // 3. 소켓 초기화 (reuseAddress를 bind 전에 반드시 설정)
            receiveSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress(NetworkUtils.PORT_AUDIO))
                soTimeout = 2000
            }
            sendSocket = DatagramSocket()

            val remoteAddress = InetAddress.getByName(remoteIp)

            // 4. 송신 스레드
            recordThread = Thread({
                val buffer = ByteArray(BUFFER_CHUNK_SIZE)
                while (isRunning.get()) {
                    try {
                        val record = audioRecord
                        if (record == null || isRecordingPaused.get() || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            Thread.sleep(50)
                            continue
                        }

                        val bytesRead = record.read(buffer, 0, buffer.size)
                        if (bytesRead > 0 && !isMuted.get()) {
                            val packet = DatagramPacket(
                                buffer,
                                bytesRead,
                                remoteAddress,
                                NetworkUtils.PORT_AUDIO
                            )
                            sendSocket?.send(packet)
                        } else if (bytesRead < 0) {
                            Log.w(TAG, "AudioRecord.read error: $bytesRead")
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error in record thread: ${e.message}")
                        }
                    }
                }
            }, "MockAudio-Record").apply { start() }

            // 5. 수신 및 재생 스레드
            playbackThread = Thread({
                val receiveBuf = ByteArray(BUFFER_CHUNK_SIZE * 2)
                val packet = DatagramPacket(receiveBuf, receiveBuf.size)

                while (isRunning.get()) {
                    try {
                        // 중요: DatagramPacket 재사용 시 직전 수신 크기로 줄어든 length를 원래 버퍼 크기로 리셋
                        packet.length = receiveBuf.size
                        receiveSocket?.receive(packet)
                        if (packet.length > 0 && isRunning.get()) {
                            audioTrack?.write(packet.data, packet.offset, packet.length)
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // 정상적인 소켓 타임아웃, 루프 계속
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Error in playback thread: ${e.message}")
                        }
                    }
                }
            }, "MockAudio-Playback").apply { start() }

            speakerRequested = true
            applyCommunicationRoute(true)
            Log.d(TAG, "Audio streaming started to $remoteIp:${NetworkUtils.PORT_AUDIO}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio streaming", e)
            stopStreaming()
        }
    }

    fun stopStreaming() {
        if (!isRunning.getAndSet(false)) return

        try {
            recordThread?.interrupt()
            playbackThread?.interrupt()

            recordThread = null
            playbackThread = null

            sendSocket?.close()
            receiveSocket?.close()
            sendSocket = null
            receiveSocket = null

            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                // Ignore
            }
            audioRecord = null
            isRecordingPaused.set(false)

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                // Ignore
            }
            audioTrack = null

            applyCommunicationRoute(false)
            Log.d(TAG, "Audio streaming stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping streamer", e)
        }
    }

    @Synchronized
    private fun createAndStartAudioRecord() {
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val recBufSize = maxOf(minRecBuf, BUFFER_CHUNK_SIZE * 4)

        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                recBufSize
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create AudioRecord with VOICE_COMMUNICATION source, falling back to MIC", e)
            try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING,
                    recBufSize
                )
            } catch (e2: Exception) {
                AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    CHANNEL_IN,
                    ENCODING,
                    recBufSize
                )
            }
        }

        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord?.startRecording()
            Log.d(TAG, "AudioRecord created and started successfully (source=${audioRecord?.audioSource})")
        } else {
            Log.e(TAG, "AudioRecord initialization failed (state=${audioRecord?.state})")
        }
    }

    fun pauseRecording() {
        if (!isRecordingPaused.getAndSet(true)) {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                Log.d(TAG, "AudioRecord completely released for SpeechRecognizer exclusive access")
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioRecord", e)
            }
            // 통신 모드(MODE_IN_COMMUNICATION)/통신 장치가 이 앱에 잡혀 있으면 Android 동시 녹음 정책상
            // 다른 프로세스에서 도는 SpeechRecognizer(Google 앱)의 마이크 입력이 무음 처리될 수 있으므로 해제
            applyCommunicationRoute(false)
        }
    }

    fun resumeRecording() {
        if (isRecordingPaused.getAndSet(false)) {
            try {
                if (isRunning.get()) {
                    applyCommunicationRoute(speakerRequested)
                    createAndStartAudioRecord()
                    Log.d(TAG, "AudioRecord recreated and resumed after SpeechRecognizer")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resuming AudioRecord", e)
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun setSpeakerphoneOn(speakerOn: Boolean) {
        speakerRequested = speakerOn
        // STT가 마이크를 쓰는 동안에는 라우팅을 건드리지 않고, 재개 시 반영
        if (!isRecordingPaused.get()) {
            applyCommunicationRoute(speakerOn)
        }
    }

    private fun applyCommunicationRoute(speakerOn: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.mode = if (speakerOn) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle speakerphone", e)
        }
    }
}
