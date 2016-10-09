package com.grenade.smoke.smartumbrella;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private String RPI_MAC = "B8:27:EB:8F:C2:C5";
    private String S7_MAC = "78:00:9E:32:7D:37";

    boolean usePebble = true;
    boolean rain = true;
    boolean umbrellaOn = true;

    ConnectedThread ct = null;

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;

            write("Hi AYA".getBytes());
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    Log.d("AYA", "run: "  + bytes);
                } catch (IOException e) {
                    break;
                }
            }

            if (usePebble) sendPebble("AYA Umbrella", "You forgot your umbrella and it looks like it's going to rain!");
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            /*BluetoothSocket tmp = null;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"));
            } catch (IOException e) { }
            mmSocket = tmp;*/
        }

        public void run() {
            // auto reconnect with this awesome while
            while (true) {
                BluetoothSocket tmp = null;
                try {
                    tmp = mmDevice.createRfcommSocketToServiceRecord(UUID.fromString("94f39d29-7d6d-437d-973b-fba39e49d4ee"));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (tmp != null)
                    mmSocket = tmp;

                try {
                    // Connect the device through the socket. This will block
                    // until it succeeds or throws an exception
                    mmSocket.connect();
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and get out
                    try {
                        mmSocket.close();
                    } catch (IOException closeException) {
                    }

                    continue;
                }

                // manage the connection (writes reads)
                ct = new ConnectedThread(mmSocket);
                ct.start();
                try {
                    ct.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }

    public void sendPebble(String title, String body) {
        final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        final Map<String, String> data = new HashMap<String, String>();
        data.put("title", title);
        data.put("body", body);

        final JSONObject jsonData = new JSONObject(data);
        final String notificationData = new JSONArray().put(jsonData).toString();
        i.putExtra("messageType", "PEBBLE_ALERT");
        i.putExtra("sender", "Test");
        i.putExtra("notificationData", notificationData);

        Log.d("AYA", "Sending to Pebble: " + notificationData);
        sendBroadcast(i);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CheckBox umb = ( CheckBox ) findViewById( R.id.enable_umbrella );
        umb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (ct != null) {
                    umbrellaOn = isChecked;
                    if (isChecked) {
                        ct.write("umbrella_on".getBytes());
                    } else {
                        ct.write("umbrella_off".getBytes());
                    }
                }
            }
        });

        CheckBox peb = ( CheckBox ) findViewById( R.id.use_pebble );
        peb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                usePebble = isChecked;
            }
        });

        CheckBox twi = ( CheckBox ) findViewById( R.id.use_twilio );
        twi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (ct != null) {
                    if (isChecked) {
                        ct.write("twilio_on".getBytes());
                    } else {
                        ct.write("twilio_off".getBytes());
                    }
                }
            }
        });

        connectToBt();
    }

    private void connectToBt() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        BluetoothDevice mmDevice = null;

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("raspberrypi")) //Note, you will need to change this to match the name of your device
                {
                    Log.e("Umbrella", device.getName());
                    mmDevice = device;
                    break;
                } else {
                    Log.d("Umbrella", "Found device: " + device.getName());
                }
            }
        }

        if (mmDevice != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplication(), "Connected", Toast.LENGTH_LONG).show();
                }
            });

            (new ConnectThread(mmDevice)).start();
        }
    }
}