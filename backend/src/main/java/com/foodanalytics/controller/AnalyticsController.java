package com.foodanalytics.controller;

import com.foodanalytics.dto.FiltersResponse;
import com.foodanalytics.dto.HealthResponse;
import com.foodanalytics.dto.KpiResponse;
import com.foodanalytics.repository.AnalyticsRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsController(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    /**
     * GET /api/health -> {"status":"ok"}
     */
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    /**
     * GET /api/filters -> {"cities":[],"cuisines":[],"restaurants":[],"minDate":"","maxDate":""}
     */
    @GetMapping("/filters")
    public FiltersResponse filters() {
        return analyticsRepository.getFilters();
    }

    /**
     * GET /api/kpis -> {"totalOrders":0,"totalRevenue":0,"avgOrderValue":0,"totalCustomers":0}
     * Filter-aware with optional query parameters: start, end, city, cuisine, restaurant.
     */
    @GetMapping("/kpis")
    public KpiResponse kpis(
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "cuisine", required = false) String cuisine,
            @RequestParam(name = "restaurant", required = false) String restaurant
    ) {
        return analyticsRepository.getKpis(start, end, city, cuisine, restaurant);
    }

    /**
     * GET /api/trend?granularity=month|week -> [{"period":"","orders":0,"revenue":0}]
     * Filter-aware with optional query parameters: granularity, start, end, city, cuisine, restaurant.
     */
    @GetMapping("/trend")
    public java.util.List<com.foodanalytics.dto.TrendResponse> trend(
            @RequestParam(name = "granularity", defaultValue = "month") String granularity,
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "cuisine", required = false) String cuisine,
            @RequestParam(name = "restaurant", required = false) String restaurant
    ) {
        return analyticsRepository.getTrend(granularity, start, end, city, cuisine, restaurant);
    }
}
