package lib.location.commands;


import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import lib.location.ILocationCenter;
import lib.location.LocationRequestOptions;


public class StartUpdatesCommand extends LocationCommand {
    @NonNull private final LocationRequestOptions options;

    public StartUpdatesCommand(@Nullable Looper deliverOn, @NonNull LocationRequestOptions options) {
        super(deliverOn);
        this.options = options;
    }

    @Override
    public void execute(@NonNull ILocationCenter center) {
        if (isCanceled()) return;
        center.start(options);
        // İstersen success callback eklenebilir
    }
}
