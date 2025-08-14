package lib.bt.android.util;

import android.os.Handler;
import android.os.Looper;

import lib.bt.client.UiExecutor;

public final class AndroidUiExecutor implements UiExecutor {
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override public void post(Runnable r) { if (r != null) handler.post(r); }
}
