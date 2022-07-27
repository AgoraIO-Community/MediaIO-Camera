package io.agora.capture.framework.modules.consumers;

import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.video.camera.VideoCaptureFrame;

public interface ICaptureFrameConsumer {

    void onConsumeFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context);
}
