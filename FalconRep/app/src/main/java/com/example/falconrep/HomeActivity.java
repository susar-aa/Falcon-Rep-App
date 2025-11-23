package com.example.falconrep;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnOpenCatalog = findViewById(R.id.btnOpenCatalog);
        Button btnCategories = findViewById(R.id.btnCategories); // Renamed

        // 1. Open Catalog (All Products)
        btnOpenCatalog.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, MainActivity.class);
            startActivity(intent);
        });

        // 2. Open Categories Screen
        btnCategories.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, CategoryActivity.class);
            startActivity(intent);
        });
    }
}