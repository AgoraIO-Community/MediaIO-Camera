// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.agora.capture.video.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

import io.agora.capture.framework.modules.producers.VideoProducer;
import io.agora.capture.framework.util.CameraUtils;
import io.agora.capture.framework.util.FpsUtil;
import io.agora.capture.framework.util.LogUtil;

/**
 * Video Capture Device base class, defines a set of methods that native code
 * needs to use to configure, start capture, and to be reached by callbacks and
 * provides some necessary data type(s) with accessors.
 **/
public abstract class VideoCapture extends VideoProducer {

    public static final int ERROR_UNKNOWN = 0;
    public static final int ERROR_IN_USE = 1;
    public static final int ERROR_CANNOT_OPEN_MORE = 2;
    public static final int ERROR_CAMERA_DISABLED = 3;
    public static final int ERROR_CAMERA_DEVICE = 4;
    public static final int ERROR_CAMERA_SERVICE = 5;
    public static final int ERROR_CAMERA_DISCONNECTED = 6;
    public static final int ERROR_CAMERA_FREEZED = 7;
    public static final int ERROR_ALLOCATE = 8;
    public static final int ERROR_CONSUME_VIDEO_FRAME = 9;


    /**
     * Common class for storing a frameRate range. Values should be multiplied by 1000.
     */
    public static class FrameRateRange {
        public int min;
        public int max;

        public FrameRateRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            FrameRateRange that = (FrameRateRange) o;

            if (min != that.min) return false;
            return max == that.max;
        }

        @Override
        public int hashCode() {
            int result = min;
            result = 31 * result + max;
            return result;
        }

