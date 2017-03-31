package example.naoki.ble_myo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * Created by naoki on 15/04/15.
 *
 * Modified by TimeString on Mar. 18. 2017
 */
 
public class MyoGattCallback extends BluetoothGattCallback {
    // Service ID
    private static final UUID MYO_CONTROL_UUID = UUID.fromString("d5060001-a904-deb9-4748-2c7f4a124842");
    private static final UUID MYO_EMG_DATA_UUID = UUID.fromString("d5060005-a904-deb9-4748-2c7f4a124842");

    // Characteristics ID
    private static final UUID MYO_INFO_UUID = UUID.fromString("d5060101-a904-deb9-4748-2c7f4a124842");
    private static final UUID FIRMWARE_UUID = UUID.fromString("d5060201-a904-deb9-4748-2c7f4a124842");
    private static final UUID COMMAND_UUID = UUID.fromString("d5060401-a904-deb9-4748-2c7f4a124842");
    private static final UUID EMG_0_UUID = UUID.fromString("d5060105-a904-deb9-4748-2c7f4a124842");
    private static final UUID EMG_1_UUID = UUID.fromString("d5060205-a904-deb9-4748-2c7f4a124842");
    private static final UUID EMG_2_UUID = UUID.fromString("d5060305-a904-deb9-4748-2c7f4a124842");
    private static final UUID EMG_3_UUID = UUID.fromString("d5060405-a904-deb9-4748-2c7f4a124842");
    private static final UUID[] EMG_UUIDS = {EMG_0_UUID, EMG_1_UUID, EMG_2_UUID, EMG_3_UUID};

    // android Characteristic ID (from Android
    // Samples/BluetoothLeGatt/SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG_ID_STRING)
    private static final UUID CLIENT_CHARACTERISTIC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> characteristicReadQueue = new LinkedList<BluetoothGattCharacteristic>();
    private boolean isProcessingQueue = false;

    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCommandCharacteristic;
    private BluetoothGattCharacteristic[] mEmgCharacteristics = new BluetoothGattCharacteristic[EMG_UUIDS.length];
    private HashMap<BluetoothGattCharacteristic, Integer> mEmgCharacteristics2Idx = new HashMap<BluetoothGattCharacteristic, Integer>();

    private String mBaseFolder;

    private String mMyoName;
    private String logCatTag;

    private IReportEmg mEmgReceiver;

    private PrintWriter mFileWriter = null;


    // ---- constructor --------------------------------------------------------------------------
    public MyoGattCallback(String myoName, BluetoothDevice device, Context context,
                           String baseFolder, IReportEmg emgReceiver) {
        logCatTag = "MyoGatt[" + myoName + "]";
        mMyoName = myoName;

        mBluetoothGatt = device.connectGatt(context, false, this);
        mBaseFolder = baseFolder;
        mEmgReceiver = emgReceiver;
    }

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
            //TODO: report disconnected
            stopCallback();
            Log.d(logCatTag,"Bluetooth Disconnected");
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        Log.d(logCatTag, "onServicesDiscovered received: " + status);

        if (status != BluetoothGatt.GATT_SUCCESS)
            return;

