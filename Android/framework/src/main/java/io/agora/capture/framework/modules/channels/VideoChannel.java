package io.agora.capture.framework.modules.channels;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGLContext;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.agora.capture.framework.gles.ProgramTexture2d;
import io.agora.capture.framework.gles.ProgramTextureOES;
import io.agora.capture.framework.gles.ProgramWatermark;
import io.agora.capture.framework.gles.core.EglCore;
import io.agora.capture.framework.modules.consumers.IVideoConsumer;
import io.agora.capture.framework.modules.processors.IPreprocessor;
import io.agora.capture.framework.modules.processors.RotateProcessor;
import io.agora.capture.framework.modules.producers.IVideoProducer;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.video.camera.VideoCaptureFrame;

public class VideoChannel extends HandlerThread {
    private static final String TAG = VideoChannel.class.getSimpleName();

    private int mChannelId;
    private boolean mOffScreenMode;

    private IVideoProducer mProducer;
    private List<IVideoConsumer> mOnScreenConsumers = new ArrayList<>();
    private List<IVideoConsumer> mOffScreenConsumers = new ArrayList<>();
    private int mOnScreenConsumerMirrorMode;
    private Bitmap watermarkBitmap;
    private float watermarkAlpha;
    private IPreprocessor mPreprocessor;

    // Used to rotate the image to normal direction according
    // to texture transformation matrix and possibly surface
    // rotation if the surface is not in natural rotation.
    private RotateProcessor mRotateProcessor;

    private Handler mHandler;

    private ChannelContext mContext;
    private EGLSurface mDummyEglSurface;

    public VideoChannel(Context context, int id) {
        super(ChannelManager.ChannelID.toString(id));
        mChannelId = id;
        mContext = new ChannelContext();
        mContext.setContext(context);
    }

    void setPreprocessor(IPreprocessor preprocessor) {
        mPreprocessor = preprocessor;
    }

    @Override
    public void run() {
        init();
        super.run();
        release();
    }

    private void init() {
        LogUtil.i(TAG, "channel opengl init");
        initOpenGL();
        initPreprocessor();
        initRotateProcessor();
        onChannelContextCreated();
    }

    // The initialization phase for sub classes
    protected void onChannelContextCreated() {

    }

    private void initOpenGL() {
        EglCore eglCore = new EglCore();
        mContext.setEglCore(eglCore);
        mDummyEglSurface = eglCore.createOffscreenSurface(1, 1);
        eglCore.makeCurrent(mDummyEglSurface);
        mContext.setProgramWatermark(new ProgramWatermark());
        mContext.setProgram2D(new ProgramTexture2d());
        mContext.setProgramOES(new ProgramTextureOES());
    }

    private void initPreprocessor() {
        if (mPreprocessor != null) {
            mPreprocessor.initPreprocessor();
        }
    }

    private void initRotateProcessor() {
        mRotateProcessor = new RotateProcessor();
        mRotateProcessor.init(mContext);
    }

    private void release() {
        LogUtil.i(TAG, "channel opengl release");
        releasePreprocessor();
        releaseRotateProcessor();
        releaseOpenGL();
    }

    private void releasePreprocessor() {
        if (mPreprocessor != null) {
            mPreprocessor.releasePreprocessor(getChannelContext());
            mPreprocessor = null;
        }
    }

    private void releaseRotateProcessor() {
        if (mRotateProcessor != null) {
            mRotateProcessor.release(mContext);
            mRotateProcessor = null;
        }
    }

    private void releaseOpenGL() {
        ProgramWatermark programWatermark = mContext.getProgramWatermark();
        if(programWatermark != null){
            programWatermark.destroyProgram();
        }
        mContext.getProgram2D().release();
        mContext.getProgramOES().release();
        mContext.getEglCore().releaseSurface(mDummyEglSurface);
        mContext.getEglCore().release();
        mContext = null;
    }

    public ChannelContext getChannelContext() {
        return mContext;
    }

    IPreprocessor getPreprocessor() {
        return mPreprocessor;
    }

    void startChannel() {
        if (isRunning()) {
            return;
        }
        start();
        mHandler = new Handler(getLooper());
    }

    public Handler getHandler() {
        checkThreadRunningState();
        return mHandler;
    }

