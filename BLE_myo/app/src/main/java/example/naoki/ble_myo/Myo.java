package example.naoki.ble_myo;

import android.bluetooth.BluetoothGatt;

/**
 * Created by timestring on 3/17/17.
 */
public class Myo {
    private String mac_addr;
    private BluetoothGatt gatt;
    private MyoGattCallback myoCallback;

    public Myo(String mac_addr, BluetoothGatt gatt, MyoGattCallback myoCallback){
        this.mac_addr = mac_addr;
        this.gatt = gatt;
        this.myoCallback = myoCallback;
    }

    //
    // getters
    //
    public String getMacAddr() {
        return mac_addr;
    }

    public void close() {
        myoCallback.stopCallback();
        gatt.close();
    }

}