        // find EMG gatt service
        BluetoothGattService emgBtService = gatt.getService(MYO_EMG_DATA_UUID);
        if (emgBtService == null)
            Log.d(logCatTag, "No Myo EMG-Data Service !!");
        else {
            Log.d(logCatTag, "Find Myo EMG-Data Service !!");

            //for (int i = 0; i < EMG_UUIDS.length; i++) {
            for (int i = 3; i >= 0; i--) {
                UUID emgUUID = EMG_UUIDS[i];

                Log.d(logCatTag, "Try to get EMG service " + i);

                // Getting CommandCharacteristic
                mEmgCharacteristics[i] = emgBtService.getCharacteristic(emgUUID);
                if (mEmgCharacteristics[i] == null) {
                    //TODO: report error
                    //callback_msg = "Not Found EMG-Data Characteristic";
                    Log.d(logCatTag, "Cannot get characteristics");
                } else {
                    // Setting the notification
                    boolean registered = gatt.setCharacteristicNotification(mEmgCharacteristics[i], true);
                    if (!registered) {
                        Log.d(logCatTag, "EMG-Data Notification FALSE !!");
                    } else {
                        Log.d(logCatTag, "EMG-Data Notification TRUE !!");
                        // Turn ON the Characteristic Notification
                        BluetoothGattDescriptor descriptor = mEmgCharacteristics[i].getDescriptor(CLIENT_CHARACTERISTIC_UUID);
                        if (descriptor == null)
                            Log.d(logCatTag, "No descriptor");
                        else {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            writeGattDescriptor(descriptor);

                            mEmgCharacteristics2Idx.put(mEmgCharacteristics[i], i);

                            Log.d(logCatTag, "Set descriptor: " + descriptor.toString());
                        }
                    }
                }
            }
        }

