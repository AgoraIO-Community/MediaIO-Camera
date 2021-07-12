package io.agora.capture.video.camera;

import android.content.Context;

import io.agora.capture.framework.modules.channels.ChannelManager;
import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.framework.util.LogUtil;

public class CameraVideoChannel extends VideoChannel {
    private static final String TAG = CameraVideoChannel.class.getSimpleName();

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int FRAME_RATE = 24;
    private static final int FACING = Constant.CAMERA_FACING_FRONT;

    private VideoCapture mVideoCapture;
    private volatile boolean mCapturedStarted;

    private int mWidth = WIDTH;
    private int mHeight = HEIGHT;
    private int mFrameRate = FRAME_RATE;
    private int mFacing = FACING;

    public CameraVideoChannel(Context context, int id) {
        super(context, id);
    }

    @Override
    protected void onChannelContextCreated() {
        mVideoCapture = VideoCaptureFactory.createVideoCapture(getChannelContext().getContext());
    }

    /**
     * Set the current camera facing
     * @param facing must be one of Constant.CAMERA_FACING_FRONT
     *               or Constant.CAMERA_FACING_BACK
     * Will not take effect until next startCapture or
     * switchCamera succeeds.
     */
    public void setFacing(int facing) {
        mFacing = facing;
    }

    /**
     * Set the ideal capture image size in pixels.
     * Note the size is only a reference to find the
     * most closest size that the camera hardware supports.
     * The size is usually horizontal, that is, the width
     * is larger than the height, or the picture will be
     * cropped more than desired.
     * The default picture size is 1920 * 1080
     * Will not take effect until next startCapture or
     * switchCamera succeeds.
     */
    public void setPictureSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public void setIdealFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public void startCapture() {
        if (isRunning()) {
            getHandler().post(() -> {
                if (!mCapturedStarted) {
                    mVideoCapture.connectChannel(ChannelManager.ChannelID.CAMERA);
                    mVideoCapture.setSharedContext(getChannelContext().getEglCore().getEGLContext());
                    mVideoCapture.allocate(mWidth, mHeight, mFrameRate, mFacing);
                    mVideoCapture.startCaptureMaybeAsync(false);
                    mCapturedStarted = true;
                }
            });
        }
    }

    public void switchCamera() {
        if (isRunning()) {
            getHandler().postAtFrontOfQueue(() -> {
                if (mCapturedStarted) {
                    mVideoCapture.deallocate();
                    switchCameraFacing();
                    mVideoCapture.allocate(mWidth, mHeight, mFrameRate, mFacing);
                    mVideoCapture.startCaptureMaybeAsync(false);
                }
            });
        }
    }

    private void switchCameraFacing() {
        if (mFacing == Constant.CAMERA_FACING_FRONT) {
            mFacing = Constant.CAMERA_FACING_BACK;
        } else if (mFacing == Constant.CAMERA_FACING_BACK) {
            mFacing = Constant.CAMERA_FACING_FRONT;
        }
    }

    void stopCapture() {
        if (isRunning()) {
            getHandler().postAtFrontOfQueue(() -> {
                if (mCapturedStarted) {
                    mVideoCapture.deallocate();
                    mCapturedStarted = false;
                }
            });
        }
    }

    public boolean hasCaptureStarted() {
        return mCapturedStarted;
    }

    void setCameraStateListener(VideoCapture.VideoCaptureStateListener listener) {
        if (isRunning()) {
            getHandler().postAtFrontOfQueue(() -> mVideoCapture.setCaptureStateListener(listener));
        }
    }


    public void updatePreviewOrientation() {
        if (isRunning()) {
            getHandler().postAtFrontOfQueue(() -> {
                if (mCapturedStarted) {
                    LogUtil.e(TAG, "video isUpsideDown: 22" );
                    mVideoCapture.updatePreviewOrientation();
                }
            });
        }
    }

}
