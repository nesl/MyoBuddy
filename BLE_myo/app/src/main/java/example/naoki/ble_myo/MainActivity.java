package example.naoki.ble_myo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.echo.holographlibrary.LineGraph;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import ro.kazy.tcpclient.TcpClient;

public class MainActivity extends ActionBarActivity implements BluetoothAdapter.LeScanCallback {
    public static final int MENU_LIST = 0;
    public static final int MENU_BYE = 1;

    /** Device Scanning Time (ms) */
    private static final long SCAN_PERIOD = 5000;

    /** Intent code for requesting Bluetooth enable */
    private static final int REQUEST_ENABLE_BT = 1;

    private static final String TAG = "BLE_Myo";

    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;
    private TextView         emgDataText;
    private TextView         gestureText;

    private MyoCommandList commandList = new MyoCommandList();
    private TcpClient mTcpClient = null;
    private ConnectTask mConnectTask = new ConnectTask();
    private ArrayAdapter<String> myoListAdapter;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private HashMap<String,View> views = null;
    private FileOutputStream mOutputStream;

    private class DeviceData {
        private boolean is_connected;
        private String mac_addr;
        private BluetoothGatt gatt;
        private MyoGattCallback myoCallback;

        public DeviceData (boolean is_connected, String mac_addr, BluetoothGatt gatt, MyoGattCallback myoCallback){
            this.is_connected = is_connected;
            this.mac_addr = mac_addr;
            this.gatt = gatt;
            this.myoCallback = myoCallback;
        }
    };
    HashMap<String,DeviceData> deviceMap = new HashMap<>();

    private String updateUIDeviceName = null;

    private LineGraph graph;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ready
        graph = (LineGraph) findViewById(R.id.holo_graph_view);
        emgDataText = (TextView)findViewById(R.id.emgDataTextView);
        gestureText = (TextView)findViewById(R.id.gestureTextView);
        mHandler = new Handler();

        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();


        ListView lv = (ListView) findViewById(R.id.MyoListView);
        myoListAdapter = new ArrayAdapter<>(this,
                R.layout.mylist_item, deviceNames);
        lv.setAdapter(myoListAdapter);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ListView listView = (ListView) parent;
                String item = (String) listView.getItemAtPosition(position);
                Toast.makeText(getApplicationContext(), item + " connect", Toast.LENGTH_SHORT).show();
                String myoName = item;

                if (views == null) {
                    views = new HashMap<>();
                    views.put("graph",graph);
                }

