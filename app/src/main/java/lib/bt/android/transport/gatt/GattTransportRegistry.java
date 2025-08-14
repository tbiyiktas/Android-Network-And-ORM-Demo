package lib.bt.android.transport.gatt;

import android.bluetooth.BluetoothGatt;

import java.util.concurrent.ConcurrentHashMap;

/** Connector ↔ Transport arasında bildirim köprüsü. */
public final class GattTransportRegistry {
    private static final ConcurrentHashMap<BluetoothGatt, GattTransport> MAP =
            new ConcurrentHashMap<BluetoothGatt, GattTransport>();

    private GattTransportRegistry() {}

    static void bind(BluetoothGatt gatt, GattTransport transport) {
        if (gatt != null && transport != null) {
            MAP.put(gatt, transport);
        }
    }

    static void unbind(BluetoothGatt gatt) {
        if (gatt != null) MAP.remove(gatt);
    }

    public static void dispatch(BluetoothGatt gatt, byte[] value) {
        GattTransport t = (gatt != null) ? MAP.get(gatt) : null;
        if (t != null) t.handleNotification(value);
    }
}
