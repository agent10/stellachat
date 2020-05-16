package kiol.apps.stellachat.helpers

import android.util.Log
import kiol.apps.stellachat.WRTC_LOG_TAG
import org.json.JSONObject
import org.webrtc.*

abstract class BasePeerConnectionObserver : PeerConnection.Observer {
    override fun onIceCandidate(p0: IceCandidate?) {
        Log.d(WRTC_LOG_TAG, "onIceCandidate $p0")
    }

    override fun onDataChannel(p0: DataChannel?) {
        Log.d(WRTC_LOG_TAG, "onDataChannel $p0")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        Log.d("WRTC_LOG_TAG_LOG_TAG", "onIceConnectionReceivingChange $p0")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Log.d(WRTC_LOG_TAG, "onIceConnectionChange $p0")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Log.d(WRTC_LOG_TAG, "onIceGatheringChange $p0")
    }

    override fun onAddStream(p0: MediaStream?) {
        Log.d(WRTC_LOG_TAG, "onAddStream $p0")
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Log.d(WRTC_LOG_TAG, "onSignalingChange $p0")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        Log.d(WRTC_LOG_TAG, "onIceCandidatesRemoved $p0")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        Log.d(WRTC_LOG_TAG, "onRemoveStream $p0")
    }

    override fun onRenegotiationNeeded() {
        Log.d(WRTC_LOG_TAG, "onRenegotiationNeeded")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Log.d(WRTC_LOG_TAG, "onAddTrack $p0")
    }

}