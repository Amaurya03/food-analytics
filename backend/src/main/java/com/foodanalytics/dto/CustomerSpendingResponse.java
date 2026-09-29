package com.foodanalytics.dto;

public record CustomerSpendingResponse(
    String bucket,
    long customers
) {}
