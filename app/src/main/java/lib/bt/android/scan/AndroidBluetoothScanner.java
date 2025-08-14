package lib.bt.android.scan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import lib.bt.android.device.AndroidBluetoothDevice;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;

/**
 * Discovery (scan) + Pairing yönetimi.
 * - startScan/stopScan idempotent
 * - onDeviceFound/onDiscoveryFinished tek callback (idempotent: son kaydı saklar)
 * - Pairing: ara durumlar (BONDING) fail sayılmaz; BONDED/NONE/FAILED = final
 */
public final class AndroidBluetoothScanner implements IBluetoothDeviceScanner {

    private static final String TAG = "AndroidBtScanner";

    private final BluetoothAdapter adapter;
    private final Context appContext;

    private volatile boolean scanning = false;

    private final Set<String> discoveredAddrs = new HashSet<String>();
    private final List<IBluetoothDevice> discovered = new ArrayList<IBluetoothDevice>();

    private volatile Consumer<IBluetoothDevice> onFound = new Consumer<IBluetoothDevice>() {
        @Override public void accept(IBluetoothDevice d) { /* no-op */ }
    };
    private volatile Runnable onFinished = new Runnable() {
        @Override public void run() { /* no-op */ }
    };
    private volatile Consumer<PairingStatus> onPairing = new Consumer<PairingStatus>() {
        @Override public void accept(PairingStatus s) { /* no-op */ }
    };

    private volatile String pairingTargetAddress = null;
    private volatile boolean bondReceiverRegistered = false;

    public AndroidBluetoothScanner(BluetoothAdapter adapter, Context context) {
        if (adapter == null) throw new IllegalArgumentException("adapter is null");
        if (context == null) throw new IllegalArgumentException("context is null");
        this.adapter = adapter;
        this.appContext = context.getApplicationContext();
    }

    // ---------- IBluetoothDeviceScanner ----------

    @Override
    public synchronized void startScan() {
        if (scanning) {
            Log.d(TAG, "startScan: already scanning -> no-op");
            return;
        }
        scanning = true;
        discovered.clear();
        discoveredAddrs.clear();

        // Receiver register
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try {
            appContext.registerReceiver(discoveryReceiver, f);
        } catch (Exception e) {
            Log.e(TAG, "registerReceiver(discovery) failed: " + e);
        }

        try {
            // Güvenlik: eski bir discovery devam ediyorsa iptal
            try { adapter.cancelDiscovery(); } catch (Exception ignored) {}
            boolean ok = adapter.startDiscovery();
            if (!ok) {
                Log.e(TAG, "startDiscovery returned false");
                finishDiscovery();
            } else {
                Log.d(TAG, "Discovery started");
            }
        } catch (SecurityException se) {
            Log.e(TAG, "startDiscovery requires BLUETOOTH_SCAN permission on Android 12+: " + se.getMessage());
            finishDiscovery();
        } catch (Exception e) {
            Log.e(TAG, "startDiscovery error: " + e);
            finishDiscovery();
        }
    }

    @Override
    public synchronized void stopScan() {
        if (!scanning) {
            Log.d(TAG, "stopScan: not scanning -> no-op");
            return;
        }
        scanning = false;
        try { adapter.cancelDiscovery(); } catch (Exception ignored) {}
        unregisterDiscoveryReceiver();
        // finish callback’i garanti et
        try { onFinished.run(); } catch (Exception ignored) {}
    }

