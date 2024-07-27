package com.obana.h264player;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.obana.h264player.utils.AppLog;
import com.obana.h264player.utils.ByteUtility;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

import javax.net.SocketFactory;

public class TcpSocket {
    private static final String TAG = "TcpSocket";
    private static final int MEDIA_BUF_SIZE = (640*1024);
    private static final int SUCCESS = 1;
    private static final int FAILED = 0;
    private static final int SOCKET_TIMEOUT_MS = 3000;

    private static final String SP_KEY_MAC= "clientId";
    private static final String SP_KEY_REDIS_IP= "serverIp";
    private static final String SP_KEY_REDIS_PORT= "serverPort";
    private static final String SP_KEY_NETWORK_TYPE= "networkType";

    private static final String P2P_HOST_URL = "http://obana.f3322.org:38086/wificar/getClientIp";
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private Socket mediaSocket;
    private byte[] mediaBuffer = new byte[MEDIA_BUF_SIZE];
    private Thread mediaRecvThread;

    private String targetHost = "192.168.10.1";
    private int targetPort = 5555;
    private Context context;
    boolean bMediaConnected = false;
    boolean mediaThreadBool = false;

    public TcpSocket(Context ctx){
        context = ctx;
    }

    public int connect() {
        if (bMediaConnected) {
            AppLog.i(TAG, "--->alreay connect media socket, just return");
            return SUCCESS;
        }

        //use cached host & port
        targetHost = getSharedPreference(SP_KEY_REDIS_IP, targetHost);
        String strPort = getSharedPreference(SP_KEY_REDIS_PORT, Integer.toString(targetPort));
        int intPort = Integer.parseInt(strPort);
        targetPort = intPort > 0 ? intPort : targetPort;

        if ("p2p".equalsIgnoreCase(getSharedPreference(SP_KEY_NETWORK_TYPE, ""))){
            targetHost = getIpv6HostName();
            targetPort = 28001;
        }

        AppLog.i(TAG, "--->media socket creating .....host:" + targetHost + " port:" + targetPort);
        createMediaReceiverSocket(targetHost, targetPort);


        if(!mediaSocket.isConnected()){
            AppLog.e(TAG, "--->media socket connect failed!");
            return FAILED;
        }

        try {
            dataInputStream = new DataInputStream(mediaSocket.getInputStream());
        } catch (IOException e) {
            AppLog.e(TAG, "--->media socket connect failed!");
            return FAILED;
        }

        mediaThreadBool = true;
        mediaRecvThread = new Thread(new Runnable() {
            public void run() {
                int i;
                do {
                    try {
                        i = dataInputStream.read(mediaBuffer);
                        AppLog.i(TAG, "Receive media socket data, length:" + i);
                    } catch(IOException ioexception) {
                        AppLog.e(TAG, "media receive loop io exception!, just exit thread!");
                        break;
                    }
                    if(i == -1) {
                        AppLog.e(TAG, "media receive length error!, just exit thread!");
                        break;
                    }

                    MainActivity activity = (MainActivity)context;
                    activity.drawH264View(ByteUtility.arrayCopy(mediaBuffer, 0, i), i);
                    try {
                        Thread.sleep(5L);
                    } catch(Exception e) {
                    }
                } while(mediaThreadBool);

            }
        });
        mediaRecvThread.setName("Media Thread");
        mediaRecvThread.start();
        bMediaConnected = true;
        return SUCCESS;
    }

    public boolean isConnected(){
        return bMediaConnected;
    }
    private void createMediaReceiverSocket(String host, int port) {
        try {
            AppLog.i(TAG, "--->media socket connecting host:" + host + " port:" + port);
            mediaSocket = SocketFactory.getDefault().createSocket();
            InetSocketAddress addr = new InetSocketAddress(host, port);
            mediaSocket.setReceiveBufferSize(MEDIA_BUF_SIZE);

            //if (cachedNetwork != null) cachedNetwork.bindSocket(mediaSocket);

            AppLog.i(TAG, "--->media socket connecting .....");
            mediaSocket.connect(addr, SOCKET_TIMEOUT_MS);

        } catch (IOException e) {
            AppLog.e(TAG, "--->media socket connected exception! e:" + e.getMessage());
            return;
        }
        AppLog.i(TAG, "--->media socket connected successfully!.....");
    }

    private String getSharedPreference(String key, String def) {
        //return AndRovio.getSharedPreference(key);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(key, def);
    }

    private String getIpv6HostName() {
        String targetHost = null;
        Socket socket = null;

        String url = P2P_HOST_URL;
        String clientId = getSharedPreference(SP_KEY_MAC, "dji");
        String redisServerIp = "obana.f3322.org";
        String redisServerPort = "38086";

        url = String.format("http://obana.f3322.org:38086/wificar/getClientIp?mac=%s", clientId);

        AppLog.i(TAG, "redis server url:" + url);

        String ipaddr = getURLContent(url);

        AppLog.i(TAG, "ip v6 addr:" + ipaddr);
        return ipaddr;
    }

    private static String getURLContent(String url) {
        StringBuffer sb = new StringBuffer();

        try {
            URL updateURL = new URL(url);
            URLConnection conn = updateURL.openConnection();
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF8"));
            while (true) {
                String s = rd.readLine();
                if (s == null) {
                    break;
                }
                sb.append(s);
            }
        } catch (Exception e){

        }
        return sb.toString();
    }
}
