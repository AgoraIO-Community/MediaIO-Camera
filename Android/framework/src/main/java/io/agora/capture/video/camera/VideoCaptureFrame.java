package io.agora.capture.video.camera;

import android.graphics.SurfaceTexture;

import java.util.Arrays;

public class VideoCaptureFrame {
    /**
     * Frame information, such as the width and height.
     * Frames directly created from the system camera
     * will by default have pixel format NV21 and texture
     * format TEXTURE_OES
     */
    public VideoCaptureFormat format;

    public int textureId;

    /**
     * The texture transformation matrix obtained from
     * SurfaceTexture.updateTexImage() and
     * SurfaceTexture.getTransformMatrix()
     */
    public float[] textureTransform;

    public float[] rotatedTextureTransform;

    /**
     * If the frames are from system texture, they
     * must contain the texture content in the
     * preview surface textures.
     * It is particularly useful when obtaining the image
     * size and transformation matrix, and keeping the
     * texture consistent across threads.
     */
    public SurfaceTexture surfaceTexture;

    /**
     * The degrees that the image needs to be rotated
     * clockwise. If This frame is captured from system
     * camera, this rotation should be the camera
     * orientation (interpreted to degrees).
     */
    public int rotation;

    public long timestamp;

    /**
     * Raw image data from the system camera. For android
     * devices this format is NV21 by default.
     */
    public byte[] image;

    /**
     * Whether this image is mirrored. The definition of
     * mirror mode keeps align with the system camera.
     * It describes the current mirror state of current frame.
     * By default, the frames captured by the front camera
     * are mirrored, and those by the back camera are not.
     */
    public boolean mirrored;

    public VideoCaptureFrame(VideoCaptureFormat format, SurfaceTexture texture,
                             int textureId, byte[] image, float[] textureTransform,
                             long timestamp, int rotation, boolean mirror) {
        this.format = format;
        this.textureId = textureId;
        this.surfaceTexture = texture;
        this.image = image;
        this.textureTransform = textureTransform;
        this.timestamp = timestamp;
        this.rotation = rotation;
        this.mirrored = mirror;
    }

    public VideoCaptureFrame(VideoCaptureFrame frame) {
        this.format = frame.format.copy();
        this.textureId = frame.textureId;
        this.surfaceTexture = frame.surfaceTexture;
        this.image = frame.image;
        this.textureTransform = frame.textureTransform;
        this.timestamp = frame.timestamp;
        this.rotation = frame.rotation;
        this.mirrored = frame.mirrored;
    }

    public String toString() {
        return "VideoCaptureFrame{" +
                "mFormat=" + format +
                ", mRotation=" + rotation +
                ", mMirror=" + mirrored +
                ", mTimeStamp=" + timestamp +
                ", mTextureId=" + textureId +
                ", mTexMatrix=" + Arrays.toString(textureTransform) +
                '}';
    }
}