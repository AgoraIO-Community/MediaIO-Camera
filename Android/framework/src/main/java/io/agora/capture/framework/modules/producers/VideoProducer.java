package io.agora.capture.framework.modules.producers;

import android.opengl.Matrix;
import android.os.Handler;

import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.video.camera.VideoCaptureFrame;
import io.agora.capture.video.camera.VideoModule;

public abstract class VideoProducer implements IVideoProducer {
    private static final String TAG = VideoProducer.class.getSimpleName();

    private VideoChannel videoChannel;
    protected volatile Handler pChannelHandler;

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
                //frame.surfaceTexture.getTransformMatrix(frame.textureTransform);
                Matrix.setIdentityM(frame.textureTransform, 0);
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
