package io.agora.capture.video.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;

import io.agora.capture.framework.gles.MatrixOperator;
import io.agora.capture.framework.modules.channels.ChannelManager;
import io.agora.capture.framework.modules.consumers.CaptureFrameWrapConsumer;
import io.agora.capture.framework.modules.consumers.ICaptureFrameConsumer;
import io.agora.capture.framework.modules.consumers.IVideoConsumer;
import io.agora.capture.framework.modules.consumers.SurfaceViewConsumer;
import io.agora.capture.framework.modules.consumers.TextureViewConsumer;
import io.agora.capture.framework.modules.processors.IPreprocessor;
import io.agora.capture.framework.modules.processors.WatermarkProcessor;
import io.agora.capture.framework.util.LogUtil;

/**
 * VideoManager is designed as the up-level encapsulation of
 * video module. It opens a series of APIs to the outside world,
 * and makes camera behavior much easier by containing some of
 * the camera logical procedures.
 * It can be seen as a particular utility class to control the
 * camera video channel, which is defined as one implementation
 * of the video channel designed in the framework.
 * Maintaining a single CameraManager instance globally is enough.
 * Although it is ok to create an instance every time it needs
 * control over cameras, such behavior is unlikely to bring benefits.
 */
public class CameraVideoManager {
    // CameraManager only controls camera channel
    private static final int CHANNEL_ID = ChannelManager.ChannelID.CAMERA;

    private static final int DEFAULT_FACING = Constant.CAMERA_FACING_FRONT;

    private CameraVideoChannel mCameraChannel;

    private volatile boolean available = true;
    private Exception releaseException = null;

    private volatile static CameraVideoManager sInstance;
    private CameraVideoManager(){}

    public static CameraVideoManager create(Context context){
        return create(context, null, DEFAULT_FACING);
    }

    public static CameraVideoManager create(Context context, IPreprocessor preprocessor){
        return create(context, preprocessor, DEFAULT_FACING);
    }

    public static CameraVideoManager create(Context context, IPreprocessor preprocessor, int facing){
        return create(context, preprocessor, facing, false, false);
    }

    public static CameraVideoManager create(Context context, IPreprocessor preprocessor, int facing, boolean enableDebug, boolean useCamera2){
        if (sInstance == null) {
            synchronized (CameraVideoManager.class) {
                if (sInstance == null) {
                    sInstance = new CameraVideoManager();
                    LogUtil.setDEBUG(enableDebug);
                    sInstance.init(context, preprocessor, facing, useCamera2);
                } else {
                    sInstance.init(context, preprocessor, facing, useCamera2);
                }
            }
        } else {
            sInstance.init(context, preprocessor, facing, useCamera2);
        }
        return sInstance;
    }

    public static CameraVideoManager getInstance(){
        synchronized (CameraVideoManager.class){
            if(sInstance == null){
                throw new IllegalStateException("The instance of cameraVideoManager has not been created yet, please call create(...) firstly.");
            }
        }
        return sInstance;
    }

    /**
     * Initializes the camera video channel, loads all the
     * resources needed during camera capturing.
     * @param context Android context
     * @param preprocessor usually is the implementation
     *                     of a third-party beautification library
     * @param facing must be one of Constant.CAMERA_FACING_FRONT
     *               and Constant.CAMERA_FACING_BACK
     * @see io.agora.capture.video.camera.Constant
     */
    private void init(Context context, IPreprocessor preprocessor, int facing, boolean useCamera2) {
        VideoModule videoModule = VideoModule.instance();
        if (!videoModule.hasInitialized()) {
            videoModule.init(context);
        }

        // The preprocessor must be set before
        // the video channel starts
        videoModule.setPreprocessor(CHANNEL_ID, preprocessor);
        mCameraChannel = (CameraVideoChannel)
                videoModule.getVideoChannel(CHANNEL_ID);
        mCameraChannel.setFacing(facing);
        mCameraChannel.setUseCamera2(useCamera2);
        videoModule.startChannel(CHANNEL_ID);
        videoModule.enableOffscreenMode(CHANNEL_ID, true);

    }

