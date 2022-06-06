package io.agora.capture.framework.modules.consumers;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;

import io.agora.capture.framework.gles.ProgramTexture2d;
import io.agora.capture.framework.gles.ProgramTextureOES;
import io.agora.capture.framework.gles.core.EglCore;
import io.agora.capture.framework.modules.channels.ChannelManager;
import io.agora.capture.framework.modules.channels.VideoChannel;
import io.agora.capture.framework.util.LogUtil;
import io.agora.capture.framework.util.MatrixOperator;
import io.agora.capture.video.camera.Constant;
import io.agora.capture.video.camera.VideoCaptureFrame;
import io.agora.capture.video.camera.VideoModule;

public abstract class BaseWindowConsumer implements IVideoConsumer {
    static final int CHANNEL_ID = ChannelManager.ChannelID.CAMERA;

    private final VideoModule videoModule;
    private VideoChannel videoChannel;
    private int mirrorMode;
    private String mId;

    private EGLSurface drawingEglSurface;
    volatile boolean needResetSurface = true;
    volatile boolean surfaceDestroyed;

    private final MatrixOperator mMVPMatrix;

    private final boolean uniqueGLEnv;
    private volatile boolean uniqueIsRunning = false;
    private volatile boolean uniqueIsQuit = false;
    private EglCore uniqueEglCore;
    private HandlerThread uniqueThread;
    private Handler uniqueThreadHandler;
    private ProgramTextureOES uniqueProgramOES;
    private ProgramTexture2d uniqueProgram2d;

    protected BaseWindowConsumer(VideoModule videoModule, boolean uniqueGLEnv, @MatrixOperator.ScaleType int scaleTyp) {
        this.videoModule = videoModule;
        mMVPMatrix = new MatrixOperator(scaleTyp);
        this.uniqueGLEnv = uniqueGLEnv;
    }

    @Override
    public void connectChannel(int channelId) {
        videoChannel = videoModule.connectConsumer(this, channelId, IVideoConsumer.TYPE_ON_SCREEN);
        if (uniqueGLEnv) {
            initUniqueGLEnv(videoChannel.getChannelContext().getEglContext());
        }
    }

    @Override
    public void disconnectChannel(int channelId) {
        videoModule.disconnectConsumer(this, channelId);
        // un init uniqueGLEnv in recycle() method
        unInitUniqueGlEnv();
    }

    /**
     * Id is used to identify different consumer instances
     * which can be seen as the same.
     * A consumer is attached to a channel and will replace
     * any consumers of the same type (on screen or off screen)
     * in the consumer list.
     * Consumers of the same type with null or empty tags will be
     * considered as different consumers.
     *
     * @param mId tag of the consumer
     */
    public void setId(String mId) {
        this.mId = mId;
    }

    @Override
    public String getId() {
        return this.mId;
    }

    @Override
    public void onConsumeFrame(VideoCaptureFrame frame, VideoChannel.ChannelContext context) {
        if (uniqueGLEnv) {
            if (uniqueEglCore != null) {
                runOnUniqueThread(() -> {
                    drawFrame(frame, uniqueEglCore, uniqueProgramOES, uniqueProgram2d);
                });
            }
        } else {
            drawFrame(frame, context.getEglCore(), context.getProgramOES(), context.getProgram2D());
        }
    }

    public void setMirrorMode(int mode) {
        mirrorMode = mode;
    }

    @Override
    public void recycle() {
        if (uniqueGLEnv) {
            unInitUniqueGlEnv();
        } else {
            recycle(videoChannel.getChannelContext().getEglCore());
        }
    }

    private void initUniqueGLEnv(EGLContext shareContext) {
        uniqueIsQuit = false;
        uniqueThread = new HandlerThread(this.getClass().getSimpleName()){
            @Override
            public void run() {
                if(uniqueIsQuit){
                    return;
                }
                uniqueEglCore = new EglCore(shareContext, 0);
                uniqueIsRunning = true;
                super.run();
                uniqueIsRunning = false;
                if (uniqueProgramOES != null) {
                    uniqueProgramOES.release();
                    uniqueProgramOES = null;
                }
                if (uniqueProgram2d != null) {
                    uniqueProgram2d.release();
                    uniqueProgram2d = null;
                }
                if (uniqueEglCore != null) {
                    recycle(uniqueEglCore);
                    uniqueEglCore.release();
                    uniqueEglCore = null;
                }
            }
        };
        uniqueThread.start();

        runOnUniqueThread(() -> {
            if (uniqueIsRunning) {
                uniqueEglCore = new EglCore(shareContext, 0);
            }
        });
    }

