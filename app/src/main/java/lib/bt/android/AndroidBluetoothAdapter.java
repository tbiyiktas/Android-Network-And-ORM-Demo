package lib.bt.android;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothConnector_old;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;

public class AndroidBluetoothAdapter implements IBluetoothStatusManager, IBluetoothDeviceScanner, IBluetoothConnector_old {

    private static final String TAG = "AndroidBluetoothAdapter";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // SPP UUID

    private final Context context;
    private final BluetoothAdapter nativeAdapter;

    private Consumer<IBluetoothDevice> deviceFoundCallback;
    private Runnable discoveryFinishedCallback;
    private Consumer<IBluetoothDeviceScanner.PairingStatus> pairingStatusCallback;

    private Consumer<ConnectionStatus> connectionStatusCallback;
    private Consumer<byte[]> dataReceivedCallback;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private IBluetoothDevice connectedDevice;
    private Thread dataReceiverThread;

    private List<IBluetoothDevice> discoveredDevices = new ArrayList<>();

    public AndroidBluetoothAdapter(Context context) {
        this.context = context;
        this.nativeAdapter = BluetoothAdapter.getDefaultAdapter();
        if (this.nativeAdapter == null) {
            Log.e(TAG, "Bluetooth desteklenmiyor.");
        }

        IntentFilter pairingFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(pairingReceiver, pairingFilter);
    }

    // IBluetoothStatusManager
    @Override
    public boolean isEnabled() {
        return nativeAdapter != null && nativeAdapter.isEnabled();
    }

    @Override
    public void onStatusChanged(Consumer<BluetoothStatus> callback) {
        // Bu metot için broadcast receiver gereklidir.
    }

    @Override
    public boolean isDevicePaired(IBluetoothDevice device) {
        if (nativeAdapter == null || device == null) {
            return false;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT izni yok. Eşleşme durumu kontrol edilemiyor.");
            return false;
        }
        Set<BluetoothDevice> pairedDevices = nativeAdapter.getBondedDevices();
        if (pairedDevices != null) {
            for (BluetoothDevice nativeDevice : pairedDevices) {
                if (nativeDevice.getAddress().equals(device.getAddress())) {
                    return true;
                }
            }
        }
        return false;
    }

