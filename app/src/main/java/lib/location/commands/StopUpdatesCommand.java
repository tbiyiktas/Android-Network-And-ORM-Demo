package lib.location.commands;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import lib.location.ILocationCenter;


public class StopUpdatesCommand extends LocationCommand {

    public StopUpdatesCommand(@Nullable Looper deliverOn) {
        super(deliverOn);
    }

    @Override
    public void execute(@NonNull ILocationCenter center) {
        if (isCanceled()) return;
        center.stop();
    }
}