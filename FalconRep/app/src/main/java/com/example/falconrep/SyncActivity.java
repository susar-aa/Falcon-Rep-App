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
            // 1. If Online -> Start Sync
            startSync();
        } else {
            // 2. If Offline -> Check if we can just go to Home
            DatabaseHelper db = new DatabaseHelper(this);
            if (db.getProductCount() > 0) {
                finishSync("Offline Mode: Data Available");
            } else {
                // 3. If Offline AND Empty -> Stay here
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

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .addTag("falcon_sync")
                .build();

        // Always REPLACE existing work to ensure a fresh sync on app start or button click
        WorkManager.getInstance(this).enqueueUniqueWork(
                "falcon_manual_sync",
                ExistingWorkPolicy.REPLACE,
                syncRequest
        );

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(syncRequest.getId())
                .observe(this, workInfo -> {
                    if (workInfo != null) {
                        Data progress = workInfo.getProgress();
                        int percent = progress.getInt("progress", 0);
                        String status = progress.getString("status");

                        progressBar.setProgress(percent);
                        if (status != null) txtStatus.setText(status);
                        txtProgress.setText(percent + "%");

                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            finishSync("Sync Complete!");
                        } else if (workInfo.getState() == WorkInfo.State.FAILED) {
                            String error = workInfo.getOutputData().getString("error");
                            finishSync("Sync Failed: " + error);
                        }
                    }
                });

        // Allow skipping sync if it gets stuck
        btnCancel.setOnClickListener(v -> {
            WorkManager.getInstance(this).cancelWorkById(syncRequest.getId());
            finishSync("Sync Cancelled");
        });
    }

    private void finishSync(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        // FIX: Always navigate to HomeActivity after sync
        Intent intent = new Intent(SyncActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // Close SyncActivity so Back button works correctly in Home
    }
}