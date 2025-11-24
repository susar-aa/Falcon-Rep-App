package com.example.falconrep;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.falconrep.models.Product;
import com.example.falconrep.models.Variation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ImageWorker extends Worker {

    private static final String TAG = "FalconImages";
    private static final String CHANNEL_ID = "falcon_download_channel";
    private static final int NOTIFICATION_ID = 1;

    private final DatabaseHelper dbHelper;
    private final NotificationManager notificationManager;

    public ImageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        dbHelper = new DatabaseHelper(context);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Smart Image Sync Started...");

        // 1. Calculate Total Work
        List<Product> productsToSync = dbHelper.getProductsNeedingImageSync();
        List<Variation> varsToSync = dbHelper.getVariationsNeedingImageSync();

        int total = productsToSync.size() + varsToSync.size();
        if (total == 0) return Result.success();

        // 2. Start Foreground Service (Notification)
        setForegroundAsync(createForegroundInfo("Starting Download...", 0, total));

        int processed = 0;

        // 3. Sync Products
        for (Product p : productsToSync) {
            if (isStopped()) break;
            processProduct(p);
            dbHelper.markProductImageSynced(p.getId());

            processed++;
            // Update notification every few items to reduce flicker
            if (processed % 2 == 0) {
                setForegroundAsync(createForegroundInfo("Downloading images (" + processed + "/" + total + ")", processed, total));
            }
        }

        // 4. Sync Variations
        for (Variation v : varsToSync) {
            if (isStopped()) break;
            processVariation(v);
            dbHelper.markVariationImageSynced(v.getId());

            processed++;
            if (processed % 2 == 0) {
                setForegroundAsync(createForegroundInfo("Downloading variants (" + processed + "/" + total + ")", processed, total));
            }
        }

        Log.d(TAG, "Image Sync Complete.");
        return Result.success();
    }

    // --- Notification Logic ---
    private ForegroundInfo createForegroundInfo(String status, int progress, int max) {
        String title = "Syncing Catalog Images";

        // Create Channel (Required for Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Image Downloads", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setProgress(max, progress, false)
                .setPriority(NotificationCompat.PRIORITY_LOW) // Silent notification
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            return new ForegroundInfo(NOTIFICATION_ID, notification);
        }
    }

    private void processProduct(Product p) {
        List<String> webUrls = p.getWebUrls();
        if (webUrls.isEmpty()) return;

        List<String> localPaths = new ArrayList<>();
        boolean changed = false;

        for (int i = 0; i < webUrls.size(); i++) {
            String url = webUrls.get(i);
            String fileName = "img_" + p.getId() + "_" + i + ".jpg";
            File file = new File(getApplicationContext().getFilesDir(), fileName);

            if (file.exists()) {
                localPaths.add(file.getAbsolutePath());
            } else {
                String savedPath = downloadFile(url, fileName);
                if (!savedPath.isEmpty()) {
                    localPaths.add(savedPath);
                    changed = true;
                }
            }
        }

        if (changed) {
            p.setLocalPaths(localPaths);
            dbHelper.upsertProduct(p);
        }
    }

    private void processVariation(Variation v) {
        String url = v.getWebImageUrl();
        if (url == null || url.isEmpty()) return;

        String fileName = "var_" + v.getParentId() + "_" + v.getId() + ".jpg";
        File file = new File(getApplicationContext().getFilesDir(), fileName);

        if (!file.exists()) {
            String savedPath = downloadFile(url, fileName);
            if (!savedPath.isEmpty()) {
                v.setLocalImagePath(savedPath);
                dbHelper.upsertVariation(v);
            }
        }
    }

    private String downloadFile(String urlStr, String fileName) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return "";

            File file = new File(getApplicationContext().getFilesDir(), fileName);
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(file);

            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            out.close();
            in.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }
}