package com.foodanalytics.dto;

import java.util.List;

public record FiltersResponse(
    List<String> cities,
    List<String> cuisines,
    List<String> restaurants,
    String minDate,
    String maxDate
) {}