        // find control gatt service
        BluetoothGattService controlBtService = gatt.getService(MYO_CONTROL_UUID);
        if (controlBtService == null)
            Log.d(logCatTag, "No Myo Control Service !!");
        else {
            Log.d(logCatTag, "Find Myo Control Service !!");
            // Get the MyoInfoCharacteristic
            BluetoothGattCharacteristic characteristic =
                    controlBtService.getCharacteristic(MYO_INFO_UUID);
            if (characteristic != null) {
                Log.d(logCatTag, "Find service read Characteristic !!");
                readGattCharacteristics(characteristic);
            }

            // Get CommandCharacteristic
            mCommandCharacteristic = controlBtService.getCharacteristic(COMMAND_UUID);
            if (mCommandCharacteristic != null) {
                Log.d(logCatTag, "Find command Characteristic !!");
            }
        }
    }


    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS)
            Log.d(logCatTag, "Callback: Wrote GATT Descriptor successfully.");
        else
            Log.d(logCatTag, "Callback: Error writing GATT Descriptor: "+ status);

        // pop the item that we just finishing writing
        descriptorWriteQueue.remove();
        isProcessingQueue = false;

        // if there is more to write, do it!
        runNextBtRequest();
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        characteristicReadQueue.remove();

        if (status != BluetoothGatt.GATT_SUCCESS)
            Log.d(logCatTag, "onCharacteristicRead error: " + status);
        else {
            UUID characteristicUUID = characteristic.getUuid();

            ByteReader byteReader = null;
            byte[] data = characteristic.getValue();
            if (data != null && data.length > 0)
                byteReader = new ByteReader(data);

            if (characteristicUUID.equals(FIRMWARE_UUID)) {
                // Myo Firmware Infomation
                if (byteReader == null)
                    Log.d(logCatTag,"Characteristic String is " + characteristic.toString());
                else {
                    Log.d(logCatTag, String.format("This Version is %d.%d.%d - %d",
                            byteReader.getShort(), byteReader.getShort(),
                            byteReader.getShort(), byteReader.getShort()));

                }
            } else if (characteristic.getUuid().equals(MYO_INFO_UUID)) {
                // Myo Device Information
                if (byteReader != null) {
                    //callback_msg = String.format("Serial Number     : %02x:%02x:%02x:%02x:%02x:%02x",
                    //        byteReader.getByte(), byteReader.getByte(), byteReader.getByte(),
                    //        byteReader.getByte(), byteReader.getByte(), byteReader.getByte()) +
                    //        '\n' + String.format("Unlock            : %d", byteReader.getShort()) +
                    //        '\n' + String.format("Classifier builtin:%d active:%d (have:%d)",
                    //        byteReader.getByte(), byteReader.getByte(), byteReader.getByte()) +
                    //        '\n' + String.format("Stream Type       : %d", byteReader.getByte());

                    //mHandler.post(new Runnable() {
                    //    @Override
                    //    public void run() {
                    //        dataView.setText(callback_msg);
                    //    }
                    //});

                }
            }
        }

        isProcessingQueue = false;
        runNextBtRequest();
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(logCatTag, "onCharacteristicWrite success");
        } else {
            Log.d(logCatTag, "onCharacteristicWrite error: " + status);
        }
    }

    private long lastSendNeverSleepTimeMs = System.currentTimeMillis();
    private final static long NEVER_SLEEP_SEND_TIME = 10000;  // Milli Second
    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        //Log.d(TAG, "onCharacteristicChanged: " + EMG_0_ID_STRING +"=="+ characteristic.getUuid().toString());

        if (mEmgCharacteristics2Idx.containsKey(characteristic)) {
            int emgIndex = mEmgCharacteristics2Idx.get(characteristic);

            long systemTimeMs = System.currentTimeMillis();
            byte[] emgData = characteristic.getValue();
            int[][] emgChannels = new int[2][8];
            for (int i = 0; i < 2; i++)
                for (int j = 0; j < 8; j++)
                    emgChannels[i][j] = emgData[i * 8 + j];

            for (int i = 0; i < 2; i++) {
                StringBuilder sbForFile = new StringBuilder();
                sbForFile.append(systemTimeMs).append(",").append(emgIndex);
                for (int j = 0; j < 8; j++)
                    sbForFile.append(',').append(emgChannels[i][j]);
                sbForFile.append('\n');
                String strForFile = sbForFile.toString();

                try {
                    mFileWriter.write(strForFile);
                    mFileWriter.flush();
                    Log.d("mFileWriter", strForFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            mEmgReceiver.onReportEmg(this, systemTimeMs, emgChannels);

            if (systemTimeMs > lastSendNeverSleepTimeMs + NEVER_SLEEP_SEND_TIME) {
                // set Myo [Never Sleep Mode]
                setMyoControlCommand(MyoCommandList.sendUnSleep());
                lastSendNeverSleepTimeMs = systemTimeMs;
            }
        }
    }

    public boolean setMyoControlCommand(byte[] command) {
        if (mCommandCharacteristic == null)
            return false;

        mCommandCharacteristic.setValue(command);
        if (mCommandCharacteristic.getProperties() != BluetoothGattCharacteristic.PROPERTY_WRITE)
            return false;

        return mBluetoothGatt.writeCharacteristic(mCommandCharacteristic);
    }

    public void stopCallback() {
        // Before the closing GATT, set Myo [Normal Sleep Mode].
        setMyoControlCommand(MyoCommandList.sendNormalSleep());
        descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
        characteristicReadQueue = new LinkedList<BluetoothGattCharacteristic>();
        if (mCommandCharacteristic != null) {
            mCommandCharacteristic = null;
        }
        if (mEmgCharacteristics != null) {
            mEmgCharacteristics = null;
        }

        //TODO: this seems to break the original use, but can fit into our purpose
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
    }

    // ---- write descriptor and read characteristic queue dispatching ---------------------------
    private void writeGattDescriptor(BluetoothGattDescriptor d) {
        //put the descriptor into the write queue
        descriptorWriteQueue.add(d);
        runNextBtRequest();
    }

    private void readGattCharacteristics(BluetoothGattCharacteristic c) {
        characteristicReadQueue.add(c);
        runNextBtRequest();
    }

    private void runNextBtRequest() {
        if (isProcessingQueue)
            return;

        isProcessingQueue = true;

        if (descriptorWriteQueue.size() > 0)
            mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());
        else if (characteristicReadQueue.size() > 0)
            mBluetoothGatt.readCharacteristic(characteristicReadQueue.element());
        else
            isProcessingQueue = false;
    }

    // ---- command helper functions -------------------------------------------------------------
    public void startCollectEmgData(String timeString) {
        // generate file
        String myoNameForFile = mMyoName.replaceAll(" ", "-");
        String filename = "log_" + timeString + "_" + myoNameForFile + ".txt";
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
