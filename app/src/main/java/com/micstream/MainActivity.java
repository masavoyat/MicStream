package com.micstream;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";

    List<String> supportedPayloadTypes;
    List<Integer> supportedSampleRates;
    private Button btnStream;
    private EditText editTextDestinationIP;
    private EditText editTextDestinationPort;
    private Spinner spinnerPayloadType;
    private Spinner spinnerSampleRate;
    private TextView textViewDataRate;

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
        btnStream = findViewById(R.id.btnStream);
        btnStream.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mBoundService.isStreaming()){
                    mBoundService.stopStreaming();
                    btnStream.setText("Start");
                }
                else{
                    Log.i(TAG, editTextDestinationIP.getText().toString());
                    Log.i(TAG, editTextDestinationPort.getText().toString());
                    Log.i(TAG, spinnerPayloadType.getSelectedItem().toString());
                    Log.i(TAG, String.valueOf((int)spinnerSampleRate.getSelectedItem()));
                    mBoundService.setStreamDestinationIP(editTextDestinationIP.getText().toString());
                    mBoundService.setStreamDestinationPort(Integer.parseInt(editTextDestinationPort.getText().toString()));
                    mBoundService.setPayloadType(PayloadType.valueOf(spinnerPayloadType.getSelectedItem().toString()));
                    mBoundService.setSampleRate((int)spinnerSampleRate.getSelectedItem());
                    mBoundService.startStreaming();
                    btnStream.setText("Stop");
                }
            }
        });
        btnStream.setEnabled(false);
        editTextDestinationIP = findViewById(R.id.editTextDestinationIP);
        editTextDestinationIP.setEnabled(false);
        editTextDestinationPort = findViewById(R.id.editTextDestinationPort);
        editTextDestinationPort.setEnabled(false);
        spinnerPayloadType = findViewById(R.id.spinnerPayloadType);
        spinnerPayloadType.setEnabled(false);
        supportedPayloadTypes = new ArrayList<>();
        for(PayloadType payloadType : PayloadType.values()) {
            int bufferSize = AudioRecord.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_IN_MONO, payloadType.getAudioFormat());
            if(bufferSize>0)
                supportedPayloadTypes.add(payloadType.toString());
        }
        ArrayAdapter<String> sampleByteSizesAdapter =  new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, supportedPayloadTypes);
        spinnerPayloadType.setAdapter(sampleByteSizesAdapter);
        textViewDataRate = findViewById(R.id.textViewDataRate);
        spinnerSampleRate = findViewById(R.id.spinnerSampleRate);
        supportedSampleRates = this.getDeviceSampleRates();
        ArrayAdapter<Integer> sampleRateAdapter =  new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, supportedSampleRates);
        spinnerSampleRate.setAdapter(sampleRateAdapter);
        spinnerSampleRate.setEnabled(false);
    }

    private ArrayList<Integer> getDeviceSampleRates(){
        ArrayList<Integer> ratesList = new ArrayList<>();
        final int[] RECORDING_RATES = {1000, 2000, 4000, 8000, 11025, 16000, 22050, 32000, 44100, 48000, 96000, 192000};
        for(int rate : RECORDING_RATES) {
            try {
                int bufferSize = AudioRecord.getMinBufferSize(rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (bufferSize > 0) {
                    AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                    ratesList.add(rate);
                    ar.release();
                }
            }
            catch (Exception ignored) { }
        }
        return ratesList;
    }

    private void UpdateGUI() {
        myHandler.post(new Runnable() {
            public void run() {
                if(mBoundService != null) {
                    if (!editTextDestinationIP.isEnabled())
                        editTextDestinationIP.setText(mBoundService.getStreamDestinationIP());
                    if (!editTextDestinationPort.isEnabled())
                        editTextDestinationPort.setText(String.valueOf(mBoundService.getStreamDestinationPort()));
                    if (!spinnerPayloadType.isEnabled()) {
                        spinnerPayloadType.setSelection(supportedPayloadTypes.indexOf(mBoundService.getPayloadType().toString()));
                    if (!spinnerSampleRate.isEnabled()){
                        spinnerSampleRate.setSelection(supportedSampleRates.indexOf(mBoundService.getSampleRate()));
                    }
                    }
                    if (mBoundService.isStreaming()) {
                        btnStream.setText("Stop");
                        editTextDestinationIP.setEnabled(false);
                        editTextDestinationPort.setEnabled(false);
                        spinnerPayloadType.setEnabled(false);
                        spinnerSampleRate.setEnabled(false);
                        String dataRateString = String.format(Locale.getDefault(),
                                "%.1f",
                                ((float)mBoundService.getCurrentDataRate())/1000.0f) + " Ko/s";
                        textViewDataRate.setText(dataRateString);
                    }
                    else {
                        btnStream.setText("Start");
                        editTextDestinationIP.setEnabled(true);
                        editTextDestinationPort.setEnabled(true);
                        spinnerPayloadType.setEnabled(true);
                        spinnerSampleRate.setEnabled(true);
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
    }
}
