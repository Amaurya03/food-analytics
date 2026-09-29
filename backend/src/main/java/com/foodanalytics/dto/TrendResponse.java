package com.foodanalytics.dto;

public record TrendResponse(
    String period,
    long orders,
    double revenue
) {}
