package io.agora.capture.video.camera;

import android.content.Context;
import android.view.SurfaceView;
import android.view.TextureView;

import io.agora.capture.framework.modules.channels.ChannelManager;
import io.agora.capture.framework.modules.consumers.IVideoConsumer;
import io.agora.capture.framework.modules.consumers.SurfaceViewConsumer;
import io.agora.capture.framework.modules.consumers.TextureViewConsumer;
import io.agora.capture.framework.modules.processors.IPreprocessor;
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
    public CameraVideoManager(Context context, IPreprocessor preprocessor, int facing) {
        init(context, preprocessor, facing);
    }

    public CameraVideoManager(Context context, IPreprocessor preprocessor) {
        init(context, preprocessor, DEFAULT_FACING);
    }

    public CameraVideoManager(Context context, IPreprocessor preprocessor, boolean enableDebug) {
        LogUtil.setDEBUG(enableDebug);
        init(context, preprocessor, DEFAULT_FACING);
    }

    public CameraVideoManager(Context context) {
        init(context, null, DEFAULT_FACING);
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
        if (mCameraChannel != null) {
            mCameraChannel.setFacing(facing);
        }
    }

    public void setPictureSize(int width, int height) {
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
        if (mCameraChannel != null) {
            mCameraChannel.connectConsumer(consumer, IVideoConsumer.TYPE_OFF_SCREEN);
        }
    }

    public void detachOffScreenConsumer(IVideoConsumer consumer) {
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
        if (mCameraChannel != null) {
            mCameraChannel.setIdealFrameRate(frameRate);
        }
    }

    public void startCapture() {
        if (mCameraChannel != null) {
            mCameraChannel.startCapture();
        }
    }

    public void stopCapture() {
        if (mCameraChannel != null) {
            mCameraChannel.stopCapture();
        }
    }

    public void switchCamera() {
        if (mCameraChannel != null) {
            mCameraChannel.switchCamera();
        }
    }

    public IPreprocessor getPreprocessor() {
        if (mCameraChannel != null) {
            return VideoModule.instance().getPreprocessor(CHANNEL_ID);
        }

        return null;
    }

    public void setLocalPreviewMirror(int mode) {
        if (mCameraChannel != null) {
            mCameraChannel.setOnScreenConsumerMirror(mode);
        }
    }

    public void setCameraStateListener(VideoCapture.VideoCaptureStateListener listener) {
        if (mCameraChannel != null) {
            mCameraChannel.setCameraStateListener(listener);
        }
    }

}
