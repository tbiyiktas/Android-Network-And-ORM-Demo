package lib.bt;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import lib.bt.android.AndroidBluetoothDevice;
import lib.bt.interfaces.IBluetoothDevice;

import java.util.function.Consumer;

public class DeviceFoundReceiver extends BroadcastReceiver {
    private static final String TAG = "BtReceiver";
    private final Consumer<IBluetoothDevice> callback;

    public DeviceFoundReceiver(Consumer<IBluetoothDevice> callback) {
        this.callback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            BluetoothDevice androidDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (androidDevice != null) {
                Log.d(TAG, "Yeni bir cihaz bulundu: " + androidDevice.getName() + " - " + androidDevice.getAddress());
                callback.accept(new AndroidBluetoothDevice(androidDevice));
            }
        }
    }
}