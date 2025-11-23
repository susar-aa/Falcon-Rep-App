package com.example.falconrep;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.falconrep.models.Product;
import com.example.falconrep.models.Variation;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProductDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_PRODUCT_ID = "product_id";

    private ViewPager2 viewPager;
    private ImageButton btnMaximizeImage;
    private TextView txtIndicator, txtName, txtPrice, txtDesc, lblQuickSelect;
    private RecyclerView rvVariations, rvVariationSlider;
    private DatabaseHelper dbHelper;

    // Keep track of gallery paths to find index later
    private List<String> mainGalleryPaths = new ArrayList<>();

    public static ProductDetailBottomSheet newInstance(int productId) {
        ProductDetailBottomSheet fragment = new ProductDetailBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_PRODUCT_ID, productId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_product_detail, container, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new DatabaseHelper(requireContext());

        viewPager = view.findViewById(R.id.viewPagerGallery);
        btnMaximizeImage = view.findViewById(R.id.btnMaximizeImage);
        txtIndicator = view.findViewById(R.id.txtPageIndicator);
        txtName = view.findViewById(R.id.detailName);
        txtPrice = view.findViewById(R.id.detailPrice);
        txtDesc = view.findViewById(R.id.detailDescription);
        rvVariations = view.findViewById(R.id.rvVariations);

        rvVariationSlider = view.findViewById(R.id.rvVariationSlider);
        lblQuickSelect = view.findViewById(R.id.lblQuickSelect);

        // Setup Maximize Button Click Listener
        btnMaximizeImage.setOnClickListener(v -> {
            if (!mainGalleryPaths.isEmpty()) {
                int currentItem = viewPager.getCurrentItem();
                if (currentItem >= 0 && currentItem < mainGalleryPaths.size()) {
                    String imagePath = mainGalleryPaths.get(currentItem);

                    // Don't open placeholder
                    if (!imagePath.equals("placeholder")) {
                        Intent intent = new Intent(requireContext(), FullScreenImageActivity.class);
                        intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_PATH, imagePath);
                        startActivity(intent);
                    }
                }
            }
        });

        if (getArguments() != null) {
            int productId = getArguments().getInt(ARG_PRODUCT_ID);
            loadProductData(productId);
        }
    }

    private void loadProductData(int id) {
        Product p = dbHelper.getProductById(id);
        if (p == null) {
            dismiss();
            return;
        }

        // Initial Load: Use Product Name and Price (Decoded)
        txtName.setText(Html.fromHtml(p.getName(), Html.FROM_HTML_MODE_LEGACY));
        txtPrice.setText("Rs " + p.getWholesalePrice());

        if (p.getDescription() != null) {
            txtDesc.setText(Html.fromHtml(p.getDescription(), Html.FROM_HTML_MODE_COMPACT));
        }

        // 1. Fetch Variations
        List<Variation> variations = dbHelper.getVariationsForProduct(id);

        // 2. Setup Images (Merge Product + Variation images for full gallery)
        setupImages(p, variations);

        // 3. Setup Variations UI (Pass 'p' to handle name updates)
        setupVariationsUI(variations, p);
    }

    private void setupImages(Product p, List<Variation> variations) {
        mainGalleryPaths.clear();

        // A. Add Main Product Images
        List<String> localPaths = p.getLocalPaths();
        List<String> webUrls = p.getWebUrls();
        int count = Math.max(localPaths.size(), webUrls.size());

        for (int i = 0; i < count; i++) {
            String path = null;
            if (i < localPaths.size()) path = localPaths.get(i);
            if (path == null || path.isEmpty() || !new File(path).exists()) {
                if (i < webUrls.size()) path = webUrls.get(i);
            }
            if (path != null) mainGalleryPaths.add(path);
        }

        // B. Add Variation Images (Unique ones)
        if (variations != null) {
            for (Variation v : variations) {
                String vPath = null;
                // Prefer local
                if (v.getLocalImagePath() != null && new File(v.getLocalImagePath()).exists()) {
                    vPath = v.getLocalImagePath();
                } else {
                    vPath = v.getWebImageUrl();
                }

                // Add if valid and not duplicate (simple string check)
                if (vPath != null && !vPath.isEmpty() && !mainGalleryPaths.contains(vPath)) {
                    mainGalleryPaths.add(vPath);
                }
            }
        }

        if (mainGalleryPaths.isEmpty()) mainGalleryPaths.add("placeholder");

        GalleryAdapter adapter = new GalleryAdapter(mainGalleryPaths);
        viewPager.setAdapter(adapter);
        txtIndicator.setText("1 / " + mainGalleryPaths.size());

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                txtIndicator.setText((position + 1) + " / " + mainGalleryPaths.size());
            }
        });
    }

    private void setupVariationsUI(List<Variation> variations, Product p) {
        if (variations == null || variations.isEmpty()) {
            rvVariations.setVisibility(View.GONE);
            rvVariationSlider.setVisibility(View.GONE);
            lblQuickSelect.setVisibility(View.GONE);
            return;
        }

        // 1. Vertical List (Existing)
        rvVariations.setVisibility(View.VISIBLE);
        rvVariations.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVariations.setAdapter(new VariationsAdapter(variations));

        // 2. Horizontal Slider (New)
        rvVariationSlider.setVisibility(View.VISIBLE);
        lblQuickSelect.setVisibility(View.VISIBLE);
        rvVariationSlider.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        VariationSliderAdapter sliderAdapter = new VariationSliderAdapter(variations, variation -> {
            // ACTION 1: Find image in gallery and scroll to it
            String targetPath = (variation.getLocalImagePath() != null && new File(variation.getLocalImagePath()).exists())
                    ? variation.getLocalImagePath()
                    : variation.getWebImageUrl();

            if (targetPath != null && mainGalleryPaths.contains(targetPath)) {
                int index = mainGalleryPaths.indexOf(targetPath);
                viewPager.setCurrentItem(index, true); // Smooth scroll
            }

            // ACTION 2: Update Price
            txtPrice.setText("Rs " + variation.getPrice());

            // ACTION 3: Update Name (Append attributes)
            String baseName = Html.fromHtml(p.getName(), Html.FROM_HTML_MODE_LEGACY).toString();
            String variantInfo = variation.getAttributesString();
            if (variantInfo != null && !variantInfo.isEmpty()) {
                txtName.setText(baseName + " (" + variantInfo + ")");
            } else {
                txtName.setText(baseName);
            }
        });
        rvVariationSlider.setAdapter(sliderAdapter);
    }

    // --- ADAPTERS ---

    // 1. Main Gallery Adapter
    class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.ImgViewHolder> {
        private List<String> paths;
        public GalleryAdapter(List<String> paths) { this.paths = paths; }
        @NonNull @Override public ImgViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new ImgViewHolder(iv);
        }
        @Override public void onBindViewHolder(@NonNull ImgViewHolder holder, int position) {
            String path = paths.get(position);
            if (path.equals("placeholder")) {
                holder.img.setImageResource(android.R.drawable.ic_menu_gallery);
                return;
            }
            if (path.startsWith("http")) Glide.with(holder.itemView).load(path).into(holder.img);
            else Glide.with(holder.itemView).load(new File(path)).into(holder.img);
        }
        @Override public int getItemCount() { return paths.size(); }
        class ImgViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            public ImgViewHolder(@NonNull View itemView) { super(itemView); this.img = (ImageView) itemView; }
        }
    }

    // 2. Variation Slider Adapter (The Horizontal Cards)
    class VariationSliderAdapter extends RecyclerView.Adapter<VariationSliderAdapter.CardViewHolder> {
        private List<Variation> list;
        private OnVariationClickListener listener;

        public VariationSliderAdapter(List<Variation> list, OnVariationClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull @Override
        public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_variation_card, parent, false);
            return new CardViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
            Variation v = list.get(position);
            holder.name.setText(v.getAttributesString());
            holder.price.setText("Rs " + v.getPrice());

            // Load Image
            String path = v.getLocalImagePath();
            if (path != null && new File(path).exists()) {
                Glide.with(holder.itemView).load(new File(path)).into(holder.img);
            } else {
                Glide.with(holder.itemView).load(v.getWebImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.img);
            }

            holder.itemView.setOnClickListener(view -> {
                if (listener != null) listener.onClick(v);
            });
        }

        @Override public int getItemCount() { return list.size(); }

        class CardViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            TextView name, price;
            public CardViewHolder(@NonNull View itemView) {
                super(itemView);
                img = itemView.findViewById(R.id.imgVariation);
                name = itemView.findViewById(R.id.txtVarName);
                price = itemView.findViewById(R.id.txtVarPrice);
            }
        }
    }

    // 3. Simple List Adapter (The Vertical List)
    class VariationsAdapter extends RecyclerView.Adapter<VariationsAdapter.VarViewHolder> {
        private List<Variation> list;
        public VariationsAdapter(List<Variation> list) { this.list = list; }
        @NonNull @Override public VarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(16, 24, 16, 24); // Increased padding for touch

            TextView text = new TextView(parent.getContext());
            text.setId(android.R.id.text1);
            text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView price = new TextView(parent.getContext());
            price.setId(android.R.id.text2);
            price.setTextColor(0xFFE91E63);

            layout.addView(text);
            layout.addView(price);
            return new VarViewHolder(layout);
        }
        @Override public void onBindViewHolder(@NonNull VarViewHolder holder, int position) {
            Variation v = list.get(position);
            holder.text.setText(v.getAttributesString());
            holder.price.setText("Rs " + v.getPrice());
        }
        @Override public int getItemCount() { return list.size(); }
        class VarViewHolder extends RecyclerView.ViewHolder {
            TextView text, price;
            public VarViewHolder(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(android.R.id.text1);
                price = itemView.findViewById(android.R.id.text2);
            }
        }
    }

    interface OnVariationClickListener {
        void onClick(Variation v);
    }
}