// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.agora.capture.video.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
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
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.agora.capture.framework.gles.core.GlUtil;
import io.agora.capture.framework.util.CameraUtils;
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
            if (mPendingStartRequest) {
                mPendingStartRequest = false;
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
                    mPreviewSession.setRepeatingRequest(
                            mPreviewRequest, null, null);
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

    private volatile boolean mPendingStartRequest;

    private Range<Integer> mAeFpsRange;
    private CameraState mCameraState = CameraState.STOPPED;
    private Surface mSurface;

    private int mImageYSize;
    private int mImageUVSize;
    private byte[] mBuffer;

    private CameraCharacteristics getCameraCharacteristics(String id) {
        try {
            return mCameraManager.getCameraCharacteristics(id);
        } catch (CameraAccessException | IllegalArgumentException | AssertionError ex) {
            LogUtil.e(TAG, "getCameraCharacteristics: ");
        }
        return null;
    }

    private void createPreviewObjectsAndStartPreviewOrFail() {
        if (createPreviewObjectsAndStartPreview()) return;

        changeCameraStateAndNotify(CameraState.STOPPED);
        LogUtil.e(TAG, "Error starting or restarting preview");
    }

    private boolean createPreviewObjectsAndStartPreview() {
        if (mCameraDevice == null) return false;

        mImageReader = ImageReader.newInstance(pCaptureFormat.getWidth(),
                pCaptureFormat.getHeight(), pCaptureFormat.getPixelFormat(), 2);
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
        LogUtil.d(TAG, "allocate: requested width: " + width + " height: " + height + " fps: " + frameRate);

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
        final FrameRateRange aeRange =
                CameraUtils.getClosestFrameRateRangeExactly(ranges, frameRate * 1000);
        mAeFpsRange = new Range<Integer>(
                aeRange.min / fpsUnitFactor, aeRange.max / fpsUnitFactor);
        LogUtil.d(TAG, "allocate: fps set to [" + mAeFpsRange.getLower() + "-" + mAeFpsRange.getUpper() + "]");

        mPreviewWidth = closestSupportedSize.getWidth();
        mPreviewHeight = closestSupportedSize.getHeight();

        // |mCaptureFormat| is also used to configure the ImageReader.
        pCaptureFormat = new VideoCaptureFormat(mCameraId, closestSupportedSize.getWidth(),
                closestSupportedSize.getHeight(),
                aeRange.max / fpsUnitFactor,
                ImageFormat.YUV_420_888, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        pCameraNativeOrientation =
                cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        pInvertDeviceOrientationReadings =
                cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
                == CameraCharacteristics.LENS_FACING_FRONT;

        return true;
    }

    @Override
    public void startCaptureMaybeAsync(boolean needsPreview) {
        LogUtil.d(TAG, "startCaptureMaybeAsync " + pPreviewTextureId);
        synchronized (mCameraStateLock) {
            if (mCameraState == CameraState.STOPPING) {
                mPendingStartRequest = true;
            } else if (mCameraState == CameraState.STOPPED) {
                mNeedsPreview = needsPreview;
                changeCameraStateAndNotify(CameraState.OPENING);
                if (pPreviewTextureId == -1) pPreviewTextureId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                if (pPreviewTextureId != -1) startPreview();
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
        stopCaptureAndBlockUntilStopped();

        if (pPreviewTextureId != -1) {
            int[] textures = new int[]{pPreviewTextureId};
            GLES20.glDeleteTextures(1, textures, 0);
            LogUtil.d(this, "EGL >> deallocate glDeleteTextures texture=" + pPreviewTextureId );
            pPreviewTextureId = -1;
        }
    }

    @Override
    public boolean isZoomSupported() {
        return false;
    }

    @Override
    public int setZoom(float zoomValue) {
        return 0;
    }

    @Override
    public float getMaxZoom() {
        return 0;
    }

    @Override
    public boolean isTorchSupported() {
        return false;
    }

    @Override
    public int setTorchMode(boolean isOn) {
        return 0;
    }

    @Override
    public void setExposureCompensation(int value) {

    }

    @Override
    public int getExposureCompensation() {
        return 0;
    }

    @Override
    public int getMinExposureCompensation() {
        return 0;
    }

    @Override
    public int getMaxExposureCompensation() {
        return 0;
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
