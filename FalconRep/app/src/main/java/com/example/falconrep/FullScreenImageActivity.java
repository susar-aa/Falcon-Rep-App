package com.example.falconrep;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.io.File;

public class FullScreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "image_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_image);

        ImageView fullScreenImage = findViewById(R.id.fullScreenImage);
        ImageButton btnClose = findViewById(R.id.btnClose);

        String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);

        if (imagePath != null) {
            if (imagePath.startsWith("http")) {
                // Load from URL
                Glide.with(this).load(imagePath).into(fullScreenImage);
            } else {
                // Load from local file
                Glide.with(this).load(new File(imagePath)).into(fullScreenImage);
            }
        }

        btnClose.setOnClickListener(v -> finish());

        // Hide system UI for a true full-screen experience
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }
}