        @Override
        public String toString() {
            return "FrameRateRange{" +
                    "min=" + min +
                    ", max=" + max +
                    '}';
        }
    }

    public enum CameraState {
        OPENING,
        CONFIGURING,
        STARTED,
        STOPPING,
        STOPPED;

        @Override
        public String toString() {
            switch (this) {
                case OPENING: return "opening";
                case CONFIGURING: return "configure";
                case STARTED: return "started";
                case STOPPING: return "stopping";
                case STOPPED: return "stopped";
                default: return "";
            }
        }
    }

    public interface VideoCaptureStateListener {
        void onFirstCapturedFrame(int width, int height);

        void onCameraCaptureError(int error, String message);

        void onCameraOpen();

        void onCameraClosed();
    }


    public interface FrameRateRangeSelector{
        FrameRateRange onSelectCameraFpsRange(List<FrameRateRange> supportFpsRange, FrameRateRange selectedRange, int frameRateScaled);
    }

    private static final String TAG = VideoCapture.class.getSimpleName();

    // The angle (0, 90, 180, 270) that the image needs to be rotated to show in
    // the display's native orientation.
    int pCameraNativeOrientation;

    // In some occasions we need to invert the device rotation readings, see the
    // individual implementations.
    boolean pInvertDeviceOrientationReadings;

    VideoCaptureFormat pCaptureFormat;
    Context pContext;
    EGLContext pEGLContext;

    int pPreviewTextureId = -1;
    SurfaceTexture pPreviewSurfaceTexture;
    byte[] pYUVImage;
    float[] pTextureTransform = null;

    boolean mNeedsPreview;
    int mPreviewWidth;
    int mPreviewHeight;

    int mCameraId;
    String mCamera2Id;
    int curCameraFacing;
    int lastCameraFacing;
    boolean cameraSteady;
    boolean firstFrame;

    VideoCaptureStateListener stateListener;

    private FrameRateRangeSelector frameRateRangeSelector;

    private boolean enableExactFrameRate;

    private FpsUtil fpsUtil;

    VideoCapture(Context context) {
        pContext = context;
    }

    // Allocate necessary resources for capture.
    public boolean allocate(int width, int height, int frameRate, int facing) {
        if (fpsUtil == null) {
            fpsUtil = new FpsUtil("Camera", new Handler(Looper.myLooper()), 2000, 4000, new Runnable() {
                @Override
                public void run() {
                    LogUtil.e(TAG, "Camera freezed.");
                    if(stateListener != null){
                        stateListener.onCameraCaptureError(ERROR_CAMERA_FREEZED, "Camera failure. Client must return video buffers.");
                    }
                }
            });
        }
        return true;
    }

    public abstract void startCaptureMaybeAsync(boolean needsPreview);

    // Blocks until it is guaranteed that no more frames are sent.
    public abstract void stopCaptureAndBlockUntilStopped();

    public abstract void deallocate(boolean disconnect);

    // zoom api
    public abstract boolean isZoomSupported();
    public abstract int setZoom(float zoomValue);
    public abstract float getMaxZoom();

    // torch api
    public abstract boolean isTorchSupported();
    public abstract int setTorchMode(boolean isOn);

    // ExposureCompensation api
    public abstract void setExposureCompensation (int value);
    public abstract int getExposureCompensation ();
    public abstract int getMinExposureCompensation ();
    public abstract int getMaxExposureCompensation ();



    void deallocate() {
        if (fpsUtil != null) {
            fpsUtil.release();
            fpsUtil = null;
        }
        deallocate(true);
    }

    protected abstract int getNumberOfCameras();

    protected abstract void startPreview();

    protected abstract void handleCaptureError(int error, String msg);

    void setSharedContext(EGLContext eglContext) {
        pEGLContext = eglContext;
    }

    void onFrameAvailable() {
        VideoCaptureFrame frame = new VideoCaptureFrame(
                // The format may be changed during processing.
                // Create a copy of the format config to avoid
                // the original format instance from being
                // modified unexpectedly.
                pCaptureFormat.copy(),
                pPreviewSurfaceTexture,
                pPreviewTextureId,
                pYUVImage,
                pTextureTransform,
                System.currentTimeMillis(),
                pCameraNativeOrientation,
                pInvertDeviceOrientationReadings);

        if(fpsUtil != null){
            fpsUtil.addFrame();
        }

        pushVideoFrame(frame);

        if (firstFrame) {
            if (stateListener != null) {
                stateListener.onFirstCapturedFrame(frame.format.getWidth(), frame.format.getHeight());
            }
            LogUtil.i(TAG, "first capture frame detected");
            firstFrame = false;
        }
    }

    @Override
    protected void onConsumeVideoFrameError(Exception e) {
        super.onConsumeVideoFrameError(e);
        if (stateListener != null) {
            stateListener.onCameraCaptureError(ERROR_CONSUME_VIDEO_FRAME, e.toString());
        }
    }

    /**
     * The state listener should be set before starting capture, or
     * some state callbacks may be missing
     * @param listener state callback listener
     */
    void setCaptureStateListener(VideoCaptureStateListener listener) {
        stateListener = listener;
    }

    public void setFrameRateRangeSelector(FrameRateRangeSelector frameRateRangeSelector) {
        this.frameRateRangeSelector = frameRateRangeSelector;
    }

    public void enableExactFrameRange(boolean enable){
        this.enableExactFrameRate = enable;
    }

    VideoCapture.FrameRateRange getClosestFrameRateRange(List<VideoCapture.FrameRateRange> ranges, int frameRate){
        // API fps ranges are scaled up x1000 to avoid floating point.
        int frameRateScaled = frameRate * 1000;
        FrameRateRange frameRateRange;
        if (enableExactFrameRate) {
            frameRateRange = CameraUtils.getClosestFrameRateRangeExactly(
                    ranges, frameRateScaled);
        } else {
            frameRateRange = CameraUtils.getClosestFrameRateRangeWebrtc(
                    ranges, frameRateScaled);
        }

        if(frameRateRangeSelector != null){
            FrameRateRange reSelectFpsRange = frameRateRangeSelector.onSelectCameraFpsRange(ranges, frameRateRange, frameRate);
            if(reSelectFpsRange != null && ranges.contains(reSelectFpsRange)){
                frameRateRange = reSelectFpsRange;
            }
        }

        return frameRateRange;
    }
}
