package example.naoki.ble_myo;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.echo.holographlibrary.LineGraph;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import example.naoki.ble_myo.uihelper.LineGraphHelper;
import example.naoki.ble_myo.uihelper.TextViewCountingHelper;
import example.naoki.ble_myo.uihelper.TextViewHelper;

public class MainActivity extends ActionBarActivity
        implements BluetoothAdapter.LeScanCallback, IReportEmg {
    // Activity-wise Tag
    private static final String TAG = "BLE_Myo";

    // Intent code for requesting Bluetooth enable
    private static final int REQUEST_ENABLE_BT = 1;

    // Device Scanning Time (ms)
    private static final long SCAN_PERIOD = 2000;

    // UI update rate
    private static final int UI_UPDATE_FPS = 45;
    private static final long FRAME_PER_MS = 1000 / UI_UPDATE_FPS + 3;

    // Workout control
    private static final long REP_TIME_MS = 2500L;
    private static final double[] REP_TIME_OPTIONS = new double[]{2.0, 2.5, 3.0, 3.5, 4.0};

    // UI elements and helpers
    private Button findMyoButton;
    private Button emgButton;
    private Spinner repTimeSpinner;

    private LineGraphHelper channelGraph;
    private TextViewHelper emgDataTextHelper;
    private TextViewCountingHelper countingTextHelper;
    private double chosenRepTimeSec;

    // Bluetooth and its usage status
    private BluetoothAdapter mBluetoothAdapter;

    // Data collection status
    private boolean isCollectingData = false;
    private String filePrefix;

    // Myos
    private ArrayAdapter<String> myoListAdapter;
    private ArrayList<String> deviceNames = new ArrayList<>();
    private ArrayList<MyoGattCallback> myoGattCallbacks = new ArrayList<MyoGattCallback>();

    // environment and helper
    private String baseFolder;
    private UiHandler uiHandler;
    private Handler btHandler;

    // Graph and real-time info
    private int selectedMyoIdx;
    private int numSamplesPrevSecond;
    private int numSamplesCurSecond;
    private long secondOfPrevSample;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize handler
        uiHandler = new UiHandler();
        btHandler = new Handler();

        // initialize UIs
        LineGraph graph = (LineGraph) findViewById(R.id.holoGraphView);
        TextView emgDataText = (TextView) findViewById(R.id.emgDataTextView);
        TextView repCntText = (TextView) findViewById(R.id.repCountingTextView);
        findMyoButton = (Button) findViewById(R.id.bFindMyo);
        emgButton = (Button) findViewById(R.id.bEMG);
        //repTimeEditView = (EditText) findViewById(R.id.editRepTime);

        emgButton.setEnabled(false);

        initializeMyoListView();
        initializeRepTimeSpinner();

        channelGraph = new LineGraphHelper(graph);
        emgDataTextHelper = new TextViewHelper(emgDataText);
        countingTextHelper = new TextViewCountingHelper(repCntText, REP_TIME_MS);

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
            btHandler.postDelayed(new Runnable() {
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
                    resetSamplingRateStatistics();
                    channelGraph.reset();
                    uiHandler.updateOnce();
                }
            }
        });
    }

    private void initializeRepTimeSpinner() {
        repTimeSpinner = (Spinner) findViewById(R.id.repTimeSpinner);

        ArrayList<String> repTimeOptionsStrings = new ArrayList<String>();
        for (double v : REP_TIME_OPTIONS)
            repTimeOptionsStrings.add(String.format(Locale.getDefault(), "%.1f secs", v));


        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, repTimeOptionsStrings);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        repTimeSpinner.setAdapter(adapter);
        repTimeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                chosenRepTimeSec = REP_TIME_OPTIONS[pos];
            }

            public void onNothingSelected(AdapterView<?> parent) {
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

            deviceNames.clear();
            myoGattCallbacks.clear();
            channelGraph.reset();
            uiHandler.updateOnce();
            resetSamplingRateStatistics();
            myoListAdapter.notifyDataSetChanged();

            Toast.makeText(getApplicationContext(), "Scanning BLE devices...", Toast.LENGTH_SHORT).show();
            mBluetoothAdapter.startLeScan(this);

            // Scanning Time out by Handler.
            // The device scanning needs high energy.
            btHandler.postDelayed(new Runnable() {
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
            repTimeSpinner.setEnabled(false);
            uiHandler.startUpdate();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("PST"));
            filePrefix = sdf.format(new Date());

            for (MyoGattCallback m : myoGattCallbacks)
                m.startCollectEmgData(filePrefix);

            countingTextHelper.start(chosenRepTimeSec);
            channelGraph.reset();
            uiHandler.updateOnce();
        }
        else {
            isCollectingData = false;
            emgButton.setText("Start");
            findMyoButton.setEnabled(true);
            repTimeSpinner.setEnabled(true);
            countingTextHelper.stop();
            uiHandler.stopUpdate();

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
    public void onReportEmg(MyoGattCallback myoCallback, long timestampMs, int[][] channels) {
        if (myoCallback == myoGattCallbacks.get(selectedMyoIdx)) {
            channelGraph.update(channels[0]);
            channelGraph.update(channels[1]);

            long timestampSec = timestampMs / 1000;
            if (timestampSec != secondOfPrevSample) {
                numSamplesPrevSecond = numSamplesCurSecond;
                numSamplesCurSecond = 0;
            }
            numSamplesCurSecond += 2;
            secondOfPrevSample = timestampSec;

            StringBuilder sb = new StringBuilder();
            sb.append("File prefix: ").append(filePrefix).append('\n');
            sb.append("Myo name: ").append(deviceNames.get(selectedMyoIdx)).append("\n");
            for (int i = 0; i < 8; i++)
                sb.append(String.format(Locale.getDefault(), "%5d", channels[1][i]));
            sb.append("\n");
            sb.append(String.format(Locale.getDefault(), "Sampling rate: %dHz", numSamplesPrevSecond));
            sb.append("\n");

            emgDataTextHelper.update(sb.toString());
        }
    }

    private void resetSamplingRateStatistics() {
        numSamplesPrevSecond = 0;
        numSamplesCurSecond = 0;
    }

    // ---- Handlers -----------------------------------------------------------------------------
    private class UiHandler extends Handler {
        private boolean isUpdating = false;
        private void startUpdate() {
            isUpdating = true;
            sendEmptyMessage(0);
        }
        private void stopUpdate() {
            isUpdating = false;
            removeMessages(0);
        }
        private void updateOnce() {
            if (isUpdating == false)
                sendEmptyMessage(0);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            channelGraph.draw();
            emgDataTextHelper.draw();
            countingTextHelper.draw();
            if (isUpdating)
                sendEmptyMessageDelayed(0, FRAME_PER_MS);
        }
    }
}

