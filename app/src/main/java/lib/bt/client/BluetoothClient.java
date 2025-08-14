package lib.bt.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothConnector;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;
import lib.bt.interfaces.IBluetoothTransport;
import lib.bt.model.BtResult;
import lib.bt.model.exceptions.ConnectionFailedException;
import lib.bt.model.exceptions.PairingFailedException;

/**
 * Temel orchestrator: alt bileşenleri bir araya getirir, UI callback'lerini UiExecutor ile post eder.
 * Ağır işler için thread yönetmez. (Android'de concurrent sarmalayıcı kullanılacak.)
 */
public final class BluetoothClient implements IBluetoothClient {

    //----------------------------------------------------
    private final java.util.concurrent.ScheduledExecutorService scheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private final Object reconnectLock = new Object();
    private volatile boolean reconnecting = false;
    private volatile java.util.concurrent.ScheduledFuture<?> reconnectFuture;
    private volatile String lastAddress = null;         // connect/connectAsync içinde set et
    private volatile boolean autoReconnectEnabled = true; // istersen dışarıdan kapat/aç
    private final java.util.concurrent.ExecutorService io =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    //----------------------------------------------------
    private final IBluetoothDeviceScanner scanner;
    private final IBluetoothConnector connector;
    private final IBluetoothTransport transport;
    private final IBluetoothStatusManager status;
    private final UiExecutor ui;

    // Sadece timeout gibi işleri planlamak için küçük bir scheduler (bloklayıcı değil)
    public BluetoothClient(IBluetoothDeviceScanner scanner,
                           IBluetoothConnector connector,
                           IBluetoothTransport transport,
                           IBluetoothStatusManager status) {
        this(scanner, connector, transport, status, r -> { if (r != null) r.run(); }); // default direct UI
    }

    public BluetoothClient(IBluetoothDeviceScanner scanner,
                           IBluetoothConnector connector,
                           IBluetoothTransport transport,
                           IBluetoothStatusManager status,
                           UiExecutor uiExecutor) {
        this.scanner   = Objects.requireNonNull(scanner, "scanner");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.status    = Objects.requireNonNull(status, "status");
        this.ui        = Objects.requireNonNull(uiExecutor, "uiExecutor");

        transport.onClosed(() -> {
            try { connector.disconnect(); } catch (Exception ignore) {}
            // connector DISCONNECTED yayınlayınca alttaki dinleyici maybeScheduleReconnect() çağırır
        });


        // Bağlantı durumuna göre transport'u bağla/ayır
        this.connector.onConnectionStatusChanged(st -> {
            switch (st) {
                case CONNECTED:
                    transport.attach(connector.getLowLevelChannel());
                    break;
                case CONNECTING:
                    break;
                case DISCONNECTED:
                case FAILED:
                default:
                    transport.detach();
                    maybeScheduleReconnect();
                    break;
            }
        });
    }

    // -------- Scan --------
    @Override public void startScan() { scanner.startScan(); }
    @Override public void stopScan()  { scanner.stopScan();  }

    @Override public List<IBluetoothDevice> getDevices()        { return scanner.getDevices(); }
    @Override public List<IBluetoothDevice> getPairedDevices()  { return scanner.getPairedDevices(); }

    @Override public void onDeviceFound(Consumer<IBluetoothDevice> cb) {
        if (cb == null) return;
        scanner.onDeviceFound(d -> ui.post(() -> cb.accept(d)));
    }
    @Override public void onDiscoveryFinished(Runnable cb) {
        if (cb == null) return;
        scanner.onDiscoveryFinished(() -> ui.post(cb));
    }

    // -------- Pairing --------
    @Override public void pairDevice(IBluetoothDevice device) { scanner.pairDevice(device); }
    @Override public void cancelPairing() { scanner.cancelPairing(); }
    @Override public void stopListeningForPairingStatus() { scanner.stopListeningForPairingStatus(); }

