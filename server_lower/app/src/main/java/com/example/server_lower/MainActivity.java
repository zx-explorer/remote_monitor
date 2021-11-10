package com.example.server_lower;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

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

    private String TAG = "MainActivity";
    private boolean device_up = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button_sendpacket = findViewById(R.id.button_sendpacket);
        Button button_cancel = findViewById(R.id.button_cancel);
        button_sendpacket.setBackgroundColor(Color.BLUE);

        button_cancel.setBackgroundColor(Color.BLACK);

        button_cancel.setText("终止控制");
        button_sendpacket.setText("开启设备");

        final Timer[] timer = {null};

        button_sendpacket.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                device_up = !device_up;
                if(timer[0] != null) timer[0].cancel();
                timer[0] = new Timer();
                timer[0].schedule(new TimerTask() {
                    @Override
                    public void run() {
                        sendMessage(device_up);
                    }
                }, 0, 1000);

                if(device_up) {
                    button_sendpacket.setText("关闭设备");
                    button_sendpacket.setBackgroundColor(Color.RED);
                } else {
                    button_sendpacket.setText("开启设备");
                    button_sendpacket.setBackgroundColor(Color.BLUE);
                }
            }
        });

        button_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(timer[0] != null) {
                    timer[0].cancel();
                    timer[0] = null;
                    button_sendpacket.setText("开启设备");
                    button_sendpacket.setBackgroundColor(Color.BLUE);
                    device_up = false;

                }
            }
        });
    }

    private void sendMessage(boolean turn_up) {
        String ip_str = getIPAddress(true);

        String broadcast_ip_str = ipToBroadcast(ip_str);

        int mport = 3600;

        DatagramSocket socket = null;
        try {
            InetAddress server = InetAddress.getByName(broadcast_ip_str);
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            String turn_up_str = ((Boolean) turn_up).toString();
            DatagramPacket packet = new DatagramPacket(turn_up_str.getBytes(), turn_up_str.length(), server, mport);
            socket.send(packet);

        } catch (UnknownHostException | SocketException e) {
            Log.d(TAG, "sendBroadCastToCenter: " + e);
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "sendBroadCastToCenter: " + e);
            e.printStackTrace();
        } finally {
            if (socket != null) {
                socket.close();
            }
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

    private String getIPstr(int ipAddress) {
        return ((ipAddress & 0xff) + "." + (ipAddress >> 8 & 0xff) + "."
                + (ipAddress >> 16 & 0xff) + "." + (ipAddress >> 24 & 0xff));
    }
}