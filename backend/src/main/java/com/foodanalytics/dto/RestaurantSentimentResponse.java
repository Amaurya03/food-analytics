package com.foodanalytics.dto;

public record RestaurantSentimentResponse(
    String restaurant,
    long positive,
    long neutral,
    long negative
) {}
