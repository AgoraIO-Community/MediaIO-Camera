package io.agora.capture.framework.gles;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

import io.agora.capture.framework.gles.core.Drawable2d;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.gles.core.Program;

public class ProgramWatermark extends Program {

    // Simple vertex shader, used for all programs.
    private static final String VERTEX_SHADER =
      "uniform mat4 uMVPMatrix;\n" +
        "uniform mat4 uTexMatrix;\n" +
        "attribute vec4 aPosition;\n" +
        "attribute vec4 aTextureCoord;\n" +
        "varying vec2 vTextureCoord;\n" +
        "void main() {\n" +
        "    gl_Position = uMVPMatrix * aPosition;\n" +
        "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
        "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
      "precision mediump float;\n" +
        "varying vec2 vTextureCoord;\n" +
        "uniform sampler2D sTexture;\n" +
        "void main() {\n" +
        "    gl_FragColor = vec4(texture2D(sTexture, vTextureCoord).rgb, 1.0);\n" + "gl_FragColor.a *= " + 1.0f +";\n" +
        "}\n";

    private int muMVPMatrixLoc;
    private int muTexMatrixLoc;
    private int maPositionLoc;
    private int maTextureCoordLoc;

    private int[] mFramebuffer = new int[1];
    private int[] mTargetTexture = new int[1];

    private int mWidth;
    private int mHeight;
    private int waterTextureId;

    public ProgramWatermark() {
        this(1f);
    }

    public ProgramWatermark(float watermarkAlpha) {
//        super(CAMERA_INPUT_VERTEX_SHADER, FRAGMENT_SHADER_2D);
        super(VERTEX_SHADER, "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform sampler2D sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = vec4(texture2D(sTexture, vTextureCoord).rgb, 1.0);\n" + "gl_FragColor.a *= " + watermarkAlpha +";\n" +
                "}\n");
        //        Log.d("lq", "ProgramWatermark: "+watermarkAlpha);
    }

    public void update(int width, int height) {
        if (mWidth != width || mHeight != height) {
            mWidth = width;
            mHeight = height;
            deleteFramebuffer();
            bindFramebuffer(mWidth, mHeight);
        }
    }

    private void bindFramebuffer(int width, int height) {
        GLES20.glGenFramebuffers(1, mFramebuffer, 0);
        GlUtil.checkGlError("glGenFramebuffers");

        GLES20.glGenTextures(1, mTargetTexture, 0);
        GlUtil.checkGlError("glGenTextures");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTargetTexture[0]);
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

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
          GLES20.GL_COLOR_ATTACHMENT0,
          GLES20.GL_TEXTURE_2D,
          mTargetTexture[0], 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }


    private void deleteFramebuffer() {
        if (mTargetTexture[0] != 0) {
            GLES20.glDeleteTextures(1, mTargetTexture, 0);
            mTargetTexture[0] = 0;
        }

        if (mFramebuffer[0] != 0) {
            GLES20.glDeleteFramebuffers(1, mFramebuffer, 0);
            mFramebuffer[0] = 0;
        }
    }

    public void destroyProgram() {
        deleteFramebuffer();
        release();
    }

    @Override
    protected Drawable2d getDrawable2d() {
        return new Drawable2dFull();
    }

    @Override
    protected void getLocations() {
        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition");
        GlUtil.checkLocation(maPositionLoc, "aPosition");
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord");
        GlUtil.checkLocation(maTextureCoordLoc, "aTextureCoord");
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix");
        GlUtil.checkLocation(muMVPMatrixLoc, "uMVPMatrix");
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix");
        GlUtil.checkLocation(muTexMatrixLoc, "uTexMatrix");
    }

    @Override
    public void drawFrame(int textureId, float[] texMatrix, float[] mvpMatrix) {
        // drawRotateFrame(textureId, texMatrix, mvpMatrix);
    }

    public int drawRotateFrame(int textureId, float[] texMatrix, float[] mvpMatrix) {
        GlUtil.checkGlError("preProcess");
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");


        //-----------
        //sourceId
        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(maPositionLoc, Drawable2d.COORDS_PER_VERTEX,
          GLES20.GL_FLOAT, false, Drawable2d.VERTEXTURE_STRIDE, mDrawable2d.vertexArray());
        GlUtil.checkGlError("glVertexAttribPointer");

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(maTextureCoordLoc, 2,
          GLES20.GL_FLOAT, false, Drawable2d.TEXTURE_COORD_STRIDE, mDrawable2d.texCoordArray());
        GlUtil.checkGlError("glVertexAttribPointer");


        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer[0]);
        GlUtil.checkGlError("glBindFramebuffer");
        //GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mDrawable2d.vertexCount());
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);


        //-----------
        //waterId
        if (waterTextureId != 0) {
            drawWatermark();
        }
        //-----------

        GLES20.glDisableVertexAttribArray(maPositionLoc);
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(0);

        return mTargetTexture[0];
    }


    private void drawWatermark() {
        //GLES20.glViewport(0, 0, 10 * mWidth, mHeight);
        //bitmap
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waterTextureId);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }


    /**
     * Tested on Smartisan Nut Pro 2s | Android O       => OK
     *           Google Pixel 3       | Android S       => NO Image Just Shadow
     *           Pixel 3 Emulator     | Android S       => NO Image Just Shadow
     */
    public void createWaterTexture1(@Nullable Bitmap bitmap) {
        if (bitmap == null) return;

        final int[] textureObjectIds = new int[1];
        GLES20.glGenTextures(1, textureObjectIds, 0);
        if(textureObjectIds[0] == 0){
            Log.e("lq","Could not generate a new OpenGL texture object!");
            return;
        }
        //告诉OpenGL后面纹理调用应该是应用于哪个纹理对象
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureObjectIds[0]);
        //设置缩小的时候（GL_TEXTURE_MIN_FILTER）使用mipmap三线程过滤
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        //设置放大的时候（GL_TEXTURE_MAG_FILTER）使用双线程过滤
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        //Android设备y坐标是反向的，正常图显示到设备上是水平颠倒的，解决方案就是设置纹理包装，纹理T坐标（y）设置镜面重复
        //ball读取纹理的时候 t范围坐标取正常值+1
        //GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_MIRRORED_REPEAT);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        // bitmap.recycle();
        //快速生成mipmap贴图
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        //解除纹理操作的绑定
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        waterTextureId  = textureObjectIds[0];
    }

    /**
     * Tested on Smartisan Nut Pro 2s | Android O       => OK
     *           Google Pixel 3       | Android S       => OK
     *           Pixel 3 Emulator     | Android S       => OK
     */
    public void createWaterTexture2(@NonNull Bitmap bitmap) {
        Bitmap desiredBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        int[] textureIds = new int[1];
        // 创建纹理
        GLES20.glGenTextures(1,textureIds,0);
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
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        waterTextureId = textureIds[0];
    }

    public int getWatermarkId() {
        return waterTextureId;
    }

    public void disableWatermarkId() {
        waterTextureId = 0;
    }
}
