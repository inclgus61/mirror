package com.example.mirror;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import java.io.OutputStream;



@SuppressLint("HandlerLeak")
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private LinearLayout linearLayout;

    private OutputStream os;
    private String file;
    private String type;
    private ScreenRecording recording;
    private TakeScreenshot takeScreenshot;
    private Activity act;
    public String defalutip;
    private static final int PERMISSIONS_MULTIPLE_REQUEST = 123;

    static TextView textView;
    static TextView iptext;
    static EditText editText;
    static Handler handler = new Handler();

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        linearLayout = findViewById(R.id.button);
        textView = findViewById(R.id.textView);
        iptext = findViewById(R.id.iptext);
        editText = findViewById(R.id.ip);
        takeScreenshot = new TakeScreenshot();
        findViewById(R.id.screenshotDisplay).setOnClickListener(this);
        findViewById(R.id.serverconnect).setOnClickListener(this);

        Intent intent = new Intent(this, TakeScreenshot.class);
        startForegroundService(intent);

        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    ServiceConnection conn=new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            TakeScreenshot.ServiceBinder binder= (TakeScreenshot.ServiceBinder) iBinder;
            takeScreenshot=binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                + ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.FOREGROUND_SERVICE)
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.FOREGROUND_SERVICE)) {
                Snackbar.make(this.findViewById(R.id.linearayout),
                        "Please Grant Permissions",
                        Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                requestPermissions(new String[]{Manifest.permission.INTERNET,
                                                Manifest.permission.FOREGROUND_SERVICE},
                                        PERMISSIONS_MULTIPLE_REQUEST);
                            }
                        }).show();
            } else {
                requestPermissions(new String[]{Manifest.permission.INTERNET,
                                Manifest.permission.FOREGROUND_SERVICE},
                        PERMISSIONS_MULTIPLE_REQUEST);
            }
        } else {
            linearLayout.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.screenshotDisplay:
                if (((ToggleButton) view).isChecked()) {
                    takeScreenshot.start(this);
                } else {
                    takeScreenshot.stop();
                }
                break;
            case R.id.serverconnect:
                if (((ToggleButton) view).isChecked()) {
                    defalutip = String.valueOf(editText.getText());
                    takeScreenshot.startConnection(defalutip);
                }
                break;
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_MULTIPLE_REQUEST:
                if (grantResults.length > 0) {
                    boolean writeExternalStorage = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean recordAudio = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (writeExternalStorage && recordAudio) {
                        linearLayout.setVisibility(View.VISIBLE);
                    } else {
                        Snackbar.make(this.findViewById(R.id.linearayout),
                                "Please Grant Permissions",
                                Snackbar.LENGTH_INDEFINITE).setAction("ENABLE",
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            requestPermissions(
                                                    new String[]{Manifest.permission
                                                            .READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                                                    PERMISSIONS_MULTIPLE_REQUEST);
                                        }
                                    }
                                }).show();
                    }
                }
                break;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    public static void printClientLog(final String data) {
        Log.d("MainActivity", data);

        handler.post(new Runnable() {
            @Override
            public void run() {
                textView.append(data + "\n");
            }
        });
    }

    public static void printServerLog(final String data) {
        Log.d("MainActivity", data);

        handler.post(new Runnable() {
            @Override
            public void run() {
                textView.append(data + "\n");
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        takeScreenshot.onActivityResult(requestCode, resultCode, result);

        super.onActivityResult(requestCode, resultCode, result);
    }
}
