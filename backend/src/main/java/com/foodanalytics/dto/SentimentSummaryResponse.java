package com.foodanalytics.dto;

public record SentimentSummaryResponse(
    String sentiment,
    long count
) {}
