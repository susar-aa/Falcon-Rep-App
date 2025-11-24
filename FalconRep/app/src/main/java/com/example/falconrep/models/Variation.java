package com.example.falconrep.models;

import java.util.List;

public class Variation {
    private int id;
    private int parent_id;
    private String price;
    private String regular_price;
    private String sale_price;
    private List<Attribute> attributes;
    private Product.Image image;
    private List<Product.MetaData> meta_data;

    private String localImagePath;
    private String webImageUrl;
    private String attributesString;

    public Variation(int id, String price, List<Attribute> attributes, Product.Image image, List<Product.MetaData> meta_data) {
        this.id = id;
        this.price = price;
        this.attributes = attributes;
        this.image = image;
        this.meta_data = meta_data;
    }

    public Variation(int id, int parent_id, String price, String attributesString, String localImagePath, String webImageUrl) {
        this.id = id;
        this.parent_id = parent_id;
        this.price = price;
        this.attributesString = attributesString;
        this.localImagePath = localImagePath;
        this.webImageUrl = webImageUrl;
    }

    public int getId() { return id; }
    public int getParentId() { return parent_id; }
    public void setParentId(int parent_id) { this.parent_id = parent_id; }

    public String getPrice() {
        if (meta_data == null) return cleanPrice(price);

        // PASS 1: B2B Specific Keys
        for (Product.MetaData meta : meta_data) {
            if (meta.key == null) continue;
            String k = meta.key.toLowerCase();
            String val = String.valueOf(meta.value);
            if (!isValidPrice(val)) continue;

            if (k.contains("b2b") && k.contains("price")) {
                return cleanPrice(val);
            }
        }

        // PASS 2: General Wholesale Keys
        for (Product.MetaData meta : meta_data) {
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

    public String getAttributesString() {
        if (attributesString != null) return attributesString;
        if (attributes == null || attributes.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Attribute a : attributes) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(a.name).append(": ").append(a.option);
        }
        return sb.toString();
    }

    public String getWebImageUrl() {
        if (webImageUrl != null) return webImageUrl;
        if (image != null) return image.src;
        return null;
    }

    public String getLocalImagePath() { return localImagePath; }
    public void setLocalImagePath(String path) { this.localImagePath = path; }

    public static class Attribute {
        String name;
        String option;
    }
}