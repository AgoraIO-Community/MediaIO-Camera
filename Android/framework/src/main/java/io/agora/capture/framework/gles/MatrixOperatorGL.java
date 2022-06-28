package io.agora.capture.framework.gles;

import android.graphics.PointF;
import android.opengl.Matrix;
import android.util.Size;

import java.util.Arrays;

public class MatrixOperatorGL extends MatrixOperator{
    private final float[] mMatrix;

    public MatrixOperatorGL(@MatrixOperator.ScaleType int scaleType) {
        super(scaleType);
        this.mMatrix = new float[16];
        Matrix.setIdentityM(mMatrix, 0);
    }

    @Override
    public void reset() {
        super.reset();
        Matrix.setIdentityM(mMatrix, 0);
    }

    @Override
    public float[] getFinalMatrix() {
        return Arrays.copyOf(mMatrix, mMatrix.length);
    }

    @Override
    protected void updateTransform() {
        float scaleX = 1.0f, scaleY = 1.0f;
        PointF outScale = new PointF();
        int ret = getScaleByType(new Size(displayWidth, displayHeight),
                new Size(realWidth, realHeight),
                scaleType, outScale);
        if(ret == 0){
            scaleX = outScale.x;
            scaleY = outScale.y;
        }

        float[] tmp = new float[16];
        Matrix.setIdentityM(tmp, 0);

        float _scaleX = scaleX * scaleRadio;
        float _scaleY = scaleY * scaleRadio;
        boolean _flipH = flipH;
        boolean _flipV = flipV;
        float _translateX = translateX;
        float _translateY = translateY;
        if (rotation % 180 != 0) {
            _flipH = flipV;
            _flipV = flipH;
        }

        Matrix.scaleM(tmp, 0, tmp, 0, preFlipH ? -1f : 1f, preFlipV ? -1.0f : 1f, 1f);

        if (_scaleX != 1 || _scaleY != 1) {
            Matrix.scaleM(tmp, 0, tmp, 0, _scaleX, _scaleY, 1.f);
        }

        if (_translateX != 0 || _translateY != 0) {
            Matrix.translateM(tmp, 0, tmp, 0, _translateX * (1- _scaleX) / _scaleX, _translateY* (1- _scaleY) / _scaleY, 0);
        }


        if (_flipH) {
            Matrix.scaleM(tmp, 0, tmp, 0, -1f, 1f, 1f);
        }
        if (_flipV) {
            Matrix.scaleM(tmp, 0, tmp, 0, 1f, -1f, 1f);
        }

        float _rotation = rotation;
        if (_rotation != 0) {
            if(_flipH != _flipV){
                _rotation *= -1;
            }
            Matrix.rotateM(tmp, 0, tmp, 0, _rotation, 0, 0, 1);
        }

        Matrix.multiplyMM(mMatrix, 0, tmp, 0, transformMatrix, 0);
    }

    private static int getScaleByType(Size displaySize, Size realSize, @ScaleType int scaleType, PointF outScale) {
        if (displaySize == null || realSize == null || displaySize.getHeight() == 0 || realSize.getWidth() == 0 || displaySize.getWidth() == 0 || realSize.getHeight() == 0) {
            return -1;
        }
        float scaleX = 1.0f, scaleY = 1.0f;
        if (scaleType == ScaleType.CenterCrop) {
            float scale = displaySize.getWidth() * realSize.getHeight() * 1.0f / displaySize.getHeight() / realSize.getWidth();
            if (scale != 1) {
                scaleX = scale > 1 ? 1F : (1F / scale);
                scaleY = scale > 1 ? scale : 1F;
            }
        } else if (scaleType == ScaleType.FitCenter) {
            float scale = displaySize.getWidth() * realSize.getHeight() * 1.0f / displaySize.getHeight() / realSize.getWidth();
            if (scale != 1) {
                scaleX = scale < 1 ? 1F : (1F / scale);
                scaleY = scale < 1 ? scale : 1F;
            }
        }
        if (outScale != null) {
            outScale.x = scaleX;
            outScale.y = scaleY;
            return 0;
        } else {
            return -2;
        }
    }

}
