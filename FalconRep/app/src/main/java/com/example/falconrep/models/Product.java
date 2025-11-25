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

    // NEW FIELD: Stores the exact time the product was last edited (in UTC)
    private String date_modified_gmt;

    private List<Image> images;
    private List<MetaData> meta_data;
    private List<CategoryStub> categories;

    private List<String> localPaths;
    private List<String> webUrls;
    private String localWholesalePrice;
    private String categoryTokens;
    private String displayPrice;

    // --- API CONSTRUCTOR ---
    public Product(int id, String name, String sku, String price, String description, String type, String date_modified_gmt, List<Image> images, List<MetaData> meta_data, List<CategoryStub> categories) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.type = type;
        this.date_modified_gmt = date_modified_gmt;
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
    public Product(int id, String name, String sku, String price, String description, String type, String localPathsStr, String localWholesalePrice, String webUrlsStr, String categoryTokens, String displayPrice) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.description = description;
        this.type = type;
        this.localWholesalePrice = localWholesalePrice;
        this.categoryTokens = categoryTokens;
        this.displayPrice = displayPrice;

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
    public String getPrice() { return cleanPrice(price); }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getDisplayPrice() { return displayPrice; }

    // Getter for the logic
    public String getDateModifiedGmt() { return date_modified_gmt; }

    public String getCategoryTokens() {
        if (categoryTokens != null) return categoryTokens;
        if (categories == null || categories.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (CategoryStub c : categories) {
            sb.append("cat_").append(c.id).append(" ");
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

    // --- B2B PRICE LOGIC ---
    public String getWholesalePrice() {
        if (displayPrice != null && !displayPrice.isEmpty()) return displayPrice;
        if (localWholesalePrice != null && !localWholesalePrice.isEmpty()) return localWholesalePrice;

        if (meta_data == null) return cleanPrice(price);

        for (MetaData meta : meta_data) {
            if (meta.key == null) continue;
            String k = meta.key.toLowerCase();
            String val = String.valueOf(meta.value);
            if (!isValidPrice(val)) continue;

            if (k.contains("b2b") && k.contains("price")) {
                return cleanPrice(val);
            }
        }

        for (MetaData meta : meta_data) {
            if (meta.key == null) continue;
            String k = meta.key.toLowerCase();
            String val = String.valueOf(meta.value);
            if (!isValidPrice(val)) continue;

            if (k.contains("regular")) continue;

            if (k.contains("wholesalex") && k.contains("price")) {
                return cleanPrice(val);
            }
        }

        return cleanPrice(price);
    }

    private boolean isValidPrice(String val) {
        if (val == null || val.isEmpty() || val.equals("0")) return false;
        return val.matches("[0-9.,\\- ]+");
    }

    private String cleanPrice(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]*>", "").trim();
    }

    public void setLocalWholesalePrice(String price) { this.localWholesalePrice = price; }

    public static class Image { String src; }
    public static class MetaData { String key; Object value; }
    public static class CategoryStub { int id; String name; }
}