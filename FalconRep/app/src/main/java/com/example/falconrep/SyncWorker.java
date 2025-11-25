package com.example.falconrep;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
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

public class SyncWorker extends Worker {

    private static final String BASE_URL = "https://falconstationery.com/wp-json/wc/v3/";
    private static final String TAG = "FalconSync";

    private final DatabaseHelper dbHelper;
    private final SharedPreferences prefs;
    private final WooCommerceAPI api;

    private final SimpleDateFormat iso8601Format;
    private String newSyncTime;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        dbHelper = new DatabaseHelper(context);
        prefs = context.getSharedPreferences("FalconStorePrefs", Context.MODE_PRIVATE);

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
                            .header("User-Agent", "FalconRep/1.0")
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

            newSyncTime = iso8601Format.format(new Date());
            Date cutoffDate = getSafeLastSyncDate();

            // 1. Fetch Categories (Now with proper pagination)
            fetchCategories();

            // 2. Fetch Products
            fetchNewAndModifiedProducts(cutoffDate);

            // 3. Cleanup
            performZombieCleanup();

            prefs.edit().putString("LAST_SYNC_DATE", newSyncTime).apply();

            updateProgress("Data Sync Complete", 100);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync Crashed", e);
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }
    }

    private Date getSafeLastSyncDate() {
        if (dbHelper.getProductCount() == 0) return null;
        String storedTime = prefs.getString("LAST_SYNC_DATE", null);
        if (storedTime == null) return null;
        try {
            Date date = iso8601Format.parse(storedTime);
            if (date != null) {
                long bufferedTime = date.getTime() - (10 * 60 * 1000);
                return new Date(bufferedTime);
            }
        } catch (ParseException e) {
            Log.e(TAG, "Date parse error");
        }
        return null;
    }

    // UPDATED: Loops through all category pages and cleans up deleted ones
    private void fetchCategories() throws IOException {
        updateProgress("Fetching Categories...", 10);

        int page = 1;
        boolean hasMore = true;
        boolean syncFailed = false;
        List<Integer> serverCategoryIds = new ArrayList<>();

        while (hasMore) {
            // hide_empty = false: Ensures we update a category even if you just deleted its last product
            Response<List<Category>> response = api.fetchCategories(
                    BuildConfig.WC_KEY, BuildConfig.WC_SECRET, 100, page, false).execute();

            if (response.isSuccessful() && response.body() != null) {
                List<Category> batch = response.body();
                if (batch.isEmpty()) {
                    hasMore = false;
                } else {
                    for (Category c : batch) {
                        dbHelper.upsertCategory(c);
                        serverCategoryIds.add(c.getId());
                    }
                    page++;
                }
            } else {
                Log.e(TAG, "Category Sync Error: " + response.code());
                hasMore = false;
                syncFailed = true;
            }
        }

        // Cleanup: Delete local categories that are no longer on server
        // Only run if sync was fully successful to prevent accidental wipes on network error
        if (!syncFailed && !serverCategoryIds.isEmpty()) {
            List<Category> localCats = dbHelper.getAllCategories();
            List<Integer> toDelete = new ArrayList<>();

            for (Category c : localCats) {
                if (!serverCategoryIds.contains(c.getId())) {
                    toDelete.add(c.getId());
                }
            }

            if (!toDelete.isEmpty()) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                String args = TextUtils.join(", ", toDelete);
                // Note: 'categories' table and 'cat_id' column names must match DatabaseHelper
                db.execSQL("DELETE FROM categories WHERE cat_id IN (" + args + ")");
            }
        }
    }

    private void fetchNewAndModifiedProducts(Date cutoffDate) throws IOException {
        int page = 1;
        boolean hasMore = true;
        boolean stopFetching = false;
        long cutoffTime = (cutoffDate != null) ? cutoffDate.getTime() : 0;

        while (hasMore && !stopFetching) {
            updateProgress("Syncing Products (Page " + page + ")...", 30);

            Response<List<Product>> response = api.fetchWooCommerceProducts(
                    BuildConfig.WC_KEY, BuildConfig.WC_SECRET,
                    50, page, "publish", "modified", "desc").execute();

            if (response.isSuccessful() && response.body() != null) {
                List<Product> batch = response.body();
                if (batch.isEmpty()) {
                    hasMore = false;
                } else {
                    for (Product p : batch) {
                        if (cutoffTime > 0 && p.getDateModifiedGmt() != null) {
                            try {
                                Date pDate = iso8601Format.parse(p.getDateModifiedGmt());
                                if (pDate != null && pDate.getTime() < cutoffTime) {
                                    stopFetching = true;
                                    break;
                                }
                            } catch (ParseException e) {}
                        }
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
        String storedTime = prefs.getString("LAST_SYNC_DATE", null);
        if (storedTime != null) return;

        updateProgress("Cleaning up deleted items...", 90);
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