    public void setPreprocessor(IPreprocessor preprocessor) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setPreprocessor(preprocessor);
        }
    }

    public void enablePreprocessor(boolean enabled) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.enablePreProcess(enabled);
        }
    }

    /**
     * Set a TextureView to be the camera local preview
     * without an identifier
     * @param textureView the local preview surface
     */
    public void setLocalPreview(TextureView textureView) {
        setLocalPreview(textureView, null);
    }

    public void setLocalPreview(TextureView textureView, String id) {
        setLocalPreview(textureView, MatrixOperator.ScaleType.CenterCrop, id);
    }

    /**
     * Set a TextureView as the local preview with an identifier.
     * The local preview can be attached or not attached
     * to the window system.
     * The preview surface will replace any other consumers
     * with the same drawing target. Otherwise, it will
     * replace any consumers that has the same identifier.
     * @param textureView the local preview surface
     * @param id identifier for the preview, nullable.
     */
    public void setLocalPreview(TextureView textureView, @MatrixOperator.ScaleType int scaleType, String id) {
        checkAvailable();
        TextureViewConsumer consumer = new TextureViewConsumer(scaleType);
        consumer.setId(id);
        textureView.setSurfaceTextureListener(consumer);

        if (textureView.isAttachedToWindow()) {
            consumer.connectChannel(CHANNEL_ID);
            if (textureView.getSurfaceTexture() != null) {
                consumer.setDefault(textureView.getSurfaceTexture(),
                        textureView.getMeasuredWidth(),
                        textureView.getMeasuredHeight());
            }
        }
    }

    /**
     * Set a SurfaceView to be the camera local preview
     * without an identifier
     * @param surfaceView the local preview surface
     */
    public void setLocalPreview(SurfaceView surfaceView) {
        setLocalPreview(surfaceView, null);
    }

    public void setLocalPreview(SurfaceView surfaceView, String id){
        setLocalPreview(surfaceView, MatrixOperator.ScaleType.CenterCrop, id);
    }

    /**
     * Set a SurfaceView as the local preview with an identifier.
     * The local preview can be attached or not attached
     * to the window system.
     * The preview surface will replace any other consumers
     * with the same drawing target. Otherwise, it will
     * replace any consumers that has the same identifier.
     * @param surfaceView the local preview surface
     * @param id identifier for the preview, nullable.
     */
    public void setLocalPreview(SurfaceView surfaceView, @MatrixOperator.ScaleType int scaleType, String id) {
        checkAvailable();
        SurfaceViewConsumer consumer =
                new SurfaceViewConsumer(surfaceView, scaleType);
        consumer.setId(id);
        surfaceView.getHolder().addCallback(consumer);

        if (surfaceView.isAttachedToWindow()) {
            consumer.setDefault();
            consumer.connectChannel(CHANNEL_ID);
        }
    }

    public void setFacing(int facing) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setFacing(facing);
        }
    }

    public void setPictureSize(int width, int height) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setPictureSize(width, height);
        }
    }

    /**
     * Attach an off-screen consumer to the camera channel.
     * The consumer does not render on-screen frames.
     * The on-screen and off-screen consumers can be
     * attached and detached dynamically without affecting
     * the others.
     * @param consumer the consumer implementation
     */
    public void attachOffScreenConsumer(ICaptureFrameConsumer consumer) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.connectConsumer(new CaptureFrameWrapConsumer(consumer), IVideoConsumer.TYPE_OFF_SCREEN);
        }
    }

    public void detachOffScreenConsumer(ICaptureFrameConsumer consumer) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.disconnectConsumer(new CaptureFrameWrapConsumer(consumer));
        }
    }

    /**
     * Set the desired frame rate of the capture.
     * If not set, the default frame rate is 24
     * @param frameRate
     */
    public void setFrameRate(int frameRate) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setIdealFrameRate(frameRate);
        }
    }

    public void startCapture() {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.startCapture();
        }
    }

    public void stopCapture() {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.stopCapture();
        }
    }

    public void switchCamera() {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.switchCamera();
        }
    }

    // zoom api
    public boolean isZoomSupported(){
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.isZoomSupported();
        }
        return false;
    }
    public int setZoom(float zoomValue){
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.setZoom(zoomValue);
        }
        return -4;
    }
    public float getMaxZoom(){
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.getMaxZoom();
        }
        return -4;
    }

    // torch api
    public boolean isTorchSupported(){
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.isTorchSupported();
        }
        return false;
    }
    public int setTorchMode(boolean isOn){
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.setTorchMode(isOn);
        }
        return -4;
    }

    public void setExposureCompensation(int value) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setExposureCompensation(value);
        }
    }

    public int getExposureCompensation() {
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.getExposureCompensation();
        }
        return 0;
    }

    public int getMinExposureCompensation() {
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.getMinExposureCompensation();
        }
        return 0;
    }

    public int getMaxExposureCompensation() {
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.getMaxExposureCompensation();
        }
        return 0;
    }

    public IPreprocessor getPreprocessor() {
        checkAvailable();
        if (mCameraChannel != null) {
            return VideoModule.instance().getPreprocessor(CHANNEL_ID);
        }

        return null;
    }

    public void setLocalPreviewMirror(int mode) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setOnScreenConsumerMirror(mode);
        }
    }

    @Nullable
    public Bitmap getWaterMark() {
        checkAvailable();
        if (mCameraChannel != null) {
            WatermarkProcessor watermarkProcessor = mCameraChannel.getWatermarkProcessor();
            if(watermarkProcessor != null){
                return watermarkProcessor.getWatermarkBitmap();
            }

        }
        return null;
    }

    public MatrixOperator setWaterMark(@Nullable Bitmap waterMarkBitmap, WatermarkConfig config) {
        checkAvailable();
        if (mCameraChannel != null) {
            WatermarkProcessor watermarkProcessor = mCameraChannel.getWatermarkProcessor();
            if(watermarkProcessor != null){
                watermarkProcessor.setOutSize(config.outWidth, config.outHeight);
                watermarkProcessor.setOriginTexScaleType(config.originTexScaleType);
                return watermarkProcessor.setWatermarkBitmap(waterMarkBitmap, config.watermarkWidth, config.watermarkHeight, config.watermarkScaleType);
            }
        }
        return null;
    }

    public void setWaterMarkAlpha(float waterMarkAlpha) {
        checkAvailable();
        if (mCameraChannel != null) {
            WatermarkProcessor watermarkProcessor = mCameraChannel.getWatermarkProcessor();
            if(watermarkProcessor != null){
                watermarkProcessor.setWatermarkAlpha(waterMarkAlpha);
            }
        }
    }

    public float getWatermarkAlpha(){
        checkAvailable();
        if (mCameraChannel != null) {
            WatermarkProcessor watermarkProcessor = mCameraChannel.getWatermarkProcessor();
            if(watermarkProcessor != null){
                return watermarkProcessor.getWatermarkAlpha();
            }
        }
        return 1f;
    }

    public void cleanWatermark(){
        checkAvailable();
        if (mCameraChannel != null) {
            WatermarkProcessor watermarkProcessor = mCameraChannel.getWatermarkProcessor();
            if(watermarkProcessor != null){
                watermarkProcessor.cleanWatermark();
            }
        }
    }

    public void setCameraStateListener(VideoCapture.VideoCaptureStateListener listener) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setCameraStateListener(listener);
        }
    }

    public void setFrameRateSelector(VideoCapture.FrameRateRangeSelector selector) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setFrameRateSelector(selector);
        }
    }

    public void enableExactFrameRange(boolean enable){
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.enableExactFrameRange(enable);
        }
    }

    private void checkAvailable(){
        if(!available){
            throw new IllegalStateException("The instance has been released, please create another one.", releaseException);
        }
    }

    public static void release(){
        if(sInstance != null){
            synchronized (CameraVideoManager.class){
                if(sInstance != null){
                    sInstance.stopCapture();
                    sInstance.available = false;
                    VideoModule.instance().stopChannel(CHANNEL_ID);
                    try {
                        throw new RuntimeException("CameraVideoManager release");
                    } catch (Exception e) {
                        sInstance.releaseException = e;
                    }
                    sInstance = null;
                }
            }
        }
    }

}
