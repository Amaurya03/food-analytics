package com.foodanalytics.dto;

public record RegionResponse(
    String city,
    long orders,
    double revenue
) {}
