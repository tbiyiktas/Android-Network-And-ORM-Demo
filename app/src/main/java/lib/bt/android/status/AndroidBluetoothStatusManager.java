package lib.bt.android.status;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothStatusManager;

public final class AndroidBluetoothStatusManager implements IBluetoothStatusManager {

    private final Context appContext;
    private final BluetoothAdapter adapter;

    private final List<Consumer<BluetoothStatus>> listeners = new CopyOnWriteArrayList<Consumer<BluetoothStatus>>();

    public AndroidBluetoothStatusManager(Context context, BluetoothAdapter adapter) {
        if (context == null) throw new IllegalArgumentException("context is null");
        if (adapter == null) throw new IllegalArgumentException("adapter is null");
        this.appContext = context.getApplicationContext();
        this.adapter = adapter;

        IntentFilter f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        try { appContext.registerReceiver(stateReceiver, f); } catch (Exception ignored) {}
    }

    @Override public boolean isEnabled() {
        try { return adapter.isEnabled(); }
        catch (SecurityException se) { return false; }
    }

    @Override public void onStatusChanged(Consumer<BluetoothStatus> callback) {
        if (callback != null) listeners.add(callback);
    }

    @Override public boolean isDevicePaired(IBluetoothDevice device) {
        if (device == null || device.getAddress() == null) return false;
        try {
            for (android.bluetooth.BluetoothDevice d : adapter.getBondedDevices()) {
                if (device.getAddress().equals(d.getAddress())) return true;
            }
            return false;
        } catch (SecurityException se) {
            return false;
        }
    }

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int st = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            BluetoothStatus mapped = mapState(st);
            for (Consumer<BluetoothStatus> cb : listeners) {
                try { cb.accept(mapped); } catch (Exception ignored) {}
            }
        }
    };

    private static BluetoothStatus mapState(int s) {
        switch (s) {
            case BluetoothAdapter.STATE_ON:          return BluetoothStatus.ENABLED;
            case BluetoothAdapter.STATE_OFF:         return BluetoothStatus.DISABLED;
            case BluetoothAdapter.STATE_TURNING_ON:  return BluetoothStatus.ENABLING;
            case BluetoothAdapter.STATE_TURNING_OFF: return BluetoothStatus.DISABLING;
            default:                                 return BluetoothStatus.DISABLED;
        }
    }
}
