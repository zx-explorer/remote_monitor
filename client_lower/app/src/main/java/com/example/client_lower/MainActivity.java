package com.example.client_lower;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.client_lower.utils.MessageType;

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
    private byte[] buffer = new byte[1024];
    private DatagramSocket socket;
    private DatagramPacket packet;
    private TextView textView;

    Handler handler;

    private final static String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.rcv_order);

        handler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(@NonNull Message msg) {
                Log.d("MSG", String.valueOf(msg));
                switch (MessageType.getFromInt(msg.what)) {
                    case RECORD_START:
                        textView.setText("正在录制…");
                        break;
                    case RECORD_STOP:
                        textView.setText("已停止录制");
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + MessageType.getFromInt(msg.what));
                }
                return false;
            }
        });

        try {
            socket = new DatagramSocket(port, InetAddress.getByName(ipToBroadcast(getIPAddress(true))));
            socket.setBroadcast(true);
        } catch (SocketException | UnknownHostException e) {
            Log.d(TAG, "onCreate: " + e);
            e.printStackTrace();
        }


        packet = new DatagramPacket(buffer, buffer.length);

        Timer timer = new Timer();
        timer.schedule(new RecordTimerTask(), 0, 100);
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

    public class RecordTimerTask extends TimerTask{
        @Override
        public void run() {
            try {
                Log.d(TAG, "run: " + "??");
                socket.receive(packet);

                String msg = new String(buffer, 0, packet.getLength());
                if(msg.equals("true") || msg.equals("false")) {
                    Message m = new Message();
                    m.what = msg.equals("true") ? MessageType.RECORD_START.toInt() : MessageType.RECORD_STOP.toInt();
                    handler.sendMessage(m);
                } else {
                    Log.d(TAG, "run: " + "Confusing Message");
                }
            } catch (IOException e) {
                Log.d(TAG, "recvPacket: " + e);
                e.printStackTrace();
            }
        }
    }
}