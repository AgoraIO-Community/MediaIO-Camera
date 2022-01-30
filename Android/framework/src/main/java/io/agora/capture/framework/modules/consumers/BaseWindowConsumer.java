package io.agora.capture.framework.modules.consumers;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import androidx.annotation.Nullable;

import io.agora.capture.framework.gles.ProgramWatermark;
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

    private float[] mCameraMVPMatrix;

    @Nullable
    private Bitmap watermarkBitmap;
    private int watermarkIdThisTime;
    private float watermarkAlpha = 1.0f;

    BaseWindowConsumer(VideoModule videoModule) {
        this.videoModule = videoModule;
        mCameraMVPMatrix = new float[16];
        Matrix.setIdentityM(mCameraMVPMatrix, 0);
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
     *
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
        // GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);

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

        setupWatermark2(surfaceWidth, surfaceHeight, mvp, frame, context);
//        setupWatermark3(surfaceWidth, surfaceHeight, mvp, frame, context);

        if (drawingEglSurface != null) {
            eglCore.swapBuffers(drawingEglSurface);
        }

        // GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void setMirrorMode(int mode) {
        mirrorMode = mode;
    }

    /**
     * Support watermark, can not change watermark alpha channel
     */
    private void setupWatermark2(int surfaceWidth, int surfaceHeight, float[] mvp, VideoCaptureFrame frame, VideoChannel.ChannelContext context) {

        if (null != watermarkBitmap && !watermarkBitmap.isRecycled()) {
            ProgramWatermark desiredWatermarkProgram = context.getProgramWatermark();
            if (desiredWatermarkProgram != null) {
                if (desiredWatermarkProgram.getWatermarkId() == 0 || watermarkIdThisTime == 0) {
                    GLES20.glEnable(GLES20.GL_BLEND);
                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                    context.getProgramWatermark().createWaterTexture2(watermarkBitmap);
                }
            }
        } else {
            ProgramWatermark desiredWatermarkProgram = context.getProgramWatermark();
            if (desiredWatermarkProgram != null) {
                context.getProgramWatermark().disableWatermarkId();
                GLES20.glDisable(GLES20.GL_BLEND);
            }
        }

        if (frame.format.getTexFormat() == GLES20.GL_TEXTURE_2D) {
            GLES20.glViewport(0, 0, frame.format.getWidth(), frame.format.getHeight());
            // draw camera and water to fbo
            context.getProgramWatermark().update(frame.format.getWidth(), frame.format.getHeight());
            frame.textureId = context.getProgramWatermark().drawRotateFrame(frame.textureId, frame.textureTransform, mCameraMVPMatrix);

            // draw fbo.getTextureId
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            context.getProgram2D().drawFrame(frame.textureId, frame.textureTransform, mvp);
        } else if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            context.getProgramOES().drawFrame(frame.textureId, frame.textureTransform, mvp);
        }
    }

    /**
     * Support change watermark alpha channel dynamically
     */
    private void setupWatermark3(int surfaceWidth, int surfaceHeight, float[] mvp, VideoCaptureFrame frame, VideoChannel.ChannelContext context) {
        ProgramWatermark desiredWatermarkProgram = context.getProgramWatermark();
        // Watermark is enabled
        if (null != watermarkBitmap && !watermarkBitmap.isRecycled()) {
            // But program is null || Bitmap changed || Alpha changed <==> initialize a new ProgramWatermark
            if (desiredWatermarkProgram == null || watermarkIdThisTime == 0) {
                desiredWatermarkProgram = new ProgramWatermark(this.watermarkAlpha);
                context.setProgramWatermark(desiredWatermarkProgram);
            }
            // Now it is definitely not null, check the textureId, exist? <==> No-OP ,else create a new one
            if (desiredWatermarkProgram.getWatermarkId() == 0) {
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                desiredWatermarkProgram.createWaterTexture2(watermarkBitmap);

                this.watermarkIdThisTime = desiredWatermarkProgram.getWatermarkId();
            }
        } else { // Watermark id disabled
            if (desiredWatermarkProgram != null) {
                desiredWatermarkProgram.destroyProgram();
                desiredWatermarkProgram = null;
                context.setProgramWatermark(null);
                GLES20.glDisable(GLES20.GL_BLEND);
            }
        }

        if (frame.format.getTexFormat() == GLES20.GL_TEXTURE_2D) {
            if (desiredWatermarkProgram != null) {
                GLES20.glViewport(0, 0, frame.format.getWidth(), frame.format.getHeight());
                desiredWatermarkProgram.update(frame.format.getWidth(), frame.format.getHeight());
                frame.textureId = desiredWatermarkProgram.drawRotateFrame(frame.textureId, frame.textureTransform, mCameraMVPMatrix);
            }
            // draw fbo.getTextureId
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);

            context.getProgram2D().drawFrame(frame.textureId, frame.textureTransform, mvp);
        } else if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
            context.getProgramOES().drawFrame(frame.textureId, frame.textureTransform, mvp);
        }
    }

    /**
     * create a watermarkBitmap through param bitmap
     *
     * @param bitmap from VideoChanel.
     * @param alpha  apply this to bitmap alpha channel
     */
    @Override
    public void setWatermark(@Nullable Bitmap bitmap, float alpha) {
        // Recycle unused bitmap every time
        if (this.watermarkBitmap != null) this.watermarkBitmap.recycle();

        this.watermarkIdThisTime = 0;

        if (null != bitmap && alpha != 0f) {
            android.graphics.Matrix mx = new android.graphics.Matrix();
            mx.setRotate(180);
            mx.setScale(1, -1);
            this.watermarkBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mx, true);
            Log.d("lq", "setWatermark: " + bitmap + "," + this.watermarkBitmap);
            this.watermarkAlpha = alpha;
        } else {
            this.watermarkBitmap = null;
        }
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
