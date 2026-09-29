package com.foodanalytics.dto;

import java.util.List;

public record ForecastResponse(
    List<HistoryPoint> history,
    List<ForecastPoint> forecast
) {
    public record HistoryPoint(
        String date,
        long orders
    ) {}

    public record ForecastPoint(
        String date,
        int predictedOrders
    ) {}
}
