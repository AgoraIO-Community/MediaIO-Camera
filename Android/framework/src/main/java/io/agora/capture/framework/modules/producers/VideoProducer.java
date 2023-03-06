package io.agora.capture.framework.modules.producers;

import android.os.Handler;

import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.video.camera.VideoCaptureFrame;
import io.agora.capture.video.camera.VideoModule;

public abstract class VideoProducer implements IVideoProducer {
    private static final String TAG = VideoProducer.class.getSimpleName();

    private VideoChannel videoChannel;
    protected volatile Handler pChannelHandler;

    private VideoCaptureFrame pendingVideoFrame = null;
    private final Object pendingVideoFrameLock = new Object();
    private volatile int draggingFrameCount = 0;

    private final Runnable consumeVideoFrameRun = () -> {
        VideoCaptureFrame frame;
        synchronized (pendingVideoFrameLock){
            if (pendingVideoFrame == null) {
                return;
            }
            frame = pendingVideoFrame;
        }


        // The capture utilizes the environment OpenGL
        // context for preview texture, so the capture
        // thread and video channel thread use their
        // shared OpenGL context.
        // Thus updateTexImage() is valid here.
        frame.surfaceTexture.updateTexImage();
        if (frame.textureTransform == null) frame.textureTransform = new float[16];
        frame.surfaceTexture.getTransformMatrix(frame.textureTransform);

        if (videoChannel != null) {
            videoChannel.pushVideoFrame(frame);
        }
        synchronized (pendingVideoFrameLock){
            pendingVideoFrame = null;
        }
    };

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

        synchronized (pendingVideoFrameLock) {
            if (pendingVideoFrame == null) {
                if (draggingFrameCount != 0) {
                    LogUtil.d(TAG, "dragging frame count: " + draggingFrameCount);
                    draggingFrameCount = 0;
                }
                pendingVideoFrame = frame;
                pChannelHandler.removeCallbacks(consumeVideoFrameRun);
                pChannelHandler.post(consumeVideoFrameRun);
            } else {
                draggingFrameCount++;
            }
        }
    }

    @Override
    public void disconnect() {
        LogUtil.i(TAG, "disconnect");

        synchronized (pendingVideoFrameLock){
            pendingVideoFrame = null;
        }

        if(pChannelHandler != null){
            pChannelHandler.removeCallbacks(consumeVideoFrameRun);
            pChannelHandler = null;
        }

        if (videoChannel != null) {
            videoChannel.disconnectProducer();
            videoChannel = null;
        }
    }

}
