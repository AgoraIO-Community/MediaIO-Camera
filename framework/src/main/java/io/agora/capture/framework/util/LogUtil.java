package io.agora.capture.framework.util;

public class LogUtil {
 
    private static boolean DEBUG = false;

    public static void setDEBUG(boolean DEBUG) {
        LogUtil.DEBUG = DEBUG;
    }

    private LogUtil() {
        throw new UnsupportedOperationException("Cannot initialize " + getClass().getCanonicalName() + " class");
    }
 
    private static String addCallerInformation() {
        int i, lio; //lio = lastIndexOf
        StackTraceElement stack[] = Thread.currentThread().getStackTrace();
        for (i = 0; !stack[i].getClassName().equals(LogUtil.class.getName()); i++) {
        }
        for (; stack[i].getClassName().equals(LogUtil.class.getName()); i++) {
        }
        lio = stack[i].getFileName().lastIndexOf('.');
        if (lio == -1) {
            return " (" + stack[i].getFileName() + ":" + stack[i].getLineNumber() + ")";
        } else {
            return " (" + stack[i].getFileName().substring(0, lio) + ":" + stack[i].getLineNumber() + ")";
        }
    }
 
    public static void i(Object obj, Object message){
        if(DEBUG) {
            if (obj != null && message != null) {
                LogUtil.i(obj instanceof CharSequence ? obj.toString() : obj.getClass().getSimpleName(), message.toString().trim() + addCallerInformation());
            }
        }
    }
 
    public static void e(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                LogUtil.e(obj instanceof CharSequence ? obj.toString() : obj.getClass().getSimpleName(), message.toString().trim() + addCallerInformation());
            }
        }
    }
 
    public static void d(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                LogUtil.d(obj instanceof CharSequence ? obj.toString() : obj.getClass().getSimpleName(), message.toString().trim() + addCallerInformation());
            }
        }
    }
 
    public static void v(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                LogUtil.v(obj instanceof CharSequence ? obj.toString() : obj.getClass().getSimpleName(), message.toString().trim() + addCallerInformation());
            }
        }
    }
 
    public static void w(Object obj, Object message) {
        if(DEBUG) {
            if (obj != null && message != null) {
                LogUtil.w(obj instanceof CharSequence ? obj.toString() : obj.getClass().getSimpleName(), message.toString().trim() + addCallerInformation());
            }
        }
    }
 
    public static void wtf(Object obj, Object message, Throwable t) {
        if(DEBUG) {
            if (obj != null && message != null) {
                LogUtil.wtf(obj instanceof CharSequence ? obj.toString() : obj.getClass().getSimpleName(), message.toString().trim() + addCallerInformation(), t);
            }
        }
    }
}