    private void unInitUniqueGlEnv() {
        uniqueIsRunning = false;
        uniqueIsQuit = true;
        if (uniqueThreadHandler != null) {
            uniqueThreadHandler.removeCallbacksAndMessages(null);
            uniqueThreadHandler = null;
        }
        if (uniqueThread != null) {
            uniqueThread.quitSafely();
            uniqueThread = null;
        }
    }

    private void runOnUniqueThread(Runnable runnable) {
        if (!uniqueIsRunning) {
            return;
        }
        if (Thread.currentThread() != uniqueThread) {
            if(uniqueThreadHandler == null){
                uniqueThreadHandler = new Handler(uniqueThread.getLooper());
            }
            uniqueThreadHandler.post(runnable);
        } else {
            runnable.run();
        }
    }


    private void drawFrame(VideoCaptureFrame frame, EglCore eglCore, ProgramTextureOES programTextureOES, ProgramTexture2d programTexture2d) {
        if (surfaceDestroyed) {
            return;
        }

        if (needResetSurface) {
            if (drawingEglSurface != null && drawingEglSurface != EGL14.EGL_NO_SURFACE) {
                eglCore.releaseSurface(drawingEglSurface);
                eglCore.makeNothingCurrent();
                drawingEglSurface = null;
            }

            Object surface = getDrawingTarget();
            if (surface != null) {
                Object target = getDrawingTarget();
                if (target != null) {
                    try {
                        drawingEglSurface = eglCore.createWindowSurface(target);
                    } catch (Exception e) {
                        LogUtil.e(this, "EGL >> createWindowSurface error : \n" + e.toString());
                        return;
                    }
                    needResetSurface = false;
                }
            }
        }

        boolean surfaceAvailable = true;
        if (drawingEglSurface != null && !eglCore.isCurrent(drawingEglSurface)) {
            try {
                eglCore.makeCurrent(drawingEglSurface);
            } catch (Exception e) {
                surfaceAvailable = false;
            }
        }

        if (!surfaceAvailable) return;

        int surfaceWidth = onMeasuredWidth();
        int surfaceHeight = onMeasuredHeight();
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glClearColor(0, 0, 0, 1.0f);

        int desiredWidth = frame.format.getWidth();
        int desiredHeight = frame.format.getHeight();

        if (frame.rotation == 90 || frame.rotation == 270) {
            desiredWidth = frame.format.getHeight();
            desiredHeight = frame.format.getWidth();
        }

        mMVPMatrix.update(surfaceWidth, surfaceHeight, desiredWidth, desiredHeight);

        if(mirrorMode == Constant.MIRROR_MODE_AUTO){
            mMVPMatrix.setMirror(frame.mirrored);
        }else if(mirrorMode == Constant.MIRROR_MODE_ENABLED){
            mMVPMatrix.setMirror(true);
        }else {
            mMVPMatrix.setMirror(false);
        }

        if (frame.format.getTexFormat() == GLES20.GL_TEXTURE_2D) {
            if (programTexture2d == null && uniqueGLEnv) {
                uniqueProgram2d = new ProgramTexture2d();
                programTexture2d = uniqueProgram2d;
            }
            programTexture2d.drawFrame(frame.textureId, frame.textureTransform, mMVPMatrix.getMatrix());
        } else if (frame.format.getTexFormat() == GLES11Ext.GL_TEXTURE_EXTERNAL_OES) {
            if (programTextureOES == null && uniqueGLEnv) {
                uniqueProgramOES = new ProgramTextureOES();
                programTextureOES = uniqueProgramOES;
            }
            programTextureOES.drawFrame(frame.textureId, frame.textureTransform, mMVPMatrix.getMatrix());
        }

        if (drawingEglSurface != null) {
            eglCore.swapBuffers(drawingEglSurface);
        }
    }

    private void recycle(EglCore eglCore) {
        if (videoChannel != null && drawingEglSurface != null
                && drawingEglSurface != EGL14.EGL_NO_SURFACE) {
            eglCore.releaseSurface(drawingEglSurface);
            drawingEglSurface = null;
            needResetSurface = true;
        }
    }
}
