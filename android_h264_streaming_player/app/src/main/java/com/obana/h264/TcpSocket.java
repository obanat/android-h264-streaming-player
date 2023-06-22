package com.obana.h264;

import android.content.Context;

import com.obana.h264.utils.AppLog;
import com.obana.h264.utils.ByteUtility;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.SocketFactory;

public class TcpSocket {
    private static final String TAG = "TcpSocket";
    private static final int MEDIA_BUF_SIZE = (640*1024);
    private static final int SUCCESS = 1;
    private static final int FAILED = 0;
    private static final int SOCKET_TIMEOUT_MS = 3000;

    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private Socket mediaSocket;
    private byte[] mediaBuffer = new byte[MEDIA_BUF_SIZE];
    private Thread mediaRecvThread;

    private String targetHost = "192.168.1.1";
    private int targetPort = 8888;
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
        AppLog.i(TAG, "--->media socket creating .....host:" + targetHost + " port:" + targetPort);

        //use cached host & port
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

}
