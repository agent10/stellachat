package kiol.apps.stellachat

import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kiol.apps.stellachat.databinding.ActivityMainBinding
import kiol.apps.stellachat.helpers.BasePeerConnectionObserver
import kiol.apps.stellachat.helpers.BaseSdpObserver
import kiol.apps.stellachat.helpers.getNV21
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.*
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isServer = true
    private var tcpChannel: TcpChannel? = null
    private var peerConnection: PeerConnection? = null

    private val sdpMediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
    }


    private val sdbObserver = object : BaseSdpObserver() {
        override fun onCreateSuccess(p0: SessionDescription?) {
            super.onCreateSuccess(p0)

            val observer = this
            peerConnection?.setLocalDescription(observer, p0)

            if (isServer) {
                val json = JSONObject()
                json.put("sdp", p0?.description.orEmpty())
                json.put("type", "offer")
                tcpChannel?.send(json.toString())
            } else {
                val json = JSONObject()
                json.put("sdp", p0?.description.orEmpty())
                json.put("type", "answer")
                tcpChannel?.send(json.toString())
            }
        }
    }

    private val peerConnectionObserver = object : BasePeerConnectionObserver() {
        override fun onIceCandidate(p0: IceCandidate?) {
            super.onIceCandidate(p0)

            with(p0!!) {
                val json = JSONObject()
                json.put("type", "candidate")
                json.put("label", sdpMLineIndex)
                json.put("id", sdpMid)
                json.put("candidate", sdp)

                tcpChannel?.send(json.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding.remoteRenderer) {
            init(EglBase.create().eglBaseContext, null)
            setZOrderMediaOverlay(true)
        }

        binding.ipPort.setText("0.0.0.0:8888")
        binding.socketTypeGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.serverCheckId) {
                isServer = true
                binding.tempLocalRenderer.setImageResource(R.drawable.ava2)
                binding.ipPort.setText("0.0.0.0:8888")
            } else {
                isServer = false
                binding.tempLocalRenderer.setImageResource(R.drawable.ava)
                binding.ipPort.setText("192.168.0.103:8888")
            }
        }

        binding.tempLocalRenderer.setImageResource(R.drawable.ava2)

        binding.connectBtn.setOnClickListener {
            val (ip, port) = binding.ipPort.text.split(":")

            tcpChannel = TcpChannel(InetAddress.getByName(ip), port.toInt(), isServer).apply {
                stream().flowOn(Dispatchers.IO).onEach {
                    Log.d("wrtc SdpObserver", "tcp received $it")
                    if (it is TcpResult.Data) {
                        val json = JSONObject(it.message)
                        val type = json.optString("type")
                        if (type.equals("candidate")) {
                            val icecand = IceCandidate(json.getString("id"), json.getInt("label"), json.getString("candidate"))
                            peerConnection?.addIceCandidate(icecand)
                        } else if (type.equals("answer")) {
                            val remotesdp =
                                SessionDescription(SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"))
                            peerConnection?.setRemoteDescription(sdbObserver, remotesdp)
                        } else if (type.equals("offer")) {
                            val remotesdp =
                                SessionDescription(SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"))
                            peerConnection?.setRemoteDescription(sdbObserver, remotesdp)
                            peerConnection?.createAnswer(sdbObserver, sdpMediaConstraints)
                        }
                    } else if (it is TcpResult.Connected) {
                        createPeerConnection()
                    }
                }.launchIn(lifecycleScope)
            }
        }
    }

    private fun createPeerConnection() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .createInitializationOptions()
        )

        val peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
            })
            .setVideoEncoderFactory(SoftwareVideoEncoderFactory())
            .setVideoDecoderFactory(SoftwareVideoDecoderFactory())
            .createPeerConnectionFactory()


        val rtcConfig =
            PeerConnection.RTCConfiguration(listOf())
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        //        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        // Enable DTLS for normal calls and disable for loopback calls.
        rtcConfig.enableDtlsSrtp = false
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, peerConnectionObserver)

        val videoSource = peerConnectionFactory.createVideoSource(false)
        val captureObserver = videoSource.capturerObserver
        captureObserver.onCapturerStarted(true)

        GlobalScope.launch(Dispatchers.IO) {
            val b = BitmapFactory.decodeResource(
                this@MainActivity.resources, if (isServer) R.drawable.ava2 else R
                    .drawable.ava
            )
            val arr = getNV21(b)
            val buff = NV21Buffer(arr, b.width, b.height) {}
            while (true) {
                val vf = VideoFrame(
                    buff, 0,
                    TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime())
                )
                captureObserver.onFrameCaptured(vf)
                delay(33)
            }
        }

        val videoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
        videoTrack.setEnabled(true)

        peerConnection?.addTrack(videoTrack, listOf("ARDAMS"))

        val remoteVideoTrack = peerConnection?.transceivers?.firstOrNull()?.receiver?.track() as? VideoTrack
        remoteVideoTrack?.setEnabled(true)
        remoteVideoTrack?.addSink(binding.remoteRenderer)

        if (isServer) {
            peerConnection?.createOffer(sdbObserver, sdpMediaConstraints)
        }
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
    }
}