    void stopChannel() {
        LogUtil.i(TAG, "StopChannel");
        if (mProducer != null) {
            mProducer.disconnect();
            mProducer = null;
        }

        if (!mOffScreenConsumers.isEmpty()) {
            for (IVideoConsumer consumer : mOffScreenConsumers) {
                consumer.recycle();
                consumer.disconnectChannel(mChannelId);
            }
        }
        mOffScreenConsumers.clear();

        removeOnScreenConsumer();
        quitSafely();
    }

    private void resetOpenGLSurface() {
        mHandler.post(this::makeDummySurfaceCurrent);
    }

    private void removeOnScreenConsumer() {
        if (mOnScreenConsumers != null) {
            mHandler.post(() -> {
                for (IVideoConsumer consumer : mOnScreenConsumers) {
                    consumer.recycle();
                    consumer.disconnectChannel(mChannelId);
                }

                mOnScreenConsumers.clear();
                // To remove on-screen consumer, we need
                // to reset the GLSurface and maintain
                // the OpenGL context properly.
                makeDummySurfaceCurrent();
            });
        }
    }

    public boolean isRunning() {
        return isAlive();
    }

    void connectProducer(IVideoProducer producer) {
        checkThreadRunningState();
        if (mProducer == null) {
            mProducer = producer;
        }
    }

    public void disconnectProducer() {
        checkThreadRunningState();
        mProducer = null;
    }

    /**
     * Attach a consumer to the channel
     * @param consumer consumer to be attached
     * @param type on-screen or off-screen
     * @see io.agora.capture.framework.modules.consumers.IVideoConsumer
     */
    public void connectConsumer(final IVideoConsumer consumer, int type) {
        if (consumer == null) return;
        checkThreadRunningState();

        mHandler.post(() -> {
            if (type == IVideoConsumer.TYPE_ON_SCREEN) {
                removeSameConsumers(mOnScreenConsumers,
                        consumer.getDrawingTarget(), consumer.getId());
                mOnScreenConsumers.add(consumer);
                consumer.setMirrorMode(mOnScreenConsumerMirrorMode);
                consumer.setWatermark(watermarkBitmap, watermarkAlpha);
            } else if (type == IVideoConsumer.TYPE_OFF_SCREEN) {
                removeSameConsumers(mOffScreenConsumers,
                        consumer.getDrawingTarget(), consumer.getId());
                mOffScreenConsumers.add(consumer);
            }
        });
    }

    private void removeSameConsumers(List<IVideoConsumer> consumers,
                                     Object newTarget, String newId) {
        List<IVideoConsumer> removeList = new ArrayList<>();

        for (IVideoConsumer consumer : consumers) {
            Object target = consumer.getDrawingTarget();
            String t = consumer.getId();

            if (target == newTarget) {
                // Consumers with the same drawing target
                // should be considered the same
                removeList.add(consumer);
            } else if (!TextUtils.isEmpty(t) && t.equals(newId)) {
                removeList.add(consumer);
            }
        }

        for (IVideoConsumer consumer : removeList) {
            consumer.recycle();
            consumers.remove(consumer);
        }

        removeList.clear();
    }

    public void setOnScreenConsumerMirror(int mode) {
        mOnScreenConsumerMirrorMode = mode;
        mHandler.post(() -> {
            for (IVideoConsumer consumer : mOnScreenConsumers) {
                consumer.setMirrorMode(mOnScreenConsumerMirrorMode);
            }
        });
    }

    public void setWatermark(@Nullable Bitmap watermarkBitmap, float watermarkAlpha) {
        if (this.watermarkBitmap != null) this.watermarkBitmap.recycle();
        this.watermarkBitmap = watermarkBitmap;
        this.watermarkAlpha = watermarkAlpha;

        mHandler.post(() -> {
            for (IVideoConsumer consumer : mOnScreenConsumers) {
                consumer.setWatermark(this.watermarkBitmap, watermarkAlpha);
            }
        });
    }

    @Nullable
    public Bitmap getWatermarkBitmap() {
        return watermarkBitmap;
    }

    public void setWatermarkAlpha(float watermarkAlpha) {
        this.watermarkAlpha = watermarkAlpha;

        mHandler.post(() -> {
            for (IVideoConsumer consumer : mOnScreenConsumers) {
                consumer.setWatermark(this.watermarkBitmap, watermarkAlpha);
            }
        });
    }

    public float getWatermarkAlpha() {
        return watermarkAlpha;
    }

