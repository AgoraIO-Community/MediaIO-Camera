// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.agora.capture.video.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.agora.capture.framework.modules.producers.VideoProducer;
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

    /**
     * Common class for storing a frameRate range. Values should be multiplied by 1000.
     */
    public static class FrameRateRange {
        int min;
        int max;

        FrameRateRange(int min, int max) {
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

        void onCameraClosed();

        FrameRateRange onSelectCameraFpsRange(List<FrameRateRange> supportFpsRange, FrameRateRange selectedRange);
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

    /**
     * update preview orientation
     */
    abstract void updatePreviewOrientation();

    void deallocate() {
        if (fpsUtil != null) {
            fpsUtil.release();
            fpsUtil = null;
        }
        deallocate(true);
    }

    /**
     * Finds the frame rate range matching |targetFrameRate|.
     * Tries to find a range with as low of a minimum value as
     * possible to allow the camera adjust based on the lighting conditions.
     * Assumes that all frame rate values are multiplied by 1000.
     *
     * This code is mostly copied from WebRTC:
     * CameraEnumerationAndroid.getClosestSupportedFramerateRange
     * in webrtc/api/android/java/src/org/webrtc/CameraEnumerationAndroid.java
     */
    static FrameRateRange getClosestFrameRateRange(
            final List<FrameRateRange> frameRateRanges, int targetFrameRate) {
        return Collections.min(frameRateRanges, new Comparator<FrameRateRange>() {
            // Threshold and penalty weights if the upper bound is further away than
            // |MAX_FPS_DIFF_THRESHOLD| from requested.
            private static final int MAX_FPS_DIFF_THRESHOLD = 5000;
            private static final int MAX_FPS_LOW_DIFF_WEIGHT = 1;
            private static final int MAX_FPS_HIGH_DIFF_WEIGHT = 3;

            // Threshold and penalty weights if the lower bound is bigger than |MIN_FPS_THRESHOLD|.
            private static final int MIN_FPS_THRESHOLD = 8000;
            private static final int MIN_FPS_LOW_VALUE_WEIGHT = 1;
            private static final int MIN_FPS_HIGH_VALUE_WEIGHT = 4;

            // Use one weight for small |value| less than |threshold|, and another weight above.
            private int progressivePenalty(
                    int value, int threshold, int lowWeight, int highWeight) {
                return (value < threshold)
                        ? value * lowWeight
                        : threshold * lowWeight + (value - threshold) * highWeight;
            }

            int diff(FrameRateRange range) {
                final int minFpsError = progressivePenalty(range.min, MIN_FPS_THRESHOLD,
                        MIN_FPS_LOW_VALUE_WEIGHT, MIN_FPS_HIGH_VALUE_WEIGHT);
                final int maxFpsError = progressivePenalty(Math.abs(targetFrameRate - range.max),
                        MAX_FPS_DIFF_THRESHOLD, MAX_FPS_LOW_DIFF_WEIGHT, MAX_FPS_HIGH_DIFF_WEIGHT);
                return minFpsError + maxFpsError;
            }

            @Override
            public int compare(FrameRateRange range1, FrameRateRange range2) {
                return diff(range1) - diff(range2);
            }
        });
    }

    protected abstract int getNumberOfCameras();

    protected abstract void startPreview();

    protected abstract void handleCaptureError(int error);

    void setSharedContext(EGLContext eglContext) {
        pEGLContext = eglContext;
    }

    void onFrameAvailable() {
        // The images from front system camera are mirrored by default.
        int facing = cameraSteady ? curCameraFacing : lastCameraFacing;
        boolean mirrored = (facing == Constant.CAMERA_FACING_FRONT);

        VideoCaptureFrame frame = new VideoCaptureFrame(
                // The format may be changed during processing.
                // Create a copy of the format config to avoid
                // the original format instance from being
                // modified unexpectedly.
                pCaptureFormat.copy(),
                pPreviewSurfaceTexture,
                pPreviewTextureId,
                pYUVImage,
                null,
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

    /**
     * The state listener should be set before starting capture, or
     * some state callbacks may be missing
     * @param listener state callback listener
     */
    void setCaptureStateListener(VideoCaptureStateListener listener) {
        stateListener = listener;
    }
}
