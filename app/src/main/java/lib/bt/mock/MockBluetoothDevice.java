package lib.bt.mock;

import lib.bt.interfaces.IBluetoothDevice;

/**
 * IBluetoothDevice arayüzünü uygulayan mock sınıfı.
 */
public class MockBluetoothDevice implements IBluetoothDevice {
    private final String address;
    private final String name;

    public MockBluetoothDevice(String address, String name) {
        this.address = address;
        this.name = name;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public String getName() {
        return name;
    }


    @Override
    public int getBondState() {
        // Test senaryosuna göre bir değer döndürebilirsiniz.
        // Örneğin, "Mock Device" adında bir cihazın eşleşmiş olduğunu varsayalım.
        if ("Mock Device".equals(name)) {
            return 12; // BluetoothDevice.BOND_BONDED;
        }
        return 10; // BluetoothDevice.BOND_NONE;
    }
}