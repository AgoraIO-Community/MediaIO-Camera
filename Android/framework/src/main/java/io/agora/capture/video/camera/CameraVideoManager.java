package io.agora.capture.video.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.Nullable;

import io.agora.capture.framework.modules.channels.ChannelManager;
import io.agora.capture.framework.modules.consumers.IVideoConsumer;
import io.agora.capture.framework.modules.consumers.SurfaceViewConsumer;
import io.agora.capture.framework.modules.consumers.TextureViewConsumer;
import io.agora.capture.framework.modules.processors.IPreprocessor;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.framework.util.MatrixOperator;

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
        return create(context, preprocessor, facing, false);
    }

    public static CameraVideoManager create(Context context, IPreprocessor preprocessor, int facing, boolean enableDebug){
        if (sInstance == null) {
            synchronized (CameraVideoManager.class) {
                if (sInstance == null) {
                    sInstance = new CameraVideoManager();
                    LogUtil.setDEBUG(enableDebug);
                    sInstance.init(context, preprocessor, facing);
                } else {
                    throw new IllegalStateException("The instance of cameraVideoManager has been created, please call getInstance() instead.");
                }
            }
        } else {
            throw new IllegalStateException("The instance of cameraVideoManager has been created, please call getInstance() instead.");
        }
        return sInstance;
    }

    public static CameraVideoManager getInstance(){
        if(sInstance == null){
            throw new IllegalStateException("The instance of cameraVideoManager has not been created yet, please call create(...) firstly.");
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
    private void init(Context context, IPreprocessor preprocessor, int facing) {
        VideoModule videoModule = VideoModule.instance();
        if (!videoModule.hasInitialized()) {
            videoModule.init(context);
        }

        // The preprocessor must be set before
        // the video channel starts
        videoModule.setPreprocessor(CHANNEL_ID, preprocessor);
        videoModule.startChannel(CHANNEL_ID);
        videoModule.enableOffscreenMode(CHANNEL_ID, true);
        mCameraChannel = (CameraVideoChannel)
                videoModule.getVideoChannel(CHANNEL_ID);
        mCameraChannel.setFacing(facing);
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
        checkAvailable();
        setLocalPreview(textureView, null);
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
    public void setLocalPreview(TextureView textureView, String id) {
        checkAvailable();
        TextureViewConsumer consumer = new TextureViewConsumer();
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
        checkAvailable();
        setLocalPreview(surfaceView, null);
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
    public void setLocalPreview(SurfaceView surfaceView, String id) {
        checkAvailable();
        SurfaceViewConsumer consumer =
                new SurfaceViewConsumer(surfaceView);
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
    public void attachOffScreenConsumer(IVideoConsumer consumer) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.connectConsumer(consumer, IVideoConsumer.TYPE_OFF_SCREEN);
        }
    }

    public void detachOffScreenConsumer(IVideoConsumer consumer) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.disconnectConsumer(consumer);
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

    public MatrixOperator setWaterMark(@Nullable Bitmap waterMarkBitmap) {
        checkAvailable();
        return setWaterMark(waterMarkBitmap, MatrixOperator.ScaleType.CenterCrop);
    }


    @Nullable
    public Bitmap getWaterMark() {
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.getWatermarkBitmap();
        }
        return null;
    }

    public MatrixOperator setWaterMark(@Nullable Bitmap waterMarkBitmap, @MatrixOperator.ScaleType int scaleType) {
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.setWatermark(waterMarkBitmap, scaleType);
        }
        return null;
    }

    public void setWaterMarkAlpha(float waterMarkAlpha) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setWatermarkAlpha(waterMarkAlpha);
        }
    }

    public float getWatermarkAlpha(){
        checkAvailable();
        if (mCameraChannel != null) {
            return mCameraChannel.getWatermarkAlpha();
        }
        return 1f;
    }

    public void setCameraStateListener(VideoCapture.VideoCaptureStateListener listener) {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.setCameraStateListener(listener);
        }
    }

    public void updatePreviewOrientation() {
        checkAvailable();
        if (mCameraChannel != null) {
            mCameraChannel.updatePreviewOrientation();
        }
    }

    public void checkAvailable(){
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
