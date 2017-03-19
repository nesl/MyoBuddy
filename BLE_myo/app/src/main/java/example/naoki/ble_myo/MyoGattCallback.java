package example.naoki.ble_myo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by naoki on 15/04/15.
 *
 * Modified by TimeString on Mar. 18. 2017
 */
 
public class MyoGattCallback extends BluetoothGattCallback {
    // Service ID
    private static final String MYO_CONTROL_ID  = "d5060001-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_EMG_DATA_ID = "d5060005-a904-deb9-4748-2c7f4a124842";

    // Characteristics ID
    private static final String MYO_INFO_ID = "d5060101-a904-deb9-4748-2c7f4a124842";
    private static final String FIRMWARE_ID = "d5060201-a904-deb9-4748-2c7f4a124842";
    private static final String COMMAND_ID  = "d5060401-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_0_ID    = "d5060105-a904-deb9-4748-2c7f4a124842";

    // android Characteristic ID (from Android
    // Samples/BluetoothLeGatt/SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";

    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic_command;
    private BluetoothGattCharacteristic mCharacteristic_emg0;

    private String mBaseFolder;

    private String mMyoName;
    private String logCatTag;

    private IReportEmg mEmgReceiver;

    private TextView dataView;
    private String callback_msg;
    private Handler mHandler;
    private int[] emgDatas = new int[16];


    private int nowGraphIndex = 0;
    private Button nowButton;


    private boolean isUpdateUI = false;
    private PrintWriter mFileWriter = null;


    // ---- constructor --------------------------------------------------------------------------
    public MyoGattCallback(String myoName, BluetoothDevice device, Context context, String baseFolder, IReportEmg emgReceiver) {
        mMyoName = myoName;
        logCatTag = "MyoGatt[" + myoName + "]";

        mBluetoothGatt = device.connectGatt(context, false, this);
        mBaseFolder = baseFolder;
        mEmgReceiver = emgReceiver;
    }


    // ---- hook up receivers --------------------------------------------------------------------
    public void isUpdateUI(boolean enable) {
        /*this.isUpdateUI = enable;*/
    }

