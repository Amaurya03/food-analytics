package com.foodanalytics.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QueryFilterHelperTest {

    @Test
    public void testEmptyFilters() {
        QueryFilterHelper.FilterResult result = QueryFilterHelper.build(null, null, null, null, null);
        assertEquals("", result.getWhereClause());
        assertEquals("1=1", result.getConditions());
        assertTrue(result.getParams().isEmpty());
    }

    @Test
    public void testAllFilters() {
        QueryFilterHelper.FilterResult result = QueryFilterHelper.build(
                "2025-01-01", "2025-12-31", "Mumbai", "North Indian", "Bombay Canteen"
        );

        assertEquals(" WHERE order_date >= :start AND order_date <= :end AND city = :city AND cuisine = :cuisine AND restaurant = :restaurant",
                result.getWhereClause());
        assertEquals(5, result.getParams().size());
        assertEquals("2025-01-01", result.getParams().get("start"));
        assertEquals("2025-12-31", result.getParams().get("end"));
        assertEquals("Mumbai", result.getParams().get("city"));
        assertEquals("North Indian", result.getParams().get("cuisine"));
        assertEquals("Bombay Canteen", result.getParams().get("restaurant"));
    }

    @Test
    public void testPartialFiltersWithTableAlias() {
        QueryFilterHelper.FilterResult result = QueryFilterHelper.build(
                "o", "2025-06-01", null, "Delhi", null, null
        );

        assertEquals(" WHERE o.order_date >= :start AND o.city = :city", result.getWhereClause());
        assertEquals("o.order_date >= :start AND o.city = :city", result.getConditions());
        assertEquals(2, result.getParams().size());
        assertEquals("2025-06-01", result.getParams().get("start"));
        assertEquals("Delhi", result.getParams().get("city"));
    }
}
