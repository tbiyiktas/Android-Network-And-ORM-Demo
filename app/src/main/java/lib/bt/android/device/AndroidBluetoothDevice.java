package lib.bt.android.device;

import android.bluetooth.BluetoothDevice;

import java.util.Objects;

import lib.bt.interfaces.IBluetoothDevice;

public final class AndroidBluetoothDevice implements IBluetoothDevice {
    private final BluetoothDevice device;
    public AndroidBluetoothDevice(BluetoothDevice device) { this.device = Objects.requireNonNull(device); }

    @Override public String getAddress() { return device.getAddress(); }
    @Override public String getName()    { String n = device.getName(); return (n != null && !n.isEmpty()) ? n : device.getAddress(); }

    @Override public Type getType() {
        switch (device.getType()) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC: return Type.CLASSIC;
            case BluetoothDevice.DEVICE_TYPE_LE:      return Type.BLE;
            case BluetoothDevice.DEVICE_TYPE_DUAL:    return Type.DUAL;
            default:                                  return Type.UNKNOWN;
        }
    }

    @Override public BondState getBondState() {
        switch (device.getBondState()) {
            case BluetoothDevice.BOND_BONDED:  return BondState.BONDED;
            case BluetoothDevice.BOND_BONDING: return BondState.BONDING;
            case BluetoothDevice.BOND_NONE:    return BondState.NONE;
            default:                           return BondState.FAILED;
        }
    }

    public BluetoothDevice platform() { return device; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AndroidBluetoothDevice)) return false;
        String a1 = getAddress(), a2 = ((AndroidBluetoothDevice)o).getAddress();
        return a1 != null && a1.equals(a2);
    }

    @Override public int hashCode() { String a = getAddress(); return a != null ? a.hashCode() : 0; }

    @Override public String toString() {
        return "AndroidBluetoothDevice{address='" + getAddress() + "', name='" + getName() +
                "', type=" + getType() + ", bondState=" + getBondState() + "}";
    }
}
