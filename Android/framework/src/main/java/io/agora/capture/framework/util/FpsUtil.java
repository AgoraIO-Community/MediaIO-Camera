package io.agora.capture.framework.util;

import android.os.Handler;
import android.os.Looper;

public class FpsUtil {
    private static final String TAG = "FpsUtil";
    private String logTag = "";
    private Handler handler;
    private int periodMs;

    private int frameCount;
    private int currFps;

    private int freezeReportTimeoutMs;
    private Runnable freezeTimeoutRun;
    private int freezePeriodCount;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            currFps = Math.round(frameCount * 1000.0f / periodMs);
            LogUtil.d(TAG, logTag + " fps: " + currFps + ".");
            if (frameCount == 0) {
                ++freezePeriodCount;
                if (periodMs * freezePeriodCount >= freezeReportTimeoutMs) {
                    if(freezeTimeoutRun != null){
                        freezeTimeoutRun.run();
                    }
                    return;
                }
            } else {
                freezePeriodCount = 0;
            }
            frameCount = 0;
            handler.postDelayed(this, periodMs);
        }
    };

    public FpsUtil(String logTag){
        this(logTag, new Handler(Looper.myLooper()), 2000, 4000, null);
    }

    public FpsUtil(String logTag, Handler handler, int periodMs, int freezeReportTimeoutMs, Runnable freezeTimeoutRun){
        this.logTag = logTag;
        this.handler = handler;
        this.periodMs = periodMs;
        this.freezeReportTimeoutMs = freezeReportTimeoutMs;
        this.freezeTimeoutRun = freezeTimeoutRun;
        handler.postDelayed(runnable, periodMs);
    }

    private void checkThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("Wrong thread");
        }
    }

    public void addFrame() {
        checkThread();
        ++frameCount;
    }

    public void release() {
        handler.removeCallbacks(runnable);
    }

    public int getCurrFps() {
        return currFps;
    }
}
