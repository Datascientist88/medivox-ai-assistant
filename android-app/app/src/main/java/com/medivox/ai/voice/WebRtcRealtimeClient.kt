package com.medivox.ai.voice

import android.content.Context
import android.media.AudioManager
import com.medivox.ai.data.RealtimeTokenProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebRtcRealtimeClient(
    context: Context,
    private val tokenProvider: RealtimeTokenProvider,
    private val listener: Listener,
) : Closeable {

    interface Listener {
        fun onPhaseChanged(phase: VoicePhase)
        fun onTranscript(speaker: String, text: String, isFinal: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val httpClient = OkHttpClient.Builder()
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    private var audioDeviceModule: AudioDeviceModule? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var microphoneTrack: AudioTrack? = null
    private var iceGatheringComplete: CompletableDeferred<Unit>? = null
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneState: Boolean? = null

    suspend fun connect() = withContext(Dispatchers.IO) {
        check(peerConnection == null) { "A MediVox voice session is already active." }
        listener.onPhaseChanged(VoicePhase.CONNECTING)

        try {
            val clientSecret = tokenProvider.createClientSecret()
            initializeWebRtc()
            configureAudioRoute()

            val pc = createPeerConnection()
            peerConnection = pc

            val source = peerConnectionFactory!!.createAudioSource(MediaConstraints())
            audioSource = source
            val localTrack = peerConnectionFactory!!.createAudioTrack("medivox-microphone", source)
            microphoneTrack = localTrack
            localTrack.setEnabled(true)

            checkNotNull(pc.addTrack(localTrack, listOf("medivox-audio"))) {
                "Could not add the microphone media track."
            }

            val channel = pc.createDataChannel(
                "oai-events",
                DataChannel.Init().apply { ordered = true }
            ) ?: throw IOException("Could not create the Realtime event data channel.")
            dataChannel = channel
            registerDataChannelObserver(channel)

            val offer = createOffer(pc)
            setLocalDescription(pc, offer)
            awaitIceGathering(pc)

            val localSdp = pc.localDescription?.description
                ?: throw IOException("WebRTC did not produce a local SDP offer.")
            val remoteSdp = exchangeSdp(clientSecret, localSdp)
            setRemoteDescription(pc, SessionDescription(SessionDescription.Type.ANSWER, remoteSdp))

            listener.onPhaseChanged(VoicePhase.LISTENING)
        } catch (error: Throwable) {
            close()
            listener.onError(error.message ?: "Unable to establish a Realtime voice session.")
            throw error
        }
    }

    private fun initializeWebRtc() {
        if (peerConnectionFactory != null) return

        if (factoryInitialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
        }

        val adm = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        audioDeviceModule = adm
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()
    }

    @Suppress("DEPRECATION")
    private fun configureAudioRoute() {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousAudioMode = manager.mode
        previousSpeakerphoneState = manager.isSpeakerphoneOn
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isSpeakerphoneOn = true
    }

    private fun createPeerConnection(): PeerConnection {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        iceGatheringComplete = CompletableDeferred()

        return checkNotNull(
            peerConnectionFactory?.createPeerConnection(
                configuration,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                        if (newState == PeerConnection.IceConnectionState.FAILED) {
                            listener.onError("The WebRTC network connection failed.")
                        }
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                        if (newState == PeerConnection.IceGatheringState.COMPLETE) {
                            iceGatheringComplete?.complete(Unit)
                        }
                    }

                    override fun onIceCandidate(candidate: IceCandidate) = Unit
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                    override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onDataChannel(channel: DataChannel) = Unit
                    override fun onRenegotiationNeeded() = Unit

                    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
                        (receiver.track() as? AudioTrack)?.setEnabled(true)
                    }

                    override fun onTrack(transceiver: RtpTransceiver) {
                        (transceiver.receiver.track() as? AudioTrack)?.setEnabled(true)
                    }
                }
            )
        ) { "Could not create a WebRTC PeerConnection." }
    }

    private fun registerDataChannelObserver(channel: DataChannel) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) {
                    listener.onPhaseChanged(VoicePhase.LISTENING)
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                handleServerEvent(String(bytes, Charsets.UTF_8))
            }
        })
    }

    private fun handleServerEvent(raw: String) {
        runCatching {
            val event = JSONObject(raw)
            when (event.optString("type")) {
                "input_audio_buffer.speech_started" -> listener.onPhaseChanged(VoicePhase.USER_SPEAKING)
                "input_audio_buffer.speech_stopped", "response.created" -> listener.onPhaseChanged(VoicePhase.THINKING)
                "response.output_audio.started", "response.audio.started", "output_audio_buffer.started" -> listener.onPhaseChanged(VoicePhase.ASSISTANT_SPEAKING)
                "output_audio_buffer.stopped", "response.done" -> listener.onPhaseChanged(VoicePhase.LISTENING)
                "response.audio_transcript.delta", "response.output_audio_transcript.delta", "response.text.delta" -> {
                    val delta = event.optString("delta")
                    if (delta.isNotBlank()) {
                        listener.onPhaseChanged(VoicePhase.ASSISTANT_SPEAKING)
                        listener.onTranscript("MediVox", delta, false)
                    }
                }
                "response.audio_transcript.done", "response.output_audio_transcript.done", "response.text.done" -> {
                    val text = event.optString("transcript", event.optString("text", ""))
                    if (text.isNotBlank()) listener.onTranscript("MediVox", text, true)
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val text = event.optString("transcript")
                    if (text.isNotBlank()) listener.onTranscript("You", text, true)
                }
                "error" -> {
                    val message = event.optJSONObject("error")?.optString("message")
                    listener.onError(message?.takeIf { it.isNotBlank() } ?: "Realtime service error.")
                }
            }
        }.onFailure {
            listener.onError("Could not process a Realtime event.")
        }
    }

    private suspend fun createOffer(pc: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { cont ->
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (cont.isActive) cont.resume(description)
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String) {
                    if (cont.isActive) cont.resumeWithException(IOException("SDP offer failed: $error"))
                }
                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
        }

    private suspend fun setLocalDescription(pc: PeerConnection, description: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setLocalDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onCreateFailure(error: String) = Unit
                override fun onSetFailure(error: String) {
                    if (cont.isActive) cont.resumeWithException(IOException("Could not set local SDP: $error"))
                }
            }, description)
        }

    private suspend fun awaitIceGathering(pc: PeerConnection) {
        if (pc.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) return
        withTimeout(20_000) {
            iceGatheringComplete?.await() ?: throw IOException("ICE gathering was not initialized.")
        }
    }

    private suspend fun setRemoteDescription(pc: PeerConnection, description: SessionDescription) =
        suspendCancellableCoroutine<Unit> { cont ->
            pc.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onCreateFailure(error: String) = Unit
                override fun onSetFailure(error: String) {
                    if (cont.isActive) cont.resumeWithException(IOException("Could not apply OpenAI SDP answer: $error"))
                }
            }, description)
        }

    private suspend fun exchangeSdp(clientSecret: String, offerSdp: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/realtime/calls")
                .header("Authorization", "Bearer $clientSecret")
                .header("Content-Type", "application/sdp")
                .post(offerSdp.toRequestBody("application/sdp".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException(
                        "OpenAI WebRTC handshake failed (HTTP ${response.code}): ${body.take(400)}"
                    )
                }
                body.takeIf { it.isNotBlank() } ?: throw IOException("OpenAI returned an empty SDP answer.")
            }
        }

    override fun close() {
        runCatching { dataChannel?.unregisterObserver() }
        runCatching { dataChannel?.close() }
        runCatching { dataChannel?.dispose() }
        dataChannel = null

        runCatching { microphoneTrack?.setEnabled(false) }
        runCatching { microphoneTrack?.dispose() }
        microphoneTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null

        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null

        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null
        runCatching { audioDeviceModule?.release() }
        audioDeviceModule = null

        restoreAudioRoute()
        iceGatheringComplete = null
    }

    @Suppress("DEPRECATION")
    private fun restoreAudioRoute() {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        previousAudioMode?.let { manager.mode = it }
        previousSpeakerphoneState?.let { manager.isSpeakerphoneOn = it }
        previousAudioMode = null
        previousSpeakerphoneState = null
    }

    companion object {
        private val factoryInitialized = AtomicBoolean(false)
    }
}
