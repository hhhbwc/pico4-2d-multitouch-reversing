package com.bytedance.nativeshell.appmanager.widget;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.text.TextUtils;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import com.bytedance.nativeshell.appmanager.AppManagerService;
import com.bytedance.nativeshell.appmanager.AppWindowState;
import com.bytedance.nativeshell.appmanager.DataModule;
import com.bytedance.nativeshell.appmanager.util.UiThread;
import com.bytedance.nativeshell.client.TransactionInterface;

/* loaded from: classes.dex */
public class SurfacePanel extends AppWindowState {
    private IBinder.DeathRecipient mClientDeathRecipient = new IBinder.DeathRecipient() { // from class: com.bytedance.nativeshell.appmanager.widget.SurfacePanel.1
        @Override // android.os.IBinder.DeathRecipient
        public void binderDied() {
            AppManagerService.getInstance().getAppManagerInternal().destroyClient(SurfacePanel.this.mClientId);
            DataModule.getInstance().removeSurfacePanel(SurfacePanel.this.mClientId);
            final SurfacePanel surfacePanel = SurfacePanel.this;
            UiThread.post(new Runnable() { // from class: com.bytedance.nativeshell.appmanager.widget.SurfacePanel.1.1
                @Override // java.lang.Runnable
                public void run() {
                    surfacePanel.destroy();
                }
            });
        }
    };
    public int mHeight;
    private IBinder mToken;
    public int mWidth;

    @Override // com.bytedance.nativeshell.appmanager.AppContainer
    protected void updateSurface(Surface surface) {
    }

    public SurfacePanel(int clientId, IBinder remoteBinder, int width, int height) {
        this.mClientId = clientId;
        this.mToken = remoteBinder;
        this.mWidth = width;
        this.mHeight = height;
        try {
            remoteBinder.linkToDeath(this.mClientDeathRecipient, 0);
        } catch (Exception unused) {
        }
    }

    public SurfacePanel(Bundle params) {
        this.mClientId = params.getInt("client_id", -1);
        IBinder binder = params.getBinder("callback");
        this.mToken = binder;
        try {
            binder.linkToDeath(this.mClientDeathRecipient, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String string = params.getString("package_name");
        String string2 = params.getString("component_name");
        if (!TextUtils.isEmpty(string) && !TextUtils.isEmpty(string2)) {
            this.mComponentName = new ComponentName(string, string2);
        }
        this.mType = params.getInt("type");
    }

    public static SurfacePanel obtain(Bundle params) {
        int i = params.getInt("client_id", -1);
        IBinder binder = params.getBinder("callback");
        if (i <= 0 || binder == null) {
            return null;
        }
        return new SurfacePanel(params);
    }

    public IBinder getRemoteBinder() {
        return this.mToken;
    }

    @Override // com.bytedance.nativeshell.appmanager.AppContainer
    public void destroy() {
        try {
            this.mToken.unlinkToDeath(this.mClientDeathRecipient, 0);
        } catch (Exception unused) {
        }
        AppManagerService.getInstance().getAppManagerInternal().destroyClient(this.mClientId);
    }

    @Override // com.bytedance.nativeshell.appmanager.AppContainer
    public boolean updateVisible(boolean visible, boolean force, boolean syncToClient, int changeType) {
        this.mVisible = visible;
        AppManagerService.getInstance().getAppManagerInternal().updateVisibility(this.mClientId, visible);
        return true;
    }

    public void deliverToClient(InputEvent event) {
        IBinder iBinder;
        if (event == null || (iBinder = this.mToken) == null || !iBinder.isBinderAlive()) {
            return;
        }
        Parcel obtain = Parcel.obtain();
        Parcel obtain2 = Parcel.obtain();
        try {
            try {
                obtain.writeInterfaceToken("com.bytedance.IRemoteCallback");
                if (event instanceof MotionEvent) {
                    obtain.writeInt(1);
                    ((MotionEvent) event).writeToParcel(obtain, 0);
                } else {
                    obtain.writeInt(2);
                    ((KeyEvent) event).writeToParcel(obtain, 0);
                }
                this.mToken.transact(TransactionInterface.CODE_NS_DISPATCH_EVENT, obtain, obtain2, 1);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } finally {
            obtain2.recycle();
            obtain.recycle();
        }
    }
}
