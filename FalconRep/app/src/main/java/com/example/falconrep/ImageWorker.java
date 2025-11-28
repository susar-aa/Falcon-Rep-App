package com.example.falconrep;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.text.TextUtils;
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
    private static final String CHANNEL_ID = "falcon_img_sync_channel";
    private static final int NOTIFICATION_ID = 888;
    // Folder name in Internal Storage
    private static final String IMAGE_FOLDER_NAME = "falcon_catalog_images";

    private final DatabaseHelper dbHelper;
    private final NotificationManager notificationManager;
    private final Context context;

    public ImageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
        dbHelper = new DatabaseHelper(context);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting Image Sync...");

        // 0. Create the Internal Storage Directory explicitly
        File internalFolder = new File(context.getFilesDir(), IMAGE_FOLDER_NAME);
        if (!internalFolder.exists()) {
            boolean created = internalFolder.mkdirs();
            Log.d(TAG, "Created Image Directory: " + created + " at " + internalFolder.getAbsolutePath());
        }

        List<Product> productsToSync = dbHelper.getProductsNeedingImageSync();
        List<Variation> varsToSync = dbHelper.getVariationsNeedingImageSync();

        int total = productsToSync.size() + varsToSync.size();
        if (total == 0) return Result.success();

        setForegroundAsync(createForegroundInfo("Preparing downloads...", 0, total));

        int processed = 0;

        // 1. Process Products
        for (Product p : productsToSync) {
            if (isStopped()) break;
            processProduct(p, internalFolder);
            // Mark as synced regardless of success to prevent infinite retry loops on bad URLs
            // (Only reset flag if URL changes in SyncWorker)
            dbHelper.markProductImageSynced(p.getId());

            processed++;
            updateNotification(processed, total);
        }

        // 2. Process Variations
        for (Variation v : varsToSync) {
            if (isStopped()) break;
            processVariation(v, internalFolder);
            dbHelper.markVariationImageSynced(v.getId());

            processed++;
            updateNotification(processed, total);
        }

        return Result.success();
    }

    private void processProduct(Product p, File directory) {
        List<String> webUrls = p.getWebUrls();
        if (webUrls.isEmpty()) return;

        List<String> validLocalPaths = new ArrayList<>();
        boolean dbNeedsUpdate = false;

        for (int i = 0; i < webUrls.size(); i++) {
            String url = webUrls.get(i);
            String fileName = "prod_" + p.getId() + "_" + i + ".jpg";

            // Define specific file in our custom internal folder
            File targetFile = new File(directory, fileName);

            if (targetFile.exists() && targetFile.length() > 0) {
                // Already exists
                validLocalPaths.add(targetFile.getAbsolutePath());
                dbNeedsUpdate = true; // Ensure DB has this path
            } else {
                // Download
                String savedPath = downloadFile(url, targetFile);
                if (!savedPath.isEmpty()) {
                    validLocalPaths.add(savedPath);
                    dbNeedsUpdate = true;
                }
            }
        }

        // CRITICAL: Write the paths to DB so "Offline Ready Count" works
        if (dbNeedsUpdate && !validLocalPaths.isEmpty()) {
            String serializedPaths = TextUtils.join("###", validLocalPaths);
            Log.d(TAG, "Updating Product " + p.getId() + " paths: " + serializedPaths);
            dbHelper.updateLocalImagePaths(p.getId(), serializedPaths);
        }
    }

    private void processVariation(Variation v, File directory) {
        String url = v.getWebImageUrl();
        if (url == null || url.isEmpty()) return;

        String fileName = "var_" + v.getParentId() + "_" + v.getId() + ".jpg";
        File targetFile = new File(directory, fileName);

        if (targetFile.exists() && targetFile.length() > 0) {
            // Already exists, ensure DB knows
            dbHelper.updateVariationImagePath(v.getId(), targetFile.getAbsolutePath());
        } else {
            String savedPath = downloadFile(url, targetFile);
            if (!savedPath.isEmpty()) {
                dbHelper.updateVariationImagePath(v.getId(), savedPath);
            }
        }
    }

    private String downloadFile(String urlStr, File targetFile) {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "FalconRep/1.0"); // Important for WooCommerce
            conn.connect();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Server returned " + conn.getResponseCode() + " for " + urlStr);
                return "";
            }

            // Write to the specific file in internal storage
            in = conn.getInputStream();
            out = new FileOutputStream(targetFile);

            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush(); // Force write

            // Verify
            if (targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "Downloaded: " + targetFile.getAbsolutePath());
                return targetFile.getAbsolutePath();
            } else {
                return "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Download Failed: " + e.getMessage());
            // Cleanup corrupt file
            if (targetFile.exists()) targetFile.delete();
            return "";
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    private void updateNotification(int current, int total) {
        if (current % 5 == 0 || current == total) { // Update every 5 items to reduce spam
            setForegroundAsync(createForegroundInfo("Downloading " + current + "/" + total, current, total));
        }
    }

    private ForegroundInfo createForegroundInfo(String status, int progress, int max) {
        String title = "Catalog Image Sync";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Image Downloads", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(max, progress, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            return new ForegroundInfo(NOTIFICATION_ID, builder.build());
        }
    }
}