package com.foodanalytics.dto;

public record SentimentStatsResponse(
    long totalReviews,
    double percentPositive,
    double avgRating
) {}
