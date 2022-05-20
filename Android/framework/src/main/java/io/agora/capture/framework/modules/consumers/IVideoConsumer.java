package io.agora.capture.framework.modules.consumers;

import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.video.camera.VideoCaptureFrame;

public interface IVideoConsumer {
    int TYPE_ON_SCREEN = 0;
    int TYPE_OFF_SCREEN = 1;

    void onConsumeFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context);
    void connectChannel(int channelId);
    void disconnectChannel(int channelId);

    void setMirrorMode(int mode);

    /**
     * Give a chance for subclasses to return a drawing target object.
     * This drawing target can only be either a Surface or a
     * SurfaceTexture.
     * The drawing target is the most important parameter to identify
     * different consumers as the same. If multiple consumer
     * instances return the same drawing target object, they will
     * be considered to be the same and cannot exist in the consumer
     * list at the same time, only the latest one attached is
     * working)
     * @return A target that is used to draw by OpenGL, nullable
     */
    Object getDrawingTarget();

    int onMeasuredWidth();
    int onMeasuredHeight();

    /**
     * Called when the consumer is intended to stay
     * in a channel but its rendering pauses maybe
     * because this consumer is not current.
     */
    void recycle();

    /**
     * Used to identify different consumer instances
     * that are considered to be the same.
     * The ids of the consumers take effect when
     * different drawing targets are returned.
     * Consumers returning different drawing object with
     * the same id can be considered as the same and
     * only the one most recently attached works, the
     * earlier ones will be removed from the consumer list.
     * Null or empty ids will not be valid and will not
     * be used to identify any consumers.
     * @return id of this consumer, nullable.
     */
    String getId();
}
