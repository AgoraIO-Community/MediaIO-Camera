package io.agora.capture.framework.util;

import android.opengl.Matrix;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import io.agora.capture.framework.gles.core.GlUtil;

public class MatrixOperator {
    private final float[] mMatrix;

    private float scaleX = 1.0f, scaleY = 1.0f, scaleRadio = 1.0f;
    private float translateX = 0.0f, translateY = 0.0f;
    private int displayWidth, displayHeight, realWidth, realHeight;
    private @ScaleType int scaleType;
    private boolean mirror = false;


    public MatrixOperator(@ScaleType int scaleType){
        this.mMatrix = new float[16];
        Matrix.setIdentityM(mMatrix, 0);
        this.scaleType = scaleType;
    }

    public float[] getMatrix() {
        return mMatrix;
    }

    public void update(int displayWidth,int displayHeight,int realWidth,int realHeight){
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.realHeight = realHeight;
        this.realWidth = realWidth;
        updateScaleType();
    }

    public void setScaleType(@ScaleType int scaleType) {
        this.scaleType = scaleType;
        updateScaleType();
    }

    private void updateScaleType() {
        if (scaleType == ScaleType.CenterCrop) {
            float scale = displayWidth * realHeight * 1.0f / displayHeight / realWidth;
            if (scale != 1) {
                scaleX = scale > 1 ? 1F : (1F / scale);
                scaleY = scale > 1 ? scale : 1F;
            }
        } else if (scaleType == ScaleType.FitCenter) {
            float scale = displayWidth * realHeight * 1.0f / displayHeight / realWidth;
            if (scale != 1) {
                scaleX = scale < 1 ? 1F : (1F / scale);
                scaleY = scale < 1 ? scale : 1F;
            }
        } else {
            scaleX = 1f;
            scaleY = 1f;
        }
        updateTransform();
    }

    public void setScaleRadio(float scaleRadio){
        this.scaleRadio = scaleRadio;
        updateTransform();
    }

    private void updateTransform() {
        float[] tmp = new float[16];
        Matrix.setIdentityM(tmp, 0);
        float transRadio = 1.0f;
        if(mirror){
            transRadio = -1f * transRadio;
            Matrix.setRotateM(tmp, 0, 180, 0, 1f, 0);
        }
        float _scaleX = scaleX * scaleRadio;
        float _scaleY = scaleY * scaleRadio;
        Matrix.scaleM(tmp, 0, _scaleX, _scaleY, 1.f);

        float _tranX = translateX * transRadio * ((1 - _scaleX) / _scaleX);
        float _tranY = translateY * transRadio * ((1 - _scaleY) / _scaleY);
        Matrix.translateM(tmp, 0, _tranX, _tranY, 0.f);
        Matrix.multiplyMM(mMatrix, 0, tmp, 0, GlUtil.IDENTITY_MATRIX, 0);
    }

    public void translateX(float translate){
        translateX = translate;
        updateTransform();
    }

    public void translateY(float translate){
        translateY = translate;
        updateTransform();
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public void reset() {
        translateX = translateY = 0.0f;
        scaleX = scaleY = scaleRadio = 1.0f;
        mirror = false;
        Matrix.setIdentityM(mMatrix, 0);
    }

    public boolean isMirror() {
        return mirror;
    }

    public float getScaleRadio() {
        return scaleRadio;
    }

    public int getScaleType() {
        return scaleType;
    }

    public float getTranslateX() {
        return translateX;
    }

    public float getTranslateY() {
        return translateY;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ScaleType.CenterCrop, ScaleType.FitCenter, ScaleType.FitXY})
    public @interface ScaleType{
        int CenterCrop = 1;
        int FitCenter = 2;
        int FitXY = 3;
    }
}
