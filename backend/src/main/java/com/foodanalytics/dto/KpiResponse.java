package com.foodanalytics.dto;

public record KpiResponse(
    long totalOrders,
    double totalRevenue,
    double avgOrderValue,
    long totalCustomers
) {}
