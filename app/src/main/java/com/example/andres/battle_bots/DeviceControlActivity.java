/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.andres.battle_bots;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import android.app.AlertDialog;



public class DeviceControlActivity extends Activity {

    // TAG will be referenced when printing logs to the Android Monitor
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    // Used to receive extras from the Intent in the OnCreate method
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // These variables are used in the Bluetooth connection UI
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;

    // Instantiating the BLE service and the necessary characteristics to perform communication
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    /*
    This boolean is used in order to implement a delay between uses of the TX characteristic, since
     the Arduino has a hard time catching inputs if they come in on a small time interval
     */
    private boolean inUse = false;

    // Used to catalog service data
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

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
        }

        // Clears the BLE Service
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
                displayData(intent.getStringExtra(mBluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private void clearUI() {
//        mDataField.setText(R.string.no_data);
    }

    // Creates the layout based on xml files and sets up the behavior for each button
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Receiving connected device data from DeviceScanActivity
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets some UI data
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Instantiates an image button and associates it with its graphic through its ID
        ImageButton U1 = (ImageButton) findViewById(R.id.up_btn1);

        /*
        This defines the button's behavior when touched. Each button has an on and off code
        associated with it. For button U1, the on code would be u1n and the off code would be u1f.
        The other buttons follow the same pattern
         */
        U1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();

                // setting on and off codes
                final String codeOn = "u1n";
                final String codeOff = "u1f";

                switch (event.getAction()) {

                    // This case is for when the user presses down on the button: send on signal
                    case MotionEvent.ACTION_DOWN:
                        /*
                        If the TX characteristic has been used recently, implement a delay on the
                        next command that is sent
                        */
                        if (inUse){
                        handler.postDelayed(new Runnable() {

                            @Override
                            public void run() {
                                makeChange(codeOn);
                            }
                        }, 140);
                        inUse = false;
                    }
                    /*
                     TX characteristic has not been used, or a delay has been implemented recently.
                     We send the next signal right away
                    */
                    else
                    {
                        makeChange(codeOn);
                        inUse = true;
                    }

                        return false;

                    // This case is for when the user lets go of the button: send off signal
                    case MotionEvent.ACTION_UP:

                        // Using the same logic as with ACTION_DOWN
                        if (inUse){
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }
                            }, 190);
                            inUse = false;
                        }

                        else
                        {
                            makeChange(codeOff);
                            inUse = true;
                        }

                        return false;
                }
                return true;

            }
        });

        // The rest of the arrow buttons follow the same logic as U1 with their corresponding on/off codes

        ImageButton R1 = (ImageButton) findViewById(R.id.right_btn1);
        R1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "r1n";
                final String codeOff = "r1f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (inUse) {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOn);
                                }
                            }, 140);
                            inUse = false;
                        } else {
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if (inUse) {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }

                            }, 190);
                            inUse = false;
                        } else {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });


        ImageButton D1 = (ImageButton) findViewById(R.id.down_btn1);
        D1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "d1n";
                final String codeOff = "d1f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (inUse){
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOn);
                                }
                            }, 140);
                            inUse = false;
                        }

                        else
                        {
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if (inUse){
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }
                            }, 190);
                            inUse = false;
                        }

                        else
                        {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });


        ImageButton L1 = (ImageButton) findViewById(R.id.left_btn1);
        L1.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "l1n";
                final String codeOff = "l1f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if(inUse) {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run(){
                                    makeChange(codeOn);
                                }
                            },140);
                            inUse = false;
                        }


                        else{
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if(inUse) {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run(){
                                    makeChange(codeOff);
                                }

                            },190);
                            inUse = false;
                        }

                        else {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });


        ImageButton U2 = (ImageButton) findViewById(R.id.up_btn2);
        U2.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "u2n";
                final String codeOff = "u2f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (inUse) {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOn);
                                }
                            }, 140);
                            inUse = false;
                        } else {
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if (inUse) {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }

                            }, 190);
                            inUse = false;
                        } else {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });

        ImageButton R2 = (ImageButton) findViewById(R.id.right_btn2);
        R2.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "r2n";
                final String codeOff = "r2f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (inUse) {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOn);
                                }
                            }, 140);
                            inUse = false;
                        } else {
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if (inUse) {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }

                            }, 190);
                            inUse = false;
                        } else {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });

        ImageButton D2 = (ImageButton) findViewById(R.id.down_btn2);
        D2.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "d2n";
                final String codeOff = "d2f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (inUse) {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOn);
                                }
                            }, 140);
                            inUse = false;
                        } else {
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if (inUse) {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }

                            }, 190);
                            inUse = false;
                        } else {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });

        ImageButton L2 = (ImageButton) findViewById(R.id.left_btn2);
        L2.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Handler handler = new Handler();
                final String codeOn = "l2n";
                final String codeOff = "l2f";
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (inUse) {
                            handler.postDelayed(new Runnable() {

                                @Override
                                public void run() {
                                    makeChange(codeOn);
                                }
                            }, 140);
                            inUse = false;
                        } else {
                            makeChange(codeOn);
                            inUse = true;
                        }

                        return false;


                    case MotionEvent.ACTION_UP:

                        if (inUse) {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    makeChange(codeOff);
                                }

                            }, 190);
                            inUse = false;
                        } else {
                            makeChange(codeOff);
                            inUse = true;
                        }


                        return false;
                }
                return true;

            }
        });

        // This button is the bluetooth logo and allows the user to select a device to connect to
        ImageButton BLE = (ImageButton) findViewById(R.id.ble_btn);
        BLE.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Starts DeviceScanActivity in order to find a device to connect to
                mBluetoothLeService.close();
                Intent intent = new Intent(DeviceControlActivity.this, com.example.andres.battle_bots.DeviceScanActivity.class);
                startActivity(intent);
            }
        });

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

    // Handles app pausing
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    // Handles app destruction
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
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                mBluetoothLeService = null;
                Intent i = getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
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
//                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {

        if (data != null) {
            mDataField.setText(data);
        }
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();


        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));

            for (BluetoothGattCharacteristic mCharacteristic : gattService.getCharacteristics()) {
                Log.i(TAG, "Found Characteristic: " + mCharacteristic.getUuid().toString());
            }
            Log.i(TAG, "onServicesDiscovered UUID: " + gattService.getUuid().toString());


            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            Log.d(TAG, LIST_UUID);

            // Instantiates characteristic when device's UUIDs match our expected RX/TX UUIDs

            if(characteristicTX == null) {
                Log.d(TAG, "Looking for RX");

                /*
                 characteristicTX will continue to be null unless the device's Gatt service's characteristic
                 contains the UUID we want
                  */
                characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_TX);
                if(characteristicTX != null)
                {
                    Log.d(TAG, "Found TX");
                }
            }

            if(characteristicRX == null) {
                Log.d(TAG, "Looking for RX");

                // same as with TX
                characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_RX);
                if(characteristicRX != null)
                {
                    Log.d(TAG, "Found RX");
                }
            }

        }

    }

    // Sets intent filters
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }


    // Function sends data to Arduino through the TX characteristic by converting input string to bytes
    private void makeChange(String str) {
        if (characteristicTX != null) {

            Log.d(TAG, "Sending result " + str);
            // Converting string to output byte array

            final byte[] tx = str.getBytes();

            // Sends data if the device is connected
            if (mConnected) {

                // characteristicTX is set to our output value
                characteristicTX.setValue(tx);

                // Sends data and enables RX notifications
                mBluetoothLeService.writeCharacteristic(characteristicTX);
                mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
                Log.d(TAG, "Success");
            } else {
                Log.d(TAG, "Failed");
            }
        }
        else
        {
            Log.d(TAG, "Null Characteristic");
            AlertDialog alert = new AlertDialog.Builder(this).create();
            alert.setTitle("An Error Has Occurred");
            alert.setMessage("The app will restart now");
            alert.setButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = getBaseContext().getPackageManager()
                            .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                }
            });
        }
    }
}