    @Override public void onPairingStatusChanged(Consumer<IBluetoothDeviceScanner.PairingStatus> cb) {
        if (cb == null) return;
        scanner.onPairingStatusChanged(ps -> ui.post(() -> cb.accept(ps)));
    }

    // -------- Connection --------
    @Override public boolean connect(String address)      {
        if (address == null || address.isEmpty()) return false;
        enableAutoReconnect(true);
        this.lastAddress = address;           // ⬅️ manual connect → hedefi güncelle
        cancelPendingReconnects();            // ⬅️ önceki reconnect denemelerini iptal et
        return connector.connect(address);    // normal akış
    }
    @Override public boolean disconnect()                 {
        enableAutoReconnect(false);
        cancelPendingReconnects();            // ⬅️ artık deneme yapma
        try { transport.detach(); } catch (Exception ignore) {}
        return connector.disconnect();
    }

    public void enableAutoReconnect(boolean enabled) {
        this.autoReconnectEnabled = enabled;
        if (!enabled) cancelPendingReconnects();
    }


    @Override public boolean isConnected()                { return connector.isConnected(); }

    @Override public void onConnectionStatusChanged(Consumer<IBluetoothConnector.ConnectionStatus> cb) {
        if (cb == null) return;
        connector.onConnectionStatusChanged(st -> ui.post(() -> cb.accept(st)));
    }

    // -------- Transport --------
    @Override public boolean send(byte[] data) { return transport.send(data); }
    @Override public void onDataReceived(Consumer<byte[]> cb) {
        if (cb == null) return;
        transport.onDataReceived(bytes -> ui.post(() -> cb.accept(bytes)));
    }

    // -------- Status --------
    @Override public boolean isBluetoothEnabled() { return status.isEnabled(); }
    @Override public void onBluetoothStatusChanged(Consumer<IBluetoothStatusManager.BluetoothStatus> cb) {
        if (cb == null) return;
        status.onStatusChanged(s -> ui.post(() -> cb.accept(s)));
    }

    // -------- Async convenience --------

    @Override
    public void scanOnce(long timeoutMs, Consumer<BtResult<List<IBluetoothDevice>>> cb) {
        if (cb == null) return;

        final List<IBluetoothDevice> bucket = new ArrayList<>();
        scanner.onDeviceFound(bucket::add);
        scanner.onDiscoveryFinished(() -> ui.post(() ->
                cb.accept(BtResult.ok(new ArrayList<>(bucket)))
        ));

        try { scanner.startScan(); }
        catch (Exception e) {
            ui.post(() -> cb.accept(BtResult.fail(new ConnectionFailedException("Scan start failed"))));
            return;
        }

        long t = Math.max(100, timeoutMs);
        scheduler.schedule(() -> { try { scanner.stopScan(); } catch (Exception ignored) {} },
                t, TimeUnit.MILLISECONDS);
    }

    private final AtomicBoolean pairingDone = new AtomicBoolean(false);

    @Override
    public void pairAsync(IBluetoothDevice device, Consumer<BtResult<IBluetoothDevice>> cb) {
        if (device == null || cb == null) return;

        final String target = device.getAddress();
        pairingDone.set(false);

        onPairingStatusChanged(ps -> {
            if (ps.getDevice() == null || target == null) return;
            if (!target.equals(ps.getDevice().getAddress())) return;

            // Yalnız nihai durumda sonuç ver
            if (ps.isFinal() && pairingDone.compareAndSet(false, true)) {
                if (ps.isPaired()) ui.post(() -> cb.accept(BtResult.ok(device)));
                else ui.post(() -> cb.accept(BtResult.fail(new PairingFailedException("Pairing failed"))));
                stopListeningForPairingStatus();
            }
        });

        try { scanner.pairDevice(device); }
        catch (Exception e) {
            ui.post(() -> cb.accept(BtResult.fail(new PairingFailedException(
                    "Pairing start error: " + (e.getMessage() != null ? e.getMessage() : e)
            ))));
            return;
        }

        // sessiz kalmayı engelle: timeout
        scheduler.schedule(() -> {
            if (pairingDone.compareAndSet(false, true)) {
                stopListeningForPairingStatus();
                ui.post(() -> cb.accept(BtResult.cancel()));
            }
        }, 30, TimeUnit.SECONDS);
    }

//    @Override
//    public void connectAsync(String address, Consumer<BtResult<Void>> cb) {
//        if (cb == null) return;
//        boolean ok;
//        try { ok = connector.connect(address) && connector.isConnected(); }
//        catch (Exception e) { ok = false; }
//        final boolean success = ok;
//        ui.post(() -> cb.accept(success ? BtResult.ok(null) : BtResult.fail(new ConnectionFailedException("Connection failed"))));
//    }