    // IBluetoothDeviceScanner
    @Override
    public void startScan() {
        if (!isEnabled()) {
            Log.e(TAG, "Bluetooth etkin değil, tarama başlatılamaz.");
            return;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_SCAN izni yok, tarama başlatılamaz.");
            return;
        }

        discoveredDevices.clear();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);
        nativeAdapter.startDiscovery();
    }

    @Override
    public void stopScan() {
        if (nativeAdapter != null && nativeAdapter.isDiscovering()) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_SCAN izni yok, tarama durdurulamıyor.");
                return;
            }
            nativeAdapter.cancelDiscovery();
        }
    }

    @Override
    public List<IBluetoothDevice> getPairedDevices() {
        List<IBluetoothDevice> pairedDevices = new ArrayList<>();
        if (nativeAdapter != null && isEnabled()) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT izni yok. Eşleşmiş cihazlar alınamıyor.");
                return pairedDevices;
            }
            Set<BluetoothDevice> bondedDevices = nativeAdapter.getBondedDevices();
            if (bondedDevices != null) {
                for (BluetoothDevice device : bondedDevices) {
                    pairedDevices.add(new AndroidBluetoothDevice(device));
                }
            }
        }
        return pairedDevices;
    }

    @Override
    public List<IBluetoothDevice> getDevices() {
        return discoveredDevices;
    }

    @Override
    public void onDeviceFound(Consumer<IBluetoothDevice> callback) {
        this.deviceFoundCallback = callback;
    }

    @Override
    public void onDiscoveryFinished(Runnable callback) {
        this.discoveryFinishedCallback = callback;
    }

    @Override
    public void onPairingStatusChanged(Consumer<IBluetoothDeviceScanner.PairingStatus> callback) {
        this.pairingStatusCallback = callback;
    }

    @Override
    public void pairDevice(IBluetoothDevice device) {
        if (device instanceof AndroidBluetoothDevice) {
            BluetoothDevice androidDevice = ((AndroidBluetoothDevice) device).getBluetoothDevice();
            if (androidDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "BLUETOOTH_CONNECT izni yok.");
                    return;
                }
                try {
                    Method createBondMethod = androidDevice.getClass().getMethod("createBond");
                    createBondMethod.invoke(androidDevice);
                    Log.d(TAG, "Eşleştirme isteği gönderildi.");
                } catch (Exception e) {
                    Log.e(TAG, "Eşleştirme başlatılırken hata oluştu.", e);
                    if (pairingStatusCallback != null) {
                        pairingStatusCallback.accept(new IBluetoothDeviceScanner.PairingStatus(device, false));
                    }
                }
            } else {
                Log.d(TAG, "Cihaz zaten eşleşmiş veya eşleşme durumunda.");
            }
        }
    }

    @Override
    public void cancelPairing() {
        // Eşleştirme iptali için bir yöntem
    }

    @Override
    public void stopListeningForPairingStatus() {
        try {
            context.unregisterReceiver(pairingReceiver);
            Log.d(TAG, "PairingReceiver kayıttan silindi.");
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "PairingReceiver zaten kayıtsızdı: " + e.getMessage());
        }
    }

    // IBluetoothConnector
    @Override
    public boolean connect(String deviceAddress) {
        if (!isEnabled()) {
            Log.e(TAG, "Bluetooth etkin değil, bağlanılamaz.");
            if (connectionStatusCallback != null) {
                connectionStatusCallback.accept(new ConnectionStatus(null, false, "Bluetooth etkin değil."));
            }
            return false;
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "BLUETOOTH_CONNECT izni yok.");
            if (connectionStatusCallback != null) {
                connectionStatusCallback.accept(new ConnectionStatus(null, false, "İzin reddedildi."));
            }
            return false;
        }

        if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
            Log.d(TAG, "Zaten bağlı.");
            return true;
        }

        BluetoothDevice nativeDevice = nativeAdapter.getRemoteDevice(deviceAddress);
        if (nativeDevice == null) {
            Log.e(TAG, "Cihaz bulunamadı: " + deviceAddress);
            if (connectionStatusCallback != null) {
                connectionStatusCallback.accept(new ConnectionStatus(null, false, "Cihaz bulunamadı."));
            }
            return false;
        }

        try {
            bluetoothSocket = nativeDevice.createRfcommSocketToServiceRecord(MY_UUID);
            if (bluetoothSocket == null) {
                Log.e(TAG, "Soket oluşturulamadı.");
                if (connectionStatusCallback != null) {
                    connectionStatusCallback.accept(new ConnectionStatus(null, false, "Soket oluşturulamadı."));
                }
                return false;
            }
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            connectedDevice = new AndroidBluetoothDevice(nativeDevice);
            startReceivingData();
            if (connectionStatusCallback != null) {
                connectionStatusCallback.accept(new ConnectionStatus(connectedDevice, true, null));
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Bağlantı hatası: " + e.getMessage());
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException closeException) {
                Log.e(TAG, "Soket kapatılırken hata oluştu: " + closeException.getMessage());
            }
            if (connectionStatusCallback != null) {
                connectionStatusCallback.accept(new ConnectionStatus(null, false, e.getMessage()));
            }
            return false;
        }
    }

    @Override
    public boolean disconnect() {
        if (bluetoothSocket != null) {
            try {
                if (dataReceiverThread != null) {
                    dataReceiverThread.interrupt();
                    dataReceiverThread = null;
                }
                bluetoothSocket.close();
                Log.d(TAG, "Bağlantı kesildi.");
                if (connectionStatusCallback != null) {
                    connectionStatusCallback.accept(new ConnectionStatus(connectedDevice, false, null));
                }
                connectedDevice = null;
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Soket kapatılırken hata oluştu: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean sendData(byte[] data) {
        if (outputStream != null) {
            try {
                outputStream.write(data);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Veri gönderme hatası: " + e.getMessage());
                if (connectionStatusCallback != null) {
                    connectionStatusCallback.accept(new ConnectionStatus(connectedDevice, false, "Veri gönderme hatası. Bağlantı kesildi."));
                }
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    @Override
    public IBluetoothDevice getDeviceFromAddress(String deviceAddress) {
        if (nativeAdapter != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_CONNECT izni yok.");
                return null;
            }
            try {
                BluetoothDevice device = nativeAdapter.getRemoteDevice(deviceAddress);
                return new AndroidBluetoothDevice(device);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Geçersiz Bluetooth cihaz adresi: " + deviceAddress, e);
                return null;
            }
        }
        return null;
    }

    @Override
    public void onConnectionStatusChanged(Consumer<ConnectionStatus> callback) {
        this.connectionStatusCallback = callback;
    }

    @Override
    public void onDataReceived(Consumer<byte[]> callback) {
        this.dataReceivedCallback = callback;
    }

    private void startReceivingData() {
        if (inputStream == null) {
            Log.e(TAG, "Giriş akışı yok, veri dinleme başlatılamıyor.");
            return;
        }
        dataReceiverThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0 && dataReceivedCallback != null) {
                        byte[] receivedData = new byte[bytes];
                        System.arraycopy(buffer, 0, receivedData, 0, bytes);
                        dataReceivedCallback.accept(receivedData);
                    }
                } catch (IOException e) {
                    if (Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "Veri okuma thread'i durduruldu.");
                    } else {
                        Log.e(TAG, "Veri okuma hatası: " + e.getMessage());
                        if (connectionStatusCallback != null) {
                            connectionStatusCallback.accept(new ConnectionStatus(connectedDevice, false, "Veri okuma hatası. Bağlantı kesildi."));
                        }
                    }
                    break;
                }
            }
        });
        dataReceiverThread.start();
    }

    // Broadcast Receiver'lar
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (deviceFoundCallback != null && device != null) {
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    IBluetoothDevice btDevice = new AndroidBluetoothDevice(device);
                    if (!discoveredDevices.contains(btDevice)) {
                        discoveredDevices.add(btDevice);
                        deviceFoundCallback.accept(btDevice);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (discoveryFinishedCallback != null) {
                    discoveryFinishedCallback.run();
                }
                try {
                    context.unregisterReceiver(this);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "DiscoveryReceiver zaten kayıtlı değil: " + e.getMessage());
                }
            }
        }
    };

    private final BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                IBluetoothDevice btDevice = new AndroidBluetoothDevice(device);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if (pairingStatusCallback != null) {
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        // Eşleştirme başarılı
                        pairingStatusCallback.accept(new IBluetoothDeviceScanner.PairingStatus(btDevice, true));
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        // Eşleştirme başarısız veya kaldırıldı
                        pairingStatusCallback.accept(new IBluetoothDeviceScanner.PairingStatus(btDevice, false));
                    }
                }
            }
        }
    };
}