    public void disconnectConsumer(IVideoConsumer consumer) {
        checkThreadRunningState();

        mHandler.post(() -> {
            if (mOnScreenConsumers.contains(consumer)) {
                consumer.recycle();
                mOnScreenConsumers.remove(consumer);
                LogUtil.d(TAG, "On-screen consumer disconnected:" + consumer);
            } else if (mOffScreenConsumers.contains(consumer)) {
                consumer.recycle();
                mOffScreenConsumers.remove(consumer);
                LogUtil.d(TAG, "Off-screen consumer disconnected:" + consumer);
            }

            if (mOnScreenConsumers.isEmpty() &&
                    mOffScreenConsumers.isEmpty()) {
                // If there's no consumer after remove
                // this off screen consumer, the OpenGL
                // drawing surface must be reset
                resetOpenGLSurface();
            }
        });
    }

    public void enablePreProcess(boolean enabled) {
        if (mPreprocessor != null) {
            mHandler.post(() -> mPreprocessor.enablePreProcess(enabled));
        }
    }

    public void pushVideoFrame(VideoCaptureFrame frame) {
        checkThreadRunningState();

        if (mPreprocessor != null) {
            frame = mPreprocessor.onPreProcessFrame(frame, getChannelContext());
            makeDummySurfaceCurrent();
        }

        if (mRotateProcessor != null) {
            // Rotate the image to the final state.
            // Further rotation procedure will not be
            // necessary for all consumers.
            frame = mRotateProcessor.process(frame, getChannelContext());
            makeDummySurfaceCurrent();
        }

        if (mOnScreenConsumers.size() > 0) {
            // Multiple on-screen consumers are drawn there
            for (IVideoConsumer consumer : mOnScreenConsumers) {
                consumer.onConsumeFrame(frame, mContext);
                makeDummySurfaceCurrent();
            }
        }

        if (mOnScreenConsumers.size() > 0 || mOffScreenMode) {
            // If there is no on-screen consumers connected,
            // the off-screen consumers cannot actually be
            // called, unless the channel runs in off-screen
            // mode.
            for (IVideoConsumer consumer : mOffScreenConsumers) {
                consumer.onConsumeFrame(frame, mContext);
                makeDummySurfaceCurrent();
            }
        }
    }

    private void makeDummySurfaceCurrent() {
        // Every time after the preprocessor or consumers do
        // their jobs, we may need to restore the original
        // dummy EGL surface. Thus the current EGL context
        // will remain consistent even if the surfaces or
        // pixel buffers used by preprocessors or consumers
        // are destroyed in or out of the OpenGL threads.
        if (!mContext.isCurrent(mDummyEglSurface)) {
            mContext.makeCurrent(mDummyEglSurface);
        }
    }

    private void checkThreadRunningState() {
        if (!isAlive()) {
            throw new IllegalStateException("Video Channel is not alive");
        }
    }

    void enableOffscreenMode(boolean enabled) {
        mOffScreenMode = enabled;
    }

    public static class ChannelContext {
        private Context mContext;
        private EglCore mEglCore;
        private ProgramTexture2d mProgram2D;
        private ProgramTextureOES mProgramOES;
        @Nullable
        private ProgramWatermark mProgramWatermark;

        public Context getContext() {
            return mContext;
        }

        public void setContext(Context context) {
            this.mContext = context;
        }

        public EglCore getEglCore() {
            return mEglCore;
        }

        private void setEglCore(EglCore mEglCore) {
            this.mEglCore = mEglCore;
        }

        public EGLContext getEglContext() {
            return getEglCore().getEGLContext();
        }

        @Nullable
        public ProgramWatermark getProgramWatermark() {
            return mProgramWatermark;
        }

        public void setProgramWatermark(@Nullable ProgramWatermark mFullFrameRectTexture2D) {
            this.mProgramWatermark = mFullFrameRectTexture2D;
        }

        public ProgramTexture2d getProgram2D() {
            return mProgram2D;
        }

        private void setProgram2D(ProgramTexture2d mFullFrameRectTexture2D) {
            this.mProgram2D = mFullFrameRectTexture2D;
        }

        public ProgramTextureOES getProgramOES() {
            return mProgramOES;
        }

        private void setProgramOES(ProgramTextureOES mTextureOES) {
            this.mProgramOES = mTextureOES;
        }

        public EGLSurface getCurrentSurface() {
            return mEglCore.getCurrentDrawingSurface();
        }

        public void makeCurrent(EGLSurface surface) {
            mEglCore.makeCurrent(surface);
        }

        public boolean isCurrent(EGLSurface surface) {
            return mEglCore.isCurrent(surface);
        }
    }
}
