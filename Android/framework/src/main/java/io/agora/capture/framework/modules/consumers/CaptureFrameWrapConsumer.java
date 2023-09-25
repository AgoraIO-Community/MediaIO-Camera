package io.agora.capture.framework.modules.consumers;

import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.video.camera.VideoCaptureFrame;

public class CaptureFrameWrapConsumer implements IVideoConsumer {

    private final ICaptureFrameConsumer frameConsumer;

    public CaptureFrameWrapConsumer(ICaptureFrameConsumer frameConsumer){
        this.frameConsumer = frameConsumer;
    }

    @Override
    public void onConsumeFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context) {
        if(frameConsumer != null){
            frameConsumer.onConsumeFrame(frame, context);
        }
    }

    @Override
    public void connectChannel(int channelId) {
        // connect to nothing
    }

    @Override
    public void disconnectChannel(int channelId) {

    }

    @Override
    public void setMirrorMode(int mode) {

    }

    @Override
    public Object getDrawingTarget() {
        return frameConsumer;
    }

    @Override
    public int onMeasuredWidth() {
        return 0;
    }

    @Override
    public int onMeasuredHeight() {
        return 0;
    }

    @Override
    public void recycle() {

    }

    @Override
    public String getId() {
        if(frameConsumer != null){
            return frameConsumer.hashCode() + "";
        }
        return null;
    }
}
