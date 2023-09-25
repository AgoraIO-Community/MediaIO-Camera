package io.agora.capture.framework.util;

import android.util.Log;

public class LogUtil {
 
    private static volatile boolean DEBUG = false;

    public synchronized static void setDEBUG(boolean DEBUG) {
        LogUtil.DEBUG = DEBUG;
    }

    private LogUtil() {
        throw new UnsupportedOperationException("Cannot initialize " + getClass().getCanonicalName() + " class");
    }
 
    public static void i(Object obj, Object message){
        if(DEBUG) {
            if (obj != null && message != null) {
                Log.i(obj instanceof String ? (String) obj : obj.getClass().getSimpleName(), message.toString().trim());
            }
        }
    }
 
    public static void e(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                Log.e(obj instanceof String ? (String) obj : obj.getClass().getSimpleName(), message.toString().trim());
            }
        }
    }

    public static void e(Object obj, Object message, Throwable exception) {
        if(DEBUG) {
            if (obj != null && message != null) {
                Log.e(obj instanceof String ? (String) obj : obj.getClass().getSimpleName(), message.toString().trim(), exception);
            }
        }
    }
 
    public static void d(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                Log.d(obj instanceof String ? (String) obj : obj.getClass().getSimpleName(), message.toString().trim());
            }
        }
    }
 
    public static void w(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                Log.w(obj instanceof String ? (String) obj : obj.getClass().getSimpleName(), message.toString().trim());
            }
        }
    }
}