    @Override
    public List<IBluetoothDevice> getPairedDevices() {
        try {
            Set<BluetoothDevice> set = adapter.getBondedDevices();
            List<IBluetoothDevice> out = new ArrayList<IBluetoothDevice>(set != null ? set.size() : 0);
            if (set != null) {
                for (BluetoothDevice d : set) {
                    out.add(new AndroidBluetoothDevice(d));
                }
            }
            return out;
        } catch (SecurityException se) {
            Log.e(TAG, "getBondedDevices requires BLUETOOTH_CONNECT on 12+: " + se.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<IBluetoothDevice> getDevices() { return new ArrayList<IBluetoothDevice>(discovered); }

    @Override
    public void onDeviceFound(Consumer<IBluetoothDevice> callback) {
        this.onFound = (callback != null) ? callback : new Consumer<IBluetoothDevice>() {
            @Override public void accept(IBluetoothDevice ibluetoothDevice) { /* no-op */ }
        };
    }

    @Override
    public void onDiscoveryFinished(Runnable callback) {
        this.onFinished = (callback != null) ? callback : new Runnable() {
            @Override public void run() { /* no-op */ }
        };
    }

    @Override
    public void onPairingStatusChanged(Consumer<PairingStatus> callback) {
        this.onPairing = (callback != null) ? callback : new Consumer<PairingStatus>() {
            @Override public void accept(PairingStatus pairingStatus) { /* no-op */ }
        };
        // Bond receiver gerektiğinde (pairDevice çağrısıyla) register edilir
    }

    @Override
    public void pairDevice(IBluetoothDevice device) {
        if (device == null || device.getAddress() == null) return;
        pairingTargetAddress = device.getAddress();

        // Bond-state receiver'ı lazımsa aç
        ensureBondReceiver();

        try {
            BluetoothDevice d = adapter.getRemoteDevice(device.getAddress());
            // createBond çağrısı (Android 12+ için CONNECT izni gerekir)
            d.createBond();
        } catch (SecurityException se) {
            Log.e(TAG, "pairDevice requires BLUETOOTH_CONNECT: " + se.getMessage());
            // FAILED final event'i gönder
            emitPairing(new PairingStatus(device, IBluetoothDevice.BondState.FAILED));
            stopListeningForPairingStatus();
        } catch (Exception e) {
            Log.e(TAG, "pairDevice error: " + e);
            emitPairing(new PairingStatus(device, IBluetoothDevice.BondState.FAILED));
            stopListeningForPairingStatus();
        }
    }

    @Override
    public void cancelPairing() {
        final String target = pairingTargetAddress;
        if (target == null) return;

        try {
            BluetoothDevice d = adapter.getRemoteDevice(target);

            boolean ok = HiddenBt.cancelBondProcess(d); // reflection: cancelBondProcess()
            if (!ok) {
                // Eğer cihaz BONDING/BONDED ise removeBond deneyebiliriz.
                HiddenBt.removeBond(d); // reflection: removeBond()
            }

            // UI'a "eşleşme iptal/temizlendi" gibi net bir final durum göndermek istersen:
            emitPairing(new PairingStatus(new lib.bt.android.device.AndroidBluetoothDevice(d),
                    IBluetoothDevice.BondState.NONE));

        } catch (SecurityException se) {
            // Android 12+ BLUETOOTH_CONNECT gerekebilir
            // Yapacak bir şey yok; sadece dinlemeyi bırak.
        } catch (Exception ignore) {
            // Reflection başarısız olabilir (hidden API kısıtları)
        } finally {
            stopListeningForPairingStatus(); // receiver’ları kapat
        }
    }

    @Override
    public synchronized void stopListeningForPairingStatus() {
        pairingTargetAddress = null;
        unregisterBondReceiver();
    }

    // ---------- Receivers ----------

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent it) {
            String action = it.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice d = it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d == null) return;
                String addr = d.getAddress();
                if (addr == null) return;

                if (discoveredAddrs.add(addr)) {
                    IBluetoothDevice ibd = new AndroidBluetoothDevice(d);
                    discovered.add(ibd);
                    try { onFound.accept(ibd); } catch (Exception ignored) {}
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                finishDiscovery();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // no-op
            }
        }
    };

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent it) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(it.getAction())) return;

            BluetoothDevice d = it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d == null) return;
            String addr = d.getAddress();
            if (addr == null) return;

            // Sadece hedef cihaza bak
            String target = pairingTargetAddress;
            if (target != null && !target.equals(addr)) return;

            int bs = d.getBondState();
            IBluetoothDevice.BondState st;
            switch (bs) {
                case BluetoothDevice.BOND_BONDED:
                    st = IBluetoothDevice.BondState.BONDED; break;
                case BluetoothDevice.BOND_BONDING:
                    st = IBluetoothDevice.BondState.BONDING; break;
                case BluetoothDevice.BOND_NONE:
                    st = IBluetoothDevice.BondState.NONE; break;
                default:
                    st = IBluetoothDevice.BondState.FAILED; break;
            }

            IBluetoothDevice ibd = new AndroidBluetoothDevice(d);
            emitPairing(new PairingStatus(ibd, st));

            // Nihai durumda receiver'ı kapat
            if (st == IBluetoothDevice.BondState.BONDED ||
                    st == IBluetoothDevice.BondState.NONE ||
                    st == IBluetoothDevice.BondState.FAILED) {
                stopListeningForPairingStatus();
            }
        }
    };

    // ---------- Helpers ----------

    private synchronized void finishDiscovery() {
        if (!scanning) return;
        scanning = false;
        unregisterDiscoveryReceiver();
        try { onFinished.run(); } catch (Exception ignored) {}
    }

    private void unregisterDiscoveryReceiver() {
        try { appContext.unregisterReceiver(discoveryReceiver); } catch (Exception ignored) {}
    }

    private void ensureBondReceiver() {
        if (bondReceiverRegistered) return;
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        try {
            appContext.registerReceiver(bondReceiver, f);
            bondReceiverRegistered = true;
        } catch (Exception e) {
            Log.e(TAG, "registerReceiver(bond) failed: " + e);
        }
    }

    private void unregisterBondReceiver() {
        if (!bondReceiverRegistered) return;
        try {
            appContext.unregisterReceiver(bondReceiver);
        } catch (Exception ignored) {}
        bondReceiverRegistered = false;
    }

    private void emitPairing(PairingStatus ps) {
        try { onPairing.accept(ps); } catch (Exception ignored) {}
    }

    // import java.lang.reflect.Method; dosya başına ekleyin.

    private static final class HiddenBt {
        static boolean cancelBondProcess(BluetoothDevice d) {
            try {
                Method m = d.getClass().getMethod("cancelBondProcess");
                Object r = m.invoke(d);
                return (r instanceof Boolean) ? (Boolean) r : false;
            } catch (Throwable t) {
                return false;
            }
        }

        static boolean removeBond(BluetoothDevice d) {
            try {
                Method m = d.getClass().getMethod("removeBond");
                Object r = m.invoke(d);
                return (r instanceof Boolean) ? (Boolean) r : false;
            } catch (Throwable t) {
                return false;
            }
        }
    }

}
