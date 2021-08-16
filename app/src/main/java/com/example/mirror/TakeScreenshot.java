package com.example.mirror;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.Lock;

public class TakeScreenshot extends Service implements ImageReader.OnImageAvailableListener  {

    private String defaultip = "172.30.1.45";
    //private String defaultip = "192.168.0.2";
    private int defaultport = 50006;
    private Activity activity;
    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private Bitmap bitmap;
    private Bitmap resize;
    private Socket sock;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private boolean ismirroring = false;
    private boolean isok = false;
    private boolean issending = false;
    private boolean getSizeok = false;
    private boolean imagesend = false;
    private Handler mHandler;

    public static final int REQUEST_CODE_CAPTURE_IMAGE = 789;
    private static final String TAG = "Screen Recording App";

    private MyInterface myInterface;

    @Override
    public void onCreate() {
            Intent testIntent = new Intent(getApplicationContext(), MainActivity.class);
            @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent
                    = PendingIntent.getActivity(this, 0, testIntent, PendingIntent.FLAG_CANCEL_CURRENT);

            NotificationChannel channel = new NotificationChannel("channel", "play!!",
                    NotificationManager.IMPORTANCE_DEFAULT);
            // Notification과 채널 연걸
            NotificationManager mNotificationManager = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
            mNotificationManager.createNotificationChannel(channel);
            // Notification 세팅
            NotificationCompat.Builder notification
                    = new NotificationCompat.Builder(getApplicationContext(), "channel")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Mirroring")
                    .setContentIntent(pendingIntent)
                    .setContentText("");
            // id 값은 0보다 큰 양수가 들어가야 한다.
            mNotificationManager.notify(1, notification.build());
            // foreground에서 시작
            startForeground(1, notification.build());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(Service.STOP_FOREGROUND_DETACH);
        super.onDestroy();
    }

    class ServiceBinder extends Binder {
        TakeScreenshot getService(){
            return TakeScreenshot.this;
        }
    }

    public interface MyInterface {
        void onSending(byte[] arr) throws InterruptedException;
    }
    public void setOnSendListener(MyInterface myInterface) {
        this.myInterface = myInterface;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CAPTURE_IMAGE) {
            if (resultCode != Activity.RESULT_OK) {
                MainActivity.printClientLog("Screen Cast Permission Denied");
                return;
            }
            ismirroring = true;
            MainActivity.printClientLog("request code ok");
            mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
            mirroring_start();
        }
    }

    @SuppressLint("WrongConstant")
    public void takescreenshot(){
        if (mMediaProjection != null) {
            try {
                if(mImageReader == null) {
                    MainActivity.printClientLog("mImagereader null");
                    mHandler = new Handler(Looper.getMainLooper());
                    getSize();
                    mImageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                    mImageReader.setOnImageAvailableListener(this, mHandler);
                }
                mMediaProjection.createVirtualDisplay(
                        "Screenshot",
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(),
                        null,
                        null);

                MainActivity.printClientLog("takescreenshot ok");
                imagesend = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            MainActivity.printClientLog("take scrrenshot error");
        }
    }

    @Override
    public synchronized void onImageAvailable(final ImageReader reader) {
        while(true) {
            try {
                if (imagesend) {
                    imagesend = false;
                    MainActivity.printClientLog("onImageAvailable");
                    Image image = reader.acquireLatestImage();
                    if (image == null) {
                        continue;
                    }
                    final Image.Plane[] planes = image.getPlanes();
                    final Buffer buffer = planes[0].getBuffer().rewind();
                    if (!getSizeok) {
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * screenWidth;
                        // create bitmap
                        bitmap = Bitmap.createBitmap((screenWidth + rowPadding / pixelStride), screenHeight, Bitmap.Config.ARGB_8888);
                        getSizeok = true;
                    }
                    bitmap.copyPixelsFromBuffer(buffer);
                    //resize = Bitmap.createScaledBitmap(bitmap, 720, 480, true);
                    MainActivity.printClientLog("onsending start");
                    if (bitmap != null) {
                        final byte[] arr = bitmapToByteArray(bitmap);
                        MainActivity.printClientLog("onsending ok");
                        isok = true;
                        myInterface.onSending(arr);
                    } else {
                        MainActivity.printClientLog("onsending error");
                    }
                    image.close();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void clean(){
        /*
        if (mMediaProjectionManager != null) {
            mMediaProjectionManager = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
        if (bitmap != null) {
            bitmap = null;
        }*/
        if (mImageReader != null) {
            mImageReader.discardFreeBuffers();
            mImageReader.close();
        }
    }

    private void getSize(){
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }

    public void startConnection() {
        new Thread() {
            @Override
            synchronized public void run() {
                try {
                    sock = new Socket(defaultip, defaultport);
                    MainActivity.printClientLog("소켓 연결함.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.start();
    }

    public void start(Activity act){
        this.activity = act;
        issending  = true;
        MainActivity.printClientLog("start");
        mMediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent = mMediaProjectionManager.createScreenCaptureIntent();
        activity.startActivityForResult(intent, REQUEST_CODE_CAPTURE_IMAGE);
    }

    public void mirroring_start(){
        new Thread () {
            @Override
            synchronized public void run() {
                while (ismirroring) {
                    try {
                        if(issending) {
                            issending = false;
                            Thread.sleep(40);
                            MainActivity.printClientLog("takescreenshot start");
                            takescreenshot();
                            setOnSendListener(arr -> {
                                if (arr != null) {
                                    send(arr);
                                } else {
                                    MainActivity.printClientLog("arr is null");
                                }
                            });
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        }.start();
    }

    public void stop(){
        ismirroring = false;
        stopSelf();
    }

    public String getPrintStackTrace(Exception e) {

        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));

        return errors.toString();
    }

    synchronized public void send(byte[] data) {
        new Thread () {
            @Override
            synchronized public void run() {
                if (isok) {
                    isok = false;
                    String errorMessage = "";
                    try {
                        DataOutputStream outstream = new DataOutputStream(sock.getOutputStream());
                        outstream.write('B');
                        MainActivity.printClientLog("1");
                        byte[] len = intToByteArray(screenHeight + screenWidth + data.length);
                        MainActivity.printClientLog("2" + len);
                        outstream.write(len);
                        MainActivity.printClientLog("3");
                        outstream.write(data);
                        MainActivity.printClientLog("4");
                        outstream.flush();
                        MainActivity.printClientLog("데이터 전송함." + len);
                        issending = true;
                    } catch (Exception ex) {
                        errorMessage = getPrintStackTrace(ex);
                    } finally {
                        MainActivity.printClientLog(errorMessage);
                    }
                }
            }
        }.start();
    }

    private byte[] intToByteArray(final int integer) {
        ByteBuffer buff = ByteBuffer.allocate(Integer.SIZE / 8);
        buff.putInt(integer);
        buff.order(ByteOrder.BIG_ENDIAN);
        return buff.array();
    }
    public byte[] bitmapToByteArray( Bitmap bitmap ) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream() ;
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream) ;
        byte[] byteArray = stream.toByteArray();
        return byteArray ;
    }

    public Bitmap getResizedBitmap(Bitmap image, int maxSize) {
        int width = image.getWidth();
        int height = image.getHeight();

        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = maxSize;
            height = (int) (width / bitmapRatio);
        } else {
            height = maxSize;
            width = (int) (height * bitmapRatio);
        }

        return Bitmap.createScaledBitmap(image, width, height, true);
    }

    @SuppressWarnings("deprecation")
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}