    @Override
    public void connectAsync(final String address,
                             final java.util.function.Consumer<lib.bt.model.BtResult<Void>> cb) {
        if (cb == null) return;
        io.execute(() -> {
            boolean ok;
            try {
                ok = connect(address); // ⬅️ TEK KAYNAK: lastAddress burada set edilir, AR açılır
            } catch (Exception e) {
                ok = false;
            }
            final lib.bt.model.BtResult<Void> res = ok
                    ? lib.bt.model.BtResult.ok(null)
                    : lib.bt.model.BtResult.fail(new lib.bt.model.exceptions.ConnectionFailedException("Connection failed"));
            ui.post(() -> cb.accept(res));
        });
    }


    // -------- Cleanup --------
    @Override
    public void shutdown() {
        try { scanner.stopScan(); } catch (Exception ignored) {}
        try { scanner.cancelPairing(); } catch (Exception ignored) {}
        try { scanner.stopListeningForPairingStatus(); } catch (Exception ignored) {}
        try { transport.detach(); } catch (Exception ignored) {}
        try { connector.disconnect(); } catch (Exception ignored) {}
        scheduler.shutdownNow();
    }

    //--------------------------------------------------------
    private void maybeScheduleReconnect() {
        if (!autoReconnectEnabled) return;

        final String target = lastAddress;
        if (target == null || target.isEmpty()) return;
        if (connector.isConnected()) return;

        synchronized (reconnectLock) {
            if (reconnecting) return;      // zaten deniyor
            reconnecting = true;
        }

        // Sürekli dene: gecikmeyi kademeli artır, üst sınır koy
        reconnectFuture = (ScheduledFuture<?>) scheduler.submit(new Runnable() {
            @Override public void run() {
                int attempt = 0;
                final long base = lib.bt.BluetoothConfig.RECONNECT_DELAY_MS; //Math.max(500L, lib.bt.BluetoothConfig.RECONNECT_DELAY_MS); // örn. 1000ms
                final long cap  = lib.bt.BluetoothConfig.RECONNECT_MAX_DELAY_MS; // en fazla 15sn bekle

                try {
                    while (autoReconnectEnabled
                            && target.equals(lastAddress)
                            && !connector.isConnected()) {

                        attempt++;
                        try {
                            connector.connect(target);
                        } catch (Exception ignore) {}

                        if (connector.isConnected()) break;

                        long sleep = base * (attempt <= 6 ? attempt : 6); // 1x .. 6x
                        if (sleep > cap) sleep = cap;

                        try { Thread.sleep(sleep); }
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                } finally {
                    synchronized (reconnectLock) {
                        reconnecting = false;
                        reconnectFuture = null;
                    }
                }
            }
        });
    }

    private void cancelPendingReconnects() {
        synchronized (reconnectLock) {
            reconnecting = false;
            if (reconnectFuture != null) {
                reconnectFuture.cancel(true);
                reconnectFuture = null;
            }
        }
    }
    //--------------------------------------------------------
}
