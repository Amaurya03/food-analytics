package com.foodanalytics.dto;

public record CuisineResponse(
    String cuisine,
    long orders,
    double revenue
) {}
