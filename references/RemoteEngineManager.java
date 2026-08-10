package com.picovr.systemext;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import com.bytedance.nativeshell.appmanager.AppManagerService;
import com.bytedance.nativeshell.appmanager.FlagManager;
import com.bytedance.nativeshell.appmanager.PanelRenderInfo;
import com.bytedance.nativeshell.appmanager.RemoteInterfaceProxy;
import com.bytedance.nativeshell.appmanager.action.GuideModeManager;
import com.bytedance.nativeshell.appmanager.input.EventDispatcher;
import com.bytedance.nativeshell.appmanager.util.DataThread;
import com.bytedance.nativeshell.appmanager.util.UiThread;
import com.picovr.systemext.RemoteEngineManager;
import java.util.ArrayList;

/* loaded from: classes2.dex */
public class RemoteEngineManager {
    private static final String METHOD_CONNECT_ENGINE = "connect_engine";
    private static final RemoteEngineManager sSelf = new RemoteEngineManager();
    private final RemoteCallback mRemoteCallback = new RemoteCallback();
    private final Runnable mBindEngineTask = new Runnable() { // from class: com.picovr.systemext.-$$Lambda$RemoteEngineManager$YZ9jJChyioIRi4z0kzg62RgTQFU
        @Override // java.lang.Runnable
        public final void run() {
            RemoteEngineManager.this.lambda$new$1$RemoteEngineManager();
        }
    };

    public static RemoteEngineManager getInstance() {
        return sSelf;
    }

    /* JADX INFO: Access modifiers changed from: private */
    class RemoteCallback extends Binder {
        static final int CODE_DISPATCH_ENGINE_STATE = 103;
        static final int CODE_DISPATCH_SHOWING_3DAPP_PKG = 104;
        static final int CODE_GUIDE_TASK_FINISH = 105;
        static final int CODE_INJECT_KEY_EVENT = 102;
        static final int CODE_INJECT_MOTION_EVENT = 101;
        static final int CODE_SET_WINDOW_FOCUS = 107;
        static final int CODE_SYNC_PANEL_RENDER_LIST = 106;
        private static final String DESCRIPTOR = "com.bytedance.IRemoteCallback";

        private RemoteCallback() {
        }

        static /* synthetic */ void lambda$onTransact$0(final String showing3dAppPkg, final int displayState) {
            Bundle bundle = new Bundle();
            bundle.putString("showing_3d_app", showing3dAppPkg);
            bundle.putInt("runtime_state", displayState);
            AppManagerService.getInstance().getAppManagerInternal().sendXrRuntimeState(bundle);
        }

        @Override // android.os.Binder
        protected boolean onTransact(int code, Parcel data, Parcel reply, int binderFlags) throws RemoteException {
            switch (code) {
                case 101:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    EventDispatcher.getInstance().injectMotionEvent(data.readInt(), data.readInt(), data.readInt(), data.readFloat(), data.readFloat(), data.readInt(), data.readInt(), data.readInt());
                    return true;
                case 102:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    EventDispatcher.getInstance().injectKeyEvent(data.readInt(), data.readInt(), data.readInt(), data.readInt(), data.readInt());
                    return true;
                case 103:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    if (data.readInt() == 1 && !FlagManager.getInstance().hasFlag(2)) {
                        FlagManager.getInstance().addFlags(2);
                    }
                    return true;
                case 104:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    final String readString = data.readString();
                    final int readInt = data.readInt();
                    DataThread.post(new Runnable() { // from class: com.picovr.systemext.-$$Lambda$RemoteEngineManager$RemoteCallback$7f4i4BCI8MiIY4v-HQxZIGV0uDQ
                        @Override // java.lang.Runnable
                        public final void run() {
                            RemoteEngineManager.RemoteCallback.lambda$onTransact$0(readString, readInt);
                        }
                    });
                    return true;
                case 105:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    int readInt2 = data.readInt();
                    Log.e("SYSTEM_EXT", "guide task finish taskId =" + readInt2);
                    GuideModeManager.getInstance().onGuideModeTaskFinish(readInt2);
                    return true;
                case 106:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    ArrayList arrayList = new ArrayList();
                    data.readParcelableList(arrayList, PanelRenderInfo.class.getClassLoader());
                    Log.i("SYSTEM_EXT", "CODE_SYNC_PANEL_RENDER_LIST list " + arrayList);
                    AppManagerService.getInstance().updatePanelRenderInfoList(arrayList);
                    break;
                case 107:
                    data.enforceInterface("com.bytedance.IRemoteCallback");
                    int readInt3 = data.readInt();
                    Log.i("SYSTEM_EXT", "CODE_SET_WINDOW_FOCUS clientId " + readInt3);
                    AppManagerService.getInstance().requestUpdateWindowFocus(readInt3);
                    return true;
            }
            return super.onTransact(code, data, reply, binderFlags);
        }
    }

    public void bindRemoteEngineIfNeeded(Context context) {
        Uri parse = Uri.parse("content://com.picovr.xs.engine.interface");
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Bundle bundle = new Bundle();
            bundle.putBinder("systemext_binder", this.mRemoteCallback);
            final IBinder binder = contentResolver.call(parse, METHOD_CONNECT_ENGINE, (String) null, bundle).getBinder("engine_binder");
            if (binder != null && binder.isBinderAlive()) {
                UiThread.post(new Runnable() { // from class: com.picovr.systemext.-$$Lambda$RemoteEngineManager$rAgVm1rnDIjo7nO3GttP5CVv_hE
                    @Override // java.lang.Runnable
                    public final void run() {
                        RemoteInterfaceProxy.getInstance().initRemoteEngine(binder);
                    }
                });
                return;
            }
            Log.w("SYSTEM_EXT", "engine_binder not alive, retry bind remote engine");
            retryBindEngine();
        } catch (Exception e) {
            e.printStackTrace();
            retryBindEngine();
        }
    }

    public /* synthetic */ void lambda$new$1$RemoteEngineManager() {
        bindRemoteEngineIfNeeded(AppManagerService.getInstance().getContext());
    }

    public void retryBindEngine() {
        DataThread.getHandler().removeCallbacks(this.mBindEngineTask);
        DataThread.postDelayed(this.mBindEngineTask, 500L);
    }
}
