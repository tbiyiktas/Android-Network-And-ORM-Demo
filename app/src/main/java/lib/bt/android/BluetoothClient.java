package lib.bt.android;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lib.bt.BluetoothConfig;
import lib.bt.callbacks.PairingCallback;
import lib.bt.callbacks.ScanCallback;
import lib.bt.interfaces.IBluetoothConnector_old;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;

public class BluetoothClient {
    private static final String TAG = "BluetoothClient";
    private final IBluetoothStatusManager statusManager;
    private final IBluetoothDeviceScanner deviceScanner;
    private final IBluetoothConnector_old connector;
    private final ExecutorService executorService;
    private final Handler mainThreadHandler;

    public BluetoothClient(IBluetoothStatusManager statusManager, IBluetoothDeviceScanner deviceScanner, IBluetoothConnector_old connector) {
        this.statusManager = statusManager;
        this.deviceScanner = deviceScanner;
        this.connector = connector;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainThreadHandler = new Handler(Looper.getMainLooper());
    }

    public void startScan(ScanCallback callback) {
        executorService.execute(() -> {
            try {
                deviceScanner.onDeviceFound(device -> mainThreadHandler.post(() -> callback.onDeviceFound(device)));
                deviceScanner.onDiscoveryFinished(() -> mainThreadHandler.post(() -> callback.onScanFinished(deviceScanner.getDevices())));

                deviceScanner.startScan();
                mainThreadHandler.post(callback::onScanStarted);
            } catch (Exception e) {
                mainThreadHandler.post(() -> callback.onScanFailed(e.getMessage()));
            }
        });
    }

    public void stopScan() {
        executorService.execute(deviceScanner::stopScan);
    }

    public boolean isDevicePaired(IBluetoothDevice device) {
        return statusManager.isDevicePaired(device);
    }

    public void pairDevice(IBluetoothDevice device, PairingCallback callback) {
        executorService.execute(() -> {
            mainThreadHandler.post(() -> callback.onPairingStarted(device));

            deviceScanner.onPairingStatusChanged(status -> {
                if (status.isPaired()) {
                    mainThreadHandler.post(() -> callback.onPairingSuccess(status.getDevice()));
                    // Eşleştirme tamamlandığında dinlemeyi durdur
                    stopListeningForPairingStatus();
                } else {
                    mainThreadHandler.post(() -> callback.onPairingFailed(status.getDevice(), "Eşleştirme başarısız."));
                    // Eşleştirme başarısız olduğunda dinlemeyi durdur
                    stopListeningForPairingStatus();
                }
            });

            connector.pairDevice(device);
        });
    }

    public void connect(String deviceAddress) {
        executorService.execute(() -> {
            int attempt = 0;
            boolean connected = false;
            while (attempt < BluetoothConfig.MAX_RECONNECT_ATTEMPTS + 1 && !connected) {
                Log.d(TAG, "Bağlantı denemesi: " + (attempt + 1));
                if (connector.connect(deviceAddress)) {
                    connected = true;
                    Log.d(TAG, "Cihaza başarıyla bağlandı.");
                } else {
                    attempt++;
                    if (attempt <= BluetoothConfig.MAX_RECONNECT_ATTEMPTS) {
                        Log.e(TAG, "Bağlantı denemesi " + attempt + " başarısız oldu. " + BluetoothConfig.RECONNECT_DELAY_MS + "ms sonra yeniden deneniyor.");
                        try {
                            Thread.sleep(BluetoothConfig.RECONNECT_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Log.e(TAG, "Yeniden bağlanma kesildi.");
                            break;
                        }
                    } else {
                        Log.e(TAG, "Maksimum yeniden deneme sayısına ulaşıldı. Bağlanılamadı.");
                    }
                }
            }
        });
    }

    public void disconnect() {
        executorService.execute(connector::disconnect);
    }

    public boolean isConnected() {
        return connector.isConnected();
    }

    public void sendData(byte[] data) {
        executorService.execute(() -> {
            if (connector.isConnected()) {
                connector.sendData(data);
            } else {
                Log.e(TAG, "Veri gönderme başarısız: Bağlantı yok.");
            }
        });
    }

    public void onConnectionStatusChanged(Consumer<IBluetoothConnector_old.ConnectionStatus> callback) {
        connector.onConnectionStatusChanged(status -> mainThreadHandler.post(() -> callback.accept(status)));
    }

    public void onDataReceived(Consumer<byte[]> callback) {
        connector.onDataReceived(data -> mainThreadHandler.post(() -> callback.accept(data)));
    }

    public IBluetoothDevice getDeviceFromAddress(String address) {
        return connector.getDeviceFromAddress(address);
    }

    public void stopListeningForPairingStatus() {
        executorService.execute(deviceScanner::stopListeningForPairingStatus);
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}