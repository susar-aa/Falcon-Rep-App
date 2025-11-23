package com.example.falconrep.models;

public class Category {
    private int id;
    private String name;
    private String slug;
    private int count; // Number of products in this category

    // Constructor for API & DB
    public Category(int id, String name, String slug, int count) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.count = count;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public int getCount() { return count; }

    @Override
    public String toString() { return name; }
}