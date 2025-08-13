package lib.bt;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner.PairingStatus;
import lib.bt.android.AndroidBluetoothDevice;

import java.util.function.Consumer;

public class PairingStatusReceiver extends BroadcastReceiver {
    private final Consumer<PairingStatus> callback;

    public PairingStatusReceiver(Consumer<PairingStatus> callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
            final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
            final BluetoothDevice androidDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (androidDevice != null) {
                IBluetoothDevice device = new AndroidBluetoothDevice(androidDevice);
                boolean isPaired = false;

                if (state == BluetoothDevice.BOND_BONDED) {
                    isPaired = true;
                } else if (state == BluetoothDevice.BOND_NONE) {
                    isPaired = false;
                }

                callback.accept(new PairingStatus(device, isPaired));
            }
        }
    }
}
