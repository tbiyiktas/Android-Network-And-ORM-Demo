package lib.bt.android.connection.classic;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothConnector;

public final class AndroidClassicConnector implements IBluetoothConnector {

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter adapter;
    private final List<Consumer<ConnectionStatus>> listeners = new CopyOnWriteArrayList<Consumer<ConnectionStatus>>();

    private volatile BluetoothSocket socket;
    private volatile boolean connectedFlag = false;

    public AndroidClassicConnector(BluetoothAdapter adapter) {
        if (adapter == null) throw new IllegalArgumentException("adapter is null");
        this.adapter = adapter;
    }

    @Override
    public boolean connect(String deviceAddress) {
        notifyStatus(ConnectionStatus.CONNECTING);
        try {
            BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
            try { adapter.cancelDiscovery(); } catch (Exception ignored) {}

            BluetoothSocket s = trySecure(device);
            if (s == null) s = tryInsecure(device);
            if (s == null) s = tryReflect(device);

            if (s == null) {
                notifyStatus(ConnectionStatus.FAILED);
                return false;
            }
            this.socket = s;
            notifyStatus(ConnectionStatus.CONNECTED);
            return true;

        } catch (IllegalArgumentException e) {
            notifyStatus(ConnectionStatus.FAILED);
            return false;
        } catch (SecurityException se) {
            // Android 12+ BLUETOOTH_CONNECT gerekebilir
            notifyStatus(ConnectionStatus.FAILED);
            return false;
        }
    }

    @Override
    public boolean disconnect() {
        closeQuietly();
        notifyStatus(ConnectionStatus.DISCONNECTED);
        return true;
    }

    @Override
    public boolean isConnected() {
        return connectedFlag;
    }

    @Override
    public void onConnectionStatusChanged(Consumer<ConnectionStatus> cb) {
        if (cb != null) listeners.add(cb);
    }

    @Override
    public Object getLowLevelChannel() {
        return socket;
    }

    // ---- helpers ----

    private BluetoothSocket trySecure(BluetoothDevice d) {
        try {
            BluetoothSocket s = d.createRfcommSocketToServiceRecord(SPP);
            s.connect();
            return s;
        } catch (IOException e) {
            closeQuietly();
            return null;
        }
    }

    private BluetoothSocket tryInsecure(BluetoothDevice d) {
        try {
            BluetoothSocket s = d.createInsecureRfcommSocketToServiceRecord(SPP);
            s.connect();
            return s;
        } catch (IOException e) {
            closeQuietly();
            return null;
        }
    }

    private BluetoothSocket tryReflect(BluetoothDevice d) {
        try {
            Method m = d.getClass().getMethod("createRfcommSocket", int.class);
            BluetoothSocket s = (BluetoothSocket) m.invoke(d, 1);
            s.connect();
            return s;
        } catch (Exception e) {
            closeQuietly();
            return null;
        }
    }

    private void notifyStatus(ConnectionStatus st) {
        connectedFlag = (st == ConnectionStatus.CONNECTED);
        for (Consumer<ConnectionStatus> cb : listeners) {
            try { cb.accept(st); } catch (Exception ignored) {}
        }
    }

    private void closeQuietly() {
        BluetoothSocket s = socket;
        socket = null;
        if (s != null) { try { s.close(); } catch (Exception ignored) {} }
        connectedFlag = false;
    }
}
