package io.agora.capture.framework.modules.consumers;

import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;

import io.agora.capture.framework.gles.core.EglCore;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.modules.channels.ChannelManager;
import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.video.camera.Constant;
import io.agora.capture.video.camera.VideoCaptureFrame;
import io.agora.capture.video.camera.VideoModule;

public abstract class BaseWindowConsumer implements IVideoConsumer {
    static final int CHANNEL_ID = ChannelManager.ChannelID.CAMERA;

    VideoModule videoModule;
    VideoChannel videoChannel;
    int mirrorMode;

    private String mId;

    private EGLSurface drawingEglSurface;
    volatile boolean needResetSurface = true;
    volatile boolean surfaceDestroyed;
    private float[] mMVPMatrix = new float[16];
    private float[] mMirrorMatrix = new float[16];
    private volatile boolean mViewportInit;

    BaseWindowConsumer(VideoModule videoModule) {
        this.videoModule = videoModule;
    }

    @Override
    public void connectChannel(int channelId) {
        videoChannel = videoModule.connectConsumer(this, channelId, IVideoConsumer.TYPE_ON_SCREEN);
    }

    @Override
    public void disconnectChannel(int channelId) {
        videoModule.disconnectConsumer(this, channelId);
    }

    /**
     * Id is used to identify different consumer instances
     * which can be seen as the same.
     * A consumer is attached to a channel and will replace
     * any consumers of the same type (on screen or off screen)
     * in the consumer list.
     * Consumers of the same type with null or empty tags will be
     * considered as different consumers.
     * @param mId tag of the consumer
     */
    public void setId(String mId) {
        this.mId = mId;
    }

    @Override
    public String getId() {
        return this.mId;
    }

    @Override
    public void onConsumeFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context) {
        drawFrame(frame, context);
    }

    private void drawFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context) {
        if (surfaceDestroyed) {
            return;
        }

        EglCore eglCore = context.getEglCore();
        if (needResetSurface) {
            if (drawingEglSurface != null && drawingEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.releaseSurface(drawingEglSurface);
                eglCore.makeNothingCurrent();
                drawingEglSurface = null;
            }

            Object surface = getDrawingTarget();
            if (surface != null) {
                Object target = getDrawingTarget();
                if (target != null) {
                    drawingEglSurface = eglCore.createWindowSurface(target);
                    needResetSurface = false;
                }
            }
        }

        boolean surfaceAvailable = true;
        if (drawingEglSurface != null && !eglCore.isCurrent(drawingEglSurface)) {
            try {
                eglCore.makeCurrent(drawingEglSurface);
            } catch (Exception e) {
                surfaceAvailable = false;
            }
        }

        if (!surfaceAvailable) return;

        int surfaceWidth = onMeasuredWidth();
        int surfaceHeight = onMeasuredHeight();
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);

        if (!mViewportInit) {
            mMVPMatrix = GlUtil.changeMVPMatrix(
                    GlUtil.IDENTITY_MATRIX,
                    surfaceWidth, surfaceHeight,
                    frame.format.getWidth(),
                    frame.format.getHeight());
            mViewportInit = true;
        }

        float[] mvp = mMVPMatrix;
        boolean mirrored = mirrorMode == Constant.MIRROR_MODE_ENABLED;
        if (mirrorMode != Constant.MIRROR_MODE_AUTO && frame.mirrored != mirrored) {
            Matrix.rotateM(mMirrorMatrix, 0, mMVPMatrix, 0, 180, 0, 1f, 0);
            mvp = mMirrorMatrix;
        }

        if (frame.format.getTexFormat() == GLES20.GL_TEXTURE_2D) {
            context.getProgram2D().drawFrame(
                    frame.textureId, frame.textureTransform, mvp);
        } else if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
            context.getProgramOES().drawFrame(
                    frame.textureId, frame.textureTransform, mvp);
        }

        if (drawingEglSurface != null) {
            eglCore.swapBuffers(drawingEglSurface);
        }
    }

    public void setMirrorMode(int mode) {
        mirrorMode = mode;
    }

    protected void resetViewport() {
        mViewportInit = false;
    }

    @Override
    public void recycle() {
        if (videoChannel != null && drawingEglSurface != null
                && drawingEglSurface != EGL14.EGL_NO_SURFACE) {
            EglCore eglCore = videoChannel.getChannelContext().getEglCore();
            eglCore.releaseSurface(drawingEglSurface);
            drawingEglSurface = null;
            needResetSurface = true;
        }
    }
}
