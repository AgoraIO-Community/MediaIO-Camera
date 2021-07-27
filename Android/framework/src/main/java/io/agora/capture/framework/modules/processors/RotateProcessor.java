package io.agora.capture.framework.modules.processors;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Surface;
import android.view.WindowManager;

import io.agora.capture.framework.gles.RotateProgram2D;
import io.agora.capture.framework.gles.RotateProgramOES;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.video.camera.VideoCaptureFrame;

public class RotateProcessor {
    private RotateProgram2D mRotateProgram2D;
    private RotateProgramOES mRotateProgramOES;

    private WindowManager mWindowManager;
    private int mCurrentSurfaceRotation = -1;
    private float[] mRotateMVPMatrix;

    public void init(VideoChannel.ChannelContext context) {
        mRotateProgram2D = new RotateProgram2D();
        mRotateProgramOES = new RotateProgramOES();
        mWindowManager = (WindowManager) context.getContext()
                .getSystemService(Context.WINDOW_SERVICE);

        mRotateMVPMatrix = new float[16];
        Matrix.setIdentityM(mRotateMVPMatrix, 0);
    }

    public VideoCaptureFrame process(VideoCaptureFrame frame,
                                        VideoChannel.ChannelContext context) {
        int desiredWidth = frame.format.getWidth();
        int desiredHeight = frame.format.getHeight();

        if (frame.rotation == 90 || frame.rotation == 270) {
            desiredWidth = frame.format.getHeight();
            desiredHeight = frame.format.getWidth();
        }

        int surfaceRotation = getSurfaceRotation();
        if (surfaceRotation == 90 || surfaceRotation == 270) {
            int temp = desiredWidth;
            desiredWidth = desiredHeight;
            desiredHeight = temp;
        }

        if (mCurrentSurfaceRotation != surfaceRotation) {
            mCurrentSurfaceRotation = surfaceRotation;
            Matrix.setRotateM(mRotateMVPMatrix, 0,
                    mCurrentSurfaceRotation, 0, 0, 1);
        }

        if (frame.format.getTexFormat() == GLES20.GL_TEXTURE_2D) {
            mRotateProgram2D.update(desiredWidth, desiredHeight);
            frame.textureId = mRotateProgram2D.drawRotateFrame(
                    frame.textureId, frame.textureTransform, mRotateMVPMatrix);
        } else if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
            mRotateProgramOES.update(desiredWidth, desiredHeight);
            frame.textureId = mRotateProgramOES.drawRotateFrame(
                    frame.textureId, frame.textureTransform, mRotateMVPMatrix);
        }

        frame.rotation = 0;
        frame.format.setWidth(desiredWidth);
        frame.format.setHeight(desiredHeight);
        frame.format.setTexFormat(GLES20.GL_TEXTURE_2D);
        frame.textureTransform = GlUtil.IDENTITY_MATRIX;

        return frame;
    }

    public void release(VideoChannel.ChannelContext context) {
        if (mRotateProgram2D != null) mRotateProgram2D.destroyProgram();
        if (mRotateProgramOES != null) mRotateProgramOES.destroyProgram();
    }

    private int getSurfaceRotation() {
        int rotation = mWindowManager != null ?
                mWindowManager.getDefaultDisplay().getRotation()
                : Surface.ROTATION_0;

        switch (rotation) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            case Surface.ROTATION_0:
            default: return 0;
        }
    }
}
