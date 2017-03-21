package example.naoki.ble_myo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.echo.holographlibrary.LineGraph;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends ActionBarActivity
        implements BluetoothAdapter.LeScanCallback, IReportEmg {
    // Activity-wise Tag
    private static final String TAG = "BLE_Myo";

    // Intent code for requesting Bluetooth enable
    private static final int REQUEST_ENABLE_BT = 1;

    // Device Scanning Time (ms)
    private static final long SCAN_PERIOD = 2000;

    // UI elements and helpers
    private TextView emgDataText;
    private Button findMyoButton;
    private Button emgButton;
    private LineGraph graph;

    private LineGraphHelper channelAGraph;

    // Bluetooth and its usage status
    private BluetoothAdapter mBluetoothAdapter;

    // Data collection status
    private boolean isCollectingData = false;

    // Myos
    private ArrayAdapter<String> myoListAdapter;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayList<MyoGattCallback> myoGattCallbacks = new ArrayList<MyoGattCallback>();

    // environment and helper
    private String baseFolder;
    private Handler uiHandler;

    // Graph
    private int selectedMyoIdx;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize handler
        uiHandler = new Handler();

        // initialize UIs
        graph = (LineGraph) findViewById(R.id.holo_graph_view);
        emgDataText = (TextView) findViewById(R.id.emgDataTextView);
        findMyoButton = (Button) findViewById(R.id.bFindMyo);
        emgButton = (Button) findViewById(R.id.bEMG);

        emgButton.setEnabled(false);

        initializeMyoListView();

        channelAGraph = new LineGraphHelper(graph, uiHandler);

        // Bluetooth
        BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // initialize working environment
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            // check if external storage is available
            baseFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).toString();
        }
        else {
            // revert to using internal storage (not sure if there's an equivalent to the above)
            baseFolder = getFilesDir().getAbsolutePath();
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        this.closeBLEGatt();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(MainActivity.this);
                }
            }, SCAN_PERIOD);
            mBluetoothAdapter.startLeScan(this);
        }
    }


    public void closeBLEGatt() {
        for (MyoGattCallback m : myoGattCallbacks)
            m.stopCallback();

        deviceNames.clear();
        myoGattCallbacks.clear();
    }

    // ---- initialize UI components -------------------------------------------------------------
    private void initializeMyoListView() {
        ListView lv = (ListView) findViewById(R.id.MyoListView);
        myoListAdapter = new ArrayAdapter<>(this, R.layout.mylist_item, deviceNames);
        lv.setAdapter(myoListAdapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position != selectedMyoIdx) {
                    selectedMyoIdx = position;
                    channelAGraph.reset();
                }
            }
        });
    }


    // ---- Button callbacks ---------------------------------------------------------------------
    public void onClickFindMyo(View btn) {
        findMyoButton.setEnabled(false);
        emgButton.setEnabled(false);

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            closeBLEGatt();

            Toast.makeText(getApplicationContext(), "Scanning BLE devices...", Toast.LENGTH_SHORT).show();
            mBluetoothAdapter.startLeScan(this);

            // Scanning Time out by Handler.
            // The device scanning needs high energy.
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mBluetoothAdapter.stopLeScan(MainActivity.this);
                    myoListAdapter.notifyDataSetChanged();
                    Toast.makeText(getApplicationContext(), "Stop Device Scan", Toast.LENGTH_SHORT).show();
                    findMyoButton.setEnabled(true);
                    if (deviceNames.size() > 0) {
                        selectedMyoIdx = 0;
                        emgButton.setEnabled(true);
                    }
                }
            }, SCAN_PERIOD);
        }
    }

    public void onClickEMG(View v) {
        if (isCollectingData == false) {
            isCollectingData = true;
            emgButton.setText("Stop");
            findMyoButton.setEnabled(false);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("PST"));
            String timeString = sdf.format(new Date());

            for (MyoGattCallback m : myoGattCallbacks)
                m.startCollectEmgData(timeString);
        }
        else {
            isCollectingData = false;
            emgButton.setText("Start");
            findMyoButton.setEnabled(true);

            for (MyoGattCallback m : myoGattCallbacks)
                m.stopCollectEmgData();
        }
    }

    // ---- BLE callback -------------------------------------------------------------------------
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

            MyoGattCallback myoCallback = new MyoGattCallback(device.getName(), device, this, baseFolder, this);
            myoGattCallbacks.add(myoCallback);
        }
    }

    // ---- Get EMG data -------------------------------------------------------------------------
    @Override
    public void onReportEmg(MyoGattCallback myoCallback, int[] channelA, int[] channelB) {
        if (myoCallback == myoGattCallbacks.get(selectedMyoIdx)) {
            channelAGraph.update(channelA);

            StringBuilder sb = new StringBuilder();
            sb.append("[").append(deviceNames.get(selectedMyoIdx)).append("]\n");
            sb.append("Channel A: ");
            for (int i = 0; i < 8; i++)
                sb.append(String.format(Locale.getDefault(), "%5d", channelA[i]));
            sb.append("\n");
            sb.append("Channel B: ");
            for (int i = 0; i < 8; i++)
                sb.append(String.format(Locale.getDefault(), "%5d", channelB[i]));
            sb.append("\n");

            final String displayText = sb.toString();

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    emgDataText.setText(displayText);
                }
            });
        }
    }
}

