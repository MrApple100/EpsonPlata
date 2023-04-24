package ru.`object`.epsoncamera.epsonRTSP

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import com.pedro.encoder.Frame
import com.pedro.encoder.audio.GetAacData
import com.pedro.encoder.input.audio.*
import com.pedro.encoder.input.video.*
import com.pedro.encoder.utils.CodecUtil
import com.pedro.encoder.video.FormatVideoEncoder
import com.pedro.encoder.video.GetVideoData
import com.pedro.encoder.video.VideoEncoder
import com.pedro.rtplibrary.base.recording.RecordController
import com.pedro.rtplibrary.util.FpsListener
import ru.`object`.epsoncamera.epsonRTSP.input.EpsonApiManager
import java.io.IOException
import java.nio.ByteBuffer


abstract class EpsonBase(
    surfaceView: SurfaceView
) : GetAacData, GetCameraData, GetVideoData, GetMicrophoneData {
    private val context: Context
    private val cameraManager: EpsonApiManager
    protected var videoEncoder: VideoEncoder? = null
    var isStreaming = false
    private var isOnPreview = false
    private var previewWidth = 0
    private var previewHeight = 0
    private val fpsListener = FpsListener()


    private fun init() {
        videoEncoder = VideoEncoder(this)
    }


    fun setCameraCallbacks(callbacks: CameraCallbacks?) {
        cameraManager.setCameraCallbacks(callbacks)
    }


    /**
     * @param callback get fps while record or stream
     */
    fun setFpsListener(callback: FpsListener.Callback?) {
        fpsListener.setCallback(callback)
    }


    /**
     * Basic auth developed to work with Wowza. No tested with other server
     *
     * @param user auth.
     * @param password auth.
     */
    abstract fun setAuthorization(user: String?, password: String?)
    /**
     * Call this method before use @startStream. If not you will do a stream without video. NOTE:
     * Rotation with encoder is silence ignored in some devices.
     *
     * @param width resolution in px.
     * @param height resolution in px.
     * @param fps frames per second of the stream.
     * @param bitrate H264 in bps.
     * @param rotation could be 90, 180, 270 or 0. You should use CameraHelper.getCameraOrientation
     * with SurfaceView or TextureView and 0 with OpenGlView or LightOpenGlView. NOTE: Rotation with
     * encoder is silence ignored in some devices.
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    /**
     * backward compatibility reason
     */
    @JvmOverloads
    fun prepareVideo(
        width: Int, height: Int, fps: Int, bitrate: Int, iFrameInterval: Int,
        rotation: Int, avcProfile: Int = -1, avcProfileLevel: Int = -1
    ): Boolean {
        if (isOnPreview && width != previewWidth || height != previewHeight || fps != videoEncoder!!.fps || rotation != videoEncoder!!.rotation) {
            //stopPreview()
            //isOnPreview = true
        }
        val formatVideoEncoder = FormatVideoEncoder.YUV420Dynamical
        return videoEncoder!!.prepareVideoEncoder(
            width, height, fps, bitrate, rotation, iFrameInterval,
            formatVideoEncoder, avcProfile, avcProfileLevel
        )
    }

    fun prepareVideo(width: Int, height: Int, fps: Int, bitrate: Int, rotation: Int): Boolean {
        return prepareVideo(width, height, fps, bitrate, 0, rotation)
    }

    fun prepareVideo(width: Int, height: Int, bitrate: Int): Boolean {
        val rotation = CameraHelper.getCameraOrientation(context)
        return prepareVideo(width, height, 30, bitrate, 0, rotation)
    }

    protected abstract fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int)


    /**
     * Same to call: rotation = 0; if (Portrait) rotation = 90; prepareVideo(640, 480, 30, 1200 *
     * 1024, false, rotation);
     *
     * @return true if success, false if you get a error (Normally because the encoder selected
     * doesn't support any configuration seated or your device hasn't a H264 encoder).
     */
    fun prepareVideo(): Boolean {
        val rotation = CameraHelper.getCameraOrientation(context)
        return prepareVideo(1920, 1080, 30, 1024 * 8000, rotation)
    }

    /**
     * @param forceVideo force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     * @param forceAudio force type codec used. FIRST_COMPATIBLE_FOUND, SOFTWARE, HARDWARE
     */
    fun setForce(forceVideo: CodecUtil.Force?, forceAudio: CodecUtil.Force?) {
        videoEncoder!!.setForce(forceVideo)
    }


    /**
     * Start camera preview. Ignored, if stream or preview is started.
     *
     * @param cameraId camera id.
     * [com.pedro.encoder.input.video.CameraHelper.Facing.FRONT]
     * @param width of preview in px.
     * @param height of preview in px.
     * @param rotation camera rotation (0, 90, 180, 270). Recommended: [ ][com.pedro.encoder.input.video.CameraHelper.getCameraOrientation]
     */
    fun startPreview(width: Int, height: Int, fps: Int, rotation: Int) {
        if (!isStreaming && !isOnPreview) {
            previewWidth = width
            previewHeight = height
            videoEncoder!!.fps = fps
            videoEncoder!!.rotation = rotation

            cameraManager.setRotation(rotation)
            cameraManager.start( width, height, videoEncoder!!.fps)
            isOnPreview = true
        } else {
            Log.e(EpsonBase.Companion.TAG, "Streaming or preview started, ignored")
        }
    }

    fun startPreview(
        width: Int = videoEncoder!!.width,
        height: Int = videoEncoder!!.height,
        rotation: Int = CameraHelper.getCameraOrientation(context)
    ) {
        startPreview( width, height, videoEncoder!!.fps, rotation)
    }


    /**
     * Stop camera preview. Ignored if streaming or already stopped. You need call it after
     *
     * @stopStream to release camera properly if you will close activity.
     */

    fun stopPreview() {
        if (!isStreaming
            && isOnPreview
        ) {
            cameraManager.stop()
            isOnPreview = false
            previewWidth = 0
            previewHeight = 0
        } else {
            Log.e(EpsonBase.TAG, "Streaming or preview stopped, ignored")
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(IOException::class)
    fun startStreamAndRecord(url: String, path: String?, listener: RecordController.Listener?) {
        startStream(url)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Throws(IOException::class)
    fun startStreamAndRecord(url: String, path: String) {
        startStreamAndRecord(url, path, null)
    }

    protected abstract fun startStreamRtp(url: String)

    /**
     * Need be called after @prepareVideo or/and @prepareAudio. This method override resolution of
     *
     * @param url of the stream like: protocol://ip:port/application/streamName
     *
     * RTSP: rtsp://192.168.1.1:1935/live/pedroSG94 RTSPS: rtsps://192.168.1.1:1935/live/pedroSG94
     * RTMP: rtmp://192.168.1.1:1935/live/pedroSG94 RTMPS: rtmps://192.168.1.1:1935/live/pedroSG94
     * @startPreview to resolution seated in @prepareVideo. If you never startPreview this method
     * startPreview for you to resolution seated in @prepareVideo.
     */

    fun startStream(url: String) {
        isStreaming = true
        // System.out.println("recordController "+ recordController.isRecording());
        Log.d(TAG,"isOnPreview "+isOnPreview)

            startEncoders()

            requestKeyFrame()

        startStreamRtp(url)
    }

    private fun startEncoders() {
        videoEncoder!!.start()
        cameraManager.setRotation(videoEncoder!!.rotation)
        if (!cameraManager.isRunning) {
            cameraManager.start(videoEncoder!!.width, videoEncoder!!.height, videoEncoder!!.fps)
        }
        isOnPreview = true
    }

    fun requestKeyFrame() {
        if (videoEncoder!!.isRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                videoEncoder!!.requestKeyframe()
            }
        }
    }

//    private fun prepareGlView() {
//        cameraManager.setSurfaceTexture(glInterface!!.surfaceTexture)
//    }

    protected abstract fun stopStreamRtp()

    /**
     * Stop stream started with @startStream.
     */
    fun stopStream() {
        if (isStreaming) {
            isStreaming = false
            stopStreamRtp()
        }

        cameraManager.stop()
        isOnPreview = false

        videoEncoder!!.stop()

    }

    init {
        context = surfaceView.context
        cameraManager = EpsonApiManager(surfaceView, this)
        cameraManager.registerHeadsetStateCallback()
        init()
    }


    /**
     * Retries to connect with the given delay. You can pass an optional backupUrl
     * if you'd like to connect to your backup server instead of the original one.
     * Given backupUrl replaces the original one.
     */
    fun reTry(delay: Long, reason: String?, backupUrl: String? = null): Boolean {
        val result = shouldRetry(reason)
        if (result) {
            requestKeyFrame()
            reConnect(delay, backupUrl)
        }
        return result
    }

    protected abstract fun shouldRetry(reason: String?): Boolean
    abstract fun setReTries(reTries: Int)
    protected abstract fun reConnect(delay: Long, backupUrl: String?)

    //cache control
    abstract fun hasCongestion(): Boolean

    @Throws(RuntimeException::class)
    abstract fun resizeCache(newSize: Int)

    abstract fun getCacheSize(): Int

    abstract fun getSentAudioFrames(): Long

    abstract fun getSentVideoFrames(): Long

    abstract fun getDroppedAudioFrames(): Long

    abstract fun getDroppedVideoFrames(): Long

    abstract fun resetSentAudioFrames()
    abstract fun resetSentVideoFrames()
    abstract fun resetDroppedAudioFrames()
    abstract fun resetDroppedVideoFrames()

    /**
     * Set video bitrate of H264 in bits per second while stream.
     *
     * @param bitrate H264 in bits per second.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    fun setVideoBitrateOnFly(bitrate: Int) {
        videoEncoder!!.setVideoBitrateOnFly(bitrate)
    }

    /**
     * Set limit FPS while stream. This will be override when you call to prepareVideo method. This
     * could produce a change in iFrameInterval.
     *
     * @param fps frames per second
     */
    fun setLimitFPSOnFly(fps: Int) {
        videoEncoder!!.fps = fps
    }

    /**
     * Get record state.
     *
     * @return true if recording, false if not recoding.
     */

    protected abstract fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo)
    override fun getAacData(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {

        if (isStreaming) getAacDataRtp(aacBuffer, info)
    }

    protected abstract fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?)
    override fun onSpsPpsVps(sps: ByteBuffer, pps: ByteBuffer, vps: ByteBuffer?) {
        onSpsPpsVpsRtp(sps.duplicate(), pps.duplicate(), if (vps != null) vps.duplicate() else null)
    }

    protected abstract fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    override fun getVideoData(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        fpsListener.calculateFps()

        if (isStreaming) getH264DataRtp(h264Buffer, info)
    }

    override fun inputPCMData(frame: Frame) {
        //  audioEncoder!!.inputPCMData(frame)
    }

    override fun inputYUVData(frame: Frame) {
        videoEncoder!!.inputYUVData(frame)
    }

    override fun onVideoFormat(mediaFormat: MediaFormat) {
        //   recordController!!.setVideoFormat(mediaFormat, !audioInitialized)
    }

    override fun onAudioFormat(mediaFormat: MediaFormat) {
        //   recordController!!.setAudioFormat(mediaFormat)
    }

    abstract fun setLogs(enable: Boolean)
    abstract fun setCheckServerAlive(enable: Boolean)

    companion object {
        private const val TAG = "Camera1Base"
    }
}