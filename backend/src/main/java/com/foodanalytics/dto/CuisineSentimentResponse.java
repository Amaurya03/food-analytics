package com.foodanalytics.dto;

public record CuisineSentimentResponse(
    String cuisine,
    long positive,
    long neutral,
    long negative
) {}
