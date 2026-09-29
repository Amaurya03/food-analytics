package com.foodanalytics.dto;

public record RestaurantResponse(
    String restaurant,
    long orders,
    double revenue
) {}
