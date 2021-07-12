package io.agora.capture.framework.modules.producers;

import io.agora.capture.video.camera.VideoCaptureFrame;

public interface IVideoProducer {
    void connectChannel(int channelId);
    void pushVideoFrame(VideoCaptureFrame frame);
    void disconnect();
}
