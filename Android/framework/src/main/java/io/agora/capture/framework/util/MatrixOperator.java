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
    private @ScaleType
    int scaleType;
    private boolean flipH = false, flipV = false;
    private float rotation = 0;


    public MatrixOperator(@ScaleType int scaleType) {
        this.mMatrix = new float[16];
        Matrix.setIdentityM(mMatrix, 0);
        this.scaleType = scaleType;
    }

    public float[] getMatrix() {
        return mMatrix;
    }

    public void update(int displayWidth, int displayHeight, int realWidth, int realHeight) {
        if (this.displayWidth == displayWidth && this.displayHeight == displayHeight && this.realWidth == realWidth && this.realHeight == realHeight) {
            return;
        }
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.realHeight = realHeight;
        this.realWidth = realWidth;
        updateScaleType();
    }

    public void setScaleType(@ScaleType int scaleType) {
        if (this.scaleType == scaleType) {
            return;
        }
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

    public void setScaleRadio(float scaleRadio) {
        if (this.scaleRadio == scaleRadio) {
            return;
        }
        this.scaleRadio = scaleRadio;
        updateTransform();
    }

    private void updateTransform() {
        float[] tmp = new float[16];
        Matrix.setIdentityM(tmp, 0);

        float _scaleX = scaleX * scaleRadio;
        float _scaleY = scaleY * scaleRadio;
        boolean _flipH = flipH;
        boolean _flipV = flipV;
        float _translateX = translateX;
        float _translateY = translateY;
        if(rotation % 180 != 0){
            _flipH = flipV;
            _flipV = flipH;
        }

        if (_scaleX != 1 || _scaleY != 1) {
            Matrix.scaleM(tmp, 0, tmp, 0, _scaleX, _scaleY, 1.f);
        }

        if (_translateX != 0 || _translateY != 0) {
            Matrix.translateM(tmp, 0, tmp, 0, _translateX * (1- _scaleX) / _scaleX, _translateY* (1- _scaleY) / _scaleY, 0);
        }


        if (_flipH) {
            Matrix.rotateM(tmp, 0, tmp, 0, 180, 0, 1f, 0);
        }
        if (_flipV) {
            Matrix.rotateM(tmp, 0, tmp, 0, 180, 1f, 0f, 0);
        }

        float _rotation = rotation;
        if (_rotation != 0) {
            if(_flipH != _flipV){
                _rotation *= -1;
            }
            Matrix.rotateM(tmp, 0, tmp, 0, _rotation, 0, 0, 1);
        }

        Matrix.multiplyMM(mMatrix, 0, tmp, 0, GlUtil.IDENTITY_MATRIX, 0);
    }

    public void translateX(float translate) {
        if (this.translateX == translate) {
            return;
        }
        translateX = translate;
        updateTransform();
    }

    public void translateY(float translate) {
        if (this.translateY == translate) {
            return;
        }
        translateY = translate;
        updateTransform();
    }

    /**
     * @param rotation 正数顺时针，负数逆时针
     */
    public void setRotation(float rotation) {
        if (this.rotation == rotation) {
            return;
        }
        this.rotation = rotation;
        updateTransform();
    }

    public void setFlipH(boolean flipH) {
        if (this.flipH == flipH) {
            return;
        }
        this.flipH = flipH;
        updateTransform();
    }

    public void setFlipV(boolean flipV) {
        if (this.flipV == flipV) {
            return;
        }
        this.flipV = flipV;
        updateTransform();
    }

    public void reset() {
        translateX = translateY = 0.0f;
        scaleX = scaleY = scaleRadio = 1.0f;
        flipH = false;
        Matrix.setIdentityM(mMatrix, 0);
    }

    public boolean isFlipH() {
        return flipH;
    }

    public boolean isFlipV() {
        return flipV;
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

    public float getRotation() {
        return rotation;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ScaleType.CenterCrop, ScaleType.FitCenter, ScaleType.FitXY})
    public @interface ScaleType {
        int CenterCrop = 1;
        int FitCenter = 2;
        int FitXY = 3;
    }
}
