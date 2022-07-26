// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.agora.capture.video.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.util.LogUtil;


/**
 * Video Capture Device extension of VideoCapture to provide common functionality
 * for capture using android.hardware.Camera API (deprecated in API 21). For Normal
 * Android devices, it provides functionality for receiving copies of preview
 * frames via Java-allocated buffers.
 **/
@SuppressWarnings("deprecation")
public class VideoCaptureCamera
        extends VideoCapture implements Camera.PreviewCallback {
    private static final String TAG = VideoCaptureCamera.class.getSimpleName();
    private static final int NUM_CAPTURE_BUFFERS = 3;

    private int mExpectedFrameSize;

    private Camera mCamera;
    // Lock to mutually exclude execution of OnPreviewFrame() and {start/stop}Capture().
    private ReentrantLock mPreviewBufferLock = new ReentrantLock();
    private final Object mCameraStateLock = new Object();
    private CaptureErrorCallback mErrorCallback = new CaptureErrorCallback();
    private volatile CameraState mCameraState = CameraState.STOPPED;

    private Camera.CameraInfo getCameraInfo(int id) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        try {
            Camera.getCameraInfo(id, cameraInfo);
        } catch (RuntimeException ex) {
            LogUtil.e(TAG, "getCameraInfo: Camera.getCameraInfo: " + ex);
            return null;
        }
        return cameraInfo;
    }

    private static Camera.Parameters getCameraParameters(
            Camera camera) {
        Camera.Parameters parameters;
        try {
            parameters = camera.getParameters();
        } catch (RuntimeException ex) {
            LogUtil.e(TAG, "getCameraParameters: android.hardware.Camera.getParameters: " + ex);
            if (camera != null) camera.release();
            return null;
        }
        return parameters;
    }

    private class CaptureErrorCallback implements Camera.ErrorCallback {
        @Override
        public void onError(int error, Camera camera) {
            LogUtil.e(TAG, "Camera capture error: " + error);
            handleCaptureError(error);
        }
    }

    protected void handleCaptureError(int error) {
        if (stateListener != null) {
            int errorCode = -1;
            String errorMessage = null;
            String hint = "Camera: ";
            switch (error) {
                case Camera.CAMERA_ERROR_UNKNOWN:
                    errorCode = ERROR_UNKNOWN;
                    errorMessage = hint + "unspecific camera error";
                    break;
                case Camera.CAMERA_ERROR_EVICTED:
                    errorCode = ERROR_IN_USE;
                    errorMessage = hint + "Camera was disconnected due to use by higher priority user";
                    break;
                case Camera.CAMERA_ERROR_SERVER_DIED:
                    errorCode = ERROR_CAMERA_SERVICE;
                    errorMessage = hint + "media server/service died, must release the Camera and create a new one";
                    break;
            }

            stateListener.onCameraCaptureError(errorCode, errorMessage);
        }
    }

    protected int getNumberOfCameras() {
        return Camera.getNumberOfCameras();
    }

    VideoCaptureCamera(Context context) {
        super(context);
    }

    @SuppressLint("WrongConstant")
    @Override
    public boolean allocate(final int width, final int height, final int frameRate, final int facing) {
        super.allocate(width, height, frameRate, facing);
        LogUtil.d(TAG, "allocate: requested width: " + width + " height: " + height + " fps: " + frameRate);

        synchronized (mCameraStateLock) {
            if (mCameraState != CameraState.STOPPED) {
                return false;
            }
        }

        curCameraFacing = facing;
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (curCameraFacing == Constant.CAMERA_FACING_FRONT && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCameraId = i;
                break;
            }

            if (curCameraFacing == Constant.CAMERA_FACING_BACK && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i;
                break;
            }
        }

        try {
            mCamera = Camera.open(mCameraId);
            mCamera.setDisplayOrientation(90);
        } catch (RuntimeException ex) {
            LogUtil.e(TAG, "allocate: Camera.open: " + ex);
            mErrorCallback.onError(ERROR_UNKNOWN, null);
            return false;
        }

        Camera.CameraInfo cameraInfo = getCameraInfo(mCameraId);
        if (cameraInfo == null) {
            mCamera.release();
            mCamera = null;
            return false;
        }

        // Making the texture transformation behaves
        // as the same as Camera2 api.
        pCameraNativeOrientation = cameraInfo.orientation;
        pInvertDeviceOrientationReadings = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;

        Camera.Parameters parameters = getCameraParameters(mCamera);
        if (parameters == null) {
            mCamera = null;
            return false;
        }

        // getSupportedPreviewFpsRange() returns a List with at least one
        // element, but when camera is in bad state, it can return null pointer.
        List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
        if (listFpsRange == null || listFpsRange.size() == 0) {
            LogUtil.e(TAG, "allocate: no fps range found");
            return false;
        }
        final ArrayList<FrameRateRange> ranges =
                new ArrayList<>(listFpsRange.size());
        for (int[] range : listFpsRange) {
            ranges.add(new FrameRateRange(range[0], range[1]));
        }
        // API fps ranges are scaled up x1000 to avoid floating point.
        int frameRateScaled = frameRate * 1000;
        FrameRateRange chosenRange =
                getClosestFrameRateRange(ranges, frameRateScaled);

        if(stateListener != null){
            FrameRateRange reSelectFpsRange = stateListener.onSelectCameraFpsRange(ranges, chosenRange);
            if(reSelectFpsRange != null && ranges.contains(reSelectFpsRange)){
                chosenRange = reSelectFpsRange;
            }
        }

        int[] chosenFpsRange = new int[] {chosenRange.min, chosenRange.max};
        LogUtil.d(TAG, "allocate: Camera fps set to [" + chosenFpsRange[0] + "-" + chosenFpsRange[1] + "]" + ", desired fps is " + frameRate);


        // Calculate size.
        List<Camera.Size> listCameraSize = parameters.getSupportedPreviewSizes();
        int minDiff = Integer.MAX_VALUE;
        int matchedWidth = width;
        int matchedHeight = height;
        for (Camera.Size size : listCameraSize) {
            int diff = Math.abs(size.width - width) + Math.abs(size.height - height);
            if (diff < minDiff && (size.width % 32 == 0)) {
                minDiff = diff;
                matchedWidth = size.width;
                matchedHeight = size.height;
            }
        }
        if (minDiff == Integer.MAX_VALUE) {
            LogUtil.e(TAG, "Couldn't find resolution close to (" + width + "x" + height + ")");
            return false;
        }
        LogUtil.d(TAG, "allocate: matched (" + matchedWidth +  " x " + matchedHeight + ")");

        mPreviewWidth = matchedWidth;
        mPreviewHeight = matchedHeight;
        pCaptureFormat = new VideoCaptureFormat(matchedWidth, matchedHeight,
                chosenFpsRange[1] / 1000, ImageFormat.NV21,
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        parameters.setPreviewSize(matchedWidth, matchedHeight);
        parameters.setPreviewFpsRange(chosenFpsRange[0], chosenFpsRange[1]);
        parameters.setPreviewFormat(pCaptureFormat.getPixelFormat());

        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }

        // set auto focus mode
        final List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        try {
            mCamera.setParameters(parameters);
        } catch (RuntimeException ex) {
            LogUtil.e(TAG, "setParameters: " + ex);
            return false;
        }

        mCamera.setErrorCallback(mErrorCallback);

        mExpectedFrameSize = pCaptureFormat.getWidth() * pCaptureFormat.getHeight()
                * ImageFormat.getBitsPerPixel(pCaptureFormat.getPixelFormat()) / 8;
        for (int i = 0; i < NUM_CAPTURE_BUFFERS; i++) {
            byte[] buffer = new byte[mExpectedFrameSize];
            mCamera.addCallbackBuffer(buffer);
        }

        synchronized (mCameraStateLock) {
            mCameraState = CameraState.OPENING;
        }

        return true;
    }

    protected void startPreview() {
        LogUtil.d(TAG, "start preview");
        pPreviewSurfaceTexture = new SurfaceTexture(pPreviewTextureId);

        if (mCamera == null) {
            LogUtil.e(TAG, "startCaptureAsync: mCamera is null");
            return;
        }

        try {
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.setPreviewTexture(pPreviewSurfaceTexture);
            mCamera.startPreview();
            lastCameraFacing = curCameraFacing;
            cameraSteady = true;
            firstFrame = true;
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
            cameraSteady = false;
            firstFrame = false;
        }

        synchronized (mCameraStateLock) {
            mCameraState = CameraState.STARTED;
        }

        if(stateListener != null){
            stateListener.onCameraOpen();
        }
    }

    @Override
    public void startCaptureMaybeAsync(boolean needsPreview) {
        LogUtil.d(TAG, "startCaptureMaybeAsync " + pPreviewTextureId);

        synchronized (mCameraStateLock) {
            if (mCameraState == CameraState.STOPPING) {
                LogUtil.d(TAG, "startCaptureMaybeAsync pending start request");
            } else if (mCameraState == CameraState.OPENING) {
                if (pPreviewTextureId == -1) pPreviewTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                if (pPreviewTextureId != -1) startPreview();
            } else {
                LogUtil.w(TAG, "start camera capture in illegal state:" + mCameraState);
            }
        }
    }

    @Override
    public void stopCaptureAndBlockUntilStopped() {
        LogUtil.d(TAG, "stopCaptureAndBlockUntilStopped");

        if (mCamera == null) {
            LogUtil.e(TAG, "stopCaptureAndBlockUntilStopped: mCamera is null");
            return;
        }

        if (mCameraState != CameraState.STARTED) {
            return;
        }

        try {
            mCamera.stopPreview();
        } catch (RuntimeException ex) {
            LogUtil.e(TAG, "setPreviewTexture: " + ex);
        }

        synchronized (mCameraStateLock) {
            mCameraState = CameraState.STOPPING;
        }
    }

    @Override
    public void deallocate(boolean disconnect) {
        LogUtil.d(TAG, "deallocate " + disconnect);

        if (mCamera == null) return;

        cameraSteady = false;
        stopCaptureAndBlockUntilStopped();

        if (pPreviewTextureId != -1) {
            int[] textures = new int[] {pPreviewTextureId};
            GLES20.glDeleteTextures(1, textures, 0);
            LogUtil.d(this, "EGL >> deallocate glDeleteTextures texture=" + pPreviewTextureId );
            pPreviewTextureId = -1;
        }

        pCaptureFormat = null;
        mCamera.release();
        mCamera = null;

        synchronized (mCameraStateLock) {
            mCameraState = CameraState.STOPPED;
        }

        if (stateListener != null) {
            stateListener.onCameraClosed();
        }
    }

    @Override
    void updatePreviewOrientation() {
        LogUtil.e(TAG, "vide isUpsideDown: 33");

        Camera.CameraInfo cameraInfo = getCameraInfo(mCameraId);
        if (mCamera == null || cameraInfo == null) {
            LogUtil.e(TAG, "vide isUpsideDown: mCamera is" + mCamera + "cameraInfo is " + cameraInfo);
            return;
        }
        //mCamera.setDisplayOrientation(getDisplayOrientation(cameraInfo));
    }

    private int getDisplayOrientation(Camera.CameraInfo cameraInfo) {

        int rotation = ((WindowManager) pContext.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();

        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: {
                degrees = 0;
                break;
            }
            case Surface.ROTATION_90: {
                degrees = 90;
                break;
            }
            case Surface.ROTATION_180: {
                degrees = 180;
                break;
            }
            case Surface.ROTATION_270: {
                degrees = 270;
                break;
            }
        }
        int displayOrientation;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            displayOrientation = (cameraInfo.orientation + degrees) % 360;
            displayOrientation = (360 - displayOrientation) % 360;
        } else {
            displayOrientation = (cameraInfo.orientation - degrees + 360) % 360;
        }
        return displayOrientation;
    }

    @Override
    public void onPreviewFrame(byte[] data, @NonNull Camera camera) {
        mPreviewBufferLock.lock();
        try {
            if (mCameraState != CameraState.STARTED) {
                return;
            }

            if (data.length != mExpectedFrameSize) {
                LogUtil.e(TAG, "the frame size is not as expected");
                return;
            }

            pYUVImage = data;
            onFrameAvailable();
        } finally {
            mPreviewBufferLock.unlock();
            camera.addCallbackBuffer(data);
        }
    }

    @Override
    public boolean isTorchSupported() {
        if (this.mCamera != null) {
            Camera.Parameters parameters = this.getCameraParameters();
            if (parameters != null) {
                return isSupported("torch", parameters.getSupportedFlashModes());
            }
        }

        return false;
    }

    @Override
    public int setTorchMode(boolean isOn) {
        if (this.mCamera != null) {
            Camera.Parameters parameters = this.getCameraParameters();
            if (parameters != null) {
                List<String> supportedFlashModes = parameters.getSupportedFlashModes();
                if (supportedFlashModes != null && supportedFlashModes.contains("torch")) {
                    if (isOn) {
                        parameters.setFlashMode("torch");
                    } else {
                        parameters.setFlashMode("off");
                    }

                    this.mCamera.setParameters(parameters);
                    return 0;
                }

                return -1;
            }
        }

        return -2;
    }

    @Override
    public boolean isZoomSupported() {
        if (this.mCamera != null) {
            Camera.Parameters parameters = this.getCameraParameters();
            return this.isZoomSupported(parameters);
        } else {
            return false;
        }
    }

    @Override
    public int setZoom(float zoomValue) {
        if (!(zoomValue < 0.0F) && this.mCamera != null) {
            int zoomRatio = (int)(zoomValue * 100.0F + 0.5F);
            List<Integer> zoomRatios = this.getZoomRatios();
            if (zoomRatios == null) {
                return -1;
            } else {
                int zoomLevel = 0;

                int maxZoom;
                for(int i = 0; i < zoomRatios.size(); ++i) {
                    maxZoom = (Integer)zoomRatios.get(i);
                    if (zoomRatio <= maxZoom) {
                        zoomLevel = i;
                        break;
                    }
                }

                Camera.Parameters parameters = this.getCameraParameters();
                if (!this.isZoomSupported(parameters)) {
                    return -1;
                } else {
                    maxZoom = parameters.getMaxZoom();
                    if (zoomLevel > maxZoom) {
                        Log.w(TAG, "zoom value is larger than maxZoom value");
                        return -1;
                    } else {
                        parameters.setZoom(zoomLevel);

                        try {
                            this.mCamera.setParameters(parameters);
                            return 0;
                        } catch (Exception var8) {
                            Log.w(TAG, "setParameters failed, zoomLevel: " + zoomLevel + ", " + var8);
                            return -1;
                        }
                    }
                }
            }
        } else {
            return -1;
        }
    }

    @Override
    public float getMaxZoom() {
        if (this.mCamera != null) {
            Camera.Parameters parameters = this.getCameraParameters();
            int maxZoom = 0;
            if (this.isZoomSupported(parameters)) {
                maxZoom = parameters.getMaxZoom();
            }

            List<Integer> zoomRatios = this.getZoomRatios();
            if (zoomRatios != null && zoomRatios.size() > maxZoom) {
                return (float)(Integer)zoomRatios.get(maxZoom) / 100.0F;
            }
        }

        return -1.0F;
    }

    private List<Integer> getZoomRatios() {
        if (this.mCamera != null) {
            Camera.Parameters parameters = this.getCameraParameters();
            if (this.isZoomSupported(parameters)) {
                return parameters.getZoomRatios();
            }
        }

        return null;
    }

    private boolean isZoomSupported(Camera.Parameters parameters) {
        if (parameters != null) {
            boolean isZoomSupported = parameters.isZoomSupported();
            if (isZoomSupported) {
                return true;
            } else {
                Log.w(TAG, "camera zoom is not supported!");
                return false;
            }
        } else {
            return false;
        }
    }

    public Camera.Parameters getCameraParameters() {
        try {
            Camera.Parameters parameters = this.mCamera.getParameters();
            return parameters;
        } catch (RuntimeException var3) {
            Log.e(TAG, "getCameraParameters: Camera.getParameters: ", var3);
            var3.printStackTrace();
        }
        return null;
    }

    private static boolean isSupported(String value, List<String> supported) {
        return supported != null && supported.indexOf(value) >= 0;
    }
}
