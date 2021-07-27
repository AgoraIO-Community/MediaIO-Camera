package io.agora.capture.framework.gles;

import android.opengl.GLES20;

import java.nio.FloatBuffer;

import io.agora.capture.framework.gles.core.Drawable2d;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.gles.core.Program;

public class RotateProgram2D extends Program {
    // Simple vertex shader, used for all programs.
    private static final String CAMERA_INPUT_VERTEX_SHADER =
        "uniform mat4 uTexMatrix;\n" +
        "uniform mat4 uMVPMatrix;\n" +
        "attribute vec4 position;\n" +
        "attribute vec4 inputTextureCoordinate;\n" +
        "varying vec2 textureCoordinate;\n" +
        "void main() {\n" +
        "    gl_Position = uMVPMatrix * position;\n" +
        "    textureCoordinate = (uTexMatrix * inputTextureCoordinate).xy;\n" +
        "}\n";

    // Simple fragment shader for use with "normal" 2D textures.
    private static final String FRAGMENT_SHADER_2D =
        "precision mediump float;\n" +
        "varying vec2 textureCoordinate;\n" +
        "uniform sampler2D inputImageTexture;\n" +
        "void main() {\n" +
        "    gl_FragColor = vec4(texture2D(inputImageTexture, textureCoordinate).rgb, 1.0);\n" +
        "}\n";

    // Four vertices for the four corners of the triangle
    private final static String POSITION_COORDINATE = "position";

    // Corresponding four texture coordinates for the four vertices
    private final static String TEXTURE_COORDINATE = "inputTextureCoordinate";

    private final static String TEXTURE_UNIFORM = "inputImageTexture";

    private final static String TEXUTRE_MATRIX = "uTexMatrix";

    private final static String MVP_MATRIX = "uMVPMatrix";

    // locations in the shader mProgram
    private int mVertexCoordLocation;
    private int mTexCoordLocation;
    private int mTexSampleLocation;
    private int mTexMatrixLocation;
    private int mMvpMatrixLocation;

    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTextureBuffer;

    private int[] mFramebuffer = new int[1];
    private int[] mTargetTexture = new int[1];

    private int mWidth;
    private int mHeight;

    public RotateProgram2D() {
        super(CAMERA_INPUT_VERTEX_SHADER, FRAGMENT_SHADER_2D);
        mVertexBuffer = getDrawable2d().vertexArray();
        mTextureBuffer = getDrawable2d().texCoordArray();
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
        mVertexCoordLocation = GLES20.glGetAttribLocation(mProgramHandle, POSITION_COORDINATE);
        GlUtil.checkGlError("glGetAttribLocation:" + mVertexCoordLocation);

        mTexCoordLocation = GLES20.glGetAttribLocation(mProgramHandle, TEXTURE_COORDINATE);
        GlUtil.checkGlError("glGetAttribLocation:" + mTexCoordLocation);

        mTexSampleLocation = GLES20.glGetUniformLocation(mProgramHandle, TEXTURE_UNIFORM);
        GlUtil.checkGlError("glGetUniformLocation:" + mTexSampleLocation);

        mTexMatrixLocation = GLES20.glGetUniformLocation(mProgramHandle, TEXUTRE_MATRIX);
        GlUtil.checkGlError("glGetUniformLocation:" + mTexMatrixLocation);

        mMvpMatrixLocation = GLES20.glGetUniformLocation(mProgramHandle, MVP_MATRIX);
        GlUtil.checkGlError("glGetUniformLocation:" + mMvpMatrixLocation);
    }

    @Override
    public void drawFrame(int textureId, float[] texMatrix, float[] mvpMatrix) {

    }

    public int drawRotateFrame(int textureId, float[] texMatrix, float[] mvpMatrix) {
        GlUtil.checkGlError("preProcess");
        GLES20.glUseProgram(mProgramHandle);
        GlUtil.checkGlError("glUseProgram");

        mVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mVertexCoordLocation, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);
        GLES20.glEnableVertexAttribArray(mVertexCoordLocation);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        mTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mTexCoordLocation, 2, GLES20.GL_FLOAT, false, 0, mTextureBuffer);
        GLES20.glEnableVertexAttribArray(mTexCoordLocation);
        GlUtil.checkGlError("glEnableVertexAttribArray");

        GLES20.glUniformMatrix4fv(mMvpMatrixLocation, 1, false, mvpMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        GLES20.glUniformMatrix4fv(mTexMatrixLocation, 1, false, texMatrix, 0);
        GlUtil.checkGlError("glUniformMatrix4fv");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(mTexSampleLocation, 0);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer[0]);
        GlUtil.checkGlError("glBindFramebuffer");
        GLES20.glViewport(0, 0, mWidth, mHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mVertexCoordLocation);
        GLES20.glDisableVertexAttribArray(mTexCoordLocation);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glUseProgram(0);

        return mTargetTexture[0];
    }
}
