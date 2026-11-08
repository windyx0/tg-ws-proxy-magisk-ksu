package de.robv.android.xposed;

import java.lang.reflect.Member;

public final class XposedBridge {
    public static void log(String text) {
        android.util.Log.i("LSPosed-Bridge", text);
    }

    public static void log(Throwable t) {
        android.util.Log.e("LSPosed-Bridge", "Exception in hook", t);
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return new XC_MethodHook.Unhook();
    }
}
