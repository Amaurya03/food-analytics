package com.foodanalytics.dto;

import java.util.List;

public record CustomerSegmentsResponse(
    List<SegmentSummary> summary,
    List<SegmentPoint> points
) {
    public record SegmentSummary(
        String segment,
        long customers,
        double avgSpend
    ) {}

    public record SegmentPoint(
        long customerId,
        double totalSpend,
        int orderCount,
        String segment
    ) {}
}
