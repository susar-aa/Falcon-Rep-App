package com.example.falconrep;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

public class SyncActivity extends AppCompatActivity {

    private TextView txtStatus, txtProgress;
    private ProgressBar progressBar;
    private Button btnCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);

        txtStatus = findViewById(R.id.txtStatus);
        txtProgress = findViewById(R.id.txtProgress);
        progressBar = findViewById(R.id.progressBar);
        btnCancel = findViewById(R.id.btnCancel);

        txtStatus.setText("Checking Internet...");

        if (isOnline()) {
            startSync();
        } else {
            DatabaseHelper db = new DatabaseHelper(this);
            if (db.getProductCount() > 0) {
                finishSync("Offline Mode: Data Available");
            } else {
                txtStatus.setText("No Internet & No Local Data");
                progressBar.setIndeterminate(false);
                progressBar.setProgress(0);
                Toast.makeText(this, "Connect to Internet to Sync", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(network);
        return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void startSync() {
        txtStatus.setText("Starting Sync...");

        // 1. Create Sync Worker (Data)
        OneTimeWorkRequest syncDataRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .addTag("falcon_sync_data")
                .build();

        // 2. Create Image Worker (Background Media)
        OneTimeWorkRequest syncImagesRequest = new OneTimeWorkRequest.Builder(ImageWorker.class)
                .addTag("falcon_sync_images")
                .build();

        // 3. Chain: Data -> Then Images
        WorkManager.getInstance(this)
                .beginUniqueWork("falcon_full_sync", ExistingWorkPolicy.REPLACE, syncDataRequest)
                .then(syncImagesRequest)
                .enqueue();

        // 4. Observe ONLY the Data Sync (The first one)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(syncDataRequest.getId())
                .observe(this, workInfo -> {
                    if (workInfo != null) {
                        Data progress = workInfo.getProgress();
                        int percent = progress.getInt("progress", 0);
                        String status = progress.getString("status");

                        progressBar.setProgress(percent);
                        if (status != null) txtStatus.setText(status);
                        txtProgress.setText(percent + "%");

                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            // FIX: Navigate to Home immediately after Data Sync!
                            // Image Sync will continue running in the background.
                            finishSync("Data Sync Complete! Downloading images in background...");
                        } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                            String error = workInfo.getOutputData().getString("error");
                            finishSync("Sync Failed: " + error);
                        }
                    }
                });

        btnCancel.setOnClickListener(v -> {
            WorkManager.getInstance(this).cancelAllWork();
            finishSync("Sync Cancelled");
        });
    }

    private void finishSync(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(SyncActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}