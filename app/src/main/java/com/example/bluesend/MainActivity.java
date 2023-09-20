package com.example.bluesend;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;
    private static final String APP_NAME = "BlueSend";
    private static final UUID MY_UUID = UUID.fromString("318c6089-985c-4773-b7ca-4c6130e4209e");
    Button listen, send, listDevices;
    ListView listView;
    TextView messageBox, status;
    EditText writeMessage;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] bluetoothDevices;
    SendReceive sendReceive;
    int REQUEST_ENABLE_BLUETOOTH = 1;
    Handler handler = new Handler(new Handler.Callback() {
        @SuppressLint("SetTextI18n")
        @Override
        public boolean handleMessage(Message msg) {

            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                switch (msg.what) {


                    case STATE_LISTENING:

                        status.setText(R.string.listening);
                        break;

                    case STATE_CONNECTING:

                        status.setText(R.string.connecting);
                        break;

                    case STATE_CONNECTED:

                        status.setText(R.string.connected);
                        break;

                    case STATE_CONNECTION_FAILED:

                        status.setText(R.string.failed);
                        break;

                    case STATE_MESSAGE_RECEIVED:

                        byte[] readBuffer = (byte[]) msg.obj;

                        String tempMessage = new String(readBuffer, 0, msg.arg1);

                        messageBox.setText(tempMessage);

                        break;
                }
            }
            return true;
        }
    });

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listen = findViewById(R.id.listen);
        send = findViewById(R.id.send);
        listDevices = findViewById(R.id.listDevices);
        listView = findViewById(R.id.listview);
        status = findViewById(R.id.status);
        messageBox = findViewById(R.id.msg);
        writeMessage = findViewById(R.id.writemsg);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!bluetoothAdapter.isEnabled()) {

            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);

            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }

        implementListeners();
    }

    private void implementListeners() {
        listDevices.setOnClickListener(v -> {

            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();

                String[] strings = new String[devices.size()];

                bluetoothDevices = new BluetoothDevice[devices.size()];

                int index = 0;

                if (devices.size() > 0) {

                    for (BluetoothDevice device : devices) {

                        bluetoothDevices[index] = device;

                        strings[index] = device.getName();

                        index++;
                    }

                    ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, strings);

                    listView.setAdapter(arrayAdapter);
                }


                listen.setOnClickListener(v3 -> {
                    ServerClass serverClass = new ServerClass();

                    serverClass.start();
                });

                listView.setOnItemClickListener((parent, view, i, id) -> {

                    ClientClass clientClass = new ClientClass(bluetoothDevices[i]);

                    clientClass.start();

                    status.setText(R.string.connecting);
                });


                send.setOnClickListener(v1 -> {

                    String string = String.valueOf(writeMessage.getText());

                    sendReceive.write(string.getBytes());
                });
            }
        });
    }

    private class ServerClass extends Thread {

        private BluetoothServerSocket serverSocket;

        @SuppressLint("MissingPermission")
        public ServerClass() {

            try {
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            BluetoothSocket socket = null;

            while (true) {

                try {
                    Message message = Message.obtain();

                    message.what = STATE_CONNECTING;

                    handler.sendMessage(message);

                    socket = serverSocket.accept();

                } catch (IOException e) {
                    e.printStackTrace();

                    Message message = Message.obtain();

                    message.what = STATE_CONNECTION_FAILED;

                    handler.sendMessage(message);
                }

                if (socket != null) {

                    Message message = Message.obtain();

                    message.what = STATE_CONNECTED;

                    handler.sendMessage(message);

                    sendReceive = new SendReceive(socket);

                    sendReceive.start();

                    break;
                }
            }
        }
    }


    private class ClientClass extends Thread {

        private BluetoothDevice device;

        private BluetoothSocket socket;

        public ClientClass(BluetoothDevice device1) {

            device = device1;

            try {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    socket.connect();

                    Message message = Message.obtain();

                    message.what = STATE_CONNECTED;

                    handler.sendMessage(message);

                    sendReceive = new SendReceive(socket);

                    sendReceive.start();
                }

            } catch (IOException e) {
                e.printStackTrace();

                Message message = Message.obtain();

                message.what = STATE_CONNECTION_FAILED;

                handler.sendMessage(message);
            }
        }
    }

    private class SendReceive extends Thread {
        private final InputStream inputStream;

        private final OutputStream outputStream;

        public SendReceive(BluetoothSocket socket) {


            InputStream tempInput = null;

            OutputStream tempOutput = null;

            try {
                tempInput = socket.getInputStream();

                tempOutput = socket.getOutputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }

            inputStream = tempInput;

            outputStream = tempOutput;
        }

        public void run() {
            byte[] buffer = new byte[1024];

            int bytes;

            while (true) {

                try {
                    bytes = inputStream.read(buffer);

                    handler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}