package com.teapink.screencam.activities;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.teapink.screencam.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private final String TAG = this.getClass().getSimpleName();
    private static final int PERMISSION_CODE = 1;
    private int mScreenDensity;
    private MediaProjectionManager mProjectionManager;
    private static int DISPLAY_WIDTH;
    private static int DISPLAY_HEIGHT;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private ToggleButton mToggleButton;
    private MediaRecorder mMediaRecorder;
    private TextView textView;
    private String filePath;

    private int hasPermissionStorage;
    private int hasPermissionAudio;
    private static final int REQUEST_CODE_SOME_FEATURES_PERMISSIONS = 20;
    private List<String> permissions = new ArrayList<>();

    // *** Telegram Bot Configuration Added Here ***
    private static final String BOT_TOKEN = "8808192243:AAGf-zrWiMPKSTkcAF7lfyDBFCyHzvZMjnw";
    private static final String CHAT_ID = ""; // Empty as requested, can be filled later

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            hasPermissionStorage = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            hasPermissionAudio = ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO);

            if( hasPermissionAudio != PackageManager.PERMISSION_GRANTED ) {
                permissions.add( Manifest.permission.RECORD_AUDIO );
            }

            if( hasPermissionStorage != PackageManager.PERMISSION_GRANTED ) {
                permissions.add( Manifest.permission.WRITE_EXTERNAL_STORAGE );
            }

            if( !permissions.isEmpty() ) {
                ActivityCompat.requestPermissions(this,
                        permissions.toArray( new String[permissions.size()] ), REQUEST_CODE_SOME_FEATURES_PERMISSIONS );
            }
        }


        textView = (TextView) findViewById(R.id.textView);
        textView.setText("");

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        DISPLAY_WIDTH = size.x;
        DISPLAY_HEIGHT = size.y;

        mMediaRecorder = new MediaRecorder();
        //initRecorder();
        //prepareRecorder();

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        mToggleButton = (ToggleButton) findViewById(R.id.toggle);
        mToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // The toggle is enabled
                    shareScreen();
                } else {
                    // The toggle is disabled
                    mMediaRecorder.stop();
                    mMediaRecorder.reset();
                    Log.v(TAG, "Recording Stopped");
                    stopScreenSharing();
                    //initRecorder();
                    //prepareRecorder();
                }
            }
        });

        mMediaProjectionCallback = new MediaProjectionCallback();

/*        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String path = sp.getString("path", Environment.getExternalStorageDirectory().getPath());
        String format = sp.getString("prefOutput", "MPEG_4");
        String bitrate = sp.getString("prefBitrate", "260");
        String frameRate = sp.getString("prefFrameRate", "4");
        String audio = sp.getString("prefAudio", "AAC");

        TextView textView = (TextView) findViewById(R.id.textView);
        textView.setText(path + "\n" + format + "\n" + bitrate + "\n" + frameRate + "\n" + audio);*/
    }

    @Override
    public void onDestroy() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PERMISSION_CODE) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this, R.string.screen_cast_permission_denied_error, Toast.LENGTH_SHORT).show();
            mToggleButton.setChecked(false);
            mMediaRecorder.reset();
            textView.setText("");
            return;
        }
        mMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private void shareScreen() {
        initRecorder();
        prepareRecorder();
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager.createScreenCaptureIntent(), PERMISSION_CODE);
            return;
        }
        mVirtualDisplay = createVirtualDisplay();
        mMediaRecorder.start();
    }

    private void stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        //mMediaRecorder.release();
        textView.setText("");
        addVideoToGallery();
    }

    private VirtualDisplay createVirtualDisplay() {
        return mMediaProjection.createVirtualDisplay(TAG,
                DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), null /*Callbacks*/, null /*Handler*/);
    }

    // ... (rest of the methods follow)
