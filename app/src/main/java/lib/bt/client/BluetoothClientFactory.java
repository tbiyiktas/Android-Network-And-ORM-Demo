package lib.bt.client;

import java.util.Objects;

import lib.bt.interfaces.IBluetoothConnector;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;
import lib.bt.interfaces.IBluetoothTransport;

public final class BluetoothClientFactory {
    private BluetoothClientFactory() {}

    /**
     * UiExecutor verilmezse, default olarak "direct" (r.run()) kullanılır.
     * Bu, Android dışı ortamlarda veya testlerde iş görür.
     */
    public static BluetoothClient createWith(IBluetoothDeviceScanner scanner,
                                             IBluetoothConnector connector,
                                             IBluetoothTransport transport,
                                             IBluetoothStatusManager statusManager) {
        UiExecutor direct  = r -> { if (r != null) r.run(); };
        return new BluetoothClient(
                Objects.requireNonNull(scanner, "scanner"),
                Objects.requireNonNull(connector, "connector"),
                Objects.requireNonNull(transport, "transport"),
                Objects.requireNonNull(statusManager, "statusManager"),
                direct
        );
    }

    /**
     * Tercih edilen overload: platformun UI yürütücüsünü (örn. AndroidUiExecutor) geçir.
     */
    public static BluetoothClient createWith(IBluetoothDeviceScanner scanner,
                                             IBluetoothConnector connector,
                                             IBluetoothTransport transport,
                                             IBluetoothStatusManager statusManager,
                                             UiExecutor uiExecutor) {
        if (uiExecutor == null) {
            uiExecutor = new UiExecutor() {
                @Override public void post(Runnable r) { if (r != null) r.run(); }
            };
        }
        return new BluetoothClient(
                Objects.requireNonNull(scanner, "scanner"),
                Objects.requireNonNull(connector, "connector"),
                Objects.requireNonNull(transport, "transport"),
                Objects.requireNonNull(statusManager, "statusManager"),
                Objects.requireNonNull(uiExecutor)
        );
    }
}
