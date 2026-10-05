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

    @SuppressLint("MissingPermission")
    fun startStreaming(remoteIp: String) {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "Audio streamer is already running")
            return
        }

        try {
            // 1. AudioTrack (수신 및 재생) 설정
            val minPlayBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val playBufSize = maxOf(minPlayBuf, BUFFER_CHUNK_SIZE * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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

            audioTrack?.play()

            // 2. AudioRecord (송신) 설정
            val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            val recBufSize = maxOf(minRecBuf, BUFFER_CHUNK_SIZE * 4)

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                recBufSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()
            } else {
                Log.e(TAG, "AudioRecord initialization failed")
            }

            // 3. 소켓 초기화
            receiveSocket = DatagramSocket(NetworkUtils.PORT_AUDIO).apply {
                reuseAddress = true
                soTimeout = 2000
            }
            sendSocket = DatagramSocket()

            val remoteAddress = InetAddress.getByName(remoteIp)

            // 4. 송신 스레드
            recordThread = Thread({
                val buffer = ByteArray(BUFFER_CHUNK_SIZE)
                while (isRunning.get()) {
                    try {
                        val record = audioRecord ?: break
                        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            Thread.sleep(10)
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

            setSpeakerphoneOn(true)
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

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                // Ignore
            }
            audioTrack = null

            Log.d(TAG, "Audio streaming stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping streamer", e)
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun setSpeakerphoneOn(speakerOn: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = if (speakerOn) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = speakerOn
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle speakerphone", e)
        }
    }
}
