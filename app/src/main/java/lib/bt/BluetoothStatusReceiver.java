package lib.bt;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import lib.bt.interfaces.IBluetoothStatusManager.BluetoothStatus;
import java.util.function.Consumer;

public class BluetoothStatusReceiver extends BroadcastReceiver {
    private final Consumer<BluetoothStatus> callback;

    public BluetoothStatusReceiver(Consumer<BluetoothStatus> callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            BluetoothStatus status;

            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    status = BluetoothStatus.DISABLED;
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                    status = BluetoothStatus.DISABLING;
                    break;
                case BluetoothAdapter.STATE_ON:
                    status = BluetoothStatus.ENABLED;
                    break;
                case BluetoothAdapter.STATE_TURNING_ON:
                    status = BluetoothStatus.ENABLING;
                    break;
                default:
                    return;
            }
            callback.accept(status);
        }
    }
}