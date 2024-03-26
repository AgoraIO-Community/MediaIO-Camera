package io.agora.capture.framework.modules.processors;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import io.agora.capture.framework.gles.MatrixOperator;
import io.agora.capture.framework.gles.MatrixOperatorGL;
import io.agora.capture.framework.gles.MatrixOperatorGraphics;
import io.agora.capture.framework.gles.ProgramTexture2d;
import io.agora.capture.framework.gles.ProgramTextureOES;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.video.camera.VideoCaptureFrame;

public class WatermarkProcessor {

    private final ProgramTexture2d programTexture2d;
    private final ProgramTextureOES programTextureOES;


    private int outFboId;
    private int outTexId;
    private int outWidth, outHeight;
    private volatile boolean resetFBO;

    private final MatrixOperator originTexMvp = new MatrixOperatorGL(MatrixOperator.ScaleType.CenterCrop);
    private final MatrixOperator originTexTrans = new MatrixOperatorGraphics(MatrixOperator.ScaleType.CenterCrop);

    private final Object watermarkLock = new Object();
    private Bitmap watermarkBitmap;
    private boolean watermarkBitmapChange = false;
    private int watermarkTexId;
    private float watermarkAlpha = 1.0f;
    private MatrixOperator watermarkMvp = new MatrixOperatorGL(MatrixOperator.ScaleType.CenterCrop);


    public WatermarkProcessor() {
        programTexture2d = new ProgramTexture2d();
        programTextureOES = new ProgramTextureOES();
    }

