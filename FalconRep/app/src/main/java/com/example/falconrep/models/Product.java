package com.example.falconrep.models;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Product {
    private int id;
    private String name;
    private String sku;
    private String price;
    private String description;
    private String type;
    private List<Image> images;
    private List<MetaData> meta_data;
    private List<CategoryStub> categories;

    private List<String> localPaths;
    private List<String> webUrls;
    private String localWholesalePrice;

    private String categoryTokens;

    // --- API CONSTRUCTOR ---
    public Product(int id, String name, String sku, String price, String description, String type, List<Image> images, List<MetaData> meta_data, List<CategoryStub> categories) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.type = type;
        this.images = images;
        this.meta_data = meta_data;
        this.categories = categories;

        this.webUrls = new ArrayList<>();
        if (images != null) {
            for (Image img : images) {
                if (img.src != null) this.webUrls.add(img.src);
            }
        }
    }

    // --- DB CONSTRUCTOR ---
    public Product(int id, String name, String sku, String price, String description, String type, String localPathsStr, String localWholesalePrice, String webUrlsStr, String categoryTokens) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.type = type;
        this.localWholesalePrice = localWholesalePrice;
        this.categoryTokens = categoryTokens;

        this.localPaths = new ArrayList<>();
        if (localPathsStr != null && !localPathsStr.isEmpty()) {
            this.localPaths.addAll(Arrays.asList(localPathsStr.split("###")));
        }

        this.webUrls = new ArrayList<>();
        if (webUrlsStr != null && !webUrlsStr.isEmpty()) {
            this.webUrls.addAll(Arrays.asList(webUrlsStr.split("###")));
        }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getPrice() { return price; }
    public String getDescription() { return description; }
    public String getType() { return type; }

    // FIX: Append Category Names to the search token string
    // Now FTS can search for "Arts" and find products in that category
    public String getCategoryTokens() {
        if (categoryTokens != null) return categoryTokens;

        if (categories == null || categories.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CategoryStub c : categories) {
            // Token for filtering
            sb.append("cat_").append(c.id).append(" ");
            // Name for searching (e.g. "Arts", "Crafts")
            if (c.name != null) sb.append(c.name).append(" ");
        }
        return sb.toString().trim();
    }

    public List<String> getWebUrls() {
        if ((webUrls == null || webUrls.isEmpty()) && images != null) {
            webUrls = new ArrayList<>();
            for (Image img : images) webUrls.add(img.src);
        }
        return webUrls != null ? webUrls : new ArrayList<>();
    }

    public List<String> getLocalPaths() { return localPaths != null ? localPaths : new ArrayList<>(); }
    public void setLocalPaths(List<String> paths) { this.localPaths = paths; }

    public String getLocalPathsString() {
        if (localPaths == null || localPaths.isEmpty()) return "";
        return TextUtils.join("###", localPaths);
    }

    public String getWebUrlsString() {
        List<String> urls = getWebUrls();
        if (urls.isEmpty()) return "";
        return TextUtils.join("###", urls);
    }

    public String getFirstImageLocalPath() {
        if (localPaths != null && !localPaths.isEmpty()) return localPaths.get(0);
        return null;
    }

    public String getFirstImageWebUrl() {
        List<String> urls = getWebUrls();
        if (!urls.isEmpty()) return urls.get(0);
        return null;
    }

    public String getWholesalePrice() {
        if (localWholesalePrice != null && !localWholesalePrice.isEmpty()) return localWholesalePrice;
        if (meta_data != null) {
            for (MetaData meta : meta_data) {
                if (meta.key == null) continue;
                String k = meta.key.toLowerCase();
                String val = String.valueOf(meta.value);
                if (val == null || val.isEmpty() || val.equals("0")) continue;
                if (k.contains("wholesalex") && k.contains("price")) return val;
            }
        }
        return price;
    }

    public void setLocalWholesalePrice(String price) { this.localWholesalePrice = price; }

    public String getDebugMetaKeys() {
        return "SKU: " + sku + "\nCats: " + getCategoryTokens();
    }

    public static class Image { String src; }
    public static class MetaData { String key; Object value; }
    public static class CategoryStub { int id; String name; }
}