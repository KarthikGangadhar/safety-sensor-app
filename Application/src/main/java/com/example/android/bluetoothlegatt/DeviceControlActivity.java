package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static java.lang.Float.parseFloat;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private List<Entry> lineEntries = new ArrayList<Entry>();

    LineChart pressureChart;
    LineChart tempChart;
    LineChart humidityChart;
    LineChart resistanceChart;
    LineChart no2Chart;
    LineChart coChart;
    LineChart nh3Chart;

    private Thread thread;
    private boolean plotData = true;
    private boolean showGraph = false;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private AlertDialog dialog = null;
    private Ringtone ringtone = null;
    private Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
            drawLineChart(1, "Pressure", " hPa (hectopascal: 100 x 1 pascal)");
            drawLineChart(2, "Temparature", " °C (Degree Celsius)");
            drawLineChart(3, "Humidity", " % (Percentage)");
            drawLineChart(4, "Resistance", "Normalized Plot");
            drawLineChart(5, "NO2", "Analog Value Plot");
            drawLineChart(6, "NH3", "Analog Value Plot");
            drawLineChart(7, "CO", "Analog Value Plot");

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
//                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                  displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA), context);
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data, Context context) {
        try {
            if (data != null) {
                if(data.contains("Sensor") || data.contains("��") || data.contains("\n00 00")){
                }else{
//                    String[] result =  data.split("\r\n\n", 0);
//                    String[] readings = result[0].split(",", 0);
//                    Float Average = Float.parseFloat(readings[0]);
//                    Float value = Float.parseFloat(readings[1]);
//                    if(Average <= 0.00){
                    try {
                        if(showGraph){
                            String[] result;
                            if (data.contains("\r\n\r")){
                                result =  data.split("\r\n", 0);
                            }else if(data.contains("\n")){
                                result =  data.split("\n", 0);
                            }else{
                                result =  data.split("\n", 0);
                            }

                            if(result != null && result.length >1 && plotData){
                                String[] dataSplit = result[0].split(":",0);

                                if(dataSplit[0].contentEquals("F")){
                                    String[] yvalue = dataSplit[1].split(",",0);
                                    addEntry(resistanceChart , parseFloat(yvalue[1]), "CO2");
                                    addEntry(tempChart , parseFloat(yvalue[2]), "Temparature");
                                }else if (dataSplit[0].contentEquals("S")){
                                    String[] yvalue = dataSplit[1].split(",",0);
                                    addEntry(pressureChart , parseFloat(yvalue[0]), "Pressure");
                                    addEntry(humidityChart , parseFloat(yvalue[1]), "Humidity");
                                }else if (dataSplit[0].contentEquals("M")){
                                    String[] yvalue = dataSplit[1].split(",",0);
                                    addEntry(no2Chart , parseFloat(yvalue[0]), "NO2");
                                    addEntry(nh3Chart , parseFloat(yvalue[1]), "NH3");
                                    addEntry(coChart , parseFloat(yvalue[2]), "CO");
                                }
                                plotData = false;
                            }
                        }
                    }catch (Exception e){
                        Log.d(TAG, e.getMessage());
                    }
//                    mDataField.setText(data);
//                    }else{
//                        Float NormalizedData = ((value - Average) / Average ) * 100;
//                        NormalizedData = NormalizedData < 0 ? NormalizedData * -1 : NormalizedData;
//                        mDataField.setText(NormalizedData.toString());
//                        if(NormalizedData >= 30.0){
//                            if (dialog != null && dialog.isShowing()) {
//                            } else {
//                                dialog = showAlertDialog(context, "AQMS-Lite", "CO2 levels increased, its harmful to your health", "Cancel", "OK");
//                            }
//                            ringtone.play();
//                        }
//                    }
                }

            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addEntry(LineChart chart, float yvalue, String type) {
        LineData data = chart.getData();

        if (data != null) {

            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet(type);
                data.addDataSet(set);
            }

            Log.d(TAG, "added xvalue: " + set.getEntryCount()+ ", yvalue: " + yvalue);
            data.addEntry(new Entry(set.getEntryCount(), yvalue), 0);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            chart.notifyDataSetChanged();

            // limit the number of visible entries
            chart.setVisibleXRangeMaximum(15);
            // mChart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            chart.moveViewToX(data.getEntryCount());

        }
    }

    private LineDataSet createSet(String type) {

        LineDataSet set = new LineDataSet(null, type);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(3f);
        set.setColor(Color.GRAY);
        set.setHighlightEnabled(true);
        set.setDrawValues(true);
        set.setDrawCircles(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        return set;
    }

    // Method to display dialogue
    public AlertDialog showAlertDialog(Context context, String title, String message, String posBtnMsg, String negBtnMsg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(posBtnMsg, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setNegativeButton(negBtnMsg, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        return dialog;
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private LineChart getChart(int chartId){
        LineChart lineChart;
        switch(chartId)
        {
            // values must be of same type of expression
            case 1 :
                lineChart = findViewById(R.id.lineChart1);
                pressureChart = findViewById(R.id.lineChart1);
                break;

            case 2 :
                lineChart = findViewById(R.id.lineChart2);
                tempChart = findViewById(R.id.lineChart2);
                break;

            case 3 :
                lineChart = findViewById(R.id.lineChart3);
                humidityChart = findViewById(R.id.lineChart3);
                break;

            case 4 :
                lineChart = findViewById(R.id.lineChart4);
                resistanceChart = findViewById(R.id.lineChart4);
                break;

            case 5 :
                lineChart = findViewById(R.id.lineChart5);
                no2Chart = findViewById(R.id.lineChart5);
                break;

            case 6 :
                lineChart = findViewById(R.id.lineChart6);
                coChart = findViewById(R.id.lineChart6);
                break;

            case 7 :
                lineChart = findViewById(R.id.lineChart7);
                nh3Chart = findViewById(R.id.lineChart7);
                break;

            default :
                lineChart = findViewById(R.id.lineChart1);
                pressureChart = findViewById(R.id.lineChart1);

        }
        return  lineChart;
    }

//    int chartId, String color, float xValue, float yValue
    private void drawLineChart(int chartId, String name, String description) {

        LineChart lineChart = getChart(chartId);
        // enable description text
        lineChart.getDescription().setEnabled(true);
        lineChart.getDescription().setText(description);

        // enable touch gestures
        lineChart.setTouchEnabled(false);

        // enable scaling and dragging
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setDrawGridBackground(true);

        // if disabled, scaling can be done on x- and y-axis separately
        lineChart.setPinchZoom(true);

        // set an alternative background color
        lineChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.BLACK);

        // add empty data
        lineChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = lineChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.BLACK);

        XAxis xl = lineChart.getXAxis();
        xl.setTextColor(Color.BLACK);
        xl.setDrawGridLines(true);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false);

        lineChart.getAxisLeft().setDrawGridLines(false);
        lineChart.getXAxis().setDrawGridLines(false);
        lineChart.setDrawBorders(false);

        feedMultiple();
    }

    private void feedMultiple() {

        if (thread != null){
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                while (true){
                    plotData = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });

        thread.start();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onClickWrite(View v){
        if(mBluetoothLeService != null) {
//            mBluetoothLeService.writeCustomCharacteristic(0xAA);
            showGraph = true;
        }
//        if (mGattCharacteristics != null) {
//            final BluetoothGattCharacteristic characteristic =
//                    mGattCharacteristics.get(groupPosition).get(childPosition);
//            final int charaProp = characteristic.getProperties();
//            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                // If there is an active notification on a characteristic, clear
//                // it first so it doesn't update the data field on the user interface.
//                if (mNotifyCharacteristic != null) {
//                    mBluetoothLeService.setCharacteristicNotification(
//                            mNotifyCharacteristic, false);
//                    mNotifyCharacteristic = null;
//                }
//                mBluetoothLeService.readCharacteristic(characteristic);
//            }
//            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//                mNotifyCharacteristic = characteristic;
//                mBluetoothLeService.setCharacteristicNotification(
//                        characteristic, true);
//            }
//            return true;
//        }

//        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
//            Log.w(TAG, "BluetoothAdapter not initialized");
//            return;
//        }
//        /*check if the service is available on the device*/
//        BluetoothGattService mCustomService = mBluetoothGatt.getService(UUID.fromString("d973f2e0-b19e-11e2-9e96-0800200c9a66"));
//        if(mCustomService == null){
//            Log.w(TAG, "Custom BLE Service not found");
//            return;
//        }
//        /*get the read characteristic from the service*/
//        BluetoothGattCharacteristic mWriteCharacteristic = mCustomService.getCharacteristic(UUID.fromString("d973f2e1-b19e-11e2-9e96-0800200c9a66"));
//        mWriteCharacteristic.setValue(value,android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8,0);
//        if(mBluetoothGatt.writeCharacteristic(mWriteCharacteristic) == false){
//            Log.w(TAG, "Failed to write characteristic");
//        }
    }

    public void onClickRead(View v){
        if(mBluetoothLeService != null) {
            mBluetoothLeService.readCustomCharacteristic();
        }
    }
}
