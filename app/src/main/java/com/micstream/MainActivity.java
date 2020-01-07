package com.micstream;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";

    private Runnable refreshThread;
    private AtomicBoolean runRefreshThread = new AtomicBoolean(false);
    private Button btnStream;
    private EditText editTextDestinationIP;
    private EditText editTextDestinationPort;
    private Spinner spinnerSampleByteSize;

    final Handler myHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "Create");
        checkRecordPermission();
        doBindService();
        Timer myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {UpdateGUI();}
        }, 0, 1000);
        btnStream = (Button) findViewById(R.id.btnStream);
        btnStream.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mBoundService.isStreaming()){
                    mBoundService.stopStreaming();
                    btnStream.setText("Start");
                }
                else{
                    Log.i(TAG, editTextDestinationIP.getText().toString());
                    Log.i(TAG, editTextDestinationPort.getText().toString());
                    Log.i(TAG, spinnerSampleByteSize.getSelectedItem().toString());
                    mBoundService.setStreamDestinationIP(editTextDestinationIP.getText().toString());
                    mBoundService.setStreamDestinationPort(Integer.parseInt(editTextDestinationPort.getText().toString()));
                    if (spinnerSampleByteSize.getSelectedItem().toString().contains(("16")))
                        mBoundService.setSampleByteSize(2);
                    else
                        mBoundService.setSampleByteSize(1);
                    mBoundService.startStreaming();
                    btnStream.setText("Stop");
                }
            }
        });
        btnStream.setEnabled(false);
        editTextDestinationIP = (EditText)findViewById(R.id.editTextDestinationIP);
        editTextDestinationIP.setEnabled(false);
        editTextDestinationPort = (EditText)findViewById(R.id.editTextDestinationPort);
        editTextDestinationPort.setEnabled(false);
        spinnerSampleByteSize = (Spinner)findViewById(R.id.spinnerSampleByteSize);
        spinnerSampleByteSize.setEnabled(false);

    }

    private void UpdateGUI() {
        myHandler.post(new Runnable() {
            public void run() {
                if(mBoundService != null) {
                    //Log.d(TAG, "Thread is alive " + String.valueOf(mBoundService.isStreaming()));
                    if (mBoundService.isStreaming()) {
                        btnStream.setText("Stop");
                        editTextDestinationIP.setEnabled(false);
                        editTextDestinationPort.setEnabled(false);
                        spinnerSampleByteSize.setEnabled(false);
                        editTextDestinationIP.setText(mBoundService.getStreamDestinationIP());
                        editTextDestinationPort.setText(String.valueOf(mBoundService.getStreamDestinationPort()));
                        spinnerSampleByteSize.setSelection(mBoundService.getSampleByteSize()-1);
                    }
                    else {
                        btnStream.setText("Start");
                        editTextDestinationIP.setEnabled(true);
                        editTextDestinationPort.setEnabled(true);
                        spinnerSampleByteSize.setEnabled(true);
                    }
                    btnStream.setEnabled(true);
                }
            }
        });
    }

    private void checkRecordPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    123);
        }
    }

    private MainService mBoundService;
    private boolean mIsBound;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has
            // been established, giving us the service object we can use
            // to interact with the service.  Because we have bound to a
            // explicit service that we know is running in our own
            // process, we can cast its IBinder to a concrete class and
            // directly access it.
            mBoundService = ((MainService.LocalBinder) service).getService();
            Log.d(TAG, "Service connected");
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has
            // been unexpectedly disconnected -- that is, its process
            // crashed. Because it is running in our same process, we
            // should never see this happen.
            mBoundService = null;
            Log.d(TAG, "Service disconnected");
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation
        // that we know will be running in our own process (and thus
        // won't be supporting component replacement by other
        // applications).
        Intent intent = new Intent(this, MainService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        }
        else {
            startService(intent);
        }
        bindService(intent,
                mConnection,
                Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;


        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
        runRefreshThread.set(false);
    }
}
