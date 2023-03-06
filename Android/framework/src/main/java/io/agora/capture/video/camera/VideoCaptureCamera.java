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
        if(camera == null){
            return null;
        }
        Camera.Parameters parameters;
        try {
            parameters = camera.getParameters();
        } catch (RuntimeException ex) {
            LogUtil.e(TAG, "getCameraParameters: android.hardware.Camera.getParameters: " + ex);
            return null;
        }
        return parameters;
    }

    private class CaptureErrorCallback implements Camera.ErrorCallback {
        @Override
        public void onError(int error, Camera camera) {
            LogUtil.e(TAG, "Camera capture error: " + error);
            handleCaptureError(error, null);
        }
    }

    protected void handleCaptureError(int error, String msg) {
        if (stateListener != null) {
            int errorCode = error;
            String errorMessage = msg;
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
                String message = "allocate: The camera status is not stopped!";
                LogUtil.e(TAG, message);
                handleCaptureError(ERROR_CANNOT_OPEN_MORE, message);
                return false;
            }
        }

        curCameraFacing = facing;
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            try {
                Camera.getCameraInfo(i, info);
            } catch (Exception e) {
                String message = "allocate: Camera.getCameraInfo: index=" + i + "\n" + e;
                LogUtil.e(TAG, message);
                handleCaptureError(ERROR_ALLOCATE, message);
                return false;
            }
            if (curCameraFacing == Constant.CAMERA_FACING_FRONT && info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCameraId = i;
                break;
            }

            if (curCameraFacing == Constant.CAMERA_FACING_BACK && info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i;
                break;
            }
        }

        Camera camera;
        try {
            camera = Camera.open(mCameraId);
        } catch (Exception ex) {
            String message = "allocate: Camera.open: " + ex;
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }
        if(camera == null){
            String message = "allocate: Camera.open(" + mCameraId + ") return null!";
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }

        Camera.CameraInfo cameraInfo = getCameraInfo(mCameraId);
        if (cameraInfo == null) {
            camera.release();
            String message = "allocate: getCameraInfo null";
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }

        // Making the texture transformation behaves
        // as the same as Camera2 api.
        try {
            camera.setDisplayOrientation(0);
        } catch (Exception e) {
            camera.release();
            String message = "allocate: camera.setDisplayOrientation: " + e;
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }
        pCameraNativeOrientation = cameraInfo.orientation;
        pInvertDeviceOrientationReadings = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;

        Camera.Parameters parameters = getCameraParameters(camera);
        if (parameters == null) {
            camera.release();
            String message = "allocate: getCameraParameters is null";
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }

        // getSupportedPreviewFpsRange() returns a List with at least one
        // element, but when camera is in bad state, it can return null pointer.
        List<int[]> listFpsRange = parameters.getSupportedPreviewFpsRange();
        if (listFpsRange == null || listFpsRange.size() == 0) {
            camera.release();

            String message = "allocate: no fps range found";
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
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
            camera.release();

            String message = "allocate: " + "Couldn't find resolution close to (" + width + "x" + height + ")";
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }
        LogUtil.d(TAG, "allocate: matched (" + matchedWidth +  " x " + matchedHeight + ")");

        mPreviewWidth = matchedWidth;
        mPreviewHeight = matchedHeight;
        int cameraFacing = Constant.CAMERA_FACING_INVALID;
        if(mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT){
            cameraFacing = Constant.CAMERA_FACING_FRONT;
        }else if(mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK){
            cameraFacing = Constant.CAMERA_FACING_BACK;
        }
        pCaptureFormat = new VideoCaptureFormat(cameraFacing, matchedWidth, matchedHeight,
                chosenFpsRange[1] / 1000, ImageFormat.NV21,
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        parameters.setPreviewSize(matchedWidth, matchedHeight);
        try {
            parameters.setPreviewFpsRange(chosenFpsRange[0], chosenFpsRange[1]);
        } catch (Exception e) {
            camera.release();
            String message = "allocate: parameters.setPreviewFpsRange: " + e;
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }
        parameters.setPreviewFormat(pCaptureFormat.getPixelFormat());

        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }

        // set auto focus mode
        final List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        try {
            camera.setParameters(parameters);
        } catch (Exception ex) {
            camera.release();

            String message = "allocate: setParameters error -- " + ex;
            LogUtil.e(TAG, message);
            handleCaptureError(ERROR_ALLOCATE, message);
            return false;
        }

        camera.setErrorCallback(mErrorCallback);

        mExpectedFrameSize = pCaptureFormat.getWidth() * pCaptureFormat.getHeight()
                * ImageFormat.getBitsPerPixel(pCaptureFormat.getPixelFormat()) / 8;
        for (int i = 0; i < NUM_CAPTURE_BUFFERS; i++) {
            byte[] buffer = new byte[mExpectedFrameSize];
            camera.addCallbackBuffer(buffer);
        }

        synchronized (mCameraStateLock) {
            mCameraState = CameraState.OPENING;
        }

        mCamera = camera;

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
        Camera camera = mCamera;
        if (camera == null) {
            return false;
        }
        Camera.Parameters parameters = getCameraParameters(camera);
        if (parameters == null) {
            return false;
        }

        return isSupported("torch", parameters.getSupportedFlashModes());
    }

    @Override
    public int setTorchMode(boolean isOn) {
        Camera camera = mCamera;
        if (camera == null) {
            return -1;
        }
        Camera.Parameters parameters = getCameraParameters(camera);
        if (parameters == null) {
            return -2;
        }

        List<String> supportedFlashModes = parameters.getSupportedFlashModes();

        if (supportedFlashModes == null || !supportedFlashModes.contains("torch")) {
            return -3;
        }

        String flashMode;
        if (isOn) {
            flashMode = "torch";
        } else {
            flashMode = "off";
        }

        parameters.setFlashMode(flashMode);

        try {
            camera.setParameters(parameters);
            return 0;
        } catch (Exception e) {
            LogUtil.e(TAG, "setTorchMode: setParameters error -- flashMode=" + flashMode + ", exception="+ e);
            return -4;
        }
    }

    @Override
    public boolean isZoomSupported() {
        Camera camera = mCamera;
        if (camera == null) {
            return false;
        }
        Camera.Parameters parameters = getCameraParameters(camera);
        if(parameters == null){
            return false;
        }
        return isZoomSupported(parameters);
    }

    @Override
    public int setZoom(float zoomValue) {
        Camera camera = mCamera;
        if(zoomValue < 0.0F || camera == null){
            return -1;
        }
        int zoomRatio = (int)(zoomValue * 100.0F + 0.5F);
        List<Integer> zoomRatios = this.getZoomRatios();
        if (zoomRatios == null) {
            return -1;
        }

        int zoomLevel = 0;

        int maxZoom;
        for(int i = 0; i < zoomRatios.size(); ++i) {
            maxZoom = (Integer)zoomRatios.get(i);
            if (zoomRatio <= maxZoom) {
                zoomLevel = i;
                break;
            }
        }

        Camera.Parameters parameters = getCameraParameters(camera);
        if (!this.isZoomSupported(parameters)) {
            return -1;
        }

        maxZoom = parameters.getMaxZoom();
        if (zoomLevel > maxZoom) {
            Log.w(TAG, "zoom value is larger than maxZoom value");
            return -1;
        }

        parameters.setZoom(zoomLevel);

        try {
            this.mCamera.setParameters(parameters);
            return 0;
        } catch (Exception var8) {
            Log.w(TAG, "setZoom: setParameters error -- zoomLevel=" + zoomLevel + ", exception=" + var8);
            return -1;
        }
    }

    @Override
    public float getMaxZoom() {
        Camera camera = mCamera;
        if(camera == null){
            return -1.0F;
        }
        Camera.Parameters parameters = getCameraParameters(camera);
        if (!isZoomSupported(parameters)) {
            return -1.0F;
        }

        int maxZoom = parameters.getMaxZoom();

        List<Integer> zoomRatios = this.getZoomRatios();
        if (zoomRatios == null || zoomRatios.size() <= maxZoom) {
            return -1.0F;
        }

        return (float)(Integer)zoomRatios.get(maxZoom) / 100.0F;
    }

    private List<Integer> getZoomRatios() {
        Camera camera = mCamera;
        if(camera == null){
            return null;
        }
        Camera.Parameters parameters = getCameraParameters(camera);
        if(parameters == null){
            return null;
        }
        if (!isZoomSupported(parameters)) {
            return null;
        }
        return parameters.getZoomRatios();
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

    private static boolean isSupported(String value, List<String> supported) {
        return supported != null && supported.indexOf(value) >= 0;
    }


}
