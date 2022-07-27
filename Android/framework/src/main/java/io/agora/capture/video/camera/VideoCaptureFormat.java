// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.agora.capture.video.camera;

import androidx.annotation.NonNull;

public class VideoCaptureFormat {
    private int mWidth;
    private int mHeight;
    private int mFrameRate;
    private int mPixelFormat;
    private int mTexFormat;
    private int mCameraFacing;

    VideoCaptureFormat(int cameraFacing, int width, int height, int frameRate, int pixelFormat, int texFormat) {
        mCameraFacing = cameraFacing;
        mWidth = width;
        mHeight = height;
        mFrameRate = frameRate;
        mPixelFormat = pixelFormat;
        mTexFormat = texFormat;
    }

    public int getCameraFacing() {
        return mCameraFacing;
    }

    public void setCameraFacing(int cameraFacing) {
        this.mCameraFacing = cameraFacing;
    }

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        mWidth = width;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setHeight(int height) {
        mHeight = height;
    }

    public int getFrameRate() {
        return mFrameRate;
    }

    public int getPixelFormat() {
        return mPixelFormat;
    }

    public void setPixelFormat(int format) {
        mPixelFormat = format;
    }

    public int getTexFormat() {
        return mTexFormat;
    }

    public void setTexFormat(int format) {
        mTexFormat = format;
    }

    public @NonNull String toString() {
        return "VideoCaptureFormat{" +
                "mFormat=" + mPixelFormat +
                ", mCameraFacing=" + mCameraFacing +
                ", mFrameRate=" + mFrameRate +
                ", mWidth=" + mWidth +
                ", mHeight=" + mHeight +
                '}';
    }

    public VideoCaptureFormat copy() {
        return new VideoCaptureFormat(mCameraFacing,
                mWidth, mHeight, mFrameRate, mPixelFormat, mTexFormat);
    }
}
