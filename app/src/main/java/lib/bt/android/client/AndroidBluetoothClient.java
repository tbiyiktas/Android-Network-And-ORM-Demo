package lib.bt.android.client;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import lib.bt.android.connection.classic.AndroidClassicConnector;
import lib.bt.android.connection.gatt.AndroidGattConnector;
import lib.bt.android.scan.AndroidBluetoothScanner;
import lib.bt.android.status.AndroidBluetoothStatusManager;
import lib.bt.android.transport.classic.ClassicTransport;
import lib.bt.android.transport.gatt.GattTransport;
import lib.bt.android.util.AndroidUiExecutor;
import lib.bt.client.BluetoothClient;
import lib.bt.client.IBluetoothClient;
import lib.bt.client.UiExecutor;
import lib.bt.interfaces.IBluetoothConnector;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;
import lib.bt.interfaces.IBluetoothTransport;
import lib.bt.model.BtResult;

/**
 * Android için concurrent facade: Tüm ağır çağrıları worker thread'e aktarır,
 * core BluetoothClient (temel) UI callback'lerini AndroidUiExecutor ile main thread'e post eder.
 */
public final class AndroidBluetoothClient implements IBluetoothClient {

    private final IBluetoothClient core;         // temel orchestrator
    private final ExecutorService worker;        // Android'de ağır işleri çalıştır (tek thread)

    private AndroidBluetoothClient(IBluetoothClient core, ExecutorService worker) {
        this.core = core;
        this.worker = worker;
    }

    // ---- Factory ----
    public static IBluetoothClient createClassic(Context appContext) {
        if (appContext == null) throw new IllegalArgumentException("Context is null");
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IllegalStateException("Bluetooth unsupported on this device");

        IBluetoothDeviceScanner scanner   = new AndroidBluetoothScanner(adapter, appContext);
        IBluetoothConnector connector     = new AndroidClassicConnector(adapter);
        IBluetoothTransport transport     = new ClassicTransport();
        IBluetoothStatusManager statusMgr = new AndroidBluetoothStatusManager(appContext, adapter);

        UiExecutor ui = new AndroidUiExecutor();
        BluetoothClient base = new BluetoothClient(scanner, connector, transport, statusMgr, ui);

        return new AndroidBluetoothClient(base, Executors.newSingleThreadExecutor());
    }

    public static IBluetoothClient createBle(Context appContext,
                                             UUID rxCharUuid,
                                             UUID txCharUuid,
                                             boolean writeNoResponse) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IllegalStateException("Bluetooth unsupported");

        IBluetoothDeviceScanner scanner   = new AndroidBluetoothScanner(adapter, appContext); // BLE scan logic’inle
        IBluetoothConnector connector     = new AndroidGattConnector(appContext, adapter);
        IBluetoothTransport transport     = new GattTransport(rxCharUuid, txCharUuid, writeNoResponse);
        IBluetoothStatusManager statusMgr = new AndroidBluetoothStatusManager(appContext, adapter);

        UiExecutor ui = new AndroidUiExecutor();
        BluetoothClient base = new BluetoothClient(scanner, connector, transport, statusMgr, ui);
        return new AndroidBluetoothClient(base, java.util.concurrent.Executors.newSingleThreadExecutor());
    }
    // ---- IBluetoothClient delegasyonları (worker üstünden) ----

    @Override public void startScan() { worker.execute(core::startScan); }
    @Override public void stopScan()  { worker.execute(core::stopScan); }
    @Override public List<IBluetoothDevice> getDevices() { return core.getDevices(); }
    @Override public List<IBluetoothDevice> getPairedDevices() { return core.getPairedDevices(); }

    @Override public void onDeviceFound(Consumer<IBluetoothDevice> cb) { core.onDeviceFound(cb); }
    @Override public void onDiscoveryFinished(Runnable cb)             { core.onDiscoveryFinished(cb); }

    @Override public void pairDevice(IBluetoothDevice device)          { worker.execute(() -> core.pairDevice(device)); }
    @Override public void cancelPairing()                               { worker.execute(core::cancelPairing); }
    @Override public void stopListeningForPairingStatus()               { worker.execute(core::stopListeningForPairingStatus); }
    @Override public void onPairingStatusChanged(Consumer<IBluetoothDeviceScanner.PairingStatus> cb) { core.onPairingStatusChanged(cb); }

    @Override public boolean connect(String address)                    { return core.connect(address); } // senkron isteyenler için
    @Override public boolean disconnect()                                { return core.disconnect(); }
    @Override public boolean isConnected()                               { return core.isConnected(); }
    @Override public void onConnectionStatusChanged(Consumer<IBluetoothConnector.ConnectionStatus> cb) { core.onConnectionStatusChanged(cb); }

    @Override public boolean send(byte[] data)                           { return core.send(data); }
    @Override public void onDataReceived(Consumer<byte[]> cb)            { core.onDataReceived(cb); }

    @Override public boolean isBluetoothEnabled()                        { return core.isBluetoothEnabled(); }
    @Override public void onBluetoothStatusChanged(Consumer<IBluetoothStatusManager.BluetoothStatus> cb) { core.onBluetoothStatusChanged(cb); }

    @Override public void shutdown() {
        worker.shutdownNow();
        core.shutdown();
    }

    // --- Async convenience: worker üstünden güvenli çağır ---
    @Override public void scanOnce(long timeoutMs, Consumer<BtResult<List<IBluetoothDevice>>> cb) {
        worker.execute(() -> core.scanOnce(timeoutMs, cb));
    }
    @Override public void pairAsync(IBluetoothDevice device, Consumer<BtResult<IBluetoothDevice>> cb) {
        worker.execute(() -> core.pairAsync(device, cb));
    }
    @Override public void connectAsync(String address, Consumer<BtResult<Void>> cb) {
        worker.execute(() -> core.connectAsync(address, cb));
    }
}
