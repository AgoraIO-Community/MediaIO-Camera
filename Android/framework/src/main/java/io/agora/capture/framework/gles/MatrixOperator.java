package io.agora.capture.framework.gles;

import android.opengl.Matrix;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

public abstract class MatrixOperator {

    protected float scaleRadio = 1.0f, translateX = 0.0f, translateY = 0.0f, rotation = 0;
    protected int scaleType, displayWidth = 0, displayHeight = 0, realWidth = 0, realHeight = 0;
    protected boolean flipH = false, flipV = false, preFlipH = false, preFlipV = false;
    protected final float[] transformMatrix = new float[16];

    public MatrixOperator(@ScaleType int scaleType){
        this.scaleType = scaleType;
        Matrix.setIdentityM(transformMatrix, 0);
    }

    public void update(int displayWidth, int displayHeight, int realWidth, int realHeight) {
        if (this.displayWidth == displayWidth && this.displayHeight == displayHeight && this.realWidth == realWidth && this.realHeight == realHeight) {
            return;
        }
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;
        this.realHeight = realHeight;
        this.realWidth = realWidth;
        updateTransform();
    }

    public void setScaleType(@MatrixOperator.ScaleType int scaleType) {
        if (this.scaleType == scaleType) {
            return;
        }
        this.scaleType = scaleType;
        updateTransform();
    }

    public void setScaleRadio(float scaleRadio) {
        if (this.scaleRadio == scaleRadio) {
            return;
        }
        this.scaleRadio = scaleRadio;
        updateTransform();
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

    public void setPreFlipH(boolean preFlipH) {
        if(this.preFlipH == preFlipH){
            return;
        }
        this.preFlipH = preFlipH;
        updateTransform();
    }

    public void setPreFlipV(boolean preFlipV) {
        if(this.preFlipV == preFlipV){
            return;
        }
        this.preFlipV = preFlipV;
        updateTransform();
    }

    public void setTransformMatrix(float[] matrix){
        if(Arrays.equals(transformMatrix, matrix)){
            return;
        }
        System.arraycopy(matrix, 0, transformMatrix, 0, matrix.length);
        updateTransform();
    }

    public float[] getTransformMatrix() {
        return Arrays.copyOf(transformMatrix, transformMatrix.length);
    }

    public void reset() {
        scaleRadio = 1.0f;
        translateX = translateY = rotation = 0;
        scaleType = displayWidth = displayHeight = realWidth = realHeight = 0;
        flipH = flipV = false;
        Matrix.setIdentityM(transformMatrix, 0);
    }

    public boolean isPreFlipH() {
        return preFlipH;
    }

    public boolean isPreFlipV() {
        return preFlipV;
    }

    public boolean isFlipH(){
        return flipH;
    }

    public boolean isFlipV(){
        return flipV;
    }

    public float getScaleRadio(){
        return scaleRadio;
    }

    public int getScaleType(){
        return scaleType;
    }

    public float getTranslateX(){
        return translateX;
    }

    public float getTranslateY(){
        return translateY;
    }

    public float getRotation(){
        return rotation;
    }


    public abstract float[] getFinalMatrix();

    protected abstract void updateTransform();

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ScaleType.CenterCrop, ScaleType.FitCenter, ScaleType.FitXY})
    public @interface ScaleType {
        int CenterCrop = 1;
        int FitCenter = 2;
        int FitXY = 3;
    }
}
