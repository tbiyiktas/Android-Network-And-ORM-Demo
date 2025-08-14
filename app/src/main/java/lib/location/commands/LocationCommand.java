package lib.location.commands;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import lib.location.ILocationCenter;
import lib.location.LocationResult;
import lib.location.SingleResultCallback;

public abstract class LocationCommand implements lib.location.Cancellable {
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    protected final Handler deliver;

    protected LocationCommand(@Nullable Looper deliverOn) {
        this.deliver = new Handler(deliverOn != null ? deliverOn : Looper.getMainLooper());
    }

    public abstract void execute(@NonNull ILocationCenter center);

    protected <T> void deliverResult(@NonNull LocationResult<T> r,
                                     @NonNull SingleResultCallback<T> cb) {
        if (isCanceled()) return;
        deliver.post(() -> { if (!isCanceled()) cb.onResult(r); });
    }

    @Override public void cancel() { canceled.set(true); }
    @Override public boolean isCanceled() { return canceled.get(); }
}
