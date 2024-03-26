// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.agora.capture.video.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.agora.capture.framework.gles.MatrixOperatorGraphics;
import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.util.LogUtil;

/**
 * This class implements Video Capture using Camera2 API, introduced in Android
 * API 21 (L Release). Capture takes place in the current Looper, while pixel
 * download takes place in another thread used by ImageReader. A number of
 * static methods are provided to retrieve information on current system cameras
 * and their capabilities, using android.hardware.camera2.CameraManager.
 **/
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class VideoCaptureCamera2 extends VideoCapture {
    private class CameraStateListener extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            LogUtil.i(TAG, "CameraDevice.StateCallback onOpened");
            mCameraDevice = cameraDevice;
            changeCameraStateAndNotify(CameraState.CONFIGURING);
            lastCameraFacing = curCameraFacing;
            cameraSteady = true;
            firstFrame = true;
            createPreviewObjectsAndStartPreviewOrFail();

            if (stateListener != null) {
                stateListener.onCameraOpen();
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            LogUtil.e(TAG, "cameraDevice was closed unexpectedly");
            cameraDevice.close();
            mCameraDevice = null;
            cameraSteady = false;
            firstFrame = false;
            handleCaptureError(ERROR_CAMERA_DISCONNECTED, null);
            changeCameraStateAndNotify(CameraState.STOPPED);
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            LogUtil.e(TAG, "Camera device error: " + error);
            cameraDevice.close();
            mCameraDevice = null;
            cameraSteady = false;
            firstFrame = false;
            handleCaptureError(error, null);
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            LogUtil.i(TAG, "cameraDevice closed");
            if (mPreviewSession != null) {
                mPreviewSession = null;
            }

            changeCameraStateAndNotify(CameraState.STOPPED);
            if (mPendingStartRequestWhenClosed) {
                mPendingStartRequestWhenClosed = false;
                startCaptureMaybeAsync(false);
            }

            if (stateListener != null) {
                stateListener.onCameraClosed();
            }
        }
    };


    protected void handleCaptureError(int error, String msg) {
        if (stateListener != null) {
            int errorCode = -1;
            String errorMessage = null;
            String hint = "Camera2: ";
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    errorCode = ERROR_IN_USE;
                    errorMessage = hint + "the camera is already in use maybe because a higher-priority camera API client";
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    errorCode = ERROR_CANNOT_OPEN_MORE;
                    errorMessage = hint + "you may try to open too more cameras than available";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    errorCode = ERROR_CAMERA_DISABLED;
                    errorMessage = hint + "the camera may be disabled by policy";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    errorCode = ERROR_CAMERA_DEVICE;
                    errorMessage = hint + "the camera encounters an fatal error and it needs to be reopened again.";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    errorCode = ERROR_CAMERA_SERVICE;
                    errorMessage = hint + "camera service has encountered a fatal error, maybe a hardware issue or the device needs to be restarted";
                    break;
                case ERROR_CAMERA_DISCONNECTED:
                    errorCode = error;
                    errorMessage = hint + "camera capture is disconnected";
                    break;
            }

            stateListener.onCameraCaptureError(errorCode, errorMessage);
        }
    }

    private class CameraPreviewSessionListener extends CameraCaptureSession.StateCallback {
        private final CaptureRequest mPreviewRequest;
        CameraPreviewSessionListener(CaptureRequest previewRequest) {
            mPreviewRequest = previewRequest;
        }

        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            LogUtil.d(TAG, "CameraPreviewSessionListener.onConfigured");
            mPreviewSession = cameraCaptureSession;
            try {


                // This line triggers the preview. A |listener| is registered to receive the actual
                // capture result details. A CrImageReaderListener will be triggered every time a
                // downloaded image is ready. Since |handler| is null, we'll work on the current
                // Thread Looper.
                if (mCameraState == CameraState.CONFIGURING) {
                    mPreviewSession.setRepeatingRequest(mPreviewRequest, null, null);

                    if (mIsCameraTorchStarted && VideoCaptureCamera2.this.mTorchMode != 0) {
                        setTorchMode(VideoCaptureCamera2.this.mTorchMode == 1);
                    }

                    if (!VideoCaptureCamera2.this.mIsmCameraZoomStarted && VideoCaptureCamera2.this.mCameraZoomFactor > 0.0F) {
                        VideoCaptureCamera2.this.setZoom(VideoCaptureCamera2.this.mCameraZoomFactor);
                    }

                    if (!VideoCaptureCamera2.this.mIsExposureCompensationStarted && VideoCaptureCamera2.this.mCameraExposureCompensation != 0) {
                        VideoCaptureCamera2.this.setExposureCompensation(VideoCaptureCamera2.this.mCameraExposureCompensation);
                    }
                }
            } catch (CameraAccessException | SecurityException | IllegalStateException
                    | IllegalArgumentException ex) {
                LogUtil.e(TAG, "setRepeatingRequest: ");
                return;
            }

            changeCameraStateAndNotify(CameraState.STARTED);
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            LogUtil.e(TAG, "CameraPreviewSessionListener.onConfigureFailed");
            changeCameraStateAndNotify(CameraState.STOPPED);
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession cameraCaptureSession) {
            LogUtil.d(TAG, "CameraPreviewSessionListener.onClosed");
        }
    };

    private class CameraPreviewReaderListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            // There is a case where the frame callbacks are
            // still called a short while after the camera
            // capture is stopped.
            if (mCameraState != CameraState.STARTED) return;

            try (Image image = reader.acquireLatestImage()) {
                if (image == null) return;

                if (image.getFormat() != ImageFormat.YUV_420_888 || image.getPlanes().length != 3) {
                    LogUtil.e(TAG,"Unexpected image format: " +
                            image.getFormat() + " or #planes: " +
                            image.getPlanes().length);
                    throw new IllegalStateException();
                }

                if (reader.getWidth() != image.getWidth()
                        || reader.getHeight() != image.getHeight()) {
                    LogUtil.e(TAG,"ImageReader size (" + reader.getWidth() +
                            "x" + reader.getHeight() + ") did not match Image size (" +
                            image.getWidth() + "x" + image.getHeight() + ")");
                    throw new IllegalStateException();
                }

                pYUVImage = YUV_420_888toNV21(image);
                onFrameAvailable();
            } catch (IllegalStateException ex) {
                LogUtil.e(TAG, "acquireLatestImage():");
            }
        }
    };

    private static final String TAG = VideoCaptureCamera2.class.getSimpleName();
    private final Object mCameraStateLock = new Object();

    private CameraDevice mCameraDevice;
    private CameraCaptureSession mPreviewSession;
    private CaptureRequest mPreviewRequest;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mImageReader;
    private static CameraManager mCameraManager;

    private volatile boolean mPendingStartRequestWhenClosed;

    private Range<Integer> mAeFpsRange;
    private CameraState mCameraState = CameraState.STOPPED;
    private Surface mSurface;

    private int mImageYSize;
    private int mImageUVSize;
    private byte[] mBuffer;

    private float mMaxZoom = 1.0F;
    private float mCameraZoomFactor = 1.0F;
    private boolean mIsmCameraZoomStarted = false;
    private Rect mSensorRect = null;
    private float mLastZoomRatio = -1.0F;

    private int mTorchMode = 0;
    private boolean mIsCameraTorchStarted = false;

    private int mCameraExposureCompensation = 0;
    private boolean mIsExposureCompensationStarted = false;
    private boolean mFaceDetectSupported = false;
    private int mFaceDetectMode;

    private final AtomicBoolean cameraAvailable = new AtomicBoolean(false);
    private volatile boolean mPendingStartRequestWhenAvailled = false;

    private final CameraManager.AvailabilityCallback availabilityCallback = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(@NonNull String cameraId) {
            super.onCameraAvailable(cameraId);
            LogUtil.d(TAG, "AvailabilityCallback >> onCameraAvailable cameraId=" + cameraId);
            if(cameraId.equals(mCamera2Id)){
                cameraAvailable.set(true);
                if(mPendingStartRequestWhenAvailled){
                    mPendingStartRequestWhenAvailled = false;
                    startCaptureMaybeAsync(false);
                }
            }
        }

        @Override
        public void onCameraUnavailable(@NonNull String cameraId) {
            super.onCameraUnavailable(cameraId);
            LogUtil.d(TAG, "AvailabilityCallback >> onCameraUnavailable cameraId=" + cameraId);
            if (cameraId.equals(mCamera2Id)) {
                cameraAvailable.set(false);
                if (checkCameraState(CameraState.STARTED)) {
                    stopCaptureAndBlockUntilStopped();
                    mPendingStartRequestWhenAvailled = true;
                }
            }
        }

        @Override
        public void onCameraAccessPrioritiesChanged() {
            super.onCameraAccessPrioritiesChanged();
            LogUtil.d(TAG, "AvailabilityCallback >> onCameraAccessPrioritiesChanged");
        }

        @Override
        public void onPhysicalCameraAvailable(@NonNull String cameraId, @NonNull String physicalCameraId) {
            super.onPhysicalCameraAvailable(cameraId, physicalCameraId);
            LogUtil.d(TAG, "AvailabilityCallback >> onPhysicalCameraAvailable cameraId=" + cameraId + ", physicalCameraId=" + physicalCameraId);
        }

        @Override
        public void onPhysicalCameraUnavailable(@NonNull String cameraId, @NonNull String physicalCameraId) {
            super.onPhysicalCameraUnavailable(cameraId, physicalCameraId);
            LogUtil.d(TAG, "AvailabilityCallback >> onPhysicalCameraUnavailable cameraId=" + cameraId + ", physicalCameraId=" + physicalCameraId);
        }
    };

    private CameraCharacteristics getCameraCharacteristics(String id) {
        try {
            return mCameraManager.getCameraCharacteristics(id);
        } catch (CameraAccessException | IllegalArgumentException | AssertionError ex) {
            LogUtil.e(TAG, "getCameraCharacteristics: ");
        }
        return null;
    }

    private static float getMaxZoom(CameraCharacteristics cameraCharacteristics) {
        if (cameraCharacteristics == null) {
            LogUtil.w(TAG, "warning cameraCharacteristics is null");
            return -1.0F;
        } else {
            Float maxZoom = (Float)cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            if (maxZoom == null) {
                LogUtil.w(TAG, "warning get max zoom return null");
                return -1.0F;
            } else {
                return maxZoom;
            }
        }
    }

    private void createPreviewObjectsAndStartPreviewOrFail() {
        if (createPreviewObjectsAndStartPreview()) return;

        changeCameraStateAndNotify(CameraState.STOPPED);
        LogUtil.e(TAG, "Error starting or restarting preview");
    }

    private boolean createPreviewObjectsAndStartPreview() {
        if (mCameraDevice == null) return false;

        mImageReader = ImageReader.newInstance(pCaptureFormat.getWidth(),
                pCaptureFormat.getHeight(), ImageFormat.YUV_420_888, 2);
        final CameraPreviewReaderListener imageReaderListener = new CameraPreviewReaderListener();
        mImageReader.setOnImageAvailableListener(imageReaderListener, pChannelHandler);

        try {
            // TEMPLATE_PREVIEW specifically means "high frame rate is given
            // priority over the highest-quality post-processing".
            mPreviewRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            LogUtil.e(TAG, "createCaptureRequest: ");
            return false;
        }

        if (mPreviewRequestBuilder == null) {
            LogUtil.e(TAG, "mPreviewRequestBuilder error");
            return false;
        }

        pPreviewSurfaceTexture = new SurfaceTexture(pPreviewTextureId);
        pPreviewSurfaceTexture.setDefaultBufferSize(mPreviewWidth, mPreviewHeight);
        mSurface = new Surface(pPreviewSurfaceTexture);

        // Construct an ImageReader Surface and plug it into our CaptureRequest.Builder.
        mPreviewRequestBuilder.addTarget(mSurface);
        mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

        configureCommonCaptureSettings(mPreviewRequestBuilder);

        List<Surface> surfaceList = new ArrayList<>(2);
        surfaceList.add(mSurface);
        surfaceList.add(mImageReader.getSurface());

        mPreviewRequest = mPreviewRequestBuilder.build();

        try {
            if (mCameraState == CameraState.CONFIGURING) {
                mCameraDevice.createCaptureSession(surfaceList,
                        new CameraPreviewSessionListener(mPreviewRequest), null);
            }
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            LogUtil.e(TAG, "createCaptureSession: ");
            return false;
        }

        return true;
    }

    private void configureCommonCaptureSettings(CaptureRequest.Builder requestBuilder) {
        // |mFocusMode| indicates if we're in auto/continuous, single-shot or manual mode.
        // AndroidMeteringMode.SINGLE_SHOT is dealt with independently since it needs to be
        // triggered by a capture.
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

        requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, mAeFpsRange);

        if (mFaceDetectSupported) {
            mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, this.mFaceDetectMode);
        }
    }

    private void changeCameraStateAndNotify(CameraState state) {
        synchronized (mCameraStateLock) {
            mCameraState = state;
            mCameraStateLock.notifyAll();
        }
    }

    // Finds the closest Size to (|width|x|height|) in |sizes|, and returns it or null.
    // Ignores |width| or |height| if either is zero (== don't care).
    private static Size findClosestSizeInArray(Size[] sizes, int width, int height) {
        if (sizes == null) return null;
        Size closestSize = null;
        int minDiff = Integer.MAX_VALUE;
        for (Size size : sizes) {
            final int diff = ((width > 0) ? Math.abs(size.getWidth() - width) : 0)
                    + ((height > 0) ? Math.abs(size.getHeight() - height) : 0);
            if (diff < minDiff && size.getWidth() % 32 == 0) {
                minDiff = diff;
                closestSize = size;
            }
        }
        if (minDiff == Integer.MAX_VALUE) {
            LogUtil.e(TAG, "Couldn't find resolution close to (" + width + "x" + height + ")");
            return null;
        }
        return closestSize;
    }

    protected int getNumberOfCameras() {
        try {
            return mCameraManager.getCameraIdList().length;
        } catch (CameraAccessException | SecurityException | AssertionError ex) {
            // SecurityException is undocumented but seen in the wild: https://crbug/605424.
            LogUtil.e(TAG, "getNumberOfCameras: getCameraIdList(): ");
            return 0;
        }
    }

    VideoCaptureCamera2(Context context) {
        super(context);
        mCameraManager = (CameraManager) pContext.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public boolean allocate(int width, int height, int frameRate, int facing) {
        super.allocate(width, height, frameRate, facing);
        LogUtil.d(TAG, "allocate: requested width: " + width + " height: " + height + " fps: " + frameRate);
        mCameraManager.registerAvailabilityCallback(availabilityCallback, pChannelHandler);
        curCameraFacing = facing;
        synchronized (mCameraStateLock) {
            if (mCameraState == CameraState.OPENING || mCameraState == CameraState.CONFIGURING) {
                LogUtil.e(TAG, "allocate() invoked while Camera is busy opening/configuring.");
                return false;
            }
        }

        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = getCameraCharacteristics(cameraId);

                Integer face = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (curCameraFacing == Constant.CAMERA_FACING_FRONT && face == characteristics.LENS_FACING_FRONT) {
                    mCamera2Id = cameraId;
                    break;
                }

                if (curCameraFacing == Constant.CAMERA_FACING_BACK && face == characteristics.LENS_FACING_BACK) {
                    mCamera2Id = cameraId;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        final CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(mCamera2Id);
        final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        mMaxZoom = getMaxZoom(cameraCharacteristics);

        // Find closest supported size.
        final Size[] supportedSizes = streamMap.getOutputSizes(ImageFormat.YUV_420_888);
        final Size closestSupportedSize = findClosestSizeInArray(supportedSizes, width, height);
        if (closestSupportedSize == null) {
            LogUtil.e(TAG, "No supported resolutions.");
            return false;
        }
        LogUtil.d(TAG, "allocate: matched (" + closestSupportedSize.getWidth() +  " x "
                + closestSupportedSize.getHeight() + ")");
        mPreviewWidth = closestSupportedSize.getWidth();
        mPreviewHeight = closestSupportedSize.getHeight();

        final List<Range<Integer>> fpsRanges = Arrays.asList(cameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        if (fpsRanges.isEmpty()) {
            LogUtil.e(TAG, "No supported framerate ranges.");
            return false;
        }
        final List<FrameRateRange> ranges =
                new ArrayList<FrameRateRange>(fpsRanges.size());
        // On some legacy implementations FPS values are multiplied by 1000. Multiply by 1000
        // everywhere for consistency. Set fpsUnitFactor to 1 if fps ranges are already multiplied
        // by 1000.
        final int fpsUnitFactor = fpsRanges.get(0).getUpper() > 1000 ? 1 : 1000;
        for (Range<Integer> range : fpsRanges) {
            ranges.add(new FrameRateRange(
                    range.getLower() * fpsUnitFactor, range.getUpper() * fpsUnitFactor));
        }
        final FrameRateRange aeRange = getClosestFrameRateRange(ranges, frameRate);
        mAeFpsRange = new Range<Integer>(
                aeRange.min / fpsUnitFactor, aeRange.max / fpsUnitFactor);
        LogUtil.d(TAG, "allocate: fps set to [" + mAeFpsRange.getLower() + "-" + mAeFpsRange.getUpper() + "]");

        mPreviewWidth = closestSupportedSize.getWidth();
        mPreviewHeight = closestSupportedSize.getHeight();

        // |mCaptureFormat| is also used to configure the ImageReader.
        pCaptureFormat = new VideoCaptureFormat(mCameraId, closestSupportedSize.getWidth(),
                closestSupportedSize.getHeight(),
                aeRange.max / fpsUnitFactor,
                ImageFormat.NV21, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        pCameraNativeOrientation =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        pInvertDeviceOrientationReadings =
                cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_FRONT;

        android.graphics.Matrix transformMatrix = new android.graphics.Matrix();
        transformMatrix.preTranslate(0.5f, 0.5f);
        if (pInvertDeviceOrientationReadings) {
            transformMatrix.preScale(-1f, 1f);
        }
        transformMatrix.preRotate(-1 * pCameraNativeOrientation);
        transformMatrix.preTranslate(-0.5f, -0.5f);
        pTextureTransform = MatrixOperatorGraphics.convertMatrixFromAndroidGraphicsMatrix(transformMatrix);


        int[] availableFDModes = (int[])cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        Integer maxFDCount = (Integer)cameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
        if (availableFDModes != null && availableFDModes.length > 1 && maxFDCount != null && maxFDCount > 0) {
            this.mFaceDetectSupported = true;
            int modeSum = 0;
            int[] var11 = availableFDModes;
            int var12 = availableFDModes.length;

            for(int var13 = 0; var13 < var12; ++var13) {
                int fdMode = var11[var13];
                modeSum += fdMode;
            }

            if (modeSum % 2 != 0) {
                this.mFaceDetectMode = 1;
            } else {
                this.mFaceDetectMode = 2;
            }
        }

        return true;
    }

    @Override
    public void startCaptureMaybeAsync(boolean needsPreview) {
        LogUtil.d(TAG, "startCaptureMaybeAsync mCameraState=" + mCameraState
                + ", pPreviewTextureId=" + pPreviewTextureId
                + ", cameraAvailable=" + cameraAvailable.get()
                + ", mPendingStartRequestWhenClosed=" + mPendingStartRequestWhenClosed
                + ", mPendingStartRequestWhenAvailable=" + mPendingStartRequestWhenAvailled
        );
        synchronized (mCameraStateLock) {
            if (mCameraState == CameraState.STOPPING) {
                mPendingStartRequestWhenClosed = true;
            } else if (mCameraState == CameraState.STOPPED) {
                if (!cameraAvailable.get()) {
                    mPendingStartRequestWhenAvailled = true;
                } else {
                    mNeedsPreview = needsPreview;
                    changeCameraStateAndNotify(CameraState.OPENING);
                    if (pPreviewTextureId == -1)
                        pPreviewTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                    if (pPreviewTextureId != -1) {
                        startPreview();
                    }
                }
            }
        }
    }

    protected void startPreview() {
        LogUtil.d(TAG, "startPreview");
        final CameraStateListener stateListener = new CameraStateListener();
        try {
            mCameraManager.openCamera(mCamera2Id, stateListener, pChannelHandler);
        } catch (CameraAccessException | IllegalArgumentException | SecurityException ex) {
            LogUtil.e(TAG, "allocate: manager.openCamera: ");
        }
    }

    @Override
    public void stopCaptureAndBlockUntilStopped() {
        // With Camera2 API, the capture is started asynchronously, which will cause problem if
        // stopCapture comes too quickly. Without stopping the previous capture properly, the
        // next startCapture will fail. So wait camera to be STARTED.
        LogUtil.d(TAG, "stopCaptureAndBlockUntilStopped");
        synchronized (mCameraStateLock) {
            if (mCameraState != CameraState.STOPPED &&
                    mCameraState != CameraState.STOPPING) {
                if (mPreviewSession != null && mCameraDevice != null) {
                    mPreviewSession.close();
                    mPreviewSession = null;
                    mCameraDevice.close();
                    changeCameraStateAndNotify(CameraState.STOPPING);
                }
            } else {
                LogUtil.w(TAG, "Camera is already stopped.");
            }
        }
    }

    @Override
    public void deallocate(boolean disconnect) {
        LogUtil.d(TAG, "deallocate " + disconnect);
        cameraSteady = false;

        mIsCameraTorchStarted = false;
        mIsmCameraZoomStarted = false;
        mIsExposureCompensationStarted = false;
        mSensorRect = null;
        mMaxZoom = 1.0f;

        mPendingStartRequestWhenClosed = false;
        mPendingStartRequestWhenAvailled = false;

        stopCaptureAndBlockUntilStopped();

        if (pPreviewTextureId != -1) {
            int[] textures = new int[]{pPreviewTextureId};
            GLES20.glDeleteTextures(1, textures, 0);
            LogUtil.d(this, "EGL >> deallocate glDeleteTextures texture=" + pPreviewTextureId );
            pPreviewTextureId = -1;
        }

        mCameraManager.unregisterAvailabilityCallback(availabilityCallback);
        cameraAvailable.set(false);
    }

    @Override
    public boolean isZoomSupported() {
        if (this.mMaxZoom > 1.0F) {
            return true;
        } else {
            CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
            if (cameraCharacteristics != null) {
                this.mMaxZoom = getMaxZoom(cameraCharacteristics);
            }

            return this.mMaxZoom > 1.0F;
        }
    }

    private boolean checkCameraState(CameraState... states){
        synchronized (mCameraStateLock){
            for (CameraState state : states) {
                if(mCameraState == state){
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public int setZoom(float zoomValue) {
        if (this.mPreviewSession == null || this.mPreviewRequestBuilder == null) {
            this.mCameraZoomFactor = zoomValue;
            return 0;
        }

        if (this.mIsmCameraZoomStarted && (double)Math.abs(this.mCameraZoomFactor - zoomValue) < 0.1) {
            return 0;
        }

        this.mCameraZoomFactor = zoomValue;

        LogUtil.d(TAG, "setCameraZoom api2 called zoomValue =" + zoomValue);
        if (zoomValue <= 0.0F) {
            return -1;
        } else {
            if (this.mSensorRect == null) {
                CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
                if (cameraCharacteristics == null) {
                    LogUtil.w(TAG, "warning cameraCharacteristics is null");
                    return -1;
                }

                this.mSensorRect = (Rect)cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                this.mMaxZoom = getMaxZoom(cameraCharacteristics);
            }

            if (Math.abs(this.mMaxZoom - 1.0F) < 0.001F) {
                LogUtil.w(TAG, "Camera " + this.mCamera2Id + " does not support camera zoom");
                return -1;
            } else {
                boolean needZoom = zoomValue >= 1.0F && zoomValue <= this.mMaxZoom && zoomValue != this.mLastZoomRatio;
                if (!needZoom) {
                    return -2;
                } else {
                    Rect zoomRect = this.cropRegionForZoom(zoomValue);
                    this.mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
                    this.mLastZoomRatio = zoomValue;
                    if (this.mPreviewSession != null) {
                        try {
                            this.mIsmCameraZoomStarted = true;
                            this.mPreviewSession.setRepeatingRequest(this.mPreviewRequestBuilder.build(), null, null);
                        } catch (CameraAccessException var5) {
                            var5.printStackTrace();
                            return -3;
                        } catch (IllegalStateException var6) {
                            var6.printStackTrace();
                            return -4;
                        } catch (IllegalArgumentException var7) {
                            var7.printStackTrace();
                            return -4;
                        }
                    }

                    return 0;
                }
            }
        }
    }

    private Rect cropRegionForZoom(float ratio) {
        int xCenter = this.mSensorRect.width() / 2;
        int yCenter = this.mSensorRect.height() / 2;
        int xDelta = (int)(0.5F * (float)this.mSensorRect.width() / ratio);
        int yDelta = (int)(0.5F * (float)this.mSensorRect.height() / ratio);
        return new Rect(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta);
    }

    @Override
    public float getMaxZoom() {
        if (this.mMaxZoom <= 1.0F) {
            CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
            if (cameraCharacteristics != null) {
                this.mMaxZoom = getMaxZoom(cameraCharacteristics);
            }
        }

        return this.mMaxZoom;
    }

    @Override
    public boolean isTorchSupported() {
        CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
        if (cameraCharacteristics == null) {
            LogUtil.w(TAG, "warning cameraCharacteristics is null");
            return false;
        } else {
            Boolean available = (Boolean)cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            return available == null ? false : available;
        }
    }

    @Override
    public int setTorchMode(boolean isOn) {
        int mode = isOn ? 1 : -1;
        if (this.mPreviewSession == null || this.mPreviewRequestBuilder == null) {
            this.mTorchMode = mode;
            return 0;
        }

        if (this.mIsCameraTorchStarted && this.mTorchMode == mode) {
            return 0;
        }

        this.mTorchMode = mode;

        LogUtil.d(TAG, "setTorchMode called camera api2, isOn: " + isOn);
        CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
        if (cameraCharacteristics == null) {
            LogUtil.w(TAG, "warning cameraCharacteristics is null");
            return -1;
        } else {
            Boolean available = (Boolean)cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            boolean isFlashSupported = available == null ? false : available;
            LogUtil.w(TAG, "setTorchMode isFlashSupported: " + (isFlashSupported ? "true" : "false"));
            if (isFlashSupported) {
                if (isOn) {
                    this.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, 2);
                } else {
                    this.mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, 0);
                }

                if (this.mPreviewSession != null) {
                    try {
                        this.mPreviewSession.setRepeatingRequest(this.mPreviewRequestBuilder.build(), null, null);
                        this.mIsCameraTorchStarted = true;
                        return 0;
                    } catch (CameraAccessException var6) {
                        var6.printStackTrace();
                    } catch (IllegalStateException var7) {
                        var7.printStackTrace();
                    } catch (IllegalArgumentException var8) {
                        var8.printStackTrace();
                    } catch (NoClassDefFoundError var9) {
                        var9.printStackTrace();
                    }
                }
            } else {
                LogUtil.w(TAG, "flash is not supported");
            }

            return -1;
        }
    }

    @Override
    public void setExposureCompensation(int value) {
        if (this.mPreviewSession == null || this.mPreviewRequestBuilder == null) {
            this.mCameraExposureCompensation = value;
            return;
        }

        if (this.mIsExposureCompensationStarted && this.mCameraExposureCompensation == value) {
            return;
        }

        this.mCameraExposureCompensation = value;

        LogUtil.d(TAG, "setExposureCompensation:" + value);
        CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
        if (cameraCharacteristics == null) {
            return;
        } else {
            Rational step = (Rational)cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            Range<Integer> range = (Range)cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            if (range != null && step != null) {
                int max = (Integer)range.getUpper();
                int min = (Integer)range.getLower();
                LogUtil.d(TAG, "compensation step=" + step + ", min=" + min + ", max=" + max);
                if (value > max) {
                    value = max;
                }

                if (value < min) {
                    value = min;
                }

                if (this.mPreviewSession != null) {
                    try {
                        this.mIsExposureCompensationStarted = true;
                        this.mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value);
                        this.mPreviewSession.setRepeatingRequest(this.mPreviewRequestBuilder.build(),null, null);
                    } catch (CameraAccessException var8) {
                        var8.printStackTrace();
                    } catch (IllegalStateException var9) {
                        var9.printStackTrace();
                    } catch (IllegalArgumentException var10) {
                        var10.printStackTrace();
                    } catch (NoClassDefFoundError var11) {
                        var11.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public int getExposureCompensation() {
        return mCameraExposureCompensation;
    }

    @Override
    public int getMinExposureCompensation() {
        CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
        Range<Integer> range = (Range)cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return range.getLower();
    }

    @Override
    public int getMaxExposureCompensation() {
        CameraCharacteristics cameraCharacteristics = getCameraCharacteristics(this.mCamera2Id);
        Range<Integer> range = (Range)cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return range.getUpper();
    }

    private byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        if (ySize != mImageYSize || uvSize != mImageUVSize ||
            mBuffer == null) {
            mBuffer = new byte[ySize + uvSize * 2];
            mImageYSize = ySize;
            mImageUVSize = uvSize;
        }

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert (image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(mBuffer, 0, ySize);
            pos += ySize;
        } else {
            int yBufferPos = width - rowStride; // not an actual position
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride - width;
                yBuffer.position(yBufferPos);
                yBuffer.get(mBuffer, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert (rowStride == image.getPlanes()[1].getRowStride());
        assert (pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            vBuffer.put(1, (byte) 0);
            if (uBuffer.get(0) == 0) {
                vBuffer.put(1, (byte) 255);
                if (uBuffer.get(0) == 255) {
                    vBuffer.put(1, savePixel);
                    vBuffer.get(mBuffer, ySize, uvSize);

                    return mBuffer; // shortcut
                }
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                mBuffer[pos++] = vBuffer.get(vuPos);
                mBuffer[pos++] = uBuffer.get(vuPos);
            }
        }

        return mBuffer;
    }
}
