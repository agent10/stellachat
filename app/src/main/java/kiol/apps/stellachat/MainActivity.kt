package kiol.apps.stellachat

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kiol.apps.stellachat.databinding.ActivityMainBinding
import kiol.apps.stellachat.helpers.BasePeerConnectionObserver
import kiol.apps.stellachat.helpers.BaseSdpObserver
import kiol.apps.stellachat.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.webrtc.*
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isServer = true
    private var tcpChannel: TcpChannel? = null
    private var peerConnection: PeerConnection? = null

    private val eglBase = EglBase.create()

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

    private lateinit var communicator: Communicator

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        communicator = Communicator(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eglContext = eglBase.eglBaseContext
        with(binding.remoteRenderer) {
            init(eglContext, null)
            setZOrderMediaOverlay(true)
        }

        with(binding.localRenderer) {
            init(eglContext, null)
            setZOrderMediaOverlay(true)
        }

        binding.ipPort.setText("0.0.0.0:8888")
        binding.socketTypeGroup.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId == R.id.serverCheckId) {
                isServer = true
                binding.ipPort.setText("0.0.0.0:8888")
            } else {
                isServer = false
                binding.ipPort.setText("192.168.0.103:8888")
            }
        }

        binding.connectBtn.setOnClickListener {
            val (ip, port) = binding.ipPort.text.split(":")

            createTcpChannel(
                InetAddress.getByName(ip),
                port.toInt(),
                isServer
            )
        }

        communicator.callListener = { device, accept ->
            if (!accept) {
                createTcpChannel(InetAddress.getByName("0.0.0.0"), 8888, true)
            } else {
                createTcpChannel(device.inetAddress, 8888, false)
            }
            communicator.sendAcceptCall()
        }

        communicator.devices.onEach {
            Log.d("Communicator", "list = $it")
        }.launchIn(lifecycleScope)
    }

    private fun createTcpChannel(inetAddress: InetAddress, port: Int, isServer: Boolean) {
        tcpChannel = TcpChannel(
            inetAddress,
            port,
            isServer
        ).apply {
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

    override fun onStop() {
        super.onStop()
        communicator.close()
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
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext, true /* enableIntelVp8Encoder */, true
                )
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
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

        peerConnection?.addTrack(createCameraVideoTrack(peerConnectionFactory), listOf("ARDAMS"))

        val remoteVideoTrack = peerConnection?.transceivers?.firstOrNull()?.receiver?.track() as? VideoTrack
        remoteVideoTrack?.setEnabled(true)
        remoteVideoTrack?.addSink(binding.remoteRenderer)

        if (isServer) {
            peerConnection?.createOffer(sdbObserver, sdpMediaConstraints)
        }
        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE)
    }

    private fun createCameraVideoTrack(
        peerConnectionFactory: PeerConnectionFactory
    ):
            VideoTrack {
        val camNumerator = Camera2Enumerator(this)
        with(camNumerator) {
            val capturer = camNumerator.createCapturer(deviceNames.firstOrNull {
                isFrontFacing(it)
            }, null)
            val videoSource = peerConnectionFactory.createVideoSource(false)
            capturer.initialize(
                SurfaceTextureHelper.create("CapturerThread", eglBase.eglBaseContext),
                this@MainActivity, videoSource.capturerObserver
            )
            capturer.startCapture(800, 800, 30)

            val videoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
            videoTrack.setEnabled(true)
            videoTrack.addSink(binding.localRenderer)

            return videoTrack
        }
    }
}
