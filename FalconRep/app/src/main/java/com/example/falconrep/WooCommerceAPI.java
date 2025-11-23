package com.example.falconrep;

import com.example.falconrep.models.Category;
import com.example.falconrep.models.Product;
import com.example.falconrep.models.Variation;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface WooCommerceAPI {

    @GET("products")
    Call<List<Product>> fetchWooCommerceProducts(
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("status") String status,
            @Query("after") String afterDate
    );

    @GET("products")
    Call<List<Product>> fetchAllProductIds(
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("status") String status,
            @Query("_fields") String fields
    );

    @GET("products/{id}/variations")
    Call<List<Variation>> fetchProductVariations(
            @Path("id") int productId,
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage
    );

    // NEW: Fetch Categories
    @GET("products/categories")
    Call<List<Category>> fetchCategories(
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage,
            @Query("hide_empty") boolean hideEmpty
    );
}