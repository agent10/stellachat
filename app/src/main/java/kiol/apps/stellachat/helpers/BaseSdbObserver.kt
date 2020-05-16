package kiol.apps.stellachat.helpers

import android.util.Log
import kiol.apps.stellachat.WRTC_LOG_TAG
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class BaseSdpObserver : SdpObserver {
    override fun onSetFailure(p0: String?) {
        Log.d(WRTC_LOG_TAG, "SdpObserver onSetFailure $p0")
    }

    override fun onSetSuccess() {
        Log.d(WRTC_LOG_TAG, "SdpObserver onSetSuccess")
    }

    override fun onCreateSuccess(p0: SessionDescription?) {
        Log.d(WRTC_LOG_TAG, "SdpObserver onCreateSuccess $p0")
    }

    override fun onCreateFailure(p0: String?) {
        Log.d(WRTC_LOG_TAG, "SdpObserver onCreateFailure $p0")
    }
}