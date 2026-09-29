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
}
