package io.agora.capture.framework.modules.producers;

import android.hardware.Camera;
import android.os.Handler;

import io.agora.capture.framework.gles.MatrixOperator;
import io.agora.capture.framework.gles.MatrixOperatorGraphics;
import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.video.camera.VideoCaptureFrame;
import io.agora.capture.video.camera.VideoModule;

public abstract class VideoProducer implements IVideoProducer {
    private static final String TAG = VideoProducer.class.getSimpleName();

    private VideoChannel videoChannel;
    protected volatile Handler pChannelHandler;

    private final MatrixOperator rotateMatrixOperator = new MatrixOperatorGraphics(MatrixOperator.ScaleType.FitXY);

    @Override
    public void connectChannel(int channelId) {
        videoChannel = VideoModule.instance().connectProducer(this, channelId);
        pChannelHandler = videoChannel.getHandler();
    }

    @Override
    public void pushVideoFrame(final VideoCaptureFrame frame) {
        if (pChannelHandler == null) {
            return;
        }

        pChannelHandler.post(() -> {
            try {
                // The capture utilizes the environment OpenGL
                // context for preview texture, so the capture
                // thread and video channel thread use their
                // shared OpenGL context.
                // Thus updateTexImage() is valid here.
                frame.surfaceTexture.updateTexImage();
                if (frame.textureTransform == null) frame.textureTransform = new float[16];
                frame.surfaceTexture.getTransformMatrix(frame.textureTransform);

                rotateMatrixOperator.setTransformMatrix(frame.textureTransform);
                rotateMatrixOperator.setPreFlipH(frame.format.getCameraId() == Camera.CameraInfo.CAMERA_FACING_FRONT);
                rotateMatrixOperator.setRotation(frame.rotation);
                frame.rotatedTextureTransform = rotateMatrixOperator.getFinalMatrix();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            if (videoChannel != null) {
                videoChannel.pushVideoFrame(frame);
            }
        });
    }

    @Override
    public void disconnect() {
        LogUtil.i(TAG, "disconnect");

        if (videoChannel != null) {
            videoChannel.disconnectProducer();
            videoChannel = null;
        }
    }
}
