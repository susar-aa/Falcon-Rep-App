package com.example.falconrep;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.example.falconrep.models.Category;
import com.example.falconrep.models.Product;
import com.example.falconrep.models.Variation;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WooStore.db";
    // FIX: Bump to 19. This is higher than 18, so it will trigger an upgrade (clean reset).
    private static final int DATABASE_VERSION = 19;

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_VARIATIONS = "variations";
    private static final String TABLE_CATEGORIES = "categories";

    // Product Cols
    private static final String COL_NAME = "name";
    private static final String COL_PRICE = "price";
    private static final String COL_DESC = "description";
    private static final String COL_LOCAL_PATHS = "local_image_paths";
    private static final String COL_WHOLESALE_PRICE = "wholesale_price";
    private static final String COL_SKU = "sku";
    private static final String COL_WEB_URLS = "web_image_urls";
    private static final String COL_TYPE = "product_type";
    private static final String COL_CAT_TOKENS = "cat_tokens";
    private static final String COL_DISPLAY_PRICE = "display_price";
    private static final String COL_NEEDS_IMG_SYNC = "needs_img_sync";

    // Variation Cols
    private static final String COL_VAR_ID = "var_id";
    private static final String COL_PARENT_ID = "parent_id";
    private static final String COL_VAR_PRICE = "price";
    private static final String COL_VAR_ATTR = "attributes";
    private static final String COL_VAR_IMG_WEB = "web_image";
    private static final String COL_VAR_IMG_LOCAL = "local_image";
    private static final String COL_VAR_NEEDS_IMG_SYNC = "var_needs_img_sync";

    // Category Cols
    private static final String COL_CAT_ID = "cat_id";
    private static final String COL_CAT_NAME = "cat_name";
    private static final String COL_CAT_SLUG = "cat_slug";
    private static final String COL_CAT_COUNT = "cat_count";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createProducts = "CREATE VIRTUAL TABLE " + TABLE_PRODUCTS + " USING fts4(" +
                COL_NAME + ", " +
                COL_PRICE + ", " +
                COL_WHOLESALE_PRICE + ", " +
                COL_DESC + ", " +
                COL_LOCAL_PATHS + ", " +
                COL_SKU + ", " +
                COL_WEB_URLS + ", " +
                COL_TYPE + ", " +
                COL_CAT_TOKENS + ", " +
                COL_DISPLAY_PRICE + ", " +
                COL_NEEDS_IMG_SYNC +
                ")";
        db.execSQL(createProducts);

        String createVariations = "CREATE TABLE " + TABLE_VARIATIONS + "(" +
                COL_VAR_ID + " INTEGER PRIMARY KEY, " +
                COL_PARENT_ID + " INTEGER, " +
                COL_VAR_PRICE + " TEXT, " +
                COL_VAR_ATTR + " TEXT, " +
                COL_VAR_IMG_WEB + " TEXT, " +
                COL_VAR_IMG_LOCAL + " TEXT, " +
                COL_VAR_NEEDS_IMG_SYNC + " INTEGER" +
                ")";
        db.execSQL(createVariations);

        String createCats = "CREATE TABLE " + TABLE_CATEGORIES + "(" +
                COL_CAT_ID + " INTEGER PRIMARY KEY, " +
                COL_CAT_NAME + " TEXT, " +
                COL_CAT_SLUG + " TEXT, " +
                COL_CAT_COUNT + " INTEGER" +
                ")";
        db.execSQL(createCats);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older tables if they exist
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VARIATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
        // Recreate tables
        onCreate(db);
    }

    // FIX: Handle downgrades gracefully by resetting the DB instead of crashing
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    // --- PRODUCTS ---
    public void upsertProduct(Product p) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("docid", p.getId());
        values.put(COL_NAME, p.getName());
        values.put(COL_PRICE, p.getPrice());
        values.put(COL_WHOLESALE_PRICE, p.getWholesalePrice());
        values.put(COL_DESC, p.getDescription());
        values.put(COL_SKU, p.getSku() != null ? p.getSku() : "");
        values.put(COL_LOCAL_PATHS, p.getLocalPathsString());
        values.put(COL_WEB_URLS, p.getWebUrlsString());
        values.put(COL_TYPE, p.getType());
        values.put(COL_CAT_TOKENS, p.getCategoryTokens());

        if (p.getDisplayPrice() != null) {
            values.put(COL_DISPLAY_PRICE, p.getDisplayPrice());
        } else {
            values.put(COL_DISPLAY_PRICE, p.getWholesalePrice());
        }

        values.put(COL_NEEDS_IMG_SYNC, "1");

        db.replace(TABLE_PRODUCTS, null, values);
    }

    public List<Product> getProductsNeedingImageSync() {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT docid, * FROM " + TABLE_PRODUCTS + " WHERE " + COL_NEEDS_IMG_SYNC + "='1'", null);
        if (cursor.moveToFirst()) {
            do { list.add(cursorToProduct(cursor)); } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public void markProductImageSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NEEDS_IMG_SYNC, "0");
        db.update(TABLE_PRODUCTS, values, "docid=?", new String[]{String.valueOf(id)});
    }

    // --- VARIATIONS ---
    public void upsertVariation(Variation v) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_VAR_ID, v.getId());
        values.put(COL_PARENT_ID, v.getParentId());
        values.put(COL_VAR_PRICE, v.getPrice());
        values.put(COL_VAR_ATTR, v.getAttributesString());
        values.put(COL_VAR_IMG_WEB, v.getWebImageUrl());
        values.put(COL_VAR_IMG_LOCAL, v.getLocalImagePath());
        values.put(COL_VAR_NEEDS_IMG_SYNC, 1);

        db.replace(TABLE_VARIATIONS, null, values);
    }

    public List<Variation> getVariationsNeedingImageSync() {
        List<Variation> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_VARIATIONS + " WHERE " + COL_VAR_NEEDS_IMG_SYNC + "=1", null);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_VAR_ID));
                int parentId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_PARENT_ID));
                String price = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_PRICE));
                String attrs = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_ATTR));
                String webUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_IMG_WEB));
                String localPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_IMG_LOCAL));
                list.add(new Variation(id, parentId, price, attrs, localPath, webUrl));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public void markVariationImageSynced(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_VAR_NEEDS_IMG_SYNC, 0);
        db.update(TABLE_VARIATIONS, values, COL_VAR_ID + "=?", new String[]{String.valueOf(id)});
    }

    // --- CATEGORIES & HELPERS ---
    public void upsertCategory(Category c) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CAT_ID, c.getId());
        values.put(COL_CAT_NAME, c.getName());
        values.put(COL_CAT_SLUG, c.getSlug());
        values.put(COL_CAT_COUNT, c.getCount());
        db.replace(TABLE_CATEGORIES, null, values);
    }

    public List<Category> getAllCategories() {
        List<Category> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_CATEGORIES + " ORDER BY " + COL_CAT_NAME + " ASC", null);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_NAME));
                String slug = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_SLUG));
                int count = cursor.getInt(cursor.getColumnIndexOrThrow(COL_CAT_COUNT));
                list.add(new Category(id, name, slug, count));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    public void updateProductDisplayPrice(int productId, String priceRange) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DISPLAY_PRICE, priceRange);
        values.put(COL_NEEDS_IMG_SYNC, "1");
        db.update(TABLE_PRODUCTS, values, "docid=?", new String[]{String.valueOf(productId)});
    }

    public List<Product> searchProducts(String keyword, int categoryId) {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor;
        String searchText = (keyword == null) ? "" : keyword.trim();
        StringBuilder matchQuery = new StringBuilder();
        if (!searchText.isEmpty()) matchQuery.append(searchText).append("*");
        if (categoryId > 0) {
            if (matchQuery.length() > 0) matchQuery.append(" ");
            matchQuery.append("cat_").append(categoryId);
        }
        if (matchQuery.length() == 0) cursor = db.rawQuery("SELECT docid, * FROM " + TABLE_PRODUCTS, null);
        else cursor = db.rawQuery("SELECT docid, * FROM " + TABLE_PRODUCTS + " WHERE " + TABLE_PRODUCTS + " MATCH ?", new String[]{matchQuery.toString()});
        if (cursor.moveToFirst()) do { list.add(cursorToProduct(cursor)); } while (cursor.moveToNext());
        cursor.close();
        return list;
    }

    // Compatibility methods
    public List<Variation> getAllVariations() {
        return getVariationsNeedingImageSync();
    }

    public Product getProductById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT docid, * FROM " + TABLE_PRODUCTS + " WHERE docid=?", new String[]{String.valueOf(id)});
        Product p = null;
        if (cursor.moveToFirst()) p = cursorToProduct(cursor);
        cursor.close();
        return p;
    }

    public List<Integer> getAllLocalProductIds() {
        List<Integer> ids = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT docid FROM " + TABLE_PRODUCTS, null);
        if (cursor.moveToFirst()) { do { ids.add(cursor.getInt(0)); } while (cursor.moveToNext()); }
        cursor.close();
        return ids;
    }

    public void deleteProducts(List<Integer> idsToDelete) {
        if (idsToDelete == null || idsToDelete.isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        String args = TextUtils.join(", ", idsToDelete);
        db.execSQL("DELETE FROM " + TABLE_PRODUCTS + " WHERE docid IN (" + args + ")");
        db.execSQL("DELETE FROM " + TABLE_VARIATIONS + " WHERE " + COL_PARENT_ID + " IN (" + args + ")");
    }

    private Product cursorToProduct(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow("docid"));
        String name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME));
        String price = cursor.getString(cursor.getColumnIndexOrThrow(COL_PRICE));
        String wholesale = cursor.getString(cursor.getColumnIndexOrThrow(COL_WHOLESALE_PRICE));
        String desc = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESC));
        String sku = cursor.getString(cursor.getColumnIndexOrThrow(COL_SKU));
        String localPaths = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_PATHS));
        String webUrls = cursor.getString(cursor.getColumnIndexOrThrow(COL_WEB_URLS));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(COL_TYPE));
        String catTokens = cursor.getString(cursor.getColumnIndexOrThrow(COL_CAT_TOKENS));
        String displayPrice = cursor.getString(cursor.getColumnIndexOrThrow(COL_DISPLAY_PRICE));
        return new Product(id, name, sku, price, desc, type, localPaths, wholesale, webUrls, catTokens, displayPrice);
    }

    public int getProductCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRODUCTS, null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public int getOfflineReadyCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_PRODUCTS + " WHERE " + COL_LOCAL_PATHS + " IS NOT NULL AND " + COL_LOCAL_PATHS + " != ''", null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
    }

    public List<Variation> getVariationsForProduct(int parentId) {
        List<Variation> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_VARIATIONS + " WHERE " + COL_PARENT_ID + "=?", new String[]{String.valueOf(parentId)});
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_VAR_ID));
                String price = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_PRICE));
                String attrs = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_ATTR));
                String webUrl = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_IMG_WEB));
                String localPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_VAR_IMG_LOCAL));
                list.add(new Variation(id, parentId, price, attrs, localPath, webUrl));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }
}