package lib.bt.android.connection.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothConnector;

/**
 * BLE GATT konektörü.
 * - connect(address): GATT açar ve timeout süresince bağlanmayı BLOKLU bekler.
 * - onConnectionStatusChanged ile CONNECTING/CONNECTED/DISCONNECTED/FAILED yayını yapar.
 * - getLowLevelChannel(): BluetoothGatt döner (transport.attach(...) için).
 */
public final class AndroidGattConnector implements IBluetoothConnector {

    // İstersen BluetoothConfig içine taşı.
    private static final long CONNECT_TIMEOUT_MS = 10000L;

    private final Context appContext;
    private final BluetoothAdapter adapter;

    private volatile BluetoothGatt gatt;
    private volatile boolean connectedFlag = false;

    private final List<Consumer<ConnectionStatus>> listeners = new CopyOnWriteArrayList<Consumer<ConnectionStatus>>();

    public AndroidGattConnector(Context context, BluetoothAdapter adapter) {
        if (context == null) throw new IllegalArgumentException("context is null");
        if (adapter == null) throw new IllegalArgumentException("adapter is null");
        this.appContext = context.getApplicationContext();
        this.adapter = adapter;
    }

    @Override
    public boolean connect(String deviceAddress) {
        notifyStatus(ConnectionStatus.CONNECTING);

        // Eski bağlantıyı kapat
        closeGattQuietly();

        final BluetoothDevice dev;
        try {
            dev = adapter.getRemoteDevice(deviceAddress);
        } catch (IllegalArgumentException e) {
            notifyStatus(ConnectionStatus.FAILED);
            return false;
        } catch (SecurityException se) {
            // Android 12+ BLUETOOTH_CONNECT gerekebilir
            notifyStatus(ConnectionStatus.FAILED);
            return false;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        final BluetoothGattCallback cb = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
                // status: GATT_SUCCESS vb. (OEM değişebilir); newState: STATE_CONNECTED / STATE_DISCONNECTED
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt = g;
                    connectedFlag = true;
                    notifyStatus(ConnectionStatus.CONNECTED);
                    try {
                        // Servisleri hemen keşfet (transport da gerekli görürse tekrar çağırabilir)
                        g.discoverServices();
                    } catch (Exception ignore) {}
                    latch.countDown();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedFlag = false;
                    notifyStatus(ConnectionStatus.DISCONNECTED);
                    closeGattQuietly();
                    latch.countDown();
                } else {
                    // Diğer durumlar: bağlanıyor/çözülüyor → beklemeye devam
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt g, android.bluetooth.BluetoothGattCharacteristic ch) {
                // Transport’a köprüle
                byte[] val = ch.getValue();
                if (val != null) {
                    // Registry üzerinden ilgili transport’a yönlendir
                    lib.bt.android.transport.gatt.GattTransportRegistry.dispatch(g, val);
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt g, android.bluetooth.BluetoothGattCharacteristic ch, int status) {
                // İstemezsen boş bırak; gerekirse read akışını da köprüleyebilirsin.
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt g, android.bluetooth.BluetoothGattCharacteristic ch, int status) {
                // Yazım sonuçları için gerekirse log/geri bildirim ekleyebilirsin.
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt g, int status) {
                // Transport attach sonrası burada notification enable işlemleri yapılabilir (transport tarafı da yapacak).
            }
        };

        try {
            // autoConnect=false → hemen bağlanmayı dene
            BluetoothGatt g = dev.connectGatt(appContext, false, cb);
            if (g == null) {
                notifyStatus(ConnectionStatus.FAILED);
                return false;
            }
            // Bağlantı sonucu için bekle
            boolean ok = latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!ok || !connectedFlag) {
                // timeout ya da başarısız
                notifyStatus(ConnectionStatus.FAILED);
                closeGattQuietly();
                return false;
            }
            return true;
        } catch (SecurityException se) {
            notifyStatus(ConnectionStatus.FAILED);
            closeGattQuietly();
            return false;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            notifyStatus(ConnectionStatus.FAILED);
            closeGattQuietly();
            return false;
        } catch (Exception e) {
            notifyStatus(ConnectionStatus.FAILED);
            closeGattQuietly();
            return false;
        }
    }

    @Override
    public boolean disconnect() {
        try {
            BluetoothGatt g = gatt;
            if (g != null) {
                try { g.disconnect(); } catch (Exception ignore) {}
            }
        } finally {
            notifyStatus(ConnectionStatus.DISCONNECTED);
            closeGattQuietly();
        }
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
        return gatt;
    }

    // ---- helpers ----

    private void notifyStatus(ConnectionStatus st) {
        connectedFlag = (st == ConnectionStatus.CONNECTED);
        for (Consumer<ConnectionStatus> cb : listeners) {
            try { cb.accept(st); } catch (Exception ignore) {}
        }
    }

    private void closeGattQuietly() {
        BluetoothGatt g = gatt;
        gatt = null;
        if (g != null) {
            try { g.close(); } catch (Exception ignore) {}
        }
        connectedFlag = false;
    }
}
