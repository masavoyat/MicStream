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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

public class MainService extends Service {
    private final static String TAG = "MainService";

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();
    // Objects used for streaming thread
    private AtomicBoolean run = new AtomicBoolean(false);
    private AtomicInteger dataBytes = new AtomicInteger(0);
    private AtomicLong dataBytesResetTime = new AtomicLong(0);
    private Thread thread;
    // the audio recording options
    private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    // the audio recorder
    private AudioRecord recorder;
    // Streaming objects
    String streamDestinationIP;
    int streamDestinationPort;
    int sampleRate;
    PayloadType payloadType;
    private static final int PAYLOAD_SIZE = 1000;
    SharedPreferences sharedPreferences;

    public String getStreamDestinationIP() {
        return streamDestinationIP;
    }

    public void setStreamDestinationIP(String streamDestinationIP) {
        this.streamDestinationIP = streamDestinationIP;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.streamDestinationIP_key), streamDestinationIP);
        editor.apply();
    }

    public int getStreamDestinationPort() {
        return streamDestinationPort;
    }

    public void setStreamDestinationPort(int streamDestinationPort) {
        this.streamDestinationPort = streamDestinationPort;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getString(R.string.streamDestinationPort_key), streamDestinationPort);
        editor.apply();
    }

    public PayloadType getPayloadType() { return payloadType;}

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.payloadType_key), payloadType.toString());
        editor.apply();
    }

    public int getSampleRate() { return sampleRate; }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(getString(R.string.sampleRate_key), sampleRate);
        editor.apply();
    }

    /**
     *  Compute an estimate of the datarate sent over the network based on
     *  number of bytes sent and time elapsed since last call to this function
     * @return estimated datarate
     */
    public int getCurrentDataRate(){
        int ret = dataBytes.get();
        long time = System.currentTimeMillis();
        dataBytes.set(0);
        int deltaTime =(int)(time - dataBytesResetTime.get());
        dataBytesResetTime.set(time);
        return (1000*ret)/deltaTime;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Create");
        Toast.makeText(this, "Create service", Toast.LENGTH_LONG).show();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        streamDestinationIP = sharedPreferences.getString(getString(R.string.streamDestinationIP_key), "127.0.0.1");
        streamDestinationPort = sharedPreferences.getInt(getString(R.string.streamDestinationPort_key), 2222);
        payloadType = PayloadType.valueOf(sharedPreferences.getString(getString(R.string.payloadType_key),
                                            PayloadType.RAW_16BIT.toString()));
        sampleRate = sharedPreferences.getInt(getString(R.string.sampleRate_key), 44100);
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
        if (!isStreaming())
            startStreaming();
        return Service.START_STICKY;
    }

    public void startStreaming(){
        thread = new Thread(new Runnable() {
            public void run() {
                // Initialize the recorder
                int format = payloadType.getAudioFormat();
                int bufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, format);
                if (bufferSize == AudioRecord.ERROR_BAD_VALUE)
                    return;
                bufferSize = (1+ bufferSize/PAYLOAD_SIZE)*PAYLOAD_SIZE;
                boolean recoderNotInitialized = true;
                do{
                    recorder = null;
                    try {
                        recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT,
                                sampleRate, CHANNEL, format, bufferSize * 10);
                    }
                    catch (Exception e) {
                        Log.e(TAG, e.toString());
                        continue;
                    }
                    if (recorder == null) {
                        Log.e(TAG, "Null AudioRecord");
                        continue;
                    }
                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        Log.e(TAG, "AudioRecord not initialized: " + recorder.getState());
                        recorder.release();
                        continue;
                    }
                    recoderNotInitialized = false;
                } while(recoderNotInitialized);
                Log.d(TAG, "Created AudioRecord, rate :" + sampleRate +
                                ", bufferSize: " + bufferSize);
                // Initialize the UDP stream
                byte[] buffer = new byte[bufferSize];
                DatagramSocket datagramSocket;
                DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
                try {
                    datagramSocket = new DatagramSocket();
                    datagramPacket.setAddress(InetAddress.getByName(streamDestinationIP));
                    datagramPacket.setPort(streamDestinationPort);
                    Log.d(TAG, "Created DatagramSocket : " +
                                streamDestinationIP + ":" + streamDestinationPort);
                }
                catch (Exception e) {
                    Log.e(TAG, e.toString());
                    return;
                }
                // recorder and socket are OK so we can wakeLock and WiFiLock
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.WifiLock wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF  , TAG + ":wifiLock");
                wifiLock.setReferenceCounted(true);
                wifiLock.acquire();
                PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG + ":wakeLock");
                wakeLock.acquire();
                // Start recording and stream loop
                recorder.startRecording();
                short frameNb = 0;
                int sampleNb = 0;
                dataBytes.set(0);
                dataBytesResetTime.set(System.currentTimeMillis());
                while(run.get() && (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                        && (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)){
                    try {
                        int sizeToSend = recorder.read(buffer, 0, buffer.length);
                        int index = 0;
                        while(sizeToSend>0) {
                            int packetBufferSize = Math.min(sizeToSend, PAYLOAD_SIZE);
                            byte[] bufferToSend = Arrays.copyOfRange(buffer, index, index + packetBufferSize);
                            if (payloadType.compression == PayloadType.Compression.ZIP) {
                                byte[] output = new byte[2*packetBufferSize];
                                Deflater compresser = new Deflater(Deflater.BEST_COMPRESSION);
                                compresser.setInput(bufferToSend);
                                compresser.finish();
                                int compressedDataLength = compresser.deflate(output);
                                compresser.end();
                                bufferToSend = Arrays.copyOfRange(output, 0, compressedDataLength);
                            }
                            StreamPacket rtp_packet = new StreamPacket((byte) payloadType.payloadTypeId,
                                    frameNb++, sampleNb, sampleRate,
                                    bufferToSend,
                                    bufferToSend.length);
                            byte[] packetBuffer = new byte[rtp_packet.getPacketLength()];
                            rtp_packet.getPacket(packetBuffer);
                            sizeToSend -= packetBufferSize;
                            index += packetBufferSize;
                            sampleNb += packetBufferSize/payloadType.sampleByteSize;
                            datagramPacket.setData(packetBuffer);
                            if(!datagramSocket.isClosed()) {
                                datagramSocket.send(datagramPacket);
                                dataBytes.set(dataBytes.get() + packetBuffer.length);
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, t.toString());// gérer l'exception et arrêter le traitement
                    }
                }
                // Stop and close everything
                if (!datagramSocket.isClosed())
                    datagramSocket.close();
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
                    recorder.stop();
                if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                    recorder.release();
                if(wakeLock.isHeld())
                    wakeLock.release();
                if(wifiLock.isHeld())
                    wifiLock.release();
                Log.d(TAG, "Exit Streaming thread");
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
