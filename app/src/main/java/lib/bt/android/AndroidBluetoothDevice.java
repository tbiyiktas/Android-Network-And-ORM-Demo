package lib.bt.android;

import android.bluetooth.BluetoothDevice;

import lib.bt.interfaces.IBluetoothDevice;

// Android'in BluetoothDevice sınıfını sarmalayan adaptör sınıfı
public class AndroidBluetoothDevice implements IBluetoothDevice {
    private final BluetoothDevice androidDevice;

    public AndroidBluetoothDevice(BluetoothDevice androidDevice) {
        this.androidDevice = androidDevice;
    }

    @Override
    public String getAddress() {
        return androidDevice.getAddress();
    }

    @Override
    public String getName() {
        // Cihazın adı yoksa adresini döndürebiliriz
        return androidDevice.getName() != null ? androidDevice.getName() : androidDevice.getAddress();
    }

    @Override
    public int getBondState() {
        return androidDevice.getBondState();
    }

    public BluetoothDevice getBluetoothDevice() {
        return androidDevice;
    }
}