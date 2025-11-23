package com.example.falconrep.models;

import java.util.List;

public class Variation {
    private int id;
    private int parent_id; // Links back to the main Product
    private String price;
    private String regular_price;
    private String sale_price;
    private List<Attribute> attributes; // "Size: XL", "Color: Blue"
    private Product.Image image; // Specific image for this variation

    // Local Storage fields
    private String localImagePath;
    private String webImageUrl;
    private String attributesString; // Flattened string for DB

    // Constructor for API
    public Variation(int id, String price, List<Attribute> attributes, Product.Image image) {
        this.id = id;
        this.price = price;
        this.attributes = attributes;
        this.image = image;
    }

    // Constructor for DB
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
    public String getPrice() { return price; }

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