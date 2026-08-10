package com.picoxr.multitouch;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XC_MethodHook;

/**
 * v6 MULTITOUCH: hook XRShell (com.picoxr.xrshell) 的 UInput
 *   - 拦截 UInput.nativeCreateDevice(): 用 native MT 设备替代原单点设备
 *   - 拦截 UInput.nativeSendMotionEvent(): 转成 MT 多指注入
 * 目标: 让 XRShell 创建/注入的设备成为多点触控, 2D app 收到双指
 *
 * 纯运行时 hook, 不改系统文件.
 */
public class MultiTouchMain implements IXposedHookLoadPackage {

    public static final String TAG = "PicoMultiTouchV6";
    private static boolean sNativeLoaded = false;
    private static final Object sLock = new Object();

    // ---- native (libmtinject_native.so) ----
    private static native int  nativeCreateMTDevice();
    private static native boolean nativeSendMTMotion(int action, int slot, float x, float y, float pressure, int displayId);
    private static native int  nativeReleaseMTDevice();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (lp.packageName == null) return;
        XposedBridge.log(TAG + ": entry pkg=" + lp.packageName);

        // -------- 诊断: 在 Via/webview 观察触摸接收 (保留) --------
        if ("mark.via".equals(lp.packageName) ||
                (lp.packageName != null && lp.packageName.contains("webview"))) {
            try { hookAppTouchDiag(lp); } catch (Throwable t) { XposedBridge.log(TAG + " via diag err " + t); }
        }

        // -------- 核心: hook XRShell --------
        if ("com.picoxr.xrshell".equals(lp.packageName)) {
            try {
                loadNative();
                hookUInput(lp);
                XposedBridge.log(TAG + ": XRShell hooked OK");
            } catch (Throwable t) {
                XposedBridge.log(TAG + " XRShell hook err: " + t);
            }
        }
    }

    private void loadNative() {
        synchronized (sLock) {
            if (sNativeLoaded) return;
            // 用 System.load 指定绝对路径 (LSPosed 模块的 loadLibrary 找不到 APK 内 lib)
            String[] candidates = {
                "/data/local/tmp/libmtinject_native.so",
                "/data/data/com.picoxr.multitouch/files/libmtinject_native.so"
            };
            for (String path : candidates) {
                try {
                    System.load(path);
                    sNativeLoaded = true;
                    XposedBridge.log(TAG + ": native loaded from " + path);
                    return;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + " load " + path + " err: " + t);
                }
            }
        }
    }

    private void hookUInput(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        ClassLoader cl = lp.classLoader;
        Class<?> uinput = XposedHelpers.findClass("com.picoxr.xrshell.event.UInput", cl);
        XposedBridge.log(TAG + ": found UInput " + uinput.getName());

        // ---- nativeCreateDevice(): [v6c 完全替换] 用 MT 设备替代原单点 ----
        XposedHelpers.findAndHookMethod(uinput, "nativeCreateDevice", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    XposedBridge.log(TAG + " nativeCreateDevice REPLACE with MT");
                    int mtfd = nativeCreateMTDevice();
                    XposedBridge.log(TAG + " MT device fd=" + mtfd*1);
                    p.setResult((int) mtfd);   // 完全替换: 原 create_device 不执行
                } catch (Throwable t) {
                    XposedBridge.log(TAG + " replace err: " + t);
                    p.setResult(-1);
                }
            }
        });

        // ---- nativeSendMotionEvent(x, y, action, displayId, deviceId): 转 MT 注入 ----
        XposedHelpers.findAndHookMethod(uinput, "nativeSendMotionEvent",
            int.class, int.class, int.class, int.class, int.class,
            new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        int x  = (int) p.args[0];
                        int y  = (int) p.args[1];
                        int act= (int) p.args[2];
                        int disp= (int) p.args[3];
                        // x/y 是当前 2D app 内像素. 映射到 MT 坐标 0-32767
                        float nx = ((float)x) / 1602f;
                        float ny = ((float)y) / 902f;
                        if (nx>1f) nx=1f; if (nx<0f) nx=0f;
                        if (ny>1f) ny=1f; if (ny<0f) ny=0f;
                        // displayId: 用已知 Via display=63 验证(后续动态化).
                        // 原协议 EV_MSC value = Via(63). 若 a3(disp)≠63 则用 63.
                        int targetDisp = (disp>0 && disp<100) ? disp : 63;
                        boolean ok = nativeSendMTMotion(act, 0, nx, ny, 1.0f, targetDisp);
                        if (act==0||act==1) XposedBridge.log(TAG
                            + " nSME->MT x=" + x + " y=" + y + " act=" + act
                            + " a3disp=" + disp + "->" + targetDisp + " ok=" + ok);
                        p.setResult(ok);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + " nSME err: " + t);
                        p.setResult(Boolean.TRUE);
                    }
                }
            });

        // ---- 保留 nativeSendKeyEvent (方向键等原样) ----
        // 不 hook, 让原逻辑走
    }

    // 诊断: 观察 Via/WebView 收到的触摸 (保留自 v5)
    private void hookAppTouchDiag(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        ClassLoader cl = lp.classLoader;
        try {
            Class<?> v = XposedHelpers.findClass("android.view.View", cl);
            XposedHelpers.findAndHookMethod(v, "dispatchGenericMotionEvent",
                android.view.MotionEvent.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            android.view.MotionEvent me = (android.view.MotionEvent) p.args[0];
                            XposedBridge.log(TAG + "[ViaView:generic] act=" + me.getActionMasked()
                                + " ptr=" + me.getPointerCount()
                                + " x=" + (int)me.getX() + " y=" + (int)me.getY()
                                + " src=" + Integer.toHexString(me.getSource())
                                + " disp=" + me.getDisplayId());
                        } catch (Throwable t) { /* */ }
                    }
                });
        } catch (Throwable t) { XposedBridge.log(TAG + " generic err " + t); }
    }
}
