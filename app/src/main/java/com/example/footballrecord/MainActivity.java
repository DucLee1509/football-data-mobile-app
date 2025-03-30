package com.example.footballrecord;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;


import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import android.graphics.Color;
import android.animation.ObjectAnimator;
import android.widget.EditText;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private final Handler handler = new Handler();
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean server_alive = false;
    private TextView statusTextView, matchScoreTextView, progress3TextView, progress2TextView, progress1TextView;
    private EditText urlTextView;

    private AudioManager mAudioManager;
    private BroadcastReceiver mBroadcastReceiver;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        listAudioDevices();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button buttonRecord = findViewById(R.id.button_record);
        Button buttonRemove = findViewById(R.id.button_remove);
        urlTextView = findViewById(R.id.url);

        // Request permissions if not granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.INTERNET,
            }, 200);
        }

        // Set button touch listener
        buttonRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.d(TAG, "Button pressed");
                    startRecording();

                    v.setBackgroundColor(Color.parseColor("#1B5E20")); // Dark Green
                    ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(v, "scaleX", 0.95f);
                    ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(v, "scaleY", 0.95f);
                    scaleDownX.setDuration(100);
                    scaleDownY.setDuration(100);
                    scaleDownX.start();
                    scaleDownY.start();
                    break;
                case MotionEvent.ACTION_UP:
                    Log.d(TAG, "Button released");
                    v.setBackgroundColor(Color.parseColor("#B71C1C")); // Dark Red
                    ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(v, "scaleX", 1f);
                    ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(v, "scaleY", 1f);
                    scaleUpX.setDuration(100);
                    scaleUpY.setDuration(100);
                    scaleUpX.start();
                    scaleUpY.start();

                    stopRecording();
                    break;
            }
            return false; // Allow normal button behavior
        });

        // Set OnClickListener
        buttonRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "on click remove latest");
                new Thread(() -> removeLatest()).start();
            }
        });

        statusTextView = findViewById(R.id.status);
        statusTextView = findViewById(R.id.status);
        matchScoreTextView = findViewById(R.id.match_score);
        progress3TextView = findViewById(R.id.progress_3);
        progress2TextView = findViewById(R.id.progress_2);
        progress1TextView = findViewById(R.id.progress_1);

        startServerCheck();
    }

    private void listAudioDevices() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);

        for (int i = 0; i < devices.length; i++) {
            AudioDeviceInfo device = devices[i];
            String deviceName = device.getProductName().toString();
            int channelCount = device.getChannelCounts().length > 0 ? device.getChannelCounts()[0] : 1;

            Log.d(TAG, "[duclee] Device " + i + ": " + deviceName + " (Input Channels: " + channelCount + ")");
        }
    }

    private void startServerCheck() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkServerAlive();
                handler.postDelayed(this, 500); // Run every 0.5 second
            }
        }, 500);
    }

    private void removeLatest() {
        String responseString = "";
        try {
            String inputUrl = urlTextView.getText().toString().trim();
            String SERVER_URL = inputUrl.endsWith("/") ? inputUrl + "remove" : inputUrl + "/remove";
            URL url = new URL(SERVER_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();

            if (responseCode == 200) {
                Log.d(TAG, SERVER_URL);
                // Read response from server
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                responseString = response.toString();
                Log.d(TAG, "remove_button: Server Response: " + responseString);
            } else {
                Log.e(TAG, "remove_button: Server is down: " + responseCode);
            }

            connection.disconnect();
        } catch (IOException e) {
            Log.e(TAG, "remove_button: Server check failed: " + e.getMessage());
        }
    }

    private void checkServerAlive() {
        new Thread(() -> {
            boolean isAlive = false;
            String responseString = "";
            try {
                String inputUrl = urlTextView.getText().toString().trim();
                String SERVER_URL = inputUrl.endsWith("/") ? inputUrl + "progress" : inputUrl + "/progress";
                URL url = new URL(SERVER_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                    isAlive = true;
                    Log.d(TAG, SERVER_URL);
                    Log.d(TAG, "Server is alive");
                    // Read response from server
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    responseString = response.toString();
                    Log.d(TAG, "Server Response: " + responseString);
                } else {
                    Log.e(TAG, "Server is down: " + responseCode);
                }

                connection.disconnect();
            } catch (IOException e) {
                Log.e(TAG, "Server check failed: " + e.getMessage());
            }

            boolean finalIsAlive = isAlive;
            String finalResponseString = responseString;
            runOnUiThread(() -> updateServerStatus(finalIsAlive, finalResponseString));
        }).start();
    }

    private void updateServerStatus(boolean isAlive, String response) {
        if (isAlive && !server_alive) {
            server_alive = true;
            statusTextView.setText("online");
            statusTextView.setBackgroundColor(Color.parseColor("#1B5E20"));
        } else if (!isAlive && server_alive) {
            server_alive = false;
            statusTextView.setText("offline");
            statusTextView.setBackgroundColor(Color.parseColor("#B71C1C")); // Red color for offline
        }
        // Process response and update UI
        String[] parts = response.split("\\|", -1); // Split by "|"
        if (parts.length >= 4) {
            matchScoreTextView.setText(parts[0]);
            String progress3 = "- " + parts[1];
            progress3TextView.setText(progress3);
            String progress2 = "- " + parts[2];
            progress2TextView.setText(progress2);
            String progress1 = "- " + parts[3];
            progress1TextView.setText(progress1);
        }
    }

    private void startRecording() {
        audioFilePath = Objects.requireNonNull(getExternalFilesDir(null)).getAbsolutePath() + "/recorded_audio.3gp";
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(audioFilePath);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d(TAG, "Recording started");
        } catch (IOException e) {
            Log.e(TAG, "Recording failed: " + e.getMessage());
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            Log.d(TAG, "Recording stopped. File saved at: " + audioFilePath);
        }

        // Upload the file after recording stops
        new Thread(() -> uploadFile(audioFilePath)).start();
    }

    private void uploadFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                Log.e(TAG, "File not found: " + filePath);
                return;
            }

            String boundary = "*****";
            String inputUrl = urlTextView.getText().toString().trim();
            String SERVER_URL = inputUrl.endsWith("/") ? inputUrl + "upload" : inputUrl + "/upload";
            Log.d(TAG, "SERVER_URL: " + SERVER_URL);
            URL url = new URL(SERVER_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
            outputStream.writeBytes("--" + boundary + "\r\n");
            outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
            outputStream.writeBytes("Content-Type: audio/wav\r\n\r\n");

            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fileInputStream.close();

            outputStream.writeBytes("\r\n--" + boundary + "--\r\n");
            outputStream.flush();
            outputStream.close();

            int serverResponseCode = connection.getResponseCode();
            Log.d(TAG, "POST /upload Response Code: " + serverResponseCode);
            if (serverResponseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "File uploaded successfully. Deleting file...");
                if (file.delete()) {
                    Log.d(TAG, "File deleted: " + filePath);
                } else {
                    Log.e(TAG, "Failed to delete file: " + filePath);
                }
            } else {
                Log.e(TAG, "File upload failed. Server Response Code: " + serverResponseCode);
            }

            connection.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "File upload failed: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null); // Stop checking when activity is destroyed
    }
}
