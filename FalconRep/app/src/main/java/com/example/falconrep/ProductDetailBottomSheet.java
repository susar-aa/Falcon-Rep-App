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
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
// Removed DiskCacheStrategy import as we are using direct local storage
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
    private ImageButton btnMaximizeImage, btnCloseSheet;
    private TextView txtIndicator, txtName, txtPrice, txtDesc, lblQuickSelect, lblAllVariants;
    private RecyclerView rvVariations, rvVariationSlider;
    private DatabaseHelper dbHelper;

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
                BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
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
        btnCloseSheet = view.findViewById(R.id.btnCloseSheet);
        txtIndicator = view.findViewById(R.id.txtPageIndicator);
        txtName = view.findViewById(R.id.detailName);
        txtPrice = view.findViewById(R.id.detailPrice);
        txtDesc = view.findViewById(R.id.detailDescription);
        rvVariations = view.findViewById(R.id.rvVariations);
        rvVariationSlider = view.findViewById(R.id.rvVariationSlider);
        lblQuickSelect = view.findViewById(R.id.lblQuickSelect);
        lblAllVariants = view.findViewById(R.id.lblAllVariants);

        btnCloseSheet.setOnClickListener(v -> dismiss());

        btnMaximizeImage.setOnClickListener(v -> {
            if (!mainGalleryPaths.isEmpty()) {
                int currentItem = viewPager.getCurrentItem();
                if (currentItem >= 0 && currentItem < mainGalleryPaths.size()) {
                    String imagePath = mainGalleryPaths.get(currentItem);
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

        txtName.setText(Html.fromHtml(p.getName(), Html.FROM_HTML_MODE_LEGACY));
        txtPrice.setText("Rs " + p.getWholesalePrice());

        if (p.getDescription() != null) {
            txtDesc.setText(Html.fromHtml(p.getDescription(), Html.FROM_HTML_MODE_COMPACT));
        }

        List<Variation> variations = dbHelper.getVariationsForProduct(id);
        setupImages(p, variations);
        setupVariationsUI(variations, p);
    }

    private void setupImages(Product p, List<Variation> variations) {
        mainGalleryPaths.clear();

        List<String> localPaths = p.getLocalPaths();
        List<String> webUrls = p.getWebUrls();
        int count = Math.max(localPaths.size(), webUrls.size());

        for (int i = 0; i < count; i++) {
            String path = null;

            // 1. Try DB Path
            if (i < localPaths.size()) {
                String potentialPath = localPaths.get(i);
                if (isValidFile(potentialPath)) {
                    path = potentialPath;
                }
            }

            // 2. Try Predictive Local Path (Fallback if DB is outdated but file exists)
            // Filename format matches ImageWorker: img_{id}_{index}.jpg
            if (path == null) {
                String fileName = "img_" + p.getId() + "_" + i + ".jpg";
                File fallbackFile = new File(requireContext().getFilesDir(), fileName);
                if (fallbackFile.exists() && fallbackFile.length() > 0) {
                    path = fallbackFile.getAbsolutePath();
                }
            }

            // 3. Fallback to Web URL
            if (path == null && i < webUrls.size()) {
                path = webUrls.get(i);
            }

            if (path != null) mainGalleryPaths.add(path);
        }

        // Add Variation Images
        if (variations != null) {
            for (Variation v : variations) {
                String vPath = null;

                // Try DB Path
                if (isValidFile(v.getLocalImagePath())) {
                    vPath = v.getLocalImagePath();
                }

                // Try Predictive Path
                if (vPath == null) {
                    String fileName = "var_" + v.getParentId() + "_" + v.getId() + ".jpg";
                    File fallbackFile = new File(requireContext().getFilesDir(), fileName);
                    if (fallbackFile.exists() && fallbackFile.length() > 0) {
                        vPath = fallbackFile.getAbsolutePath();
                    }
                }

                // Web Fallback
                if (vPath == null) {
                    vPath = v.getWebImageUrl();
                }

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

    private boolean isValidFile(String path) {
        return path != null && !path.isEmpty() && new File(path).exists() && new File(path).length() > 0;
    }

    private void setupVariationsUI(List<Variation> variations, Product p) {
        if (variations == null || variations.isEmpty()) {
            rvVariations.setVisibility(View.GONE);
            rvVariationSlider.setVisibility(View.GONE);
            lblQuickSelect.setVisibility(View.GONE);
            lblAllVariants.setVisibility(View.GONE);
            return;
        }

        rvVariations.setVisibility(View.VISIBLE);
        rvVariations.setLayoutManager(new LinearLayoutManager(getContext()));
        rvVariations.setAdapter(new VariationsAdapter(variations));

        rvVariationSlider.setVisibility(View.VISIBLE);
        lblQuickSelect.setVisibility(View.VISIBLE);
        lblAllVariants.setVisibility(View.VISIBLE);
        rvVariationSlider.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        VariationSliderAdapter sliderAdapter = new VariationSliderAdapter(variations, variation -> {
            String targetPath = null;

            // Check Local DB Path
            if (isValidFile(variation.getLocalImagePath())) {
                targetPath = variation.getLocalImagePath();
            }

            // Check Predictive Path
            if (targetPath == null) {
                String fileName = "var_" + variation.getParentId() + "_" + variation.getId() + ".jpg";
                File fallbackFile = new File(requireContext().getFilesDir(), fileName);
                if (fallbackFile.exists() && fallbackFile.length() > 0) {
                    targetPath = fallbackFile.getAbsolutePath();
                }
            }

            // Web Fallback
            if (targetPath == null) targetPath = variation.getWebImageUrl();

            if (targetPath != null && mainGalleryPaths.contains(targetPath)) {
                int index = mainGalleryPaths.indexOf(targetPath);
                viewPager.setCurrentItem(index, true);
            }

            txtPrice.setText("Rs " + variation.getPrice());
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

            // Robust loading: File object vs URL string
            if (path.startsWith("/")) { // Local Path usually starts with /data/user...
                Glide.with(holder.itemView).load(new File(path)).into(holder.img);
            } else {
                // HTTP URL - Only load if absolutely necessary
                Glide.with(holder.itemView).load(path).into(holder.img);
            }
        }
        @Override public int getItemCount() { return paths.size(); }
        class ImgViewHolder extends RecyclerView.ViewHolder {
            ImageView img;
            public ImgViewHolder(@NonNull View itemView) { super(itemView); this.img = (ImageView) itemView; }
        }
    }

    class VariationSliderAdapter extends RecyclerView.Adapter<VariationSliderAdapter.CardViewHolder> {
        private List<Variation> list;
        private OnVariationClickListener listener;
        public VariationSliderAdapter(List<Variation> list, OnVariationClickListener listener) {
            this.list = list;
            this.listener = listener;
        }
        @NonNull @Override public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_variation_card, parent, false);
            return new CardViewHolder(v);
        }
        @Override public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
            Variation v = list.get(position);
            holder.name.setText(v.getAttributesString());
            holder.price.setText("Rs " + v.getPrice());

            String path = null;

            // 1. Try DB
            if (isValidFile(v.getLocalImagePath())) {
                path = v.getLocalImagePath();
            }

            // 2. Try Predictive
            if (path == null) {
                String fileName = "var_" + v.getParentId() + "_" + v.getId() + ".jpg";
                File fallbackFile = new File(requireContext().getFilesDir(), fileName);
                if (fallbackFile.exists() && fallbackFile.length() > 0) {
                    path = fallbackFile.getAbsolutePath();
                }
            }

            if (path != null) {
                Glide.with(holder.itemView).load(new File(path)).into(holder.img);
            } else {
                Glide.with(holder.itemView)
                        .load(v.getWebImageUrl())
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(holder.img);
            }

            holder.itemView.setOnClickListener(view -> {
                if (listener != null) listener.onClick(v);
            });
        }
        @Override public int getItemCount() { return list.size(); }
        class CardViewHolder extends RecyclerView.ViewHolder {
            ImageView img; TextView name, price;
            public CardViewHolder(@NonNull View itemView) {
                super(itemView);
                img = itemView.findViewById(R.id.imgVariation);
                name = itemView.findViewById(R.id.txtVarName);
                price = itemView.findViewById(R.id.txtVarPrice);
            }
        }
    }

    class VariationsAdapter extends RecyclerView.Adapter<VariationsAdapter.VarViewHolder> {
        private List<Variation> list;
        public VariationsAdapter(List<Variation> list) { this.list = list; }
        @NonNull @Override public VarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_variation_row, parent, false);
            return new VarViewHolder(v);
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
                text = itemView.findViewById(R.id.txtVarAttribute);
                price = itemView.findViewById(R.id.txtVarPriceRow);
            }
        }
    }
    interface OnVariationClickListener { void onClick(Variation v); }
}