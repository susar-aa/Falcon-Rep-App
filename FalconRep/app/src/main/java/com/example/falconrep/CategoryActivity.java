package com.example.falconrep;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.falconrep.models.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DatabaseHelper dbHelper;
    private CategoryGridAdapter adapter;
    private List<Category> categoryList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        dbHelper = new DatabaseHelper(this);
        recyclerView = findViewById(R.id.rvAllCategories);

        // 2 Columns Grid
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        categoryList = new ArrayList<>();
        adapter = new CategoryGridAdapter(categoryList);
        recyclerView.setAdapter(adapter);

        loadCategories();
    }

    private void loadCategories() {
        new Thread(() -> {
            List<Category> cats = dbHelper.getAllCategories();
            runOnUiThread(() -> {
                categoryList.clear();
                categoryList.addAll(cats);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    class CategoryGridAdapter extends RecyclerView.Adapter<CategoryGridAdapter.ViewHolder> {
        private List<Category> list;

        public CategoryGridAdapter(List<Category> list) { this.list = list; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Category c = list.get(position);
            holder.name.setText(Html.fromHtml(c.getName(), Html.FROM_HTML_MODE_LEGACY));
            holder.count.setText(c.getCount() + " Products");

            holder.itemView.setOnClickListener(v -> {
                // Navigate to Catalog with Filter
                Intent intent = new Intent(CategoryActivity.this, MainActivity.class);
                intent.putExtra("SELECTED_CAT_ID", c.getId());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, count;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.txtCatName);
                count = itemView.findViewById(R.id.txtCatCount);
            }
        }
    }
}