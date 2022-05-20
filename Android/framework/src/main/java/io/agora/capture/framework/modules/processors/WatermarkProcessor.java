package io.agora.capture.framework.modules.processors;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import io.agora.capture.framework.gles.ProgramTexture2d;
import io.agora.capture.framework.gles.ProgramTextureOES;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.framework.util.MatrixOperator;
import io.agora.capture.video.camera.VideoCaptureFrame;

public class WatermarkProcessor {

    private int mFboId;
    private int mTexId;
    private int mWidth, mHeight;

    private Bitmap watermarkBitmap;
    private boolean watermarkBitmapChange = false, watermarkRectChange = false;
    private Rect watermarkRect = new Rect();
    private int watermarkTexId;
    private float watermarkAlpha = 1.0f;
    private MatrixOperator watermarkMvp;
    private int lostFrameRotation = 0;

    private final ProgramTexture2d programTexture2d;
    private final ProgramTextureOES programTextureOES;

    public WatermarkProcessor() {
        programTexture2d = new ProgramTexture2d();
        programTextureOES = new ProgramTextureOES();
    }

    public VideoCaptureFrame process(VideoCaptureFrame frame) {
        if (watermarkBitmap != null) {
            int desiredWidth = frame.format.getWidth();
            int desiredHeight = frame.format.getHeight();

            if (frame.rotation == 90 || frame.rotation == 270) {
                desiredWidth = frame.format.getHeight();
                desiredHeight = frame.format.getWidth();
            }

            boolean envChange = mayResetFBO(desiredWidth, desiredHeight) || lostFrameRotation != frame.rotation;
            boolean texChange = watermarkBitmapChange || watermarkRectChange;

            lostFrameRotation = frame.rotation;
            if (watermarkBitmapChange) {
                createWatermarkTexId(watermarkBitmap);
                watermarkBitmapChange = false;
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            GLES20.glViewport(0, 0, desiredWidth, desiredHeight);
            GLES20.glClearColor(0, 0, 0, 1.0f);

            // draw camera texture
            if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
                programTextureOES.drawFrame(frame.textureId, frame.textureTransform);
            } else {
                programTexture2d.drawFrame(frame.textureId, frame.textureTransform);
            }

            if(texChange || envChange){
                watermarkMvp.update(desiredWidth, desiredHeight, watermarkRect.width(), watermarkRect.height());
                watermarkRectChange = false;
            }

            // draw watermark texture
            programTexture2d.drawFrame(watermarkTexId, GlUtil.IDENTITY_MATRIX, watermarkMvp.getMatrix(), watermarkAlpha);

            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            frame.textureId = mTexId;
            frame.rotation = 0;
            frame.format.setWidth(desiredWidth);
            frame.format.setHeight(desiredHeight);
            frame.format.setTexFormat(GLES20.GL_TEXTURE_2D);
            frame.textureTransform = GlUtil.IDENTITY_MATRIX;
        }


        return frame;
    }

    public MatrixOperator setWatermarkBitmap(Bitmap bitmap, @MatrixOperator.ScaleType int scaleType) {
        if (watermarkBitmap != null) {
            watermarkBitmap.recycle();
        }

        if (null != bitmap) {
            android.graphics.Matrix mx = new android.graphics.Matrix();
            mx.setRotate(180);
            mx.setScale(1, -1);
            this.watermarkBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mx, true);
            Log.d("lq", "setWatermark: " + bitmap + "," + this.watermarkBitmap);
            watermarkRect = new Rect(0, 0, watermarkBitmap.getWidth(), watermarkBitmap.getHeight());
            watermarkMvp = new MatrixOperator(scaleType);
            watermarkBitmapChange = true;
        }
        return watermarkMvp;
    }

    public Bitmap getWatermarkBitmap() {
        return watermarkBitmap;
    }

    public void setWatermarkAlpha(float alpha) {
        watermarkAlpha = alpha;
    }

    public float getWatermarkAlpha() {
        return watermarkAlpha;
    }

    public void cleanWatermark() {
        if (watermarkBitmap != null) {
            watermarkBitmap.recycle();
            watermarkBitmap = null;
        }
        watermarkMvp = null;
        releaseFBO();
        releaseWatermarkTexId();
    }

    private boolean mayResetFBO(int width, int height) {
        if (mWidth == width && mHeight == height && mFboId != 0) {
            return false;
        }
        releaseFBO();
        mWidth = width;
        mHeight = height;

        int[] framebuffers = new int[1];
        GLES20.glGenFramebuffers(1, framebuffers, 0);
        GlUtil.checkGlError("glGenFramebuffers");
        LogUtil.d(this, "EGL >> bindFramebuffer glGenFramebuffers framebuffer=" + framebuffers[0]);
        mFboId = framebuffers[0];

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");
        LogUtil.d(this, "EGL >> bindFramebuffer glGenTextures texture=" + textures[0]);
        mTexId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                mTexId, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return true;
    }

    private void releaseFBO() {
        if (mTexId != 0) {
            GLES20.glDeleteTextures(1, new int[]{mTexId}, 0);
            LogUtil.d(this, "EGL >> releaseFBO glDeleteTextures texture=" + mTexId);
            mTexId = 0;
        }

        if (mFboId != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{mFboId}, 0);
            LogUtil.d(this, "EGL >> releaseFBO glDeleteFramebuffers framebuffer=" + mFboId);
            mFboId = 0;
        }
    }

    private void createWatermarkTexId(@NonNull Bitmap bitmap) {
        releaseWatermarkTexId();

        Bitmap desiredBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int[] textureIds = new int[1];
        // 创建纹理
        GLES20.glGenTextures(1, textureIds, 0);
        LogUtil.d(this, "EGL >> createWatermarkTexId glGenTextures texture=" + textureIds[0]);
        // 绑定纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);
        //环绕（超出纹理坐标范围）  （s==x t==y GL_REPEAT 重复）
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        //过滤（纹理像素映射到坐标点）  （缩小、放大：GL_LINEAR线性）
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        ByteBuffer bitmapBuffer = ByteBuffer.allocate(desiredBitmap.getHeight() * desiredBitmap.getWidth() * 4);//RGBA
        desiredBitmap.copyPixelsToBuffer(bitmapBuffer);
        //将bitmapBuffer位置移动到初始位置
        bitmapBuffer.flip();

        //设置内存大小绑定内存地址
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, desiredBitmap.getWidth(), desiredBitmap.getHeight(),
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer);

        //解绑纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        watermarkTexId = textureIds[0];
    }

    private void releaseWatermarkTexId() {
        if (watermarkTexId != 0) {
            GLES20.glDeleteTextures(1, new int[]{watermarkTexId}, 0);
            LogUtil.d(this, "EGL >> releaseWatermarkTexId glDeleteTextures texture=" + watermarkTexId);
            watermarkTexId = 0;
        }
    }

}
