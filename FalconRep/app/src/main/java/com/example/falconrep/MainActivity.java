package com.example.falconrep;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.falconrep.models.Category;
import com.example.falconrep.models.Product;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView, rvCategories;
    private ProductAdapter adapter;
    private CategoryAdapter catAdapter;

    private List<Product> productList;
    private List<Category> categoryList;

    private DatabaseHelper dbHelper;
    private SearchView searchView;
    private TextView txtOfflineCount, txtLoadedCount;

    private Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private List<Product> sessionMasterList;
    private int selectedCategoryId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        // Check for incoming Category Filter
        selectedCategoryId = getIntent().getIntExtra("SELECTED_CAT_ID", 0);

        recyclerView = findViewById(R.id.recyclerView);
        rvCategories = findViewById(R.id.rvCategories);
        searchView = findViewById(R.id.searchView);
        txtOfflineCount = findViewById(R.id.txtOfflineCount);
        txtLoadedCount = findViewById(R.id.txtLoadedCount);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        productList = new ArrayList<>();
        adapter = new ProductAdapter(productList);
        recyclerView.setAdapter(adapter);

        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryList = new ArrayList<>();
        catAdapter = new CategoryAdapter(categoryList);
        rvCategories.setAdapter(catAdapter);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { performSearch(query); return true; }
            @Override public boolean onQueryTextChange(String newText) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch(newText);
                searchHandler.postDelayed(searchRunnable, 300);
                return true;
            }
        });

        loadCategories();

        // Initial Search
        performSearch("");
    }

    private void loadCategories() {
        new Thread(() -> {
            List<Category> cats = dbHelper.getAllCategories();
            cats.add(0, new Category(0, "All Products", "all", 0));

            runOnUiThread(() -> {
                categoryList.clear();
                categoryList.addAll(cats);
                catAdapter.notifyDataSetChanged();

                // Scroll to selected category if one was passed
                if (selectedCategoryId > 0) {
                    for(int i=0; i<categoryList.size(); i++) {
                        if(categoryList.get(i).getId() == selectedCategoryId) {
                            rvCategories.scrollToPosition(i);
                            break;
                        }
                    }
                }
            });
        }).start();
    }

    private void performSearch(String query) {
        new Thread(() -> {
            final List<Product> results;

            if (selectedCategoryId > 0) {
                results = dbHelper.searchProducts(query, selectedCategoryId);
            } else {
                if (query == null || query.trim().isEmpty()) {
                    if (sessionMasterList == null || sessionMasterList.isEmpty()) {
                        sessionMasterList = dbHelper.searchProducts("", 0);
                        if (!sessionMasterList.isEmpty()) {
                            Collections.shuffle(sessionMasterList);
                        }
                    }
                    results = new ArrayList<>(sessionMasterList);
                } else {
                    results = dbHelper.searchProducts(query, 0);
                }
            }

            runOnUiThread(() -> {
                productList.clear();
                productList.addAll(results);
                adapter.notifyDataSetChanged();
                updateStatsUI();
            });
        }).start();
    }

    private void updateStatsUI() {
        new Thread(() -> {
            int loaded = dbHelper.getProductCount();
            int offlineReady = dbHelper.getOfflineReadyCount();
            runOnUiThread(() -> {
                txtLoadedCount.setText(String.valueOf(loaded));
                txtOfflineCount.setText(String.valueOf(offlineReady));
            });
        }).start();
    }

    // --- CATEGORY ADAPTER ---
    class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CatViewHolder> {
        private List<Category> list;
        public CategoryAdapter(List<Category> list) { this.list = list; }

        @NonNull @Override public CatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_filter, parent, false);
            return new CatViewHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull CatViewHolder holder, int position) {
            Category c = list.get(position);
            holder.name.setText(Html.fromHtml(c.getName(), Html.FROM_HTML_MODE_LEGACY));

            if (c.getId() == selectedCategoryId) {
                holder.name.setBackgroundResource(R.drawable.bg_category_chip_selected);
                holder.name.setTextColor(Color.WHITE);
            } else {
                holder.name.setBackgroundResource(R.drawable.bg_category_chip_unselected);
                holder.name.setTextColor(Color.DKGRAY);
            }

            holder.itemView.setOnClickListener(v -> {
                selectedCategoryId = c.getId();
                notifyDataSetChanged();
                performSearch(searchView.getQuery().toString());
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class CatViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            public CatViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.txtCategoryName);
            }
        }
    }

    // --- PRODUCT ADAPTER (Same as before) ---
    class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
        private List<Product> list;
        public ProductAdapter(List<Product> list) { this.list = list; }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
            return new ViewHolder(v);
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Product p = list.get(position);
            holder.name.setText(Html.fromHtml(p.getName(), Html.FROM_HTML_MODE_LEGACY));
            holder.price.setText("Rs " + p.getWholesalePrice());

            holder.itemView.setOnClickListener(v -> {
                ProductDetailBottomSheet bottomSheet = ProductDetailBottomSheet.newInstance(p.getId());
                bottomSheet.show(getSupportFragmentManager(), "ProductDetail");
            });

            holder.itemView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Debug Info")
                        .setMessage("SKU: " + p.getSku() + "\nTokens: " + p.getCategoryTokens())
                        .setPositiveButton("OK", null).show();
                return true;
            });

            String localPath = p.getFirstImageLocalPath();
            String webUrl = p.getFirstImageWebUrl();
            if (localPath != null && new File(localPath).exists()) {
                Glide.with(MainActivity.this).load(new File(localPath)).into(holder.image);
            } else {
                Glide.with(MainActivity.this).load(webUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.image);
            }
        }
        @Override public int getItemCount() { return list.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, price; ImageView image;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.txtName);
                price = itemView.findViewById(R.id.txtPrice);
                image = itemView.findViewById(R.id.imgProduct);
            }
        }
    }
}