    public VideoCaptureFrame process(VideoCaptureFrame frame) {
        if (watermarkBitmap == null) {
            releaseOutFBO();
            releaseWatermarkTexId();
            return frame;
        }

        if (outWidth == 0 || outHeight == 0) {
            return frame;
        }

        // create watermark texture
        synchronized (watermarkLock){
            if(watermarkBitmap != null){
                // draw watermark texture
                if (watermarkBitmapChange) {
                    createWatermarkTexId(watermarkBitmap);
                    watermarkBitmapChange = false;
                }
                watermarkMvp.update(outWidth, outHeight, watermarkBitmap.getWidth(), watermarkBitmap.getHeight());
            }else{
                return frame;
            }
        }


        initOutFBO();

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFboId);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glViewport(0, 0, outWidth, outHeight);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_STENCIL_BUFFER_BIT);

        // draw origin frame texture
        int originTexWidth = frame.format.getWidth();
        int originTexHeight = frame.format.getHeight();

        if (frame.rotation == 90 || frame.rotation == 270) {
            originTexWidth = frame.format.getHeight();
            originTexHeight = frame.format.getWidth();
        }
        originTexMvp.update(outWidth, outHeight, originTexWidth, originTexHeight);
        originTexMvp.setFlipH(frame.mirrored);

        originTexTrans.setTransformMatrix(frame.textureTransform);
        originTexTrans.setPreFlipH(frame.mirrored);
        originTexTrans.setRotation(frame.rotation);

        if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
            programTextureOES.drawFrame(frame.textureId, originTexTrans.getFinalMatrix(), originTexMvp.getFinalMatrix());
        } else {
            programTexture2d.drawFrame(frame.textureId, originTexTrans.getFinalMatrix(), originTexMvp.getFinalMatrix());
        }

        // draw watermark texture
        programTexture2d.drawFrame(watermarkTexId, GlUtil.IDENTITY_MATRIX, watermarkMvp.getFinalMatrix(), watermarkAlpha);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);


        frame.image = null;
        frame.format.setPixelFormat(ImageFormat.UNKNOWN);
        frame.textureId = outTexId;
        frame.rotation = 0;
        frame.mirrored = false;

        frame.format.setWidth(outWidth);
        frame.format.setHeight(outHeight);
        frame.format.setTexFormat(GLES20.GL_TEXTURE_2D);
        frame.textureTransform = GlUtil.IDENTITY_MATRIX;
        return frame;
    }

    public void setOriginTexScaleType(@MatrixOperator.ScaleType int scaleType) {
        originTexMvp.setScaleType(scaleType);
    }

    public void setOutSize(int outWidth, int outHeight) {
        if (this.outWidth != outWidth || this.outHeight != outHeight) {
            this.outWidth = outWidth;
            this.outHeight = outHeight;
            resetFBO = true;
        }
    }

    public MatrixOperator setWatermarkBitmap(Bitmap bitmap, int width, int height, @MatrixOperator.ScaleType int scaleType) {

        if (watermarkBitmap != null) {
            synchronized (watermarkLock){
                if(watermarkBitmap != null){
                    watermarkBitmap.recycle();
                    watermarkBitmap = null;
                }
            }
        }

        if (null != bitmap) {
            if (width == 0) {
                width = bitmap.getWidth();
            }
            if (height == 0) {
                height = bitmap.getHeight();
            }

            Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            Rect dst = new Rect(0, 0, width, height);
            Rect src = new Rect(0, 0, copy.getWidth(), copy.getHeight());

            if (scaleType == MatrixOperator.ScaleType.CenterCrop) {
                float scale = width * copy.getHeight() * 1.0f / height / copy.getWidth();
                if (scale != 1) {
                    float scaleX = scale > 1 ? 1F : (1F / scale);
                    float scaleY = scale > 1 ? scale : 1F;


                    src.left = (int) (copy.getWidth() * (1 - 1.0f /scaleX)/ 2 + 0.5f);
                    src.right = (int) (copy.getWidth() - src.left);
                    src.top = (int) (copy.getHeight() * (1- 1.0f / scaleY) / 2 + 0.5f) ;
                    src.bottom = (int) (copy.getHeight() - src.top);
                }
            } else if (scaleType == MatrixOperator.ScaleType.FitCenter) {
                float scale = width * copy.getHeight() * 1.0f / height / copy.getWidth();
                if (scale != 1) {
                    float scaleX = scale < 1 ? 1F : (1F / scale);
                    float scaleY = scale < 1 ? scale : 1F;

                    dst.left = 0;
                    dst.right = (int)(width * scaleX + 0.5f);
                    dst.top = 0;
                    dst.bottom = (int)(height * scaleY + 0.5f);
                }
            }

            Bitmap scaleBitmap = Bitmap.createBitmap(dst.width(), dst.height(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(scaleBitmap);
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            canvas.drawBitmap(copy, src, dst, paint);

            Matrix rotateMatrix = new Matrix();
            rotateMatrix.postScale(1f, -1f);
            Bitmap rotatedBitmap = Bitmap.createBitmap(scaleBitmap, 0, 0, scaleBitmap.getWidth(), scaleBitmap.getHeight(), rotateMatrix, false);

            copy.recycle();
            scaleBitmap.recycle();

            synchronized (watermarkLock){
                watermarkMvp = new MatrixOperatorGL(scaleType);
                watermarkBitmap = rotatedBitmap;
                watermarkBitmapChange = true;
            }
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
            synchronized (watermarkLock){
                if(watermarkBitmap != null){
                    watermarkBitmap.recycle();
                    watermarkBitmap = null;
                }
            }
        }
    }

    private void initOutFBO() {
        if (!resetFBO) {
            return;
        }

        releaseOutFBO();
        resetFBO = false;

        int[] framebuffers = new int[1];
        GLES20.glGenFramebuffers(1, framebuffers, 0);
        GlUtil.checkGlError("glGenFramebuffers");
        LogUtil.d(this, "EGL >> bindFramebuffer glGenFramebuffers framebuffer=" + framebuffers[0]);
        outFboId = framebuffers[0];

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GlUtil.checkGlError("glGenTextures");
        LogUtil.d(this, "EGL >> bindFramebuffer glGenTextures texture=" + textures[0]);
        outTexId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, outWidth, outHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, outFboId);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                outTexId, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void releaseOutFBO() {
        if (outTexId != 0) {
            GLES20.glDeleteTextures(1, new int[]{outTexId}, 0);
            LogUtil.d(this, "EGL >> releaseFBO glDeleteTextures texture=" + outTexId);
            outTexId = 0;
        }

        if (outFboId != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{outFboId}, 0);
            LogUtil.d(this, "EGL >> releaseFBO glDeleteFramebuffers framebuffer=" + outFboId);
            outFboId = 0;
        }

        resetFBO = true;
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
