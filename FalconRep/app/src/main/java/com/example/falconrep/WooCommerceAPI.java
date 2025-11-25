package com.example.falconrep;

import com.example.falconrep.models.Category;
import com.example.falconrep.models.Product;
import com.example.falconrep.models.Variation;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WooCommerceAPI {

    // UPDATED: Added 'page' parameter for pagination
    @GET("products/categories")
    Call<List<Category>> fetchCategories(
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage,
            @Query("page") int page, // Added this
            @Query("hide_empty") boolean hideEmpty
    );

    @GET("products")
    Call<List<Product>> fetchWooCommerceProducts(
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("status") String status,
            @Query("orderby") String orderBy,
            @Query("order") String order
    );

    @GET("products/{id}/variations")
    Call<List<Variation>> fetchProductVariations(
            @retrofit2.http.Path("id") int productId,
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage
    );

    @GET("products")
    Call<List<Product>> fetchAllProductIds(
            @Query("consumer_key") String key,
            @Query("consumer_secret") String secret,
            @Query("per_page") int perPage,
            @Query("page") int page,
            @Query("status") String status,
            @Query("fields") String fields
    );
}