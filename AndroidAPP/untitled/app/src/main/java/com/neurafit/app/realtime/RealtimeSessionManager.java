package com.neurafit.app.realtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import androidx.compose.runtime.internal.StabilityInferred;
import com.neurafit.app.BuildConfig;
import com.neurafit.app.food.FoodClassifier;
import com.neurafit.app.realtime.RealtimeSessionManager;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.Charsets;
import kotlin.text.Regex;
import kotlin.text.StringsKt;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/* compiled from: RealtimeSessionManager.kt */
@StabilityInferred(parameters = BuildConfig.RTC_FORCE_RELAY)
@Metadata(mv = {2, FoodClassifier.RGB_CHANNELS, BuildConfig.RTC_FORCE_RELAY}, k = 1, xi = 48, d1 = {"��Â\u0001\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0010 \n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0010!\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n��\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n��\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0006\n��\n\u0002\u0010��\n\u0002\b\u001b\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0007\u0018�� h2\u00020\u0001:\u0002ghB1\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\u0006\u0010\u0007\u001a\u00020\u0005\u0012\b\b\u0002\u0010\b\u001a\u00020\t¢\u0006\u0004\b\n\u0010\u000bJ*\u00104\u001a\u0002052\u0006\u00106\u001a\u00020\u00052\u0006\u00107\u001a\u00020\u00052\u0006\u00108\u001a\u00020\u00052\b\b\u0002\u00109\u001a\u00020:H\u0002J8\u0010;\u001a\u0002052\u0006\u0010<\u001a\u00020%2\u0006\u0010=\u001a\u00020>2\u0006\u0010?\u001a\u00020\u001f2\u0006\u0010@\u001a\u00020A2\u0006\u0010B\u001a\u00020C2\u0006\u0010D\u001a\u00020\tH\u0002J\u0019\u0010E\u001a\u0004\u0018\u00010F2\b\u0010G\u001a\u0004\u0018\u00010HH\u0002¢\u0006\u0002\u0010IJ\u0010\u0010J\u001a\u0002052\u0006\u0010K\u001a\u00020'H\u0002J\u0006\u0010L\u001a\u000205J\u0006\u0010M\u001a\u000205J\u0006\u0010N\u001a\u000205J\u000e\u0010O\u001a\u0002052\u0006\u0010P\u001a\u00020\u0005J(\u0010Q\u001a\u0002052\u0006\u0010R\u001a\u00020A2\u0006\u0010B\u001a\u00020C2\u0006\u0010D\u001a\u00020\t2\u0006\u0010S\u001a\u00020\u001fH\u0016J\b\u0010T\u001a\u000205H\u0002J\b\u0010U\u001a\u000205H\u0002J\b\u0010V\u001a\u000205H\u0002J\u0010\u0010W\u001a\u0002052\u0006\u0010X\u001a\u00020\u001dH\u0002J\b\u0010Y\u001a\u000205H\u0002J\u0010\u0010Z\u001a\u0002052\u0006\u0010[\u001a\u00020\u0005H\u0002J\u0010\u0010\\\u001a\u0002052\u0006\u0010]\u001a\u00020:H\u0002J\u0010\u0010^\u001a\u0002052\u0006\u0010_\u001a\u00020:H\u0002J\b\u0010`\u001a\u000205H\u0002J\u0018\u0010a\u001a\u0002052\u0006\u0010b\u001a\u00020\u00052\u0006\u0010c\u001a\u00020dH\u0002J\u0010\u0010e\u001a\u0002052\u0006\u0010f\u001a\u00020:H\u0002R\u000e\u0010\b\u001a\u00020\tX\u0082\u0004¢\u0006\u0002\n��R\u0016\u0010\f\u001a\n \r*\u0004\u0018\u00010\u00030\u0003X\u0082\u0004¢\u0006\u0002\n��R\u0016\u0010\u000e\u001a\n \r*\u0004\u0018\u00010\u000f0\u000fX\u0082\u0004¢\u0006\u0002\n��R\u000e\u0010\u0010\u001a\u00020\u0011X\u0082\u0004¢\u0006\u0002\n��R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u0004¢\u0006\u0002\n��R\u0010\u0010\u0014\u001a\u0004\u0018\u00010\u0005X\u0082\u0004¢\u0006\u0002\n��R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004¢\u0006\u0002\n��R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u0004¢\u0006\u0002\n��R\u0014\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001b0\u001aX\u0082\u0004¢\u0006\u0002\n��R\u0010\u0010\u001c\u001a\u0004\u0018\u00010\u001dX\u0082\u000e¢\u0006\u0002\n��R\u000e\u0010\u001e\u001a\u00020\u001fX\u0082\u000e¢\u0006\u0002\n��R\u000e\u0010 \u001a\u00020\u001fX\u0082\u000e¢\u0006\u0002\n��R\u000e\u0010!\u001a\u00020\u001fX\u0082\u000e¢\u0006\u0002\n��R\u000e\u0010\"\u001a\u00020\u001fX\u0082\u000e¢\u0006\u0002\n��R\u000e\u0010#\u001a\u00020\tX\u0082\u000e¢\u0006\u0002\n��R\u0010\u0010$\u001a\u0004\u0018\u00010%X\u0082\u000e¢\u0006\u0002\n��R\u0010\u0010&\u001a\u0004\u0018\u00010'X\u0082\u000e¢\u0006\u0002\n��R\u0014\u0010(\u001a\b\u0012\u0004\u0012\u00020*0)X\u0082\u0004¢\u0006\u0002\n��R\u0010\u0010+\u001a\u0004\u0018\u00010,X\u0082\u000e¢\u0006\u0002\n��R\u0014\u0010-\u001a\b\u0012\u0004\u0012\u00020/0.X\u0082\u0004¢\u0006\u0002\n��R\u0017\u00100\u001a\b\u0012\u0004\u0012\u00020/01¢\u0006\b\n��\u001a\u0004\b2\u00103¨\u0006i"}, d2 = {"Lcom/neurafit/app/realtime/RealtimeSessionManager;", "Lcom/neurafit/app/realtime/RealtimeVideoFrameSink;", "context", "Landroid/content/Context;", "roomId", "", "signalingUrl", "iceServersJson", "forceRelay", "", "<init>", "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V", "appContext", "kotlin.jvm.PlatformType", "eglBase", "Lorg/webrtc/EglBase;", "httpClient", "Lokhttp3/OkHttpClient;", "peerConnectionFactory", "Lorg/webrtc/PeerConnectionFactory;", "signalingHost", "videoSource", "Lorg/webrtc/VideoSource;", "videoTrack", "Lorg/webrtc/VideoTrack;", "iceServers", "", "Lorg/webrtc/PeerConnection$IceServer;", "dataChannel", "Lorg/webrtc/DataChannel;", "lastDebugVideoReportAtMs", "", "lastPoseFrameAtMs", "lastStatsReportAtMs", "lastVideoFrameAtMs", "offerInProgress", "peerConnection", "Lorg/webrtc/PeerConnection;", "videoSender", "Lorg/webrtc/RtpSender;", "pendingRemoteIceCandidates", "", "Lorg/webrtc/IceCandidate;", "webSocket", "Lokhttp3/WebSocket;", "_state", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/neurafit/app/realtime/RealtimeConnectionState;", "state", "Lkotlinx/coroutines/flow/StateFlow;", "getState", "()Lkotlinx/coroutines/flow/StateFlow;", "reportDebugEvent", "", "hypothesisId", "location", "msg", "data", "Lorg/json/JSONObject;", "sampleSenderStats", "peer", "frame", "Lorg/webrtc/VideoFrame;", "conversionDurationMs", "sourceBitmap", "Landroid/graphics/Bitmap;", "rotationDegrees", "", "mirrorHorizontally", "readStatNumber", "", "value", "", "(Ljava/lang/Object;)Ljava/lang/Double;", "applyVideoSenderTuning", "sender", "connect", "disconnect", "close", "sendPoseFrame", "payload", "onCameraFrame", "bitmap", "timestampNs", "connectSignaling", "releasePeerConnection", "createPeerConnection", "observeDataChannel", "channel", "createOffer", "handleSignalMessage", "text", "setRemoteDescription", "sdp", "addIceCandidate", "candidate", "drainPendingRemoteIceCandidates", "sendSdp", "type", "description", "Lorg/webrtc/SessionDescription;", "sendSignal", "message", "SimpleSdpObserver", "Companion", "app"})
@SourceDebugExtension({"SMAP\nRealtimeSessionManager.kt\nKotlin\n*S Kotlin\n*F\n+ 1 RealtimeSessionManager.kt\ncom/neurafit/app/realtime/RealtimeSessionManager\n+ 2 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 3 StateFlow.kt\nkotlinx/coroutines/flow/StateFlowKt\n+ 4 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,948:1\n1915#2,2:949\n1915#2,2:987\n1915#2,2:989\n230#3,5:951\n230#3,5:956\n230#3,5:962\n230#3,5:967\n230#3,5:972\n230#3,5:977\n230#3,5:982\n1#4:961\n*S KotlinDebug\n*F\n+ 1 RealtimeSessionManager.kt\ncom/neurafit/app/realtime/RealtimeSessionManager\n*L\n246#1:949,2\n805#1:987,2\n143#1:989,2\n284#1:951,5\n303#1:956,5\n603#1:962,5\n695#1:967,5\n742#1:972,5\n748#1:977,5\n754#1:982,5\n*E\n"})
/* loaded from: classes.jar:com/neurafit/app/realtime/RealtimeSessionManager.class */
public final class RealtimeSessionManager implements RealtimeVideoFrameSink {
    private final boolean forceRelay;
    private final Context appContext;
    private final EglBase eglBase;

    @NotNull
    private final OkHttpClient httpClient;

    @NotNull
    private final PeerConnectionFactory peerConnectionFactory;

    @Nullable
    private final String signalingHost;

    @NotNull
    private final VideoSource videoSource;

    @NotNull
    private final VideoTrack videoTrack;

    @NotNull
    private final List<PeerConnection.IceServer> iceServers;

    @Nullable
    private DataChannel dataChannel;
    private long lastDebugVideoReportAtMs;
    private long lastPoseFrameAtMs;
    private long lastStatsReportAtMs;
    private long lastVideoFrameAtMs;
    private boolean offerInProgress;

    @Nullable
    private PeerConnection peerConnection;

    @Nullable
    private RtpSender videoSender;

    @NotNull
    private final List<IceCandidate> pendingRemoteIceCandidates;

    @Nullable
    private WebSocket webSocket;

    @NotNull
    private final MutableStateFlow<RealtimeConnectionState> _state;

    @NotNull
    private final StateFlow<RealtimeConnectionState> state;

    @Deprecated
    @NotNull
    public static final String DATA_CHANNEL_LABEL = "pose";

    @Deprecated
    @NotNull
    public static final String STREAM_ID = "motion-stream";

    @Deprecated
    @NotNull
    public static final String TAG = "RealtimeSessionManager";

    @Deprecated
    @NotNull
    public static final String VIDEO_TRACK_ID = "android-camera-video";

    @Deprecated
    public static final long POSE_FRAME_INTERVAL_MS = 66;

    @Deprecated
    public static final long VIDEO_FRAME_INTERVAL_MS = 40;

    @Deprecated
    public static final int VIDEO_FRAME_MAX_LONG_EDGE = 720;

    @Deprecated
    public static final int VIDEO_MAX_BITRATE_BPS = 3500000;

    @Deprecated
    public static final int VIDEO_MAX_FRAMERATE = 24;

    @Deprecated
    public static final int VIDEO_MIN_BITRATE_BPS = 800000;
    private static boolean factoryInitialized;
    private static final int CANDIDATE_ADDRESS_INDEX = 4;

    @NotNull
    private static final Companion Companion = new Companion(null);
    public static final int $stable = 8;

    @NotNull
    private static final String DEBUG_SERVER_URL = "";

    @NotNull
    private static final String DEBUG_SESSION_ID = "webrtc-quality-latency";

    @NotNull
    private static final MediaType JSON_MEDIA_TYPE = MediaType.Companion.get("application/json; charset=utf-8");

    public RealtimeSessionManager(@NotNull Context context, @NotNull String roomId, @NotNull String signalingUrl, @NotNull String iceServersJson, boolean forceRelay) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(roomId, "roomId");
        Intrinsics.checkNotNullParameter(signalingUrl, "signalingUrl");
        Intrinsics.checkNotNullParameter(iceServersJson, "iceServersJson");
        this.forceRelay = forceRelay;
        this.appContext = context.getApplicationContext();
        this.eglBase = EglBase.create();
        this.httpClient = new OkHttpClient.Builder().pingInterval(15L, TimeUnit.SECONDS).build();
        this.signalingHost = Companion.parseSignalingHost(signalingUrl);
        this.iceServers = Companion.parseIceServers(iceServersJson);
        this.pendingRemoteIceCandidates = new ArrayList();
        this._state = StateFlowKt.MutableStateFlow(new RealtimeConnectionState(roomId, signalingUrl, false, null, null, null, null, 124, null));
        this.state = FlowKt.asStateFlow(this._state);
        Companion companion = Companion;
        Context context2 = this.appContext;
        Intrinsics.checkNotNullExpressionValue(context2, "appContext");
        companion.initializePeerConnectionFactory(context2);
        PeerConnectionFactory createPeerConnectionFactory = PeerConnectionFactory.builder().setVideoEncoderFactory(new DefaultVideoEncoderFactory(this.eglBase.getEglBaseContext(), true, true)).setVideoDecoderFactory(new DefaultVideoDecoderFactory(this.eglBase.getEglBaseContext())).createPeerConnectionFactory();
        Intrinsics.checkNotNullExpressionValue(createPeerConnectionFactory, "createPeerConnectionFactory(...)");
        this.peerConnectionFactory = createPeerConnectionFactory;
        VideoSource createVideoSource = this.peerConnectionFactory.createVideoSource(false);
        Intrinsics.checkNotNullExpressionValue(createVideoSource, "createVideoSource(...)");
        this.videoSource = createVideoSource;
        VideoTrack createVideoTrack = this.peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, this.videoSource);
        createVideoTrack.setEnabled(true);
        Intrinsics.checkNotNullExpressionValue(createVideoTrack, "apply(...)");
        this.videoTrack = createVideoTrack;
    }

    public /* synthetic */ RealtimeSessionManager(Context context, String str, String str2, String str3, boolean z, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(context, str, str2, str3, (i & 16) != 0 ? false : z);
    }

    @NotNull
    public final StateFlow<RealtimeConnectionState> getState() {
        return this.state;
    }

    static /* synthetic */ void reportDebugEvent$default(RealtimeSessionManager realtimeSessionManager, String str, String str2, String str3, JSONObject jSONObject, int i, Object obj) {
        if ((i & 8) != 0) {
            jSONObject = new JSONObject();
        }
        realtimeSessionManager.reportDebugEvent(str, str2, str3, jSONObject);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void reportDebugEvent(String hypothesisId, String location, String msg, JSONObject data) {
        try {
            Result.Companion companion = Result.Companion;
            RequestBody.Companion companion2 = RequestBody.Companion;
            String jSONObject = new JSONObject().put("sessionId", DEBUG_SESSION_ID).put("runId", "post-fix").put("hypothesisId", hypothesisId).put("location", location).put("msg", "[DEBUG] " + msg).put("data", data).put("ts", System.currentTimeMillis()).toString();
            Intrinsics.checkNotNullExpressionValue(jSONObject, "toString(...)");
            this.httpClient.newCall(new Request.Builder().url(DEBUG_SERVER_URL).post(companion2.create(jSONObject, JSON_MEDIA_TYPE)).build()).enqueue(new Callback() { // from class: com.neurafit.app.realtime.RealtimeSessionManager$reportDebugEvent$1$1
                public void onFailure(Call call, IOException e) {
                    Intrinsics.checkNotNullParameter(call, "call");
                    Intrinsics.checkNotNullParameter(e, "e");
                }

                public void onResponse(Call call, Response response) {
                    Intrinsics.checkNotNullParameter(call, "call");
                    Intrinsics.checkNotNullParameter(response, "response");
                    response.close();
                }
            });
            Result.constructor-impl(Unit.INSTANCE);
        } catch (Throwable th) {
            Result.Companion companion3 = Result.Companion;
            Result.constructor-impl(ResultKt.createFailure(th));
        }
    }

    private final void sampleSenderStats(PeerConnection peer, VideoFrame frame, long conversionDurationMs, Bitmap sourceBitmap, int rotationDegrees, boolean mirrorHorizontally) {
        peer.getStats((v6) -> {
            sampleSenderStats$lambda$0(r1, r2, r3, r4, r5, r6, v6);
        });
    }

    /* JADX WARN: Code restructure failed: missing block: B:189:0x04be, code lost:
    
        if (r6 == null) goto L177;
     */
    /* JADX WARN: Code restructure failed: missing block: B:232:0x0601, code lost:
    
        if (r6 == null) goto L231;
     */
    /* JADX WARN: Code restructure failed: missing block: B:240:0x062b, code lost:
    
        if (r6 == null) goto L241;
     */
    /* JADX WARN: Code restructure failed: missing block: B:248:0x0655, code lost:
    
        if (r6 == null) goto L251;
     */
    /* JADX WARN: Code restructure failed: missing block: B:256:0x067f, code lost:
    
        if (r6 == null) goto L261;
     */
    /* JADX WARN: Code restructure failed: missing block: B:260:0x06a1, code lost:
    
        if (r6 == null) goto L267;
     */
    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    /* JADX WARN: Removed duplicated region for block: B:133:0x0373  */
    /* JADX WARN: Removed duplicated region for block: B:136:0x0388  */
    /* JADX WARN: Removed duplicated region for block: B:141:0x03a3  */
    /* JADX WARN: Removed duplicated region for block: B:144:0x03b8  */
    /* JADX WARN: Removed duplicated region for block: B:149:0x03d3  */
    /* JADX WARN: Removed duplicated region for block: B:152:0x03e8  */
    /* JADX WARN: Removed duplicated region for block: B:157:0x0403  */
    /* JADX WARN: Removed duplicated region for block: B:160:0x0418  */
    /* JADX WARN: Removed duplicated region for block: B:165:0x0433  */
    /* JADX WARN: Removed duplicated region for block: B:168:0x0448  */
    /* JADX WARN: Removed duplicated region for block: B:173:0x0463  */
    /* JADX WARN: Removed duplicated region for block: B:176:0x0478  */
    /* JADX WARN: Removed duplicated region for block: B:181:0x0493  */
    /* JADX WARN: Removed duplicated region for block: B:184:0x04a7  */
    /* JADX WARN: Removed duplicated region for block: B:192:0x04d2  */
    /* JADX WARN: Removed duplicated region for block: B:197:0x04ed  */
    /* JADX WARN: Removed duplicated region for block: B:200:0x0502  */
    /* JADX WARN: Removed duplicated region for block: B:205:0x051d  */
    /* JADX WARN: Removed duplicated region for block: B:208:0x0532  */
    /* JADX WARN: Removed duplicated region for block: B:213:0x054d  */
    /* JADX WARN: Removed duplicated region for block: B:216:0x0567  */
    /* JADX WARN: Removed duplicated region for block: B:272:? A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:273:0x0553  */
    /* JADX WARN: Removed duplicated region for block: B:275:0x0523  */
    /* JADX WARN: Removed duplicated region for block: B:277:0x04f3  */
    /* JADX WARN: Removed duplicated region for block: B:280:0x0499  */
    /* JADX WARN: Removed duplicated region for block: B:282:0x0469  */
    /* JADX WARN: Removed duplicated region for block: B:284:0x0439  */
    /* JADX WARN: Removed duplicated region for block: B:286:0x0409  */
    /* JADX WARN: Removed duplicated region for block: B:288:0x03d9  */
    /* JADX WARN: Removed duplicated region for block: B:290:0x03a9  */
    /* JADX WARN: Removed duplicated region for block: B:292:0x0379  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private static final void sampleSenderStats$lambda$0(com.neurafit.app.realtime.RealtimeSessionManager r10, long r11, android.graphics.Bitmap r13, boolean r14, org.webrtc.VideoFrame r15, int r16, org.webrtc.RTCStatsReport r17) {
        /*
            Method dump skipped, instructions count: 1718
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.neurafit.app.realtime.RealtimeSessionManager.sampleSenderStats$lambda$0(com.neurafit.app.realtime.RealtimeSessionManager, long, android.graphics.Bitmap, boolean, org.webrtc.VideoFrame, int, org.webrtc.RTCStatsReport):void");
    }

    private final Double readStatNumber(Object value) {
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            return StringsKt.toDoubleOrNull((String) value);
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void applyVideoSenderTuning(RtpSender sender) {
        Object obj;
        RealtimeSessionManager realtimeSessionManager;
        RtpParameters parameters;
        List<RtpParameters.Encoding> list;
        try {
            Result.Companion companion = Result.Companion;
            realtimeSessionManager = this;
            parameters = sender.getParameters();
            list = parameters.encodings;
        } catch (Throwable th) {
            Result.Companion companion2 = Result.Companion;
            obj = Result.constructor-impl(ResultKt.createFailure(th));
        }
        if (list.isEmpty()) {
            return;
        }
        Intrinsics.checkNotNull(list);
        for (RtpParameters.Encoding encoding : list) {
            encoding.maxBitrateBps = Integer.valueOf(VIDEO_MAX_BITRATE_BPS);
            encoding.minBitrateBps = Integer.valueOf(VIDEO_MIN_BITRATE_BPS);
            encoding.maxFramerate = 24;
            encoding.scaleResolutionDownBy = Double.valueOf(1.0d);
        }
        sender.setParameters(parameters);
        JSONObject put = new JSONObject().put("maxBitrateBps", VIDEO_MAX_BITRATE_BPS).put("maxFramerate", 24).put("minBitrateBps", VIDEO_MIN_BITRATE_BPS).put("scaleResolutionDownBy", 1.0d);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        realtimeSessionManager.reportDebugEvent("C", "RealtimeSessionManager.kt:applyVideoSenderTuning", "Android sender applied outbound video tuning", put);
        obj = Result.constructor-impl(Unit.INSTANCE);
        Throwable th2 = Result.exceptionOrNull-impl(obj);
        if (th2 != null) {
            Log.w(TAG, "Unable to apply WebRTC sender tuning.", th2);
        }
    }

    public final void connect() {
        Object value;
        disconnect();
        JSONObject put = new JSONObject().put("forceRelay", this.forceRelay).put("roomId", ((RealtimeConnectionState) this._state.getValue()).getRoomId()).put("signalingUrl", ((RealtimeConnectionState) this._state.getValue()).getSignalingUrl());
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        reportDebugEvent("B", "RealtimeSessionManager.kt:connect", "Android sender started connect flow", put);
        MutableStateFlow mutableStateFlow = this._state;
        do {
            value = mutableStateFlow.getValue();
        } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default((RealtimeConnectionState) value, null, null, true, "connecting", "new", "closed", null, 3, null)));
        createPeerConnection();
        connectSignaling();
    }

    public final void disconnect() {
        Object value;
        releasePeerConnection();
        this.lastPoseFrameAtMs = 0L;
        this.lastVideoFrameAtMs = 0L;
        WebSocket webSocket = this.webSocket;
        if (webSocket != null) {
            webSocket.close(1000, "closed");
        }
        this.webSocket = null;
        MutableStateFlow mutableStateFlow = this._state;
        do {
            value = mutableStateFlow.getValue();
        } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default((RealtimeConnectionState) value, null, null, false, "offline", "closed", "closed", null, 67, null)));
    }

    public final void close() {
        disconnect();
        this.videoTrack.dispose();
        this.videoSource.dispose();
        this.peerConnectionFactory.dispose();
        this.eglBase.release();
        this.httpClient.dispatcher().executorService().shutdown();
    }

    public final void sendPoseFrame(@NotNull String payload) {
        DataChannel channel;
        Intrinsics.checkNotNullParameter(payload, "payload");
        long now = SystemClock.elapsedRealtime();
        if (now - this.lastPoseFrameAtMs >= 66 && (channel = this.dataChannel) != null && channel.state() == DataChannel.State.OPEN) {
            this.lastPoseFrameAtMs = now;
            JSONObject put = new JSONObject().put("channelState", channel.state().name());
            byte[] bytes = payload.getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
            JSONObject put2 = put.put("payloadBytes", bytes.length);
            Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
            reportDebugEvent("E", "RealtimeSessionManager.kt:sendPoseFrame", "Android sender pushed pose frame over data channel", put2);
            byte[] bytes2 = payload.getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(bytes2, "getBytes(...)");
            ByteBuffer buffer = ByteBuffer.wrap(bytes2);
            channel.send(new DataChannel.Buffer(buffer, false));
        }
    }

    @Override // com.neurafit.app.realtime.RealtimeVideoFrameSink
    public void onCameraFrame(@NotNull Bitmap bitmap, int rotationDegrees, boolean mirrorHorizontally, long timestampNs) {
        Object obj;
        Intrinsics.checkNotNullParameter(bitmap, "bitmap");
        if (((RealtimeConnectionState) this._state.getValue()).getEnabled()) {
            long now = SystemClock.elapsedRealtime();
            if (now - this.lastVideoFrameAtMs < 40) {
                return;
            }
            this.lastVideoFrameAtMs = now;
            if (now - this.lastDebugVideoReportAtMs >= 1000) {
                this.lastDebugVideoReportAtMs = now;
                JSONObject put = new JSONObject().put("mirrorHorizontally", mirrorHorizontally).put("rotationDegrees", rotationDegrees).put("timestampNs", timestampNs);
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                reportDebugEvent("A", "RealtimeSessionManager.kt:onCameraFrame", "Android sender captured camera frame for WebRTC", put);
            }
            try {
                Result.Companion companion = Result.Companion;
                RealtimeSessionManager realtimeSessionManager = this;
                long elapsedRealtime = SystemClock.elapsedRealtime();
                VideoFrame videoFrame = BitmapVideoFrameConverter.INSTANCE.toVideoFrame(bitmap, rotationDegrees, mirrorHorizontally, timestampNs, VIDEO_FRAME_MAX_LONG_EDGE);
                long elapsedRealtime2 = SystemClock.elapsedRealtime() - elapsedRealtime;
                realtimeSessionManager.videoSource.getCapturerObserver().onFrameCaptured(videoFrame);
                PeerConnection peerConnection = realtimeSessionManager.peerConnection;
                if (peerConnection != null && now - realtimeSessionManager.lastStatsReportAtMs >= 2000) {
                    realtimeSessionManager.lastStatsReportAtMs = now;
                    realtimeSessionManager.sampleSenderStats(peerConnection, videoFrame, elapsedRealtime2, bitmap, rotationDegrees, mirrorHorizontally);
                }
                videoFrame.release();
                obj = Result.constructor-impl(Unit.INSTANCE);
            } catch (Throwable th) {
                Result.Companion companion2 = Result.Companion;
                obj = Result.constructor-impl(ResultKt.createFailure(th));
            }
            Throwable th2 = Result.exceptionOrNull-impl(obj);
            if (th2 != null) {
                Log.e(TAG, "Unable to send WebRTC camera frame.", th2);
            }
        }
    }

    private final void connectSignaling() {
        Request request = new Request.Builder().url(((RealtimeConnectionState) this._state.getValue()).getSignalingUrl()).build();
        this.webSocket = this.httpClient.newWebSocket(request, new WebSocketListener() { // from class: com.neurafit.app.realtime.RealtimeSessionManager$connectSignaling$1
            public void onOpen(WebSocket webSocket, Response response) {
                Object value;
                Intrinsics.checkNotNullParameter(webSocket, "webSocket");
                Intrinsics.checkNotNullParameter(response, "response");
                Log.i(RealtimeSessionManager.TAG, "Signaling socket opened.");
                RealtimeSessionManager realtimeSessionManager = RealtimeSessionManager.this;
                JSONObject put = new JSONObject().put("responseCode", response.code()).put("roomId", ((RealtimeConnectionState) RealtimeSessionManager.this._state.getValue()).getRoomId());
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                realtimeSessionManager.reportDebugEvent("B", "RealtimeSessionManager.kt:onOpen", "Android sender signaling socket opened", put);
                MutableStateFlow mutableStateFlow = RealtimeSessionManager.this._state;
                do {
                    value = mutableStateFlow.getValue();
                } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default((RealtimeConnectionState) value, null, null, false, "online", null, null, null, 55, null)));
                RealtimeSessionManager realtimeSessionManager2 = RealtimeSessionManager.this;
                JSONObject put2 = new JSONObject().put("type", "join").put("role", "sender");
                Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
                realtimeSessionManager2.sendSignal(put2);
            }

            public void onMessage(WebSocket webSocket, String text) {
                Intrinsics.checkNotNullParameter(webSocket, "webSocket");
                Intrinsics.checkNotNullParameter(text, "text");
                RealtimeSessionManager realtimeSessionManager = RealtimeSessionManager.this;
                JSONObject put = new JSONObject().put("raw", text);
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                realtimeSessionManager.reportDebugEvent("B", "RealtimeSessionManager.kt:onMessage", "Android sender received signaling message", put);
                RealtimeSessionManager.this.handleSignalMessage(text);
            }

            public void onClosed(WebSocket webSocket, int code, String reason) {
                Object value;
                Intrinsics.checkNotNullParameter(webSocket, "webSocket");
                Intrinsics.checkNotNullParameter(reason, "reason");
                Log.i(RealtimeSessionManager.TAG, "Signaling socket closed: " + code + " " + reason);
                MutableStateFlow mutableStateFlow = RealtimeSessionManager.this._state;
                do {
                    value = mutableStateFlow.getValue();
                } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default((RealtimeConnectionState) value, null, null, false, "offline", null, null, null, 119, null)));
            }

            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Object value;
                RealtimeConnectionState realtimeConnectionState;
                String localizedMessage;
                Intrinsics.checkNotNullParameter(webSocket, "webSocket");
                Intrinsics.checkNotNullParameter(t, "t");
                Log.e(RealtimeSessionManager.TAG, "Signaling socket failed.", t);
                RealtimeSessionManager realtimeSessionManager = RealtimeSessionManager.this;
                JSONObject jSONObject = new JSONObject();
                String localizedMessage2 = t.getLocalizedMessage();
                if (localizedMessage2 == null) {
                    localizedMessage2 = "unknown";
                }
                JSONObject put = jSONObject.put("message", localizedMessage2).put("responseCode", response != null ? response.code() : -1);
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                realtimeSessionManager.reportDebugEvent("B", "RealtimeSessionManager.kt:onFailure", "Android sender signaling socket failed", put);
                MutableStateFlow mutableStateFlow = RealtimeSessionManager.this._state;
                do {
                    value = mutableStateFlow.getValue();
                    realtimeConnectionState = (RealtimeConnectionState) value;
                    localizedMessage = t.getLocalizedMessage();
                    if (localizedMessage == null) {
                        localizedMessage = "Signaling failed.";
                    }
                } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default(realtimeConnectionState, null, null, false, "error", null, null, localizedMessage, 55, null)));
            }
        });
    }

    private final void releasePeerConnection() {
        DataChannel dataChannel = this.dataChannel;
        if (dataChannel != null) {
            dataChannel.close();
        }
        DataChannel dataChannel2 = this.dataChannel;
        if (dataChannel2 != null) {
            dataChannel2.dispose();
        }
        this.dataChannel = null;
        this.videoSender = null;
        PeerConnection peerConnection = this.peerConnection;
        if (peerConnection != null) {
            peerConnection.close();
        }
        PeerConnection peerConnection2 = this.peerConnection;
        if (peerConnection2 != null) {
            peerConnection2.dispose();
        }
        this.peerConnection = null;
        this.pendingRemoteIceCandidates.clear();
        this.offerInProgress = false;
    }

    private final void createPeerConnection() {
        PeerConnection peerConnection;
        RtpSender rtpSender;
        DataChannel dataChannel;
        PeerConnection.RTCConfiguration rTCConfiguration = new PeerConnection.RTCConfiguration(this.iceServers);
        rTCConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rTCConfiguration.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rTCConfiguration.iceTransportsType = this.forceRelay ? PeerConnection.IceTransportsType.RELAY : PeerConnection.IceTransportsType.ALL;
        rTCConfiguration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rTCConfiguration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        RealtimeSessionManager realtimeSessionManager = this;
        PeerConnection createPeerConnection = this.peerConnectionFactory.createPeerConnection(rTCConfiguration, new PeerConnection.Observer() { // from class: com.neurafit.app.realtime.RealtimeSessionManager$createPeerConnection$1
            public void onSignalingChange(PeerConnection.SignalingState newState) {
            }

            /* JADX WARN: Code restructure failed: missing block: B:13:0x0074, code lost:
            
                if (r5 == null) goto L20;
             */
            /* JADX WARN: Code restructure failed: missing block: B:4:0x000b, code lost:
            
                if (r1 == null) goto L7;
             */
            /* JADX WARN: Code restructure failed: missing block: B:8:0x0036, code lost:
            
                if (r6 == null) goto L13;
             */
            /*
                Code decompiled incorrectly, please refer to instructions dump.
                To view partially-correct add '--show-bad-code' argument
            */
            public void onIceConnectionChange(org.webrtc.PeerConnection.IceConnectionState r12) {
                /*
                    r11 = this;
                    java.lang.String r0 = "RealtimeSessionManager"
                    r1 = r12
                    r2 = r1
                    if (r2 == 0) goto Le
                    java.lang.String r1 = r1.name()
                    r2 = r1
                    if (r2 != 0) goto L11
                Le:
                Lf:
                    java.lang.String r1 = "unknown"
                L11:
                    java.lang.String r1 = "ICE connection state: " + r1
                    int r0 = android.util.Log.i(r0, r1)
                    r0 = r11
                    com.neurafit.app.realtime.RealtimeSessionManager r0 = com.neurafit.app.realtime.RealtimeSessionManager.this
                    java.lang.String r1 = "D"
                    java.lang.String r2 = "RealtimeSessionManager.kt:onIceConnectionChange"
                    java.lang.String r3 = "Android sender ICE connection state changed"
                    org.json.JSONObject r4 = new org.json.JSONObject
                    r5 = r4
                    r5.<init>()
                    java.lang.String r5 = "iceConnectionState"
                    r6 = r12
                    r7 = r6
                    if (r7 == 0) goto L39
                    java.lang.String r6 = r6.name()
                    r7 = r6
                    if (r7 != 0) goto L3c
                L39:
                L3a:
                    java.lang.String r6 = "unknown"
                L3c:
                    org.json.JSONObject r4 = r4.put(r5, r6)
                    r5 = r4
                    java.lang.String r6 = "put(...)"
                    kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(r5, r6)
                    com.neurafit.app.realtime.RealtimeSessionManager.access$reportDebugEvent(r0, r1, r2, r3, r4)
                    r0 = r11
                    com.neurafit.app.realtime.RealtimeSessionManager r0 = com.neurafit.app.realtime.RealtimeSessionManager.this
                    kotlinx.coroutines.flow.MutableStateFlow r0 = com.neurafit.app.realtime.RealtimeSessionManager.access$get_state$p(r0)
                    r13 = r0
                    r0 = 0
                    r14 = r0
                L52:
                    r0 = r13
                    java.lang.Object r0 = r0.getValue()
                    r15 = r0
                    r0 = r15
                    com.neurafit.app.realtime.RealtimeConnectionState r0 = (com.neurafit.app.realtime.RealtimeConnectionState) r0
                    r16 = r0
                    r0 = 0
                    r17 = r0
                    r0 = r16
                    r1 = 0
                    r2 = 0
                    r3 = 0
                    r4 = 0
                    r5 = r12
                    r6 = r5
                    if (r6 == 0) goto L77
                    java.lang.String r5 = r5.name()
                    r6 = r5
                    if (r6 != 0) goto L7a
                L77:
                L78:
                    java.lang.String r5 = "unknown"
                L7a:
                    r6 = 0
                    r7 = r12
                    org.webrtc.PeerConnection$IceConnectionState r8 = org.webrtc.PeerConnection.IceConnectionState.FAILED
                    if (r7 != r8) goto L87
                    java.lang.String r7 = "ICE failed. Keep Web and Android on the signaling host network, or configure TURN for emulator/NAT paths."
                    goto L8c
                L87:
                    r7 = r16
                    java.lang.String r7 = r7.getErrorMessage()
                L8c:
                    r8 = 47
                    r9 = 0
                    com.neurafit.app.realtime.RealtimeConnectionState r0 = com.neurafit.app.realtime.RealtimeConnectionState.copy$default(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9)
                    r16 = r0
                    r0 = r13
                    r1 = r15
                    r2 = r16
                    boolean r0 = r0.compareAndSet(r1, r2)
                    if (r0 == 0) goto L52
                La3:
                    return
                */
                throw new UnsupportedOperationException("Method not decompiled: com.neurafit.app.realtime.RealtimeSessionManager$createPeerConnection$1.onIceConnectionChange(org.webrtc.PeerConnection$IceConnectionState):void");
            }

            /* JADX WARN: Code restructure failed: missing block: B:13:0x0061, code lost:
            
                if (r6 == null) goto L19;
             */
            /* JADX WARN: Code restructure failed: missing block: B:18:0x009f, code lost:
            
                if (r5 == null) goto L26;
             */
            /* JADX WARN: Code restructure failed: missing block: B:4:0x000b, code lost:
            
                if (r1 == null) goto L7;
             */
            /* JADX WARN: Code restructure failed: missing block: B:7:0x0021, code lost:
            
                r0 = r11.this$0.videoSender;
             */
            /*
                Code decompiled incorrectly, please refer to instructions dump.
                To view partially-correct add '--show-bad-code' argument
            */
            public void onConnectionChange(org.webrtc.PeerConnection.PeerConnectionState r12) {
                /*
                    r11 = this;
                    java.lang.String r0 = "RealtimeSessionManager"
                    r1 = r12
                    r2 = r1
                    if (r2 == 0) goto Le
                    java.lang.String r1 = r1.name()
                    r2 = r1
                    if (r2 != 0) goto L11
                Le:
                Lf:
                    java.lang.String r1 = "unknown"
                L11:
                    java.lang.String r1 = "Peer connection state: " + r1
                    int r0 = android.util.Log.i(r0, r1)
                    r0 = r12
                    org.webrtc.PeerConnection$PeerConnectionState r1 = org.webrtc.PeerConnection.PeerConnectionState.CONNECTED
                    if (r0 != r1) goto L45
                    r0 = r11
                    com.neurafit.app.realtime.RealtimeSessionManager r0 = com.neurafit.app.realtime.RealtimeSessionManager.this
                    org.webrtc.RtpSender r0 = com.neurafit.app.realtime.RealtimeSessionManager.access$getVideoSender$p(r0)
                    r1 = r0
                    if (r1 == 0) goto L43
                    r14 = r0
                    r0 = r11
                    com.neurafit.app.realtime.RealtimeSessionManager r0 = com.neurafit.app.realtime.RealtimeSessionManager.this
                    r15 = r0
                    r0 = r14
                    r16 = r0
                    r0 = 0
                    r17 = r0
                    r0 = r15
                    r1 = r16
                    com.neurafit.app.realtime.RealtimeSessionManager.access$applyVideoSenderTuning(r0, r1)
                    goto L45
                L43:
                L45:
                    r0 = r11
                    com.neurafit.app.realtime.RealtimeSessionManager r0 = com.neurafit.app.realtime.RealtimeSessionManager.this
                    java.lang.String r1 = "D"
                    java.lang.String r2 = "RealtimeSessionManager.kt:onConnectionChange"
                    java.lang.String r3 = "Android sender peer connection state changed"
                    org.json.JSONObject r4 = new org.json.JSONObject
                    r5 = r4
                    r5.<init>()
                    java.lang.String r5 = "peerConnectionState"
                    r6 = r12
                    r7 = r6
                    if (r7 == 0) goto L64
                    java.lang.String r6 = r6.name()
                    r7 = r6
                    if (r7 != 0) goto L67
                L64:
                L65:
                    java.lang.String r6 = "unknown"
                L67:
                    org.json.JSONObject r4 = r4.put(r5, r6)
                    r5 = r4
                    java.lang.String r6 = "put(...)"
                    kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(r5, r6)
                    com.neurafit.app.realtime.RealtimeSessionManager.access$reportDebugEvent(r0, r1, r2, r3, r4)
                    r0 = r11
                    com.neurafit.app.realtime.RealtimeSessionManager r0 = com.neurafit.app.realtime.RealtimeSessionManager.this
                    kotlinx.coroutines.flow.MutableStateFlow r0 = com.neurafit.app.realtime.RealtimeSessionManager.access$get_state$p(r0)
                    r13 = r0
                    r0 = 0
                    r14 = r0
                L7d:
                    r0 = r13
                    java.lang.Object r0 = r0.getValue()
                    r15 = r0
                    r0 = r15
                    com.neurafit.app.realtime.RealtimeConnectionState r0 = (com.neurafit.app.realtime.RealtimeConnectionState) r0
                    r16 = r0
                    r0 = 0
                    r17 = r0
                    r0 = r16
                    r1 = 0
                    r2 = 0
                    r3 = 0
                    r4 = 0
                    r5 = r12
                    r6 = r5
                    if (r6 == 0) goto La2
                    java.lang.String r5 = r5.name()
                    r6 = r5
                    if (r6 != 0) goto La5
                La2:
                La3:
                    java.lang.String r5 = "unknown"
                La5:
                    r6 = 0
                    r7 = r12
                    org.webrtc.PeerConnection$PeerConnectionState r8 = org.webrtc.PeerConnection.PeerConnectionState.CONNECTED
                    if (r7 != r8) goto Lb1
                    r7 = 0
                    goto Lc2
                Lb1:
                    r7 = r12
                    org.webrtc.PeerConnection$PeerConnectionState r8 = org.webrtc.PeerConnection.PeerConnectionState.FAILED
                    if (r7 != r8) goto Lbd
                    java.lang.String r7 = "WebRTC transport failed. Try reconnecting; if it repeats, add a TURN server."
                    goto Lc2
                Lbd:
                    r7 = r16
                    java.lang.String r7 = r7.getErrorMessage()
                Lc2:
                    r8 = 47
                    r9 = 0
                    com.neurafit.app.realtime.RealtimeConnectionState r0 = com.neurafit.app.realtime.RealtimeConnectionState.copy$default(r0, r1, r2, r3, r4, r5, r6, r7, r8, r9)
                    r16 = r0
                    r0 = r13
                    r1 = r15
                    r2 = r16
                    boolean r0 = r0.compareAndSet(r1, r2)
                    if (r0 == 0) goto L7d
                Ld9:
                    return
                */
                throw new UnsupportedOperationException("Method not decompiled: com.neurafit.app.realtime.RealtimeSessionManager$createPeerConnection$1.onConnectionChange(org.webrtc.PeerConnection$PeerConnectionState):void");
            }

            public void onIceConnectionReceivingChange(boolean receiving) {
            }

            public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
            }

            public void onIceCandidate(IceCandidate candidate) {
                if (candidate == null) {
                    return;
                }
                Log.i(RealtimeSessionManager.TAG, "Sending ICE candidate: " + candidate.sdpMid + ":" + candidate.sdpMLineIndex);
                RealtimeSessionManager realtimeSessionManager2 = RealtimeSessionManager.this;
                JSONObject put = new JSONObject().put("candidate", candidate.sdp).put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex);
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                realtimeSessionManager2.reportDebugEvent("D", "RealtimeSessionManager.kt:onIceCandidate", "Android sender gathered local ICE candidate", put);
                RealtimeSessionManager realtimeSessionManager3 = RealtimeSessionManager.this;
                JSONObject put2 = new JSONObject().put("type", "ice-candidate").put("candidate", new JSONObject().put("candidate", candidate.sdp).put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex));
                Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
                realtimeSessionManager3.sendSignal(put2);
            }

            public void onIceCandidatesRemoved(IceCandidate[] candidates) {
            }

            public void onAddStream(MediaStream stream) {
            }

            public void onRemoveStream(MediaStream stream) {
            }

            public void onDataChannel(DataChannel channel) {
            }

            public void onRenegotiationNeeded() {
            }

            public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
            }
        });
        if (createPeerConnection != null) {
            RealtimeSessionManager realtimeSessionManager2 = this;
            RtpSender addTrack = createPeerConnection.addTrack(this.videoTrack, CollectionsKt.listOf(STREAM_ID));
            if (addTrack != null) {
                applyVideoSenderTuning(addTrack);
                realtimeSessionManager2 = realtimeSessionManager2;
                rtpSender = addTrack;
            } else {
                rtpSender = null;
            }
            realtimeSessionManager2.videoSender = rtpSender;
            DataChannel.Init init = new DataChannel.Init();
            init.ordered = false;
            init.maxRetransmits = 0;
            Unit unit = Unit.INSTANCE;
            RealtimeSessionManager realtimeSessionManager3 = this;
            DataChannel createDataChannel = createPeerConnection.createDataChannel(DATA_CHANNEL_LABEL, init);
            if (createDataChannel != null) {
                observeDataChannel(createDataChannel);
                realtimeSessionManager3 = realtimeSessionManager3;
                dataChannel = createDataChannel;
            } else {
                dataChannel = null;
            }
            realtimeSessionManager3.dataChannel = dataChannel;
            realtimeSessionManager = realtimeSessionManager;
            peerConnection = createPeerConnection;
        } else {
            peerConnection = null;
        }
        realtimeSessionManager.peerConnection = peerConnection;
    }

    private final void observeDataChannel(final DataChannel channel) {
        Object value;
        String lowerCase;
        MutableStateFlow mutableStateFlow = this._state;
        do {
            value = mutableStateFlow.getValue();
            lowerCase = channel.state().name().toLowerCase(Locale.ROOT);
            Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)");
        } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default((RealtimeConnectionState) value, null, null, false, null, null, lowerCase, null, 95, null)));
        channel.registerObserver(new DataChannel.Observer() { // from class: com.neurafit.app.realtime.RealtimeSessionManager$observeDataChannel$2
            public void onBufferedAmountChange(long previousAmount) {
            }

            public void onStateChange() {
                Object value2;
                String lowerCase2;
                MutableStateFlow mutableStateFlow2 = RealtimeSessionManager.this._state;
                DataChannel dataChannel = channel;
                do {
                    value2 = mutableStateFlow2.getValue();
                    lowerCase2 = dataChannel.state().name().toLowerCase(Locale.ROOT);
                    Intrinsics.checkNotNullExpressionValue(lowerCase2, "toLowerCase(...)");
                } while (!mutableStateFlow2.compareAndSet(value2, RealtimeConnectionState.copy$default((RealtimeConnectionState) value2, null, null, false, null, null, lowerCase2, null, 95, null)));
            }

            public void onMessage(DataChannel.Buffer buffer) {
            }
        });
    }

    private final void createOffer() {
        PeerConnection peerConnection = this.peerConnection;
        if (peerConnection == null) {
            RealtimeSessionManager realtimeSessionManager = this;
            realtimeSessionManager.createPeerConnection();
            peerConnection = realtimeSessionManager.peerConnection;
            if (peerConnection == null) {
                return;
            }
        }
        final PeerConnection peer = peerConnection;
        if (this.offerInProgress || peer.signalingState() != PeerConnection.SignalingState.STABLE) {
            Log.i(TAG, "Skipping offer. inProgress=" + this.offerInProgress + " state=" + peer.signalingState());
            JSONObject put = new JSONObject().put("offerInProgress", this.offerInProgress).put("signalingState", peer.signalingState().name());
            Intrinsics.checkNotNullExpressionValue(put, "put(...)");
            reportDebugEvent("A", "RealtimeSessionManager.kt:createOffer", "Android sender skipped offer creation", put);
            return;
        }
        this.offerInProgress = true;
        JSONObject put2 = new JSONObject().put("roomId", ((RealtimeConnectionState) this._state.getValue()).getRoomId());
        Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
        reportDebugEvent("A", "RealtimeSessionManager.kt:createOffer", "Android sender started SDP offer creation", put2);
        peer.createOffer(new SimpleSdpObserver() { // from class: com.neurafit.app.realtime.RealtimeSessionManager$createOffer$1
            /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
            {
                super(RealtimeSessionManager.this, "createOffer");
            }

            @Override // com.neurafit.app.realtime.RealtimeSessionManager.SimpleSdpObserver
            public void onCreateSuccess(final SessionDescription description) {
                if (description == null) {
                    RealtimeSessionManager.this.offerInProgress = false;
                    return;
                }
                PeerConnection peerConnection2 = peer;
                final RealtimeSessionManager realtimeSessionManager2 = RealtimeSessionManager.this;
                peerConnection2.setLocalDescription(new RealtimeSessionManager.SimpleSdpObserver(description) { // from class: com.neurafit.app.realtime.RealtimeSessionManager$createOffer$1$onCreateSuccess$1
                    final /* synthetic */ SessionDescription $description;

                    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                    {
                        super(RealtimeSessionManager.this, "setLocalOffer");
                        this.$description = description;
                    }

                    @Override // com.neurafit.app.realtime.RealtimeSessionManager.SimpleSdpObserver
                    public void onSetSuccess() {
                        Log.i(RealtimeSessionManager.TAG, "Local offer set; sending SDP offer.");
                        RealtimeSessionManager realtimeSessionManager3 = RealtimeSessionManager.this;
                        JSONObject put3 = new JSONObject().put("sdpType", this.$description.type.canonicalForm()).put("sdpLength", this.$description.description.length());
                        Intrinsics.checkNotNullExpressionValue(put3, "put(...)");
                        realtimeSessionManager3.reportDebugEvent("A", "RealtimeSessionManager.kt:setLocalOffer", "Android sender set local offer and is sending SDP offer", put3);
                        RealtimeSessionManager.this.sendSdp("offer", this.$description);
                        RealtimeSessionManager.this.offerInProgress = false;
                    }

                    @Override // com.neurafit.app.realtime.RealtimeSessionManager.SimpleSdpObserver
                    public void onSetFailure(String error) {
                        RealtimeSessionManager.this.offerInProgress = false;
                        super.onSetFailure(error);
                    }
                }, description);
            }

            @Override // com.neurafit.app.realtime.RealtimeSessionManager.SimpleSdpObserver
            public void onCreateFailure(String error) {
                RealtimeSessionManager.this.offerInProgress = false;
                super.onCreateFailure(error);
            }
        }, new MediaConstraints());
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
    public final void handleSignalMessage(String text) {
        Object obj;
        Object value;
        RealtimeConnectionState realtimeConnectionState;
        String localizedMessage;
        MutableStateFlow mutableStateFlow;
        Object value2;
        Object value3;
        RealtimeConnectionState realtimeConnectionState2;
        String optString;
        Object value4;
        try {
            Result.Companion companion = Result.Companion;
            RealtimeSessionManager realtimeSessionManager = this;
            JSONObject jSONObject = new JSONObject(text);
            String optString2 = jSONObject.optString("type");
            if (optString2 != null) {
                switch (optString2.hashCode()) {
                    case -1696228159:
                        if (!optString2.equals("ice-candidate")) {
                            break;
                        } else {
                            JSONObject put = new JSONObject().put("candidate", jSONObject.getJSONObject("candidate").optString("candidate"));
                            Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                            realtimeSessionManager.reportDebugEvent("D", "RealtimeSessionManager.kt:ice-candidate", "Android sender received remote ICE candidate", put);
                            JSONObject jSONObject2 = jSONObject.getJSONObject("candidate");
                            Intrinsics.checkNotNullExpressionValue(jSONObject2, "getJSONObject(...)");
                            realtimeSessionManager.addIceCandidate(jSONObject2);
                            break;
                        }
                    case -1412808770:
                        if (!optString2.equals("answer")) {
                            break;
                        } else {
                            Log.i(TAG, "Received SDP answer.");
                            JSONObject put2 = new JSONObject().put("rawType", jSONObject.optString("type"));
                            Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
                            realtimeSessionManager.reportDebugEvent("A", "RealtimeSessionManager.kt:answer", "Android sender received SDP answer", put2);
                            JSONObject jSONObject3 = jSONObject.getJSONObject("sdp");
                            Intrinsics.checkNotNullExpressionValue(jSONObject3, "getJSONObject(...)");
                            realtimeSessionManager.setRemoteDescription(jSONObject3);
                            break;
                        }
                    case -1154529463:
                        if (!optString2.equals("joined")) {
                            break;
                        } else {
                            MutableStateFlow mutableStateFlow2 = realtimeSessionManager._state;
                            do {
                                value3 = mutableStateFlow2.getValue();
                                realtimeConnectionState2 = (RealtimeConnectionState) value3;
                                optString = jSONObject.optString("roomId", realtimeConnectionState2.getRoomId());
                                Intrinsics.checkNotNullExpressionValue(optString, "optString(...)");
                            } while (!mutableStateFlow2.compareAndSet(value3, RealtimeConnectionState.copy$default(realtimeConnectionState2, optString, null, false, "online", null, null, null, 118, null)));
                        }
                    case -1113975743:
                        if (!optString2.equals("room-full")) {
                            break;
                        }
                        mutableStateFlow = realtimeSessionManager._state;
                        do {
                            value2 = mutableStateFlow.getValue();
                        } while (!mutableStateFlow.compareAndSet(value2, RealtimeConnectionState.copy$default((RealtimeConnectionState) value2, null, null, false, null, null, null, jSONObject.optString("message", "Signaling error."), 63, null)));
                    case 96784904:
                        if (!optString2.equals("error")) {
                            break;
                        }
                        mutableStateFlow = realtimeSessionManager._state;
                        do {
                            value2 = mutableStateFlow.getValue();
                        } while (!mutableStateFlow.compareAndSet(value2, RealtimeConnectionState.copy$default((RealtimeConnectionState) value2, null, null, false, null, null, null, jSONObject.optString("message", "Signaling error."), 63, null)));
                    case 1307921940:
                        if (!optString2.equals("peer-joined")) {
                            break;
                        } else {
                            Log.i(TAG, "Peer joined as " + jSONObject.optString("role") + ".");
                            JSONObject put3 = new JSONObject().put("role", jSONObject.optString("role"));
                            Intrinsics.checkNotNullExpressionValue(put3, "put(...)");
                            realtimeSessionManager.reportDebugEvent("A", "RealtimeSessionManager.kt:peer-joined", "Android sender observed peer joined", put3);
                            if (Intrinsics.areEqual(jSONObject.optString("role"), "receiver")) {
                                realtimeSessionManager.createOffer();
                                break;
                            }
                        }
                        break;
                    case 2110905746:
                        if (!optString2.equals("peer-left")) {
                            break;
                        } else {
                            realtimeSessionManager.releasePeerConnection();
                            realtimeSessionManager.createPeerConnection();
                            MutableStateFlow mutableStateFlow3 = realtimeSessionManager._state;
                            do {
                                value4 = mutableStateFlow3.getValue();
                            } while (!mutableStateFlow3.compareAndSet(value4, RealtimeConnectionState.copy$default((RealtimeConnectionState) value4, null, null, false, null, "waiting", "closed", null, 79, null)));
                        }
                }
            }
            obj = Result.constructor-impl(Unit.INSTANCE);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.Companion;
            obj = Result.constructor-impl(ResultKt.createFailure(th));
        }
        Throwable th2 = Result.exceptionOrNull-impl(obj);
        if (th2 != null) {
            MutableStateFlow mutableStateFlow4 = this._state;
            do {
                value = mutableStateFlow4.getValue();
                realtimeConnectionState = (RealtimeConnectionState) value;
                localizedMessage = th2.getLocalizedMessage();
                if (localizedMessage == null) {
                    localizedMessage = "Unable to parse signal.";
                }
            } while (!mutableStateFlow4.compareAndSet(value, RealtimeConnectionState.copy$default(realtimeConnectionState, null, null, false, null, null, null, localizedMessage, 63, null)));
        }
    }

    private final void setRemoteDescription(JSONObject sdp) {
        SessionDescription description = new SessionDescription(SessionDescription.Type.fromCanonicalForm(sdp.getString("type")), sdp.getString("sdp"));
        PeerConnection peerConnection = this.peerConnection;
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(new SimpleSdpObserver() { // from class: com.neurafit.app.realtime.RealtimeSessionManager$setRemoteDescription$1
                {
                    super(RealtimeSessionManager.this, "setRemoteDescription");
                }

                @Override // com.neurafit.app.realtime.RealtimeSessionManager.SimpleSdpObserver
                public void onSetSuccess() {
                    List list;
                    list = RealtimeSessionManager.this.pendingRemoteIceCandidates;
                    Log.i(RealtimeSessionManager.TAG, "Remote description set; draining " + list.size() + " queued ICE candidates.");
                    RealtimeSessionManager.this.drainPendingRemoteIceCandidates();
                }
            }, description);
        }
    }

    private final void addIceCandidate(JSONObject candidate) {
        String candidateSdp = candidate.getString("candidate");
        Companion companion = Companion;
        Intrinsics.checkNotNull(candidateSdp);
        String resolvedCandidateSdp = companion.rewriteMdnsHostCandidate(candidateSdp, this.signalingHost);
        if (!Intrinsics.areEqual(resolvedCandidateSdp, candidateSdp)) {
            Log.i(TAG, "Rewrote browser mDNS ICE host candidate to " + this.signalingHost + " for LAN WebRTC.");
        }
        String optString = candidate.optString("sdpMid");
        Intrinsics.checkNotNull(optString);
        IceCandidate iceCandidate = new IceCandidate(!StringsKt.isBlank(optString) ? optString : null, candidate.optInt("sdpMLineIndex"), resolvedCandidateSdp);
        PeerConnection peer = this.peerConnection;
        if (peer == null) {
            return;
        }
        if (peer.getRemoteDescription() == null) {
            this.pendingRemoteIceCandidates.add(iceCandidate);
            Log.i(TAG, "Queued remote ICE candidate until remote description is set.");
        } else {
            Log.i(TAG, "Adding remote ICE candidate: " + iceCandidate.sdpMid + ":" + iceCandidate.sdpMLineIndex);
            peer.addIceCandidate(iceCandidate);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void drainPendingRemoteIceCandidates() {
        PeerConnection peer = this.peerConnection;
        if (peer == null) {
            return;
        }
        Iterable candidates = CollectionsKt.toList(this.pendingRemoteIceCandidates);
        this.pendingRemoteIceCandidates.clear();
        Iterator it = candidates.iterator();
        while (it.hasNext()) {
            peer.addIceCandidate((IceCandidate) it.next());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void sendSdp(String type, SessionDescription description) {
        JSONObject put = new JSONObject().put("type", type).put("sdp", new JSONObject().put("type", description.type.canonicalForm()).put("sdp", description.description));
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        sendSignal(put);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void sendSignal(JSONObject message) {
        String roomId = ((RealtimeConnectionState) this._state.getValue()).getRoomId();
        message.put("roomId", roomId);
        JSONObject put = new JSONObject().put("roomId", roomId).put("type", message.optString("type"));
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        reportDebugEvent("B", "RealtimeSessionManager.kt:sendSignal", "Android sender sent signaling message", put);
        WebSocket webSocket = this.webSocket;
        if (webSocket != null) {
            String jSONObject = message.toString();
            Intrinsics.checkNotNullExpressionValue(jSONObject, "toString(...)");
            webSocket.send(jSONObject);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* compiled from: RealtimeSessionManager.kt */
    @Metadata(mv = {2, FoodClassifier.RGB_CHANNELS, BuildConfig.RTC_FORCE_RELAY}, k = 1, xi = 48, d1 = {"�� \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n��\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0002\n��\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0092\u0004\u0018��2\u00020\u0001B\u000f\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0004\b\u0004\u0010\u0005J\u0012\u0010\u0006\u001a\u00020\u00072\b\u0010\b\u001a\u0004\u0018\u00010\tH\u0016J\b\u0010\n\u001a\u00020\u0007H\u0016J\u0012\u0010\u000b\u001a\u00020\u00072\b\u0010\f\u001a\u0004\u0018\u00010\u0003H\u0016J\u0012\u0010\r\u001a\u00020\u00072\b\u0010\f\u001a\u0004\u0018\u00010\u0003H\u0016R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n��¨\u0006\u000e"}, d2 = {"Lcom/neurafit/app/realtime/RealtimeSessionManager$SimpleSdpObserver;", "Lorg/webrtc/SdpObserver;", "operation", "", "<init>", "(Lcom/neurafit/app/realtime/RealtimeSessionManager;Ljava/lang/String;)V", "onCreateSuccess", "", "description", "Lorg/webrtc/SessionDescription;", "onSetSuccess", "onCreateFailure", "error", "onSetFailure", "app"})
    @SourceDebugExtension({"SMAP\nRealtimeSessionManager.kt\nKotlin\n*S Kotlin\n*F\n+ 1 RealtimeSessionManager.kt\ncom/neurafit/app/realtime/RealtimeSessionManager$SimpleSdpObserver\n+ 2 StateFlow.kt\nkotlinx/coroutines/flow/StateFlowKt\n*L\n1#1,948:1\n230#2,5:949\n230#2,5:954\n*S KotlinDebug\n*F\n+ 1 RealtimeSessionManager.kt\ncom/neurafit/app/realtime/RealtimeSessionManager$SimpleSdpObserver\n*L\n846#1:949,5\n851#1:954,5\n*E\n"})
    /* loaded from: classes.jar:com/neurafit/app/realtime/RealtimeSessionManager$SimpleSdpObserver.class */
    class SimpleSdpObserver implements SdpObserver {

        @NotNull
        private final String operation;
        final /* synthetic */ RealtimeSessionManager this$0;

        public SimpleSdpObserver(@NotNull RealtimeSessionManager this$0, String operation) {
            Intrinsics.checkNotNullParameter(operation, "operation");
            this.this$0 = this$0;
            this.operation = operation;
        }

        public void onCreateSuccess(@Nullable SessionDescription description) {
        }

        public void onSetSuccess() {
        }

        public void onCreateFailure(@Nullable String error) {
            Object value;
            RealtimeConnectionState realtimeConnectionState;
            String str;
            Log.e(RealtimeSessionManager.TAG, this.operation + " failed: " + error);
            MutableStateFlow mutableStateFlow = this.this$0._state;
            do {
                value = mutableStateFlow.getValue();
                realtimeConnectionState = (RealtimeConnectionState) value;
                str = error;
                if (str == null) {
                    str = this.operation + " failed.";
                }
            } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default(realtimeConnectionState, null, null, false, null, null, null, str, 63, null)));
        }

        public void onSetFailure(@Nullable String error) {
            Object value;
            RealtimeConnectionState realtimeConnectionState;
            String str;
            Log.e(RealtimeSessionManager.TAG, this.operation + " failed: " + error);
            MutableStateFlow mutableStateFlow = this.this$0._state;
            do {
                value = mutableStateFlow.getValue();
                realtimeConnectionState = (RealtimeConnectionState) value;
                str = error;
                if (str == null) {
                    str = this.operation + " failed.";
                }
            } while (!mutableStateFlow.compareAndSet(value, RealtimeConnectionState.copy$default(realtimeConnectionState, null, null, false, null, null, null, str, 63, null)));
        }
    }

    /* compiled from: RealtimeSessionManager.kt */
    @Metadata(mv = {2, FoodClassifier.RGB_CHANNELS, BuildConfig.RTC_FORCE_RELAY}, k = 1, xi = 48, d1 = {"��J\n\u0002\u0018\u0002\n\u0002\u0010��\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\u000b\n��\n\u0002\u0010\u0002\n��\n\u0002\u0018\u0002\n��\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\t\b\u0082\u0003\u0018��2\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u001fJ\u0014\u0010 \u001a\b\u0012\u0004\u0012\u00020\"0!2\u0006\u0010#\u001a\u00020\u0005J\u0010\u0010$\u001a\u0004\u0018\u00010\u00052\u0006\u0010%\u001a\u00020\u0005J\u0018\u0010&\u001a\u00020\u00052\u0006\u0010'\u001a\u00020\u00052\b\u0010(\u001a\u0004\u0018\u00010\u0005J\f\u0010)\u001a\u00020\u001b*\u00020\u0005H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n��R\u0014\u0010\u0006\u001a\u00020\u0005X\u0086D¢\u0006\b\n��\u001a\u0004\b\u0007\u0010\bR\u0014\u0010\t\u001a\u00020\u0005X\u0086D¢\u0006\b\n��\u001a\u0004\b\n\u0010\bR\u0011\u0010\u000b\u001a\u00020\f¢\u0006\b\n��\u001a\u0004\b\r\u0010\u000eR\u000e\u0010\u000f\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0010\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0011\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0012\u001a\u00020\u0013X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0014\u001a\u00020\u0013X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0015\u001a\u00020\u0016X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0017\u001a\u00020\u0016X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0018\u001a\u00020\u0016X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u0019\u001a\u00020\u0016X\u0086T¢\u0006\u0002\n��R\u000e\u0010\u001a\u001a\u00020\u001bX\u0082\u000e¢\u0006\u0002\n��R\u000e\u0010*\u001a\u00020\u0016X\u0082T¢\u0006\u0002\n��¨\u0006+"}, d2 = {"Lcom/neurafit/app/realtime/RealtimeSessionManager$Companion;", "", "<init>", "()V", "DATA_CHANNEL_LABEL", "", "DEBUG_SERVER_URL", "getDEBUG_SERVER_URL", "()Ljava/lang/String;", "DEBUG_SESSION_ID", "getDEBUG_SESSION_ID", "JSON_MEDIA_TYPE", "Lokhttp3/MediaType;", "getJSON_MEDIA_TYPE", "()Lokhttp3/MediaType;", "STREAM_ID", "TAG", "VIDEO_TRACK_ID", "POSE_FRAME_INTERVAL_MS", "", "VIDEO_FRAME_INTERVAL_MS", "VIDEO_FRAME_MAX_LONG_EDGE", "", "VIDEO_MAX_BITRATE_BPS", "VIDEO_MAX_FRAMERATE", "VIDEO_MIN_BITRATE_BPS", "factoryInitialized", "", "initializePeerConnectionFactory", "", "context", "Landroid/content/Context;", "parseIceServers", "", "Lorg/webrtc/PeerConnection$IceServer;", "json", "parseSignalingHost", "signalingUrl", "rewriteMdnsHostCandidate", "candidateSdp", "fallbackHost", "isLoopbackHost", "CANDIDATE_ADDRESS_INDEX", "app"})
    @SourceDebugExtension({"SMAP\nRealtimeSessionManager.kt\nKotlin\n*S Kotlin\n*F\n+ 1 RealtimeSessionManager.kt\ncom/neurafit/app/realtime/RealtimeSessionManager$Companion\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n+ 3 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n*L\n1#1,948:1\n1#2:949\n1915#3,2:950\n*S KotlinDebug\n*F\n+ 1 RealtimeSessionManager.kt\ncom/neurafit/app/realtime/RealtimeSessionManager$Companion\n*L\n896#1:950,2\n*E\n"})
    /* loaded from: classes.jar:com/neurafit/app/realtime/RealtimeSessionManager$Companion.class */
    private static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker $constructor_marker) {
            this();
        }

        private Companion() {
        }

        @NotNull
        public final String getDEBUG_SERVER_URL() {
            return RealtimeSessionManager.DEBUG_SERVER_URL;
        }

        @NotNull
        public final String getDEBUG_SESSION_ID() {
            return RealtimeSessionManager.DEBUG_SESSION_ID;
        }

        @NotNull
        public final MediaType getJSON_MEDIA_TYPE() {
            return RealtimeSessionManager.JSON_MEDIA_TYPE;
        }

        public final synchronized void initializePeerConnectionFactory(@NotNull Context context) {
            Intrinsics.checkNotNullParameter(context, "context");
            if (RealtimeSessionManager.factoryInitialized) {
                return;
            }
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions());
            RealtimeSessionManager.factoryInitialized = true;
        }

        @NotNull
        public final List<PeerConnection.IceServer> parseIceServers(@NotNull String json) {
            Object obj;
            ArrayList listOf;
            Intrinsics.checkNotNullParameter(json, "json");
            try {
                Result.Companion companion = Result.Companion;
                Companion companion2 = this;
                obj = Result.constructor-impl(new JSONArray(json));
            } catch (Throwable th) {
                Result.Companion companion3 = Result.Companion;
                obj = Result.constructor-impl(ResultKt.createFailure(th));
            }
            Object obj2 = obj;
            JSONArray parsed = (JSONArray) (Result.isFailure-impl(obj2) ? null : obj2);
            if (parsed == null || parsed.length() == 0) {
                return CollectionsKt.listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
            }
            List createListBuilder = CollectionsKt.createListBuilder();
            int length = parsed.length();
            for (int i = 0; i < length; i++) {
                JSONObject jSONObject = parsed.getJSONObject(i);
                Object obj3 = jSONObject.get("urls");
                if (obj3 instanceof JSONArray) {
                    int length2 = ((JSONArray) obj3).length();
                    ArrayList arrayList = new ArrayList(length2);
                    for (int i2 = 0; i2 < length2; i2++) {
                        arrayList.add(((JSONArray) obj3).getString(i2));
                    }
                    listOf = arrayList;
                } else {
                    listOf = CollectionsKt.listOf(obj3.toString());
                }
                Iterator it = listOf.iterator();
                while (it.hasNext()) {
                    PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder((String) it.next());
                    String optString = jSONObject.optString("username");
                    Intrinsics.checkNotNull(optString);
                    String str = !StringsKt.isBlank(optString) ? optString : null;
                    if (str != null) {
                        Intrinsics.checkNotNull(builder);
                        builder.setUsername(str);
                    }
                    String optString2 = jSONObject.optString("credential");
                    Intrinsics.checkNotNull(optString2);
                    String str2 = !StringsKt.isBlank(optString2) ? optString2 : null;
                    if (str2 != null) {
                        Intrinsics.checkNotNull(builder);
                        builder.setPassword(str2);
                    }
                    createListBuilder.add(builder.createIceServer());
                }
            }
            List<PeerConnection.IceServer> build = CollectionsKt.build(createListBuilder);
            return build.isEmpty() ? CollectionsKt.listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()) : build;
        }

        @Nullable
        public final String parseSignalingHost(@NotNull String signalingUrl) {
            Object obj;
            Intrinsics.checkNotNullParameter(signalingUrl, "signalingUrl");
            try {
                Result.Companion companion = Result.Companion;
                Companion companion2 = this;
                obj = Result.constructor-impl(new URI(signalingUrl).getHost());
            } catch (Throwable th) {
                Result.Companion companion3 = Result.Companion;
                obj = Result.constructor-impl(ResultKt.createFailure(th));
            }
            Object obj2 = obj;
            String str = (String) (Result.isFailure-impl(obj2) ? null : obj2);
            if (str == null) {
                return null;
            }
            if ((!StringsKt.isBlank(str)) && !RealtimeSessionManager.Companion.isLoopbackHost(str)) {
                return str;
            }
            return null;
        }

        @NotNull
        public final String rewriteMdnsHostCandidate(@NotNull String candidateSdp, @Nullable String fallbackHost) {
            Intrinsics.checkNotNullParameter(candidateSdp, "candidateSdp");
            String str = fallbackHost;
            if (str == null || StringsKt.isBlank(str)) {
                return candidateSdp;
            }
            List parts = CollectionsKt.toMutableList(new Regex("\\s+").split(StringsKt.trim(candidateSdp).toString(), 0));
            int typeMarkerIndex = parts.indexOf("typ");
            int candidateTypeIndex = typeMarkerIndex + 1;
            if (parts.size() <= 4 || parts.size() <= candidateTypeIndex) {
                return candidateSdp;
            }
            String address = (String) parts.get(4);
            String candidateType = (String) parts.get(candidateTypeIndex);
            if (!Intrinsics.areEqual(candidateType, "host") || !StringsKt.endsWith(address, ".local", true)) {
                return candidateSdp;
            }
            parts.set(4, fallbackHost);
            return CollectionsKt.joinToString$default(parts, " ", (CharSequence) null, (CharSequence) null, 0, (CharSequence) null, (Function1) null, 62, (Object) null);
        }

        private final boolean isLoopbackHost(String $this$isLoopbackHost) {
            return StringsKt.equals($this$isLoopbackHost, "localhost", true) || Intrinsics.areEqual($this$isLoopbackHost, "127.0.0.1") || Intrinsics.areEqual($this$isLoopbackHost, "::1") || Intrinsics.areEqual($this$isLoopbackHost, "[::1]");
        }
    }
}
