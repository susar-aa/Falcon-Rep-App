package com.example.falconrep;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.falconrep.models.Category;
import com.example.falconrep.models.Product;
import com.example.falconrep.models.Variation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.example.falconrep.BuildConfig;

public class SyncWorker extends Worker {

    private static final String BASE_URL = "https://falconstationery.com/wp-json/wc/v3/";
    private static final String TAG = "FalconSync";
    private static final int CONNECTION_TIMEOUT_MS = 45000;

    private final DatabaseHelper dbHelper;
    private final SharedPreferences prefs;
    private final WooCommerceAPI api;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        dbHelper = new DatabaseHelper(context);
        prefs = context.getSharedPreferences("FalconStorePrefs", Context.MODE_PRIVATE);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(logging)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(request);
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(WooCommerceAPI.class);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Log.d(TAG, "Sync Started...");
            updateProgress("Starting Sync...", 0);

            // 1. Fetch Categories (Fast)
            fetchCategories();

            // 2. Fetch Text Data
            fetchNewAndModifiedProducts();

            // 3. Cleanup
            performZombieCleanup();

            // 4. Download Images
            downloadAllImagesDetailed();

            String currentTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date());
            prefs.edit().putString("LAST_SYNC_DATE", currentTime).apply();

            updateProgress("Sync Complete", 100);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync Crashed", e);
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }
    }

    private void fetchCategories() throws IOException {
        updateProgress("Fetching Categories...", 5);
        Response<List<Category>> response = api.fetchCategories(
                BuildConfig.WC_KEY, BuildConfig.WC_SECRET, 100, true).execute();

        if (response.isSuccessful() && response.body() != null) {
            for (Category c : response.body()) {
                dbHelper.upsertCategory(c);
            }
        }
    }

    private void fetchNewAndModifiedProducts() throws IOException {
        String lastSync = prefs.getString("LAST_SYNC_DATE", null);
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            updateProgress("Fetching text data (Page " + page + ")...", 10);

            Response<List<Product>> response = api.fetchWooCommerceProducts(
                    BuildConfig.WC_KEY, BuildConfig.WC_SECRET,
                    50, page, "publish", lastSync).execute();

            if (response.isSuccessful() && response.body() != null) {
                List<Product> batch = response.body();
                if (batch.isEmpty()) {
                    hasMore = false;
                } else {
                    for (Product p : batch) {
                        dbHelper.upsertProduct(p);
                        if ("variable".equalsIgnoreCase(p.getType())) {
                            fetchVariationsForProduct(p.getId());
                        }
                    }
                    page++;
                }
            } else {
                hasMore = false;
            }
        }
    }

    private void fetchVariationsForProduct(int productId) throws IOException {
        Response<List<Variation>> response = api.fetchProductVariations(
                productId, BuildConfig.WC_KEY, BuildConfig.WC_SECRET, 100).execute();

        if (response.isSuccessful() && response.body() != null) {
            for (Variation v : response.body()) {
                v.setParentId(productId);
                dbHelper.upsertVariation(v);
            }
        }
    }

    private void performZombieCleanup() throws IOException {
        updateProgress("Cleaning database...", 30);
        List<Integer> serverIds = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            Response<List<Product>> response = api.fetchAllProductIds(
                    BuildConfig.WC_KEY, BuildConfig.WC_SECRET,
                    100, page, "publish", "id").execute();

            if (response.isSuccessful() && response.body() != null) {
                List<Product> batch = response.body();
                if (batch.isEmpty()) {
                    hasMore = false;
                } else {
                    for (Product p : batch) serverIds.add(p.getId());
                    page++;
                }
            } else {
                hasMore = false;
            }
        }

        if (serverIds.isEmpty()) return;

        List<Integer> localIds = dbHelper.getAllLocalProductIds();
        List<Integer> toDelete = new ArrayList<>();
        for (Integer localId : localIds) {
            if (!serverIds.contains(localId)) toDelete.add(localId);
        }
        if (!toDelete.isEmpty()) dbHelper.deleteProducts(toDelete);
    }

    private void downloadAllImagesDetailed() {
        List<Product> allProducts = dbHelper.searchProducts("", 0); // 0 = All Categories
        List<Variation> allVariations = dbHelper.getAllVariations();

        int totalItems = allProducts.size() + allVariations.size();
        int processedCount = 0;

        // 1. Product Images
        for (Product p : allProducts) {
            if (isStopped()) break;
            processedCount++;
            if (processedCount % 2 == 0) {
                int progress = 35 + (int) (((float) processedCount / totalItems) * 65);
                updateProgress("Checking Product " + processedCount + "/" + totalItems, progress);
            }

            List<String> webUrls = p.getWebUrls();
            if (webUrls.isEmpty()) continue;

            List<String> localPaths = new ArrayList<>();
            List<String> existingPaths = p.getLocalPaths();
            boolean changed = false;

            for (int i = 0; i < webUrls.size(); i++) {
                String url = webUrls.get(i);
                String fileName = "img_" + p.getId() + "_" + i + ".jpg";

                if (existingPaths.size() > i) {
                    String oldPath = existingPaths.get(i);
                    if (oldPath != null && new File(oldPath).exists()) {
                        localPaths.add(oldPath);
                        continue;
                    }
                }

                String savedPath = downloadFile(url, fileName);
                if (!savedPath.isEmpty()) {
                    localPaths.add(savedPath);
                    changed = true;
                }
            }

            if (changed || localPaths.size() != existingPaths.size()) {
                p.setLocalPaths(localPaths);
                dbHelper.upsertProduct(p);
            }
        }

        // 2. Variation Images
        for (Variation v : allVariations) {
            if (isStopped()) break;
            processedCount++;
            if (processedCount % 2 == 0) {
                int progress = 35 + (int) (((float) processedCount / totalItems) * 65);
                updateProgress("Checking Variation " + processedCount + "/" + totalItems, progress);
            }

            String url = v.getWebImageUrl();
            if (url == null || url.isEmpty()) continue;

            String currentLocal = v.getLocalImagePath();
            if (currentLocal != null && new File(currentLocal).exists()) continue;

            String fileName = "var_" + v.getParentId() + "_" + v.getId() + ".jpg";
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
            conn.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            conn.setReadTimeout(CONNECTION_TIMEOUT_MS);
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

    private void updateProgress(String status, int percent) {
        setProgressAsync(new Data.Builder()
                .putString("status", status)
                .putInt("progress", percent)
                .build());
    }
}