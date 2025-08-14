package lib.bt.android.transport.gatt;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Log;

import java.util.UUID;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothTransport;

/**
 * BLE GATT transport:
 * - attach(gatt): RX karakteristiğine notification/indication açar, TX’e yazar.
 * - onDataReceived: onCharacteristicChanged üzerinden GattTransportRegistry ile beslenir.
 * - detach(): notification kapatır ve registry bağını çözer.
 */
public final class GattTransport implements IBluetoothTransport {

    private static final String TAG = "GattTransport";
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final UUID rxCharUuid;
    private final UUID txCharUuid;
    private final boolean useWriteNoResponse;

    private volatile BluetoothGatt gatt;
    private volatile BluetoothGattCharacteristic rxChar;
    private volatile BluetoothGattCharacteristic txChar;

    private volatile Consumer<byte[]> onData = new Consumer<byte[]>() {
        @Override public void accept(byte[] bytes) { /* no-op */ }
    };

    private volatile Runnable onClosed;

    /**
     * @param rxCharUuid Bildirim/indication dinlenecek karakteristik
     * @param txCharUuid Yazım yapılacak karakteristik
     * @param useWriteNoResponse true ise WRITE_TYPE_NO_RESPONSE kullanır (daha hızlı ama onay yok)
     */
    public GattTransport(UUID rxCharUuid, UUID txCharUuid, boolean useWriteNoResponse) {
        if (rxCharUuid == null || txCharUuid == null)
            throw new IllegalArgumentException("Characteristic UUIDs must not be null");
        this.rxCharUuid = rxCharUuid;
        this.txCharUuid = txCharUuid;
        this.useWriteNoResponse = useWriteNoResponse;
    }

    @Override
    public synchronized void attach(Object lowLevelChannel) {
        detach(); // idempotent: eski bağ varsa temizle

        if (!(lowLevelChannel instanceof BluetoothGatt)) {
            Log.e(TAG, "attach: channel is not BluetoothGatt");
            return;
        }

        this.gatt = (BluetoothGatt) lowLevelChannel;

        // Registry: connector callback’leri bize bildirebilsin
        GattTransportRegistry.bind(this.gatt, this);

        // Servisleri bul ve notification aç
        // (discoverServices genelde connector’da çağrıldı; yine de burada da deneriz)
        try { this.gatt.discoverServices(); } catch (Exception ignore) {}

        resolveCharacteristics();
        enableNotifications();
    }

    @Override
    public synchronized void detach() {
        // Registry bağını bırak
        if (gatt != null) {
            GattTransportRegistry.unbind(gatt);
        }

        // Notification kapatmaya çalış (opsiyonel)
        try {
            if (gatt != null && rxChar != null) {
                gatt.setCharacteristicNotification(rxChar, false);
                BluetoothGattDescriptor cccd = rxChar.getDescriptor(CCCD);
                if (cccd != null) {
                    cccd.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                    gatt.writeDescriptor(cccd);
                }
            }
        } catch (Exception ignore) {}

        gatt = null;
        rxChar = null;
        txChar = null;

        Runnable cb = onClosed;
        if (cb != null) {
            try { cb.run(); } catch (Throwable ignore) {}
        }
    }

    @Override
    public boolean send(byte[] data) {
        BluetoothGatt g = gatt;
        BluetoothGattCharacteristic tx = txChar;
        if (g == null || tx == null) {
            Log.e(TAG, "send: not attached or TX characteristic not found");
            return false;
        }
        try {
            tx.setValue(data);
            tx.setWriteType(useWriteNoResponse
                    ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            return g.writeCharacteristic(tx);
        } catch (Exception e) {
            Log.e(TAG, "send error: " + e);
            return false;
        }
    }

    @Override
    public void onDataReceived(Consumer<byte[]> callback) {
        this.onData = (callback != null) ? callback : new Consumer<byte[]>() {
            @Override public void accept(byte[] bytes) { /* no-op */ }
        };
    }

    @Override
    public void onClosed(Runnable callback) {
        this.onClosed = callback;
    }

    // ====== Connector callback köprüsü ======

    /** Connector’daki BluetoothGattCallback üzerinden çağrılır. */
    public void handleNotification(byte[] value) {
        if (value == null) return;
        try { onData.accept(value); } catch (Throwable ignore) {}
    }

    // ====== Helpers ======

    private void resolveCharacteristics() {
        BluetoothGatt g = gatt;
        if (g == null) return;

        try {
            for (android.bluetooth.BluetoothGattService s : g.getServices()) {
                BluetoothGattCharacteristic c1 = s.getCharacteristic(rxCharUuid);
                if (c1 != null) rxChar = c1;

                BluetoothGattCharacteristic c2 = s.getCharacteristic(txCharUuid);
                if (c2 != null) txChar = c2;
            }
        } catch (Exception ignore) {}

        if (rxChar == null || txChar == null) {
            // Servisler henüz gelmemiş olabilir; discover sonrası tekrar attach çağırman gerekebilir.
            // İstiyorsan burada tekrar discoverServices tetikleyip biraz bekleyebilirsin.
        }
    }

    private void enableNotifications() {
        BluetoothGatt g = gatt;
        BluetoothGattCharacteristic rx = rxChar;
        if (g == null || rx == null) return;

        try {
            boolean ok1 = g.setCharacteristicNotification(rx, true);
            BluetoothGattDescriptor cccd = rx.getDescriptor(CCCD);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean ok2 = g.writeDescriptor(cccd);
                if (!ok1 || !ok2) {
                    Log.w(TAG, "enableNotifications: setNotification=" + ok1 + " writeCCCD=" + ok2);
                }
            } else {
                Log.w(TAG, "enableNotifications: CCCD (0x2902) not found");
            }
        } catch (Exception e) {
            Log.e(TAG, "enableNotifications error: " + e);
        }
    }
}
