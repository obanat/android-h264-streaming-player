package com.obana.h264player;

import android.app.Activity;

import android.content.Context;
import android.content.Intent;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.net.ConnectivityManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.obana.h264player.utils.AppLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends Activity implements View.OnClickListener {
    public static final String TAG = "MainActivity";

    public static final int MESSAGE_CONNECT_TO_CAMERA_FAIL = 1002;
    public static final int MESSAGE_RECONNECT_TO_CAMERA = 1003;

    public static final int MESSAGE_SYNC_TIME = 1004;
    public static final int MESSAGE_MAKE_TOAST = 6001;
    public static final boolean SHOW_DEBUG_MESSAGE = true;
    public static final String BUNDLE_KEY_TOAST_MSG = "Tmessage";

    public static final int RECONNECT_DELAY_MS = 5000;

    private Handler handler = null;
    private H264SurfaceView mH264View;
    private MjpegView mJpegView;
    private ImageButton mSetttingsBtn;
    private ImageButton mRecordVideoBtn;
    private ImageView mRecordVideoView;
    private TcpSocket mTcpSocket;

    private RandomAccessFile mH264DataFile = null;
    private boolean mBRecording = false;

    private PowerManager.WakeLock mWakeLock;


    protected void onCreate(Bundle paramBundle) {
        super.onCreate(paramBundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);

        //mH264View = findViewById(R.id.h264View);
        mJpegView = findViewById(R.id.mjpegView);
        mSetttingsBtn = findViewById(R.id.setting_button);
        mRecordVideoBtn = findViewById(R.id.record_button);
        mRecordVideoView = findViewById(R.id.recording_view);

        mTcpSocket = new TcpSocket(this);

        this.handler = new Handler() {
            public void handleMessage(Message param1Message) {
                if (!handleMessageinUI(param1Message)) {
                    super.handleMessage(param1Message);
                }
            }
        };

        PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "NEW:WakeLock");
        }

        mSetttingsBtn.setOnClickListener(this);
        mRecordVideoBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == mSetttingsBtn) {
            startActivity(new Intent(this, Settings.class));
        } else if (view == mRecordVideoBtn) {
            mBRecording = mBRecording? false : true;

            if (mTcpSocket!=null) mTcpSocket.startWriteToFile(mBRecording);
            enableRecordingButtonBlinking(mBRecording);
            if (mBRecording) {
                prepareH264File();
            } else {
                stopWriteH264File();
            }
        }
    }
    public boolean onKeyDown(int paramInt, KeyEvent paramKeyEvent) {
        Log.i(TAG, "onKeyDown key=" + paramInt + " event=" + paramKeyEvent);
        Toast.makeText(this, "k:" + paramInt + " k:" + paramKeyEvent.getKeyCode(), Toast.LENGTH_SHORT).show();
        if (paramInt == 4) {
            finish();
        }
        return super.onKeyDown(paramInt, paramKeyEvent);
    }

    public boolean onKeyUp(int paramInt, KeyEvent paramKeyEvent) {
        return super.onKeyUp(paramInt, paramKeyEvent);
    }

    private void requestSpecifyNetwork() {
        ConnectivityManager conMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (conMgr == null) return;

        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        NetworkRequest build = builder.build();
        AppLog.i(TAG, "---> start request network ");

        conMgr.requestNetwork(build, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                super.onAvailable(network);
                AppLog.i(TAG, "---> request network OK! start connectRunnable...");
                (new Thread(connectRunnable)).start();

            }
        });
    }

    Runnable connectRunnable = new Runnable() {
        public void run() {
            AppLog.i(TAG, "--->connectRunnable. connecting to camera.....");
            getHttpTime(5000);

            int ret = mTcpSocket.connect();
            if (ret <= 0) {
                sendToastMessage("Socket Connect Failed, retry in 5s ...");

                AppLog.i(TAG, "--->connect to camera failed, reconneting after 5s ....");
                Message message = new Message();
                message.what = MESSAGE_RECONNECT_TO_CAMERA;
                MainActivity.this.handler.sendMessageDelayed(message, RECONNECT_DELAY_MS);
            } else {
                mH264View.initMediaCodec();

                AppLog.d(TAG, "--->camera socket connect Succuess!");
                sendToastMessage("Socket Connect Succuess!");
            }
        }
    };

    protected void onResume() {
        super.onResume();

        AppLog.i(TAG, "on Resume");

        if (mWakeLock != null) {
            mWakeLock.acquire();
        }

        //request network, then start camera socket
        //if (!mTcpSocket.isConnected() && !handler.hasMessages(MESSAGE_RECONNECT_TO_CAMERA)) {
            requestSpecifyNetwork();
        //}

        //switch to mjpview
        //mJpegView.setStreamPara();
        //mJpegView.startStream();
        //mJpegView.startPlayback("http://192.168.10.1:8080/?action=stream");
    }

    protected void onPause() {
        super.onPause();

        if (mWakeLock != null) {
            mWakeLock.release();
        }
        AppLog.i(TAG, "on onPause");
    }


    protected void onStop() {
        super.onStop();
    }

    protected void onDestroy() {
        super.onDestroy();
        AppLog.d(TAG, "on destory");
    }


    public boolean handleMessageinUI(Message param1Message) {
        boolean handled = false;
        switch (param1Message.what) {
            case MESSAGE_CONNECT_TO_CAMERA_FAIL:
                if (SHOW_DEBUG_MESSAGE)
                    Toast.makeText(MainActivity.this, "failed to connect!", Toast.LENGTH_LONG).show();
                handled = true;
                break;
            case MESSAGE_MAKE_TOAST:
                if (SHOW_DEBUG_MESSAGE) {
                    String msg = param1Message.getData().getString(BUNDLE_KEY_TOAST_MSG);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
                handled = true;
                break;
            case MESSAGE_RECONNECT_TO_CAMERA:
                if (!mTcpSocket.isConnected() && !handler.hasMessages(MESSAGE_RECONNECT_TO_CAMERA)) {
                    (new Thread(connectRunnable)).start();
                }
                break;
            case MESSAGE_SYNC_TIME:
                getHttpTime(5000);
                break;
            default:
                return false;
        }
        return handled;
    }

    private void sendToastMessage(String str) {
        Bundle bundle = new Bundle();
        bundle.putString(BUNDLE_KEY_TOAST_MSG, str);

        Message msg = handler.obtainMessage(MESSAGE_MAKE_TOAST);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    public void drawH264View(byte[] data, int len) {

        mH264View.decodeOneFrame(data, len);
    }

    public void writeH264File(byte[] data, int len) {
        try {
            mH264DataFile.write(data, 0, len);
        } catch (IOException e) {
            AppLog.e(TAG, "writeH264File failed!");
        }
    }

    private void stopWriteH264File() {
        if (mTcpSocket != null) mTcpSocket.startWriteToFile(false);
        mH264DataFile = null;
    }

    private static String getDateTimeStr() {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH_mm_ss");
        Date date = new Date();
        String dateStr = simpleDateFormat.format(date);
        return dateStr;
    }

    private void prepareH264File() {
        File file = new File(this.getExternalFilesDir(null)
                + "/" + getDateTimeStr() + "_.mp4");
        try {
            mH264DataFile = new RandomAccessFile(file, "rw");
        } catch (FileNotFoundException e) {
            AppLog.e(TAG, "prepareH264File failed!");
        }

    }

    private void enableRecordingButtonBlinking(boolean blinking) {
        if (mRecordVideoView == null) return;
        if (blinking) {
            final Animation animation = new AlphaAnimation(1, 0); // Change alpha from fully visible to invisible
            animation.setDuration(800); // duration - half a second
            animation.setInterpolator(new LinearInterpolator()); // do not alter animation rate
            animation.setRepeatCount(Animation.INFINITE); // Repeat animation infinitely
            animation.setRepeatMode(Animation.REVERSE); //
            mRecordVideoView.setAnimation(animation);
            mRecordVideoView.setImageResource(R.drawable.recording);

            mRecordVideoBtn.setImageResource(R.drawable.video_record_start);
        } else {
            mRecordVideoView.setAnimation(null);
            mRecordVideoView.setImageResource(0);

            mRecordVideoBtn.setImageResource(R.drawable.video_record_stop);
        }
    }

    private void getHttpTime(int timeout){
        HttpURLConnection httpconn;
        String httpServerURL = "http://i4free.x3322.net:38080";
        URL url = null;
        try {
            url = new URL(httpServerURL);
            httpconn = (HttpURLConnection) url.openConnection();
            httpconn.setRequestMethod("GET");
            httpconn.setDoInput(true);
            httpconn.setUseCaches(false);
            httpconn.setInstanceFollowRedirects(false);
            httpconn.setRequestProperty("Accept-Charset", "UTF-8");
            Log.i(TAG, "timeout:" + timeout);
            httpconn.setConnectTimeout(timeout);
            httpconn.setReadTimeout(timeout);
            long requestTicks = SystemClock.elapsedRealtime();
            Log.i(TAG, "Strart to connect http server!");
            httpconn.connect();
            InputStreamReader mInputStreamReader = null;
            BufferedReader mBufferedReader = null;
            StringBuffer sb = new StringBuffer();
            int responseCode = httpconn.getResponseCode();
            Log.i(TAG, "Start to parse http response! Code:" + responseCode);
            if (responseCode != 200) {
                Log.e(TAG, "Http response error:" + responseCode);
            } else {
                mInputStreamReader = new InputStreamReader(httpconn.getInputStream(), "utf-8");
                mBufferedReader = new BufferedReader(mInputStreamReader);
                while (true) {
                    String lineString = mBufferedReader.readLine();
                    if (lineString == null) break;
                    sb.append(lineString);
                }
            }
            Log.i(TAG, "Read response finish, lineString=" + sb.toString());
            if (mBufferedReader != null) {
                mBufferedReader.close();
            }
            if (mInputStreamReader != null) {
                mInputStreamReader.close();
            }
            httpconn.disconnect();
            Log.i(TAG, "disconnect");
            String inputString = sb.toString();
            String[] dateStrings = inputString.split(",");
            if (dateStrings.length > 0) {
                Log.i(TAG, "lineString1=" + dateStrings[0]);
                String[] dateStrings2 = dateStrings[0].split(":");
                if (dateStrings2.length == 2) {
                    Log.i(TAG, "lineString2=" + dateStrings2[1]);
                    String dateTime = dateStrings2[1];
                    long lDateTime = Long.parseLong(dateTime.trim());
                    Date date = new Date(lDateTime*1000L);
                    Log.i(TAG, "lineString3=" + date.toString());
                    // 创建SimpleDateFormat对象
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    //Toast.makeText(this,sdf.format(date),Toast.LENGTH_LONG).show();
                    Log.i(TAG, "show date time =" + sdf.format(date));
                }
            }
        } catch (Exception e) {
        }
    }
}
