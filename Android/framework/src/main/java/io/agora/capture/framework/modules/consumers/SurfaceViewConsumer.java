package io.agora.capture.framework.modules.consumers;

import android.opengl.GLES20;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.lang.ref.WeakReference;

import io.agora.capture.framework.gles.MatrixOperator;
import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.video.camera.VideoCaptureFrame;
import io.agora.capture.video.camera.VideoModule;

public class SurfaceViewConsumer extends BaseWindowConsumer implements SurfaceHolder.Callback {
    private static final String TAG = SurfaceViewConsumer.class.getSimpleName();

    private final WeakReference<SurfaceView> mSurfaceView;
    private int mWidth;
    private int mHeight;

    public SurfaceViewConsumer(SurfaceView surfaceView, @MatrixOperator.ScaleType int scaleType) {
        super(VideoModule.instance(), true, scaleType);
        mSurfaceView = new WeakReference<>(surfaceView);
    }

    @Override
    public void onConsumeFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context) {
        if (mSurfaceView == null || mSurfaceView.get() == null) {
            return;
        }

        super.onConsumeFrame(frame, context);
    }

    @Override
    public Object getDrawingTarget() {
        if (mSurfaceView != null && mSurfaceView.get() != null) {
            SurfaceHolder holder = mSurfaceView.get().getHolder();
            if (holder != null) {
                Surface surface = holder.getSurface();
                if (surface != null && surface.isValid()) {
                    return surface;
                }
            }
        }
        return null;
    }

    @Override
    public int onMeasuredWidth() {
        return mSurfaceView != null && mSurfaceView.get() != null ? mSurfaceView.get().getMeasuredWidth() : mWidth;
    }

    @Override
    public int onMeasuredHeight() {
        return mSurfaceView != null && mSurfaceView.get() != null ? mSurfaceView.get().getMeasuredHeight() : mHeight;
    }

    @Override
    public void surfaceCreated(final SurfaceHolder holder) {
        LogUtil.i(TAG, "surfaceCreated");
        surfaceDestroyed = false;
        needResetSurface = true;
        connectChannel(CHANNEL_ID);
    }

    /**
     * Called when the SurfaceView has been attached to
     * the window.
     */
    public void setDefault() {
        needResetSurface = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LogUtil.i(TAG, "surfaceChanged:" + width + "x" + height);
        GLES20.glViewport(0, 0, width, height);
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LogUtil.i(TAG, "surfaceDestroyed");
        disconnectChannel(CHANNEL_ID);
        surfaceDestroyed = true;
    }
}
