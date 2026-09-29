package com.foodanalytics.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility helper to build dynamic SQL WHERE clauses and named-parameter maps
 * from optional HTTP query parameters (start, end, city, cuisine, restaurant).
 *
 * Prevents SQL injection by only using parameterized SQL placeholders.
 */
public class QueryFilterHelper {

    public static class FilterResult {
        private final String whereClause;
        private final String conditions;
        private final Map<String, Object> params;

        public FilterResult(String whereClause, String conditions, Map<String, Object> params) {
            this.whereClause = whereClause;
            this.conditions = conditions;
            this.params = Collections.unmodifiableMap(params);
        }

        /**
         * Returns full WHERE clause (e.g. " WHERE order_date >= :start") or empty string if no filters.
         */
        public String getWhereClause() {
            return whereClause;
        }

        /**
         * Returns joined conditions (e.g. "order_date >= :start AND city = :city") or "1=1" if empty.
         */
        public String getConditions() {
            return conditions;
        }

        /**
         * Returns parameter map for NamedParameterJdbcTemplate.
         */
        public Map<String, Object> getParams() {
            return params;
        }
    }

    /**
     * Builds FilterResult without table alias.
     */
    public static FilterResult build(String start, String end, String city, String cuisine, String restaurant) {
        return build(null, start, end, city, cuisine, restaurant);
    }

    /**
     * Builds FilterResult with an optional table prefix (e.g. "o").
     */
    public static FilterResult build(String tableAlias, String start, String end, String city, String cuisine, String restaurant) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        String prefix = (tableAlias != null && !tableAlias.trim().isEmpty())
                ? tableAlias.trim() + "."
                : "";

        if (start != null && !start.trim().isEmpty()) {
            clauses.add(prefix + "order_date >= :start");
            params.put("start", start.trim());
        }

        if (end != null && !end.trim().isEmpty()) {
            clauses.add(prefix + "order_date <= :end");
            params.put("end", end.trim());
        }

        if (city != null && !city.trim().isEmpty()) {
            clauses.add(prefix + "city = :city");
            params.put("city", city.trim());
        }

        if (cuisine != null && !cuisine.trim().isEmpty()) {
            clauses.add(prefix + "cuisine = :cuisine");
            params.put("cuisine", cuisine.trim());
        }

        if (restaurant != null && !restaurant.trim().isEmpty()) {
            clauses.add(prefix + "restaurant = :restaurant");
            params.put("restaurant", restaurant.trim());
        }

        String conditions = clauses.isEmpty() ? "1=1" : String.join(" AND ", clauses);
        String whereClause = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);

        return new FilterResult(whereClause, conditions, params);
    }
}
