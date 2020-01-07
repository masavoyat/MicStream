package com.micstream;


import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainService extends Service {
    private final static String TAG = "MainService";

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();
    // Objects used for streaming thread
    private AtomicBoolean run = new AtomicBoolean(false);
    private Thread thread;
    // the audio recording options
    // Rates to be tested in increasing order, max possible will be used
    private static final int[] RECORDING_RATES = {8000, 11025, 16000, 22050, 32000, 44100, 48000, 96000};
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int SAMPLE_BYTE_SIZE = 2;
    // the audio recorder
    private AudioRecord recorder;
    // Streaming objects
    String streamDestinationIP;
    int streamDestinationPort;
    private static final int PAYLOAD_TYPE = 127;
    private static final int PAYLOAD_SIZE = 1000;
    SharedPreferences sharedPreferences;

    public String getStreamDestinationIP() {
        return streamDestinationIP;
    }

    public void setStreamDestinationIP(String streamDestinationIP) {
        this.streamDestinationIP = streamDestinationIP;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.streamDestinationIP_key), streamDestinationIP);
        editor.commit();
    }

    public int getStreamDestinationPort() {
        return streamDestinationPort;
    }

    public void setStreamDestinationPort(int streamDestinationPort) {
        this.streamDestinationPort = streamDestinationPort;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getString(R.string.streamDestinationPort_key), streamDestinationPort);
        editor.commit();
    }


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Create");
        Toast.makeText(this, "Create service", Toast.LENGTH_LONG).show();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        streamDestinationIP = sharedPreferences.getString(getString(R.string.streamDestinationIP_key), "51.15.37.41"); // "192.168.0.252"
        streamDestinationPort = sharedPreferences.getInt(getString(R.string.streamDestinationPort_key), 2222);
        this.startStreaming();
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId){
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "Start");
        Notification notification = new NotificationCompat.Builder(this, TAG)
                .setContentTitle("Foreground Service")
                .setContentText("Content text")
                .build();
        startForeground(1, notification);
        if(recorder != null)
            if (!isStreaming())
                startStreaming();
        return Service.START_STICKY;
    }

    public void startStreaming(){
        thread = new Thread(new Runnable() {
            // Surcharge de la méthode run
            public void run() {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF  , TAG + ":wifiLock");
                wifiLock.setReferenceCounted(true);
                wifiLock.acquire();
                PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG + ":wakeLock");
                wakeLock.acquire();
                int bufferSize = 0;
                int rate = 0;
                for (int i=RECORDING_RATES.length-1; i>=0; i--)
                {
                    rate = RECORDING_RATES[i];
                    bufferSize = 0;
                    recorder = null;
                    try {
                        bufferSize = AudioRecord.getMinBufferSize(rate, CHANNEL, FORMAT);
                        bufferSize = (1+ bufferSize/PAYLOAD_SIZE)*PAYLOAD_SIZE;
                        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                                rate, CHANNEL, FORMAT, bufferSize * 10);
                        Log.d(TAG, "Created AudioRecord, rate :" + rate + ", bufferSize: " + bufferSize);
                    }
                    catch (Exception e) {
                        continue;
                    }
                    // If we arrive here it means that we have a valid recorder
                    break;
                }
                byte[] buffer = new byte[bufferSize];
                DatagramSocket datagramSocket = null;
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    datagramSocket = new DatagramSocket();
                    datagramPacket.setAddress(InetAddress.getByName(streamDestinationIP));
                    datagramPacket.setPort(streamDestinationPort);
                    Log.d(TAG, "Created DatagramSocket : " + streamDestinationIP + "" + String.valueOf(streamDestinationPort));
                }
                catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                    recorder.startRecording();
                short frameNb = 0;
                int sampleNb = 0;
                while(run.get() && (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                        && (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)){
                    try {
                        int read = recorder.read(buffer, 0, buffer.length);
                        int sizeToSend = read;
                        int index = 0;
                        while(sizeToSend>0) {
                            int packetBufferSize = Math.min(sizeToSend, PAYLOAD_SIZE);
                            StreamPacket rtp_packet = new StreamPacket((byte) PAYLOAD_TYPE,
                                    frameNb++, sampleNb, rate,
                                    Arrays.copyOfRange(buffer, index, index + packetBufferSize),
                                    packetBufferSize);
                            byte[] packetBuffer = new byte[rtp_packet.getPacketLength()];
                            rtp_packet.getPacket(packetBuffer);
                            sizeToSend -= packetBufferSize;
                            index += packetBufferSize;
                            sampleNb += packetBufferSize/SAMPLE_BYTE_SIZE;
                            datagramPacket.setData(packetBuffer);
                            if(datagramSocket != null)
                                datagramSocket.send(datagramPacket);
                        }
                    } catch (Throwable t) {
                        // gérer l'exception et arrêter le traitement
                    }
                }
                if(datagramSocket != null) {
                    if (!datagramSocket.isClosed())
                        datagramSocket.close();
                }
                if(recorder != null) {
                    if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                        recorder.stop();
                        recorder.release();
                    }
                }
                if(wakeLock.isHeld())
                    wakeLock.release();
                if(wifiLock.isHeld())
                    wifiLock.release();
            }
        });
        run.set(true);
        thread.start();
        Toast.makeText(this, "Streaming started", Toast.LENGTH_LONG).show();
    }

    public boolean isStreaming(){
        if (recorder == null)
            return false;
        return thread.isAlive()
                && (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                && (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING);
    }

    public void stopStreaming(){
        /**/
        run.set(false);
        try {
            thread.join();
        } catch (Throwable t) {
            // gérer l'exception et arrêter le traitement
        }
        Toast.makeText(this, "Streaming stopped", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroy");
        Toast.makeText(this, "Service Stopped", Toast.LENGTH_LONG).show();
        stopStreaming();
    }

    public class LocalBinder extends Binder {
        MainService getService() {
            Log.d(TAG, "Binder");
            return MainService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}
