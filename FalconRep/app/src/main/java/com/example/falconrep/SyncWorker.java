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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
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

    private final DatabaseHelper dbHelper;
    private final SharedPreferences prefs;
    private final WooCommerceAPI api;

    // Format for WooCommerce (UTC)
    private final SimpleDateFormat iso8601Format;

    private String newSyncTime;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        dbHelper = new DatabaseHelper(context);
        prefs = context.getSharedPreferences("FalconStorePrefs", Context.MODE_PRIVATE);

        // FIX: Use UTC Timezone for all dates to match Server Time
        iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
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
            updateProgress("Checking for updates...", 0);

            // 1. Capture Current Time (UTC) BEFORE Fetching
            newSyncTime = iso8601Format.format(new Date());

            // 2. Prepare "After" Parameter (Safely)
            String lastSyncParam = getSafeLastSyncTime();

            // 3. Execute Sync
            fetchCategories();
            fetchNewAndModifiedProducts(lastSyncParam);
            performZombieCleanup();

            // 4. Save New Time ONLY if successful
            prefs.edit().putString("LAST_SYNC_DATE", newSyncTime).apply();

            updateProgress("Data Sync Complete", 100);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync Crashed", e);
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }
    }

    private String getSafeLastSyncTime() {
        // FIX: If DB is empty (fresh install or version upgrade), force FULL SYNC
        if (dbHelper.getProductCount() == 0) {
            Log.d(TAG, "DB Empty: Forcing Full Sync");
            return null;
        }

        String storedTime = prefs.getString("LAST_SYNC_DATE", null);
        if (storedTime == null) return null;

        try {
            // FIX: Subtract 10 Minutes buffer to handle clock skew or slow server updates
            Date date = iso8601Format.parse(storedTime);
            if (date != null) {
                long bufferedTime = date.getTime() - (10 * 60 * 1000); // -10 Minutes
                return iso8601Format.format(new Date(bufferedTime));
            }
        } catch (ParseException e) {
            Log.e(TAG, "Date parse error, forcing full sync");
        }
        return null;
    }

    private void fetchCategories() throws IOException {
        updateProgress("Fetching Categories...", 10);
        Response<List<Category>> response = api.fetchCategories(
                BuildConfig.WC_KEY, BuildConfig.WC_SECRET, 100, true).execute();
        if (response.isSuccessful() && response.body() != null) {
            for (Category c : response.body()) dbHelper.upsertCategory(c);
        }
    }

    private void fetchNewAndModifiedProducts(String afterDate) throws IOException {
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            updateProgress("Downloading Products (Page " + page + ")...", 30);
            Log.d(TAG, "Fetching products after: " + afterDate + " | Page: " + page);

            Response<List<Product>> response = api.fetchWooCommerceProducts(
                    BuildConfig.WC_KEY, BuildConfig.WC_SECRET,
                    50, page, "publish", afterDate).execute();

            if (response.isSuccessful() && response.body() != null) {
                List<Product> batch = response.body();
                if (batch.isEmpty()) {
                    hasMore = false;
                } else {
                    for (Product p : batch) {
                        // Upsert flags product as dirty (needs_img_sync = 1)
                        dbHelper.upsertProduct(p);
                        if ("variable".equalsIgnoreCase(p.getType())) {
                            fetchVariationsForProduct(p.getId());
                        }
                    }
                    page++;
                }
            } else {
                Log.e(TAG, "API Error: " + response.code());
                hasMore = false;
            }
        }
    }

    private void fetchVariationsForProduct(int productId) throws IOException {
        Response<List<Variation>> response = api.fetchProductVariations(
                productId, BuildConfig.WC_KEY, BuildConfig.WC_SECRET, 100).execute();

        if (response.isSuccessful() && response.body() != null) {
            List<Double> prices = new ArrayList<>();

            for (Variation v : response.body()) {
                v.setParentId(productId);
                dbHelper.upsertVariation(v);
                try {
                    String pStr = v.getPrice().replace(",", "").trim();
                    if (!pStr.isEmpty()) prices.add(Double.parseDouble(pStr));
                } catch (NumberFormatException e) {}
            }

            if (!prices.isEmpty()) {
                double min = Collections.min(prices);
                double max = Collections.max(prices);
                String range = (Math.abs(max - min) < 0.01)
                        ? String.format(Locale.US, "%.2f", min)
                        : String.format(Locale.US, "%.2f - %.2f", min, max);
                dbHelper.updateProductDisplayPrice(productId, range);
            }
        }
    }

    private void performZombieCleanup() throws IOException {
        updateProgress("Finalizing...", 90);
        List<Integer> serverIds = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            Response<List<Product>> response = api.fetchAllProductIds(
                    BuildConfig.WC_KEY, BuildConfig.WC_SECRET,
                    100, page, "publish", "id").execute();

            if (response.isSuccessful() && response.body() != null) {
                List<Product> batch = response.body();
                if (batch.isEmpty()) hasMore = false;
                else { for (Product p : batch) serverIds.add(p.getId()); page++; }
            } else hasMore = false;
        }

        if (serverIds.isEmpty()) return;
        List<Integer> localIds = dbHelper.getAllLocalProductIds();
        List<Integer> toDelete = new ArrayList<>();
        for (Integer localId : localIds) {
            if (!serverIds.contains(localId)) toDelete.add(localId);
        }
        if (!toDelete.isEmpty()) dbHelper.deleteProducts(toDelete);
    }

    private void updateProgress(String status, int percent) {
        setProgressAsync(new Data.Builder()
                .putString("status", status)
                .putInt("progress", percent)
                .build());
    }
}