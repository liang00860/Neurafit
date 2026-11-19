package com.neurafit.app.realtime

data class RealtimeConnectionState(
    val roomId: String,
    val signalingUrl: String,
    val enabled: Boolean = false,
    val signalingState: String = "offline",
    val peerState: String = "new",
    val dataChannelState: String = "closed",
    val errorMessage: String? = null,
)

interface RealtimeVideoFrameSink {
    fun onCameraFrame(
        bitmap: android.graphics.Bitmap,
        rotationDegrees: Int,
        mirrorHorizontally: Boolean,
        timestampNs: Long,
    )
}
