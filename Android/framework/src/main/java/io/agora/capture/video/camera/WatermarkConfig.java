package io.agora.capture.video.camera;


import io.agora.capture.framework.gles.MatrixOperator;


public class WatermarkConfig {
    public int outWidth;
    public int outHeight;

    @MatrixOperator.ScaleType
    public int originTexScaleType = MatrixOperator.ScaleType.CenterCrop;

    public int watermarkWidth;
    public int watermarkHeight;
    @MatrixOperator.ScaleType
    public int watermarkScaleType = MatrixOperator.ScaleType.FitCenter;

    public WatermarkConfig(int width, int height) {
        this.outWidth = this.watermarkWidth = width;
        this.outHeight = this.watermarkHeight = height;
    }

}
