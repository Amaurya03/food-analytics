package com.foodanalytics.repository;

import com.foodanalytics.dto.FiltersResponse;
import com.foodanalytics.dto.KpiResponse;
import com.foodanalytics.util.QueryFilterHelper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class AnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private record DateRange(String minDate, String maxDate) {}

    public AnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves unique filter options (cities, cuisines, restaurants, date bounds) from orders.
     */
    public FiltersResponse getFilters() {
        String citySql = "SELECT DISTINCT city FROM orders WHERE city IS NOT NULL ORDER BY city ASC";
        List<String> cities = jdbcTemplate.queryForList(citySql, Collections.emptyMap(), String.class);

        String cuisineSql = "SELECT DISTINCT cuisine FROM orders WHERE cuisine IS NOT NULL ORDER BY cuisine ASC";
        List<String> cuisines = jdbcTemplate.queryForList(cuisineSql, Collections.emptyMap(), String.class);

        String restaurantSql = "SELECT DISTINCT restaurant FROM orders WHERE restaurant IS NOT NULL ORDER BY restaurant ASC";
        List<String> restaurants = jdbcTemplate.queryForList(restaurantSql, Collections.emptyMap(), String.class);

        String dateRangeSql = "SELECT MIN(order_date) AS min_date, MAX(order_date) AS max_date FROM orders";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        DateRange bounds = jdbcTemplate.query(dateRangeSql, Collections.emptyMap(), rs -> {
            if (rs.next()) {
                Date min = rs.getDate("min_date");
                Date max = rs.getDate("max_date");
                String minStr = (min != null) ? dateFormat.format(min) : "";
                String maxStr = (max != null) ? dateFormat.format(max) : "";
                return new DateRange(minStr, maxStr);
            }
            return new DateRange("", "");
        });

        return new FiltersResponse(
                cities,
                cuisines,
                restaurants,
                bounds != null ? bounds.minDate() : "",
                bounds != null ? bounds.maxDate() : ""
        );
    }

    /**
     * Calculates filter-aware KPIs (totalOrders, totalRevenue, avgOrderValue, totalCustomers).
     */
    public KpiResponse getKpis(String start, String end, String city, String cuisine, String restaurant) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);

        String sql = "SELECT " +
                "COUNT(*) AS total_orders, " +
                "COALESCE(SUM(order_value), 0.0) AS total_revenue, " +
                "COALESCE(AVG(order_value), 0.0) AS avg_order_value, " +
                "COUNT(DISTINCT customer_id) AS total_customers " +
                "FROM orders" + filter.getWhereClause();

        return jdbcTemplate.queryForObject(sql, filter.getParams(), (rs, rowNum) -> {
            long totalOrders = rs.getLong("total_orders");
            double rawRevenue = rs.getDouble("total_revenue");
            double rawAvg = rs.getDouble("avg_order_value");
            long totalCustomers = rs.getLong("total_customers");

            double totalRevenue = BigDecimal.valueOf(rawRevenue)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            double avgOrderValue = BigDecimal.valueOf(rawAvg)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();

            return new KpiResponse(totalOrders, totalRevenue, avgOrderValue, totalCustomers);
        });
    }

    /**
     * Retrieves trend data grouped by month or week, filter-aware.
     */
    public List<com.foodanalytics.dto.TrendResponse> getTrend(
            String granularity, String start, String end, String city, String cuisine, String restaurant
    ) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);

        boolean isWeek = "week".equalsIgnoreCase(granularity);
        String periodExpression = isWeek
                ? "DATE_FORMAT(order_date, '%x-W%v')"
                : "DATE_FORMAT(order_date, '%Y-%m')";

        String sql = "SELECT " +
                periodExpression + " AS period, " +
                "COUNT(*) AS orders, " +
                "COALESCE(SUM(order_value), 0.0) AS revenue " +
                "FROM orders" + filter.getWhereClause() + " " +
                "GROUP BY period " +
                "ORDER BY period ASC";

        return jdbcTemplate.query(sql, filter.getParams(), (rs, rowNum) -> {
            String period = rs.getString("period");
            long orders = rs.getLong("orders");
            double rawRevenue = rs.getDouble("revenue");
            double revenue = BigDecimal.valueOf(rawRevenue)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            return new com.foodanalytics.dto.TrendResponse(period, orders, revenue);
        });
    }

    /**
     * Retrieves top restaurants ordered by revenue descending, filter-aware.
     */
    public List<com.foodanalytics.dto.RestaurantResponse> getTopRestaurants(
            int limit, String start, String end, String city, String cuisine, String restaurant
    ) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);
        Map<String, Object> params = new java.util.HashMap<>(filter.getParams());
        params.put("limit", Math.max(1, limit));

        String sql = "SELECT " +
                "restaurant, " +
                "COUNT(*) AS orders, " +
                "COALESCE(SUM(order_value), 0.0) AS revenue " +
                "FROM orders" + filter.getWhereClause() + " " +
                "GROUP BY restaurant " +
                "ORDER BY revenue DESC " +
                "LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            String rest = rs.getString("restaurant");
            long orders = rs.getLong("orders");
            double rawRevenue = rs.getDouble("revenue");
            double revenue = BigDecimal.valueOf(rawRevenue)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            return new com.foodanalytics.dto.RestaurantResponse(rest, orders, revenue);
        });
    }

    /**
     * Retrieves order and revenue metrics grouped by city ordered by revenue descending, filter-aware.
     */
    public List<com.foodanalytics.dto.RegionResponse> getRegions(
            String start, String end, String city, String cuisine, String restaurant
    ) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);

        String sql = "SELECT " +
                "city, " +
                "COUNT(*) AS orders, " +
                "COALESCE(SUM(order_value), 0.0) AS revenue " +
                "FROM orders" + filter.getWhereClause() + " " +
                "GROUP BY city " +
                "ORDER BY revenue DESC";

        return jdbcTemplate.query(sql, filter.getParams(), (rs, rowNum) -> {
            String c = rs.getString("city");
            long orders = rs.getLong("orders");
            double rawRevenue = rs.getDouble("revenue");
            double revenue = BigDecimal.valueOf(rawRevenue)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            return new com.foodanalytics.dto.RegionResponse(c, orders, revenue);
        });
    }

    /**
     * Retrieves order and revenue metrics grouped by cuisine ordered by revenue descending, filter-aware.
     */
    public List<com.foodanalytics.dto.CuisineResponse> getCuisines(
            String start, String end, String city, String cuisine, String restaurant
    ) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);

        String sql = "SELECT " +
                "cuisine, " +
                "COUNT(*) AS orders, " +
                "COALESCE(SUM(order_value), 0.0) AS revenue " +
                "FROM orders" + filter.getWhereClause() + " " +
                "GROUP BY cuisine " +
                "ORDER BY revenue DESC";

        return jdbcTemplate.query(sql, filter.getParams(), (rs, rowNum) -> {
            String cuis = rs.getString("cuisine");
            long orders = rs.getLong("orders");
            double rawRevenue = rs.getDouble("revenue");
            double revenue = BigDecimal.valueOf(rawRevenue)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
            return new com.foodanalytics.dto.CuisineResponse(cuis, orders, revenue);
        });
    }
}
