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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;


public class MainActivity extends Activity implements View.OnClickListener {
    public static final String TAG = "MainActivity";

    public static final int MESSAGE_CONNECT_TO_CAMERA_FAIL = 1002;
    public static final int MESSAGE_RECONNECT_TO_CAMERA = 1003;
    public static final int MESSAGE_MAKE_TOAST = 6001;
    public static final boolean SHOW_DEBUG_MESSAGE = true;
    public static final String BUNDLE_KEY_TOAST_MSG = "Tmessage";

    public static final int RECONNECT_DELAY_MS = 5000;

    private Handler handler = null;
    private H264SurfaceView mH264View;
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

        mH264View = findViewById(R.id.h264View);
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
        if (!mTcpSocket.isConnected() && !handler.hasMessages(MESSAGE_RECONNECT_TO_CAMERA)) {
            requestSpecifyNetwork();
        }
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
}