                DeviceData data = deviceMap.get(myoName);
                Log.d(TAG, "device: " + myoName + ", mac=" + data.mac_addr + ", is_connected=" + data.is_connected);
                if (data.is_connected == false) {
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(data.mac_addr);
                    // Trying to connect GATT
                    MyoGattCallback myoCallback = new MyoGattCallback(mHandler, emgDataText, views, myoName);
                    BluetoothGatt gatt = device.connectGatt(MainActivity.this, false, myoCallback);
                    myoCallback.setBluetoothGatt(gatt);
                    data.gatt = gatt;
                    data.myoCallback = myoCallback;
                    data.is_connected = true;
                    deviceMap.put(myoName, data);
                    data = deviceMap.get(myoName);
                    Log.d(TAG, "device should be connected: " + myoName + ", mac=" + data.mac_addr + ", is_connected=" + data.is_connected);
                }
                // switch updateUIDeviceName
                if(updateUIDeviceName == null) {
                    deviceMap.get(myoName).myoCallback.isUpdateUI(true);
                    updateUIDeviceName = myoName;
                    //listView.getChildAt(position).setBackgroundColor(Color.GRAY);
                }
                else if (!updateUIDeviceName.equals(myoName)) {
                    deviceMap.get(updateUIDeviceName).myoCallback.isUpdateUI(false);
                    //listView.getChil.getChildAt(position).setBackgroundColor(Color.TRANSPARENT);
                    deviceMap.get(myoName).myoCallback.isUpdateUI(true);
                    //listView.getChildAt(position).setBackgroundColor(Color.GRAY);
                    updateUIDeviceName = myoName;
                }
            }
        });

        // Array of choices
        String BodyPart[] = {"arms", "chest", "shoulder", "back", "legs"};
        // Selection of the spinner
        final Spinner spinnerBodyPart = (Spinner) findViewById(R.id.spinnerBodyPart);
        // Application of the Array to the Spinner
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, BodyPart);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); // The drop down view
        spinnerBodyPart.setAdapter(spinnerArrayAdapter);

        spinnerBodyPart.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Log.d(TAG, "trainingMode=" + spinnerBodyPart.getSelectedItem().toString());
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }

        });
        spinnerBodyPart.setSelection(0);
    }

    @Override
    public void onStop(){
        super.onStop();
        this.closeBLEGatt();
    }

    /** Define of BLE Callback */
    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

        // Device Log
        ParcelUuid[] uuids = device.getUuids();
        String uuid = "";
        if (uuids != null) {
            for (ParcelUuid puuid : uuids) {
                uuid += puuid.toString() + " ";
            }
        }

        String msg = "name=" + device.getName() + ", bondStatus="
                + device.getBondState() + ", address="
                + device.getAddress() + ", type" + device.getType()
                + ", uuids=" + uuid;
        Log.d("BLEActivity", msg);

        if (device.getName() != null && !deviceNames.contains(device.getName())) {
            deviceNames.add(device.getName());
            DeviceData data = new DeviceData(false, device.getAddress(), null, null);
            deviceMap.put(device.getName(), data);
        }
    }

    public void scanDevice() {
        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            deviceNames.clear();
            deviceMap.clear();

            // Scanning Time out by Handler.
            // The device scanning needs high energy.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(MainActivity.this);
                    myoListAdapter.notifyDataSetChanged();
                    Toast.makeText(getApplicationContext(), "Stop Device Scan", Toast.LENGTH_SHORT).show();
                }
            }, SCAN_PERIOD);

        }
        mBluetoothAdapter.startLeScan(this);
    }

    public void onClickFindMyo(View v){

        scanDevice();
    }

    public void doConnectTCP(String cmd)
    {
        String str = ((EditText)findViewById(R.id.textServerIP)).getText().toString();
        String server_ip = str.substring(0, str.indexOf(":"));
        String server_port = str.substring(str.indexOf(":")+1);
        String trainingMode = ((Spinner)findViewById(R.id.spinnerBodyPart)).getSelectedItem().toString();
        Log.d(TAG, "server ip:" + server_ip + ":" + server_port + ",trainingMode=" + trainingMode);

        mConnectTask = new ConnectTask();
        String params[] = {server_ip, server_port, cmd};
        mConnectTask.execute(params);
    }

    public void onConnectTCP(View v)
    {
        this.doConnectTCP("test_tcp_server");
    }

    public void onClickEMG(View v) {
        Button bEMG = (Button) v.findViewById(R.id.bEMG);
        if(bEMG.getText().equals("Start")) {

            //doConnectTCP("start_emg_data");
            // open a new FileOptputStream

            String baseFolder;
            // check if external storage is available
            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                baseFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString();
            }
            // revert to using internal storage (not sure if there's an equivalent to the above)
            else {
                baseFolder = getFilesDir().getAbsolutePath();
            }


            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("PST"));
            String filename = "log_" + sdf.format(new Date()).toString() + ".txt";
            String pathname = baseFolder + File.separator + filename;

            for (String s : deviceMap.keySet()) {
                DeviceData data = deviceMap.get(s);
                if(data.is_connected == true) {
                    Log.d(TAG, s + " enable EMG");
                    data.myoCallback.setTcpClient(mTcpClient);
                    data.myoCallback.setOutputFile(pathname);
                    data.myoCallback.setMyoControlCommand(commandList.sendEmgOnly());
                }
            }

            bEMG.setText("Stop");
            setGestureText("Training!");
            deviceMap.get(updateUIDeviceName).myoCallback.isUpdateUI(true);
            Log.d(TAG, "start logging");
        }
        else if (bEMG.getText().equals("Stop")) {
            bEMG.setText("Start");
            setGestureText("Gesture");
            //bEMG.setEnabled(false);
            deviceMap.get(updateUIDeviceName).myoCallback.isUpdateUI(false);
            //mTcpClient.sendMessage("stop");

            for (String s : deviceMap.keySet()) {
                DeviceData data = deviceMap.get(s);
                if(data.is_connected == true) {
                    data.myoCallback.setMyoControlCommand(commandList.sendUnsetData());
                    //data.myoCallback.setTcpClient(null);
                }
            }

            try {
                // TODO: instead of just calling
                deviceMap.get(updateUIDeviceName).myoCallback.setOutputFile(null);
                mOutputStream.write(("[Stop]\n").getBytes());
                mOutputStream.flush();
                mOutputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Log.d(TAG, "stop logging");
        }
        //Log.d(TAG, bEMG.getText().toString());

    }



    public void closeBLEGatt() {
        /*if (mBluetoothGatt == null) {
            return;
        }
        mMyoCallback.stopCallback();
        mBluetoothGatt.close();
        mBluetoothGatt = null;*/
        for (String s : this.deviceMap.keySet()) {
            DeviceData data = deviceMap.get(s);
            if (data.is_connected) {
                data.myoCallback.stopCallback();
                data.gatt.close();
                data.myoCallback = null;
                data.gatt = null;
            }
        }
        deviceMap.clear();
        deviceNames.clear();
    }


    public void setGestureText(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                gestureText.setText(message);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(MainActivity.this);
                }
            }, SCAN_PERIOD);
            mBluetoothAdapter.startLeScan(this);
        }
    }

    // KATIE: add TCP client async task here
    public class ConnectTask extends AsyncTask<String, String, TcpClient> {

        private String command = "";


        @Override
        protected TcpClient doInBackground(String... message) {

            String server_ip = message[0];
            String server_port = message[1];
            String cmd = message[2];

            Log.d(TAG, "Start TCP AsyncTask - server ip:" + server_ip + ":" + server_port + ", command=" + cmd);

            this.command = cmd;

            //we create a TCPClient object and
            mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            }, server_ip, Integer.parseInt(server_port));
            mTcpClient.run();

            return null;
        }

        @Override
        protected void onCancelled() {
            if(mTcpClient != null) {
                mTcpClient.stopClient();
                mTcpClient = null;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);

            if(values[0].equals("TCPServerStop"))
            {
                ((TextView)findViewById(R.id.textViewConnectStatus)).setText("Stop");
                Button bEMG = (Button)findViewById(R.id.bEMG);
                bEMG.setText("Start");
                for (String s : deviceMap.keySet()) {
                    DeviceData data = deviceMap.get(s);
                    if(data.is_connected == true) {
                        data.myoCallback.setMyoControlCommand(commandList.sendUnsetData());
                        //data.myoCallback.setTcpClient(null);
                    }
                }
                mTcpClient = null;
                Log.d(TAG, "Stop to send data to tcpServer");
            }
            else if(values[0].equals("TCPServerStart"))
            {
                ((TextView)findViewById(R.id.textViewConnectStatus)).setText("Start");
                Log.d(TAG, "Server OK!");
                String trainingMode = ((Spinner)findViewById(R.id.spinnerBodyPart)).getSelectedItem().toString();
                mTcpClient.sendMessage(trainingMode);
                Log.d(TAG, "Start to send data to tcpServer, trainingMode=" + trainingMode);

                if(this.command.equals("test_tcp_server")){
                    mTcpClient.sendMessage("stop");
                    return;
                }

                for (String s : deviceMap.keySet()) {
                    DeviceData data = deviceMap.get(s);
                    if(data.is_connected == true) {
                        Log.d(TAG, s + " enable EMG");
                        data.myoCallback.setTcpClient(mTcpClient);
                        data.myoCallback.setMyoControlCommand(commandList.sendEmgOnly());
                    }
                }
            }
            else {
                Log.d(TAG, "onProgressUpdate: recv msg from server=" + values[0]);
                setGestureText(values[0]);
            }
        }
    }
}

