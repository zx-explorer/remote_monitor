package com.example.client_lower;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private final static int port = 3600;
    private boolean device_up = false;
    private byte[] buffer = new byte[1024];
    private DatagramSocket socket;
    private DatagramPacket packet;
    private TextView textView;

    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button rcv_button = findViewById(R.id.button_rcvpacket);
        textView = findViewById(R.id.rcv_order);
        rcv_button.setBackgroundColor(Color.GREEN);
        rcv_button.setText("接收指令");

        Log.d(TAG, "onCreate: " + getIPAddress(true));

        try {
            socket = new DatagramSocket(port, InetAddress.getByName(ipToBroadcast(getIPAddress(true))));
            socket.setBroadcast(true);
        } catch (SocketException e) {
            Log.d(TAG, "onCreate: " + e);
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }


        packet = new DatagramPacket(buffer, buffer.length);

        final Timer[] timer = {null};

        rcv_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(rcv_button.getText().equals("接收指令")) {
                    rcv_button.setText("终止程序");
                    rcv_button.setBackgroundColor(Color.RED);
                    timer[0] = new Timer();
                    timer[0].schedule(new TimerTask() {
                        @Override
                        public void run() {
                            recvPacket();
                        }
                    }, 0, 100);
                } else {
                    rcv_button.setText("接收指令");
                    rcv_button.setBackgroundColor(Color.GREEN);
                    if (null == timer[0]) return;
                    timer[0].cancel();
                    timer[0] = null;
                }
            }
        });
    }

    private void recvPacket() {
        try {
            socket.receive(packet);

            Log.d(TAG, "recvPacket: " + "yes");

            String msg = new String(buffer, 0, packet.getLength());
            Log.d(TAG, "recvPacket: " + msg);
            if(msg.equals("true")) {
                textView.setText("Device Open");
            } else {
                textView.setText("Device Close");
            }
        } catch (IOException e) {
            Log.d(TAG, "recvPacket: " + e);
            e.printStackTrace();
        }

    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        //boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
                        boolean isIPv4 = sAddr.indexOf(':')<0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim<0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) { } // for now eat exceptions
        return "";
    }

    private String ipToBroadcast(String ip_str) {
        String[] ip_list = ip_str.split("\\.");
        ip_list[3] = "255";

        String broadcast_ip_str = "";
        for(int i = 0; i < ip_list.length - 1; ++i) {
            broadcast_ip_str += ip_list[i];
            broadcast_ip_str += ".";
        }
        broadcast_ip_str += ip_list[ip_list.length - 1];
        return broadcast_ip_str;
    }
}