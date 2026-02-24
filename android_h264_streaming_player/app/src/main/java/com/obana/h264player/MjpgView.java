package com.obana.h264player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.obana.h264player.utils.AppLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MjpgView extends androidx.appcompat.widget.AppCompatImageView {
    private static final String TAG = "MjpgView";
    private static final String DEF_URL = "http://192.168.10.1:8080/?action=stream";
    private static final String SP_KEY_REDIS_IP= "serverIp";
    private static final String SP_KEY_REDIS_PORT= "serverPort";
    private Bitmap currentBitmap;
    private Paint errorPaint;
    private String streamUrl;
    private boolean isPlaying = false;
    private Thread streamThread;

    public MjpgView(Context context) {
        super(context);
        init();
    }

    public MjpgView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MjpgView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        errorPaint = new Paint();
        errorPaint.setColor(Color.RED);
        errorPaint.setTextSize(48);
        setScaleType(ScaleType.FIT_CENTER);
    }

    public void setStreamUrl(String url) {
        this.streamUrl = url;
    }

    @SuppressLint("DefaultLocale")
    public void setStreamPara() {
        String host =  "192.168.10.1";
        int port = 8080;

        if (TextUtils.isEmpty(host) || port <= 0) {
            this.streamUrl = DEF_URL;
        } else {
            this.streamUrl =  String.format("http://%s:%d/?action=stream", host, port);
        }
    }

    public void startStream() {
        if (isPlaying || streamUrl == null) return;

        isPlaying = true;
        streamThread = new Thread(new StreamRunnable());
        streamThread.start();
    }

    public void stopStream() {
        isPlaying = false;
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
    }

    private class StreamRunnable implements Runnable {
        @Override
        public void run() {
            HttpURLConnection connection = null;
            InputStream inputStream = null;

            try {
                URL url = new URL(streamUrl);
                AppLog.i(TAG, "--->mjpg connecting... url:" + streamUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.connect();
                AppLog.i(TAG, "--->mjpg connect successful !");
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    postInvalidate();
                    return;
                }


                inputStream = connection.getInputStream();
                parseMjpegStream(inputStream);

            } catch (Exception e) {
                AppLog.e(TAG, "--->mjpg connect failed!" + e.getMessage());
                postInvalidate();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void parseMjpegStream(InputStream inputStream) throws IOException {
            byte[] buffer = new byte[4096];
            ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
            int bytesRead;
            boolean inFrame = false;

            while (isPlaying && !Thread.currentThread().isInterrupted() &&
                    (bytesRead = inputStream.read(buffer)) != -1) {

                for (int i = 0; i < bytesRead; i++) {
                    byte currentByte = buffer[i];

                    if (!inFrame) {
                        frameBuffer.write(currentByte);
                        byte[] frameData = frameBuffer.toByteArray();

                        if (frameData.length >= 2 &&
                                frameData[frameData.length - 2] == (byte) 0xFF &&
                                frameData[frameData.length - 1] == (byte) 0xD8) {
                            inFrame = true;
                        }
                    } else {
                        frameBuffer.write(currentByte);
                        byte[] frameData = frameBuffer.toByteArray();

                        if (frameData.length >= 2 &&
                                frameData[frameData.length - 2] == (byte) 0xFF &&
                                frameData[frameData.length - 1] == (byte) 0xD9) {
                            processFrame(frameData);
                            frameBuffer.reset();
                            inFrame = false;
                        }
                    }
                }
            }


        }

        private void processFrame(byte[] frameData) {
            AppLog.i(TAG, "--->mjpg process one Frame. len=" + frameData.length);
            Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
            if (bitmap != null) {
                AppLog.i(TAG, "--->mjpg process one bitmap !");
                updateBitmap(bitmap);
            }
        }

        private void updateBitmap(final Bitmap bitmap) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (currentBitmap != null) {
                        currentBitmap.recycle();
                    }
                    currentBitmap = bitmap;
                    setImageBitmap(currentBitmap);
                }
            });
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentBitmap == null) {
            canvas.drawColor(Color.BLACK);
            canvas.drawText("等待视频流...",
                    getWidth() / 2 - 150,
                    getHeight() / 2,
                    errorPaint);
        } else {
            super.onDraw(canvas);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopStream();
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}