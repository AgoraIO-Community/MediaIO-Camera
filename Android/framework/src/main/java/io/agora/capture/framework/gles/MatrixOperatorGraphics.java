package io.agora.capture.framework.gles;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.Size;

public class MatrixOperatorGraphics extends MatrixOperator {

    private final Matrix mMatrix;


    public MatrixOperatorGraphics(@ScaleType int scaleType){
        super(scaleType);
        mMatrix = new Matrix();
    }

    @Override
    public void setScaleType(int scaleType) {
        throw new RuntimeException("MatrixOperatorGraphics setScaleType is not supported!");
    }

    @Override
    public float[] getFinalMatrix() {
        return convertMatrixFromAndroidGraphicsMatrix(mMatrix);
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

        mMatrix.reset();
        // Perform mirror and rotation around (0.5, 0.5) since that is the center of the texture.
        mMatrix.preTranslate(/* dx= */ 0.5f, /* dy= */ 0.5f);

        mMatrix.preScale(/* sx= */ preFlipH ? -1f : 1f, /* sy= */preFlipV ? -1f :  1f);
        mMatrix.preRotate(rotation);
        mMatrix.preScale(flipH ? -1f : 1f, flipV ? -1f : 1f);
        mMatrix.preScale(scaleX * scaleRadio, scaleY * scaleRadio);

        mMatrix.preTranslate(/* dx= */ -0.5f, /* dy= */ -0.5f);

        mMatrix.preTranslate(translateX, translateY);

        Matrix finalMatrix = convertMatrixToAndroidGraphicsMatrix(transformMatrix);
        finalMatrix.preConcat(mMatrix);
        mMatrix.set(finalMatrix);
    }

    private static int distance(float x0, float y0, float x1, float y1) {
        return (int) Math.round(Math.hypot(x1 - x0, y1 - y0));
    }

    private static int getScaleByType(Size displaySize, Size realSize, @ScaleType int scaleType, PointF outScale) {
        if (displaySize == null || realSize == null || displaySize.getHeight() == 0 || realSize.getWidth() == 0 || displaySize.getWidth() == 0 || realSize.getHeight() == 0) {
            return -1;
        }
        float scaleX = 1.0f, scaleY = 1.0f;
        if (scaleType == ScaleType.CenterCrop) {
            final float frameAspectRatio = realSize.getWidth() / (float) realSize.getHeight();
            final float drawnAspectRatio = displaySize.getWidth() / (float)displaySize.getHeight();
            if (frameAspectRatio > drawnAspectRatio) {
                scaleX = drawnAspectRatio / frameAspectRatio;
                scaleY = 1f;
            } else {
                scaleX = 1f;
                scaleY = frameAspectRatio / drawnAspectRatio;
            }
        } else if (scaleType == ScaleType.FitCenter) {
            final float frameAspectRatio = realSize.getWidth() / (float) realSize.getHeight();
            final float drawnAspectRatio = displaySize.getWidth() / (float)displaySize.getHeight();
            if (frameAspectRatio < drawnAspectRatio) {
                scaleX = drawnAspectRatio / frameAspectRatio;
                scaleY = 1f;
            } else {
                scaleX = 1f;
                scaleY = frameAspectRatio / drawnAspectRatio;
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

    /** Converts a float[16] matrix array to android.graphics.Matrix. */
    public static android.graphics.Matrix convertMatrixToAndroidGraphicsMatrix(float[] matrix4x4) {
        // clang-format off
        float[] values = {
                matrix4x4[0 * 4 + 0], matrix4x4[1 * 4 + 0], matrix4x4[3 * 4 + 0],
                matrix4x4[0 * 4 + 1], matrix4x4[1 * 4 + 1], matrix4x4[3 * 4 + 1],
                matrix4x4[0 * 4 + 3], matrix4x4[1 * 4 + 3], matrix4x4[3 * 4 + 3],
        };
        // clang-format on

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setValues(values);
        return matrix;
    }

    /** Converts android.graphics.Matrix to a float[16] matrix array. */
    public static float[] convertMatrixFromAndroidGraphicsMatrix(android.graphics.Matrix matrix) {
        float[] values = new float[9];
        matrix.getValues(values);

        // The android.graphics.Matrix looks like this:
        // [x1 y1 w1]
        // [x2 y2 w2]
        // [x3 y3 w3]
        // We want to contruct a matrix that looks like this:
        // [x1 y1  0 w1]
        // [x2 y2  0 w2]
        // [ 0  0  1  0]
        // [x3 y3  0 w3]
        // Since it is stored in column-major order, it looks like this:
        // [x1 x2 0 x3
        //  y1 y2 0 y3
        //   0  0 1  0
        //  w1 w2 0 w3]
        // clang-format off
        float[] matrix4x4 = {
                values[0 * 3 + 0],  values[1 * 3 + 0], 0,  values[2 * 3 + 0],
                values[0 * 3 + 1],  values[1 * 3 + 1], 0,  values[2 * 3 + 1],
                0,                  0,                 1,  0,
                values[0 * 3 + 2],  values[1 * 3 + 2], 0,  values[2 * 3 + 2],
        };
        // clang-format on
        return matrix4x4;
    }

}