    //TODO: move to begin sense
    /*public void setOutputFile(String filePath) {

    }*/


    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        Log.d(logCatTag, "onConnectionStateChange: " + status + " -> " + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            // GATT Connected
            // Searching GATT Service
            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // GATT Disconnected
            stopCallback();
            Log.d(logCatTag,"Bluetooth Disconnected");
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        Log.d(logCatTag, "onServicesDiscovered received: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Find GATT Service
            BluetoothGattService service_emg = gatt.getService(UUID.fromString(MYO_EMG_DATA_ID));
            if (service_emg == null) {
                Log.d(logCatTag, "No Myo EMG-Data Service !!");
            } else {
                Log.d(logCatTag, "Find Myo EMG-Data Service !!");
                // Getting CommandCharacteristic
                mCharacteristic_emg0 = service_emg.getCharacteristic(UUID.fromString(EMG_0_ID));
                if (mCharacteristic_emg0 == null) {
                    callback_msg = "Not Found EMG-Data Characteristic";
                } else {
                    // Setting the notification
                    boolean registered_0 = gatt.setCharacteristicNotification(mCharacteristic_emg0, true);
                    if (!registered_0) {
                        Log.d(logCatTag,"EMG-Data Notification FALSE !!");
                    } else {
                        Log.d(logCatTag,"EMG-Data Notification TRUE !!");
                        // Turn ON the Characteristic Notification
                        BluetoothGattDescriptor descriptor_0 = mCharacteristic_emg0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        if (descriptor_0 != null ){
                            descriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

                            writeGattDescriptor(descriptor_0);

                            Log.d(logCatTag, "Set descriptor: " + descriptor_0.toString());

                        } else {
                            Log.d(logCatTag, "No descriptor");
                        }
                    }
                }
            }

            BluetoothGattService service = gatt.getService(UUID.fromString(MYO_CONTROL_ID));
            if (service == null) {
                Log.d(logCatTag, "No Myo Control Service !!");
            } else {
                Log.d(logCatTag, "Find Myo Control Service !!");
                // Get the MyoInfoCharacteristic
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString(MYO_INFO_ID));
                if (characteristic == null) {
                } else {
                    Log.d(logCatTag, "Find read Characteristic !!");
                    //put the characteristic into the read queue
                    readCharacteristicQueue.add(characteristic);
                    //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
                    //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
                    if((readCharacteristicQueue.size() == 1) && (descriptorWriteQueue.size() == 0)) {
                        mBluetoothGatt.readCharacteristic(characteristic);
                    }
/*                        if (gatt.readCharacteristic(characteristic)) {
                            Log.d(TAG, "Characteristic read success !!");
                        }
*/
                }

                // Get CommandCharacteristic
                mCharacteristic_command = service.getCharacteristic(UUID.fromString(COMMAND_ID));
                if (mCharacteristic_command != null) {
                    Log.d(logCatTag, "Find command Characteristic !!");
                }
            }
        }
    }

    public void writeGattDescriptor(BluetoothGattDescriptor d){
        //put the descriptor into the write queue
        descriptorWriteQueue.add(d);
        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
        if(descriptorWriteQueue.size() == 1){
            mBluetoothGatt.writeDescriptor(d);
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(logCatTag, "Callback: Wrote GATT Descriptor successfully.");
        }
        else{
            Log.d(logCatTag, "Callback: Error writing GATT Descriptor: "+ status);
        }
        descriptorWriteQueue.remove();  //pop the item that we just finishing writing
        //if there is more to write, do it!
        if(descriptorWriteQueue.size() > 0)
            mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());
        else if(readCharacteristicQueue.size() > 0)
            mBluetoothGatt.readCharacteristic(readCharacteristicQueue.element());
    }

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        readCharacteristicQueue.remove();
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (UUID.fromString(FIRMWARE_ID).equals(characteristic.getUuid())) {
                // Myo Firmware Infomation
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    ByteReader byteReader = new ByteReader();
                    byteReader.setByteData(data);

                    Log.d(logCatTag, String.format("This Version is %d.%d.%d - %d",
                            byteReader.getShort(), byteReader.getShort(),
                            byteReader.getShort(), byteReader.getShort()));

                }
                if (data == null) {
                    Log.d(logCatTag,"Characteristic String is " + characteristic.toString());
                }
            } else if (UUID.fromString(MYO_INFO_ID).equals(characteristic.getUuid())) {
                // Myo Device Information
                final byte[] data = characteristic.getValue();
                if (data != null && data.length > 0) {
                    ByteReader byteReader = new ByteReader();
                    byteReader.setByteData(data);

                    callback_msg = String.format("Serial Number     : %02x:%02x:%02x:%02x:%02x:%02x",
                            byteReader.getByte(), byteReader.getByte(), byteReader.getByte(),
                            byteReader.getByte(), byteReader.getByte(), byteReader.getByte()) +
                            '\n' + String.format("Unlock            : %d", byteReader.getShort()) +
                            '\n' + String.format("Classifier builtin:%d active:%d (have:%d)",
                            byteReader.getByte(), byteReader.getByte(), byteReader.getByte()) +
                            '\n' + String.format("Stream Type       : %d", byteReader.getByte());

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dataView.setText(callback_msg);
                        }
                    });

                }
            }
        }
        else{
            Log.d(logCatTag, "onCharacteristicRead error: " + status);
        }

        if (readCharacteristicQueue.size() > 0)
            mBluetoothGatt.readCharacteristic(readCharacteristicQueue.element());
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(logCatTag, "onCharacteristicWrite success");
        } else {
            Log.d(logCatTag, "onCharacteristicWrite error: " + status);
        }
    }

    private long last_send_never_sleep_time_ms = System.currentTimeMillis();
    private final static long NEVER_SLEEP_SEND_TIME = 10000;  // Milli Second
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        //Log.d(TAG, "onCharacteristicChanged: " + EMG_0_ID +"=="+ characteristic.getUuid().toString());
        if (EMG_0_ID.equals(characteristic.getUuid().toString())) {
            long systemTime_ms = System.currentTimeMillis();
            byte[] emg_data = characteristic.getValue();

            ByteReader emg_br = new ByteReader();
            emg_br.setByteData(emg_data);

            StringBuilder sbForFile = new StringBuilder();
            sbForFile.append(systemTime_ms);
            for (int i = 0; i < 16; i++)
                sbForFile.append(',').append(emg_data[i]);
            sbForFile.append('\n');
            String strForFile = sbForFile.toString();

            if (mFileWriter != null) {
                try {
                    mFileWriter.write(strForFile);
                    mFileWriter.flush();
                    Log.d("mFileWriter", strForFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            int[] emgChannelA = new int[8];
            int[] emgChannelB = new int[8];
            for (int i = 0; i < 8; i++)
                emgChannelA[i] = emg_data[i];
            for (int i = 0; i < 8; i++)
                emgChannelB[i] = emg_data[i + 8];
            mEmgReceiver.onReportEmg(this, emgChannelA, emgChannelB);

            if (systemTime_ms > last_send_never_sleep_time_ms + NEVER_SLEEP_SEND_TIME) {
                // set Myo [Never Sleep Mode]
                setMyoControlCommand(MyoCommandList.sendUnSleep());
                last_send_never_sleep_time_ms = systemTime_ms;
            }
        }
    }

    public boolean setMyoControlCommand(byte[] command) {
        if (mCharacteristic_command != null) {
            mCharacteristic_command.setValue(command);
            int i_prop = mCharacteristic_command.getProperties();
            if (i_prop == BluetoothGattCharacteristic.PROPERTY_WRITE) {
                if (mBluetoothGatt.writeCharacteristic(mCharacteristic_command)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stopCallback() {
        // Before the closing GATT, set Myo [Normal Sleep Mode].
        setMyoControlCommand(MyoCommandList.sendNormalSleep());
        descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
        readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();
        if (mCharacteristic_command != null) {
            mCharacteristic_command = null;
        }
        if (mCharacteristic_emg0 != null) {
            mCharacteristic_emg0 = null;
        }

        //TODO: this seems to break the original use, but can fit into our purpose
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    // ---- helper functions --------------------------------------------------------------------
    public void startCollectEmgData() {
        // generate file
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("PST"));
        String filename = "log_" + sdf.format(new Date()) + ".txt";
        String pathname = mBaseFolder + File.separator + filename;
        try {
            File file = new File(pathname);
            file.getParentFile().mkdirs();
            Log.d(logCatTag, "save log to " + file.toString());
            mFileWriter = new PrintWriter(new FileOutputStream(file));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // send collecting command
        setMyoControlCommand(MyoCommandList.sendEmgOnly());
    }

    public void stopCollectEmgData() {
        // close file
        try {
            mFileWriter.flush();
            mFileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // send stop command
        setMyoControlCommand(MyoCommandList.sendUnsetData());
    }
}
