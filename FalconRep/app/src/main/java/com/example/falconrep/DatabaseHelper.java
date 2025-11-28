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
import com.example.falconrep.utils.SearchUtils;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "WooStore.db";
    // BUMPED VERSION to 21 to force table recreation with new token format
    private static final int DATABASE_VERSION = 21;

    private static final String TABLE_PRODUCTS = "products";
    private static final String TABLE_VARIATIONS = "variations";
    private static final String TABLE_CATEGORIES = "categories";

    // Product Cols
    private static final String COL_DOCID = "docid";
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
    private static final String COL_SEARCH_TOKENS = "search_tokens";

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
                COL_NEEDS_IMG_SYNC + ", " +
                COL_SEARCH_TOKENS +
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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VARIATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CATEGORIES);
        onCreate(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) db.enableWriteAheadLogging();
    }

    // --- PRODUCTS ---
    public void upsertProduct(Product p) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DOCID, p.getId());
        values.put(COL_NAME, p.getName());
        values.put(COL_PRICE, p.getPrice());
        values.put(COL_WHOLESALE_PRICE, p.getWholesalePrice());
        values.put(COL_DESC, p.getDescription());
        values.put(COL_SKU, p.getSku() != null ? p.getSku() : "");

        String pathsToSave = p.getLocalPathsString();
        if (TextUtils.isEmpty(pathsToSave)) {
            Cursor cursor = db.rawQuery("SELECT " + COL_LOCAL_PATHS + " FROM " + TABLE_PRODUCTS + " WHERE docid=?", new String[]{String.valueOf(p.getId())});
            if (cursor.moveToFirst()) {
                String existing = cursor.getString(0);
                if (!TextUtils.isEmpty(existing)) {
                    pathsToSave = existing;
                }
            }
            cursor.close();
        }
        values.put(COL_LOCAL_PATHS, pathsToSave);

        values.put(COL_WEB_URLS, p.getWebUrlsString());
        values.put(COL_TYPE, p.getType());
        values.put(COL_CAT_TOKENS, p.getCategoryTokens());
        values.put(COL_DISPLAY_PRICE, p.getDisplayPrice() != null ? p.getDisplayPrice() : p.getWholesalePrice());

        String fuzzyTokens = SearchUtils.generateSearchTokens(p.getName(), p.getSku(), p.getCategoryTokens());
        values.put(COL_SEARCH_TOKENS, fuzzyTokens);

        values.put(COL_NEEDS_IMG_SYNC, "1");
        db.replace(TABLE_PRODUCTS, null, values);
    }

    public void updateLocalImagePaths(int productId, String serializedPaths) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_LOCAL_PATHS, serializedPaths);
        db.update(TABLE_PRODUCTS, values, "docid = ?", new String[]{String.valueOf(productId)});
    }

    public void updateProductDisplayPrice(int productId, String priceRange) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_DISPLAY_PRICE, priceRange);
        db.update(TABLE_PRODUCTS, values, "docid=?", new String[]{String.valueOf(productId)});
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

        String pathToSave = v.getLocalImagePath();
        if (TextUtils.isEmpty(pathToSave)) {
            Cursor cursor = db.rawQuery("SELECT " + COL_VAR_IMG_LOCAL + " FROM " + TABLE_VARIATIONS + " WHERE " + COL_VAR_ID + "=?", new String[]{String.valueOf(v.getId())});
            if (cursor.moveToFirst()) {
                String existing = cursor.getString(0);
                if (!TextUtils.isEmpty(existing)) {
                    pathToSave = existing;
                }
            }
            cursor.close();
        }
        values.put(COL_VAR_IMG_LOCAL, pathToSave);

        values.put(COL_VAR_NEEDS_IMG_SYNC, 1);
        db.replace(TABLE_VARIATIONS, null, values);
    }

    public void updateVariationImagePath(int varId, String path) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_VAR_IMG_LOCAL, path);
        db.update(TABLE_VARIATIONS, values, COL_VAR_ID + "=?", new String[]{String.valueOf(varId)});
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

    // --- CATEGORIES ---
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

    // --- SEARCH (ENHANCED) ---
    public List<Product> searchProducts(String userQuery, int categoryId) {
        List<Product> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            // 1. Normalize Query
            String fuzzyQuery = SearchUtils.normalizeQuery(userQuery);

            // FIX: Replace explicit " AND " with implicit " " to prevent FTS4 literal match issues
            fuzzyQuery = fuzzyQuery.replace(" AND ", " ");

            StringBuilder matchQuery = new StringBuilder();

            // 2. Build Strict Query (Implicit AND)
            if (categoryId > 0) {
                matchQuery.append("category").append(categoryId);
                if (!fuzzyQuery.isEmpty()) {
                    matchQuery.append(" "); // Implicit AND
                }
            }

            if (!fuzzyQuery.isEmpty()) {
                matchQuery.append(fuzzyQuery);
            }

            String orderBy = " ORDER BY " + COL_NAME + " COLLATE NOCASE ASC";

            // 3. Execute Strict Search
            if (matchQuery.length() > 0) {
                String sql = "SELECT docid, * FROM " + TABLE_PRODUCTS + " WHERE " + TABLE_PRODUCTS + " MATCH ?" + orderBy;
                cursor = db.rawQuery(sql, new String[]{matchQuery.toString()});

                if (cursor.moveToFirst()) {
                    do {
                        list.add(cursorToProduct(cursor));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            } else {
                // Empty query fallback
                cursor = db.rawQuery("SELECT docid, * FROM " + TABLE_PRODUCTS + orderBy + " LIMIT 100", null);
                if (cursor.moveToFirst()) {
                    do { list.add(cursorToProduct(cursor)); } while (cursor.moveToNext());
                }
                cursor.close();
                return list;
            }

            // 4. FALLBACK: Relaxed Search (OR Logic)
            // If strict search failed to find results, try to find matches for ANY of the words.
            // Example: User types "Blue Water Bottle" -> Strict fails -> Relaxed finds "Water Bottle"
            if (list.isEmpty() && !fuzzyQuery.isEmpty() && fuzzyQuery.contains(") (")) {

                String relaxedQuery;
                if (categoryId > 0) {
                    // Enforce category strictly, but relax the user terms
                    // Format: categoryID ((A) OR (B))
                    String userPartRelaxed = "(" + fuzzyQuery.replace(") (", ") OR (") + ")";
                    relaxedQuery = "category" + categoryId + " " + userPartRelaxed;
                } else {
                    relaxedQuery = fuzzyQuery.replace(") (", ") OR (");
                }

                String sql = "SELECT docid, * FROM " + TABLE_PRODUCTS + " WHERE " + TABLE_PRODUCTS + " MATCH ?" + orderBy;
                cursor = db.rawQuery(sql, new String[]{relaxedQuery});

                if (cursor.moveToFirst()) {
                    do {
                        list.add(cursorToProduct(cursor));
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (cursor != null && !cursor.isClosed()) cursor.close();
        }

        return list;
    }

    public Product getProductById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT docid, * FROM " + TABLE_PRODUCTS + " WHERE docid=?", new String[]{String.valueOf(id)});
        Product p = null;
        if (cursor.moveToFirst()) p = cursorToProduct(cursor);
        cursor.close();
        return p;
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
        String sql = "SELECT COUNT(*) FROM " + TABLE_PRODUCTS +
                " WHERE " + COL_LOCAL_PATHS + " IS NOT NULL " +
                " AND " + COL_LOCAL_PATHS + " != ''";
        Cursor cursor = db.rawQuery(sql, null);
        int count = 0;
        if (cursor.moveToFirst()) count = cursor.getInt(0);
        cursor.close();
        return count;
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
}