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

    /**
     * Retrieves customer spending distribution across 6 sensible buckets, filter-aware.
     */
    public List<com.foodanalytics.dto.CustomerSpendingResponse> getCustomerSpending(
            String start, String end, String city, String cuisine, String restaurant
    ) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);

        String sql = "WITH cust_spend AS (" +
                "  SELECT customer_id, SUM(order_value) AS spend " +
                "  FROM orders" + filter.getWhereClause() + " " +
                "  GROUP BY customer_id" +
                ") " +
                "SELECT " +
                "  CASE " +
                "    WHEN spend <= 1000 THEN '0 - 1,000' " +
                "    WHEN spend <= 2500 THEN '1,000 - 2,500' " +
                "    WHEN spend <= 5000 THEN '2,500 - 5,000' " +
                "    WHEN spend <= 10000 THEN '5,000 - 10,000' " +
                "    WHEN spend <= 25000 THEN '10,000 - 25,000' " +
                "    ELSE '25,000+' " +
                "  END AS bucket, " +
                "  COUNT(*) AS customers, " +
                "  MIN(CASE " +
                "    WHEN spend <= 1000 THEN 1 " +
                "    WHEN spend <= 2500 THEN 2 " +
                "    WHEN spend <= 5000 THEN 3 " +
                "    WHEN spend <= 10000 THEN 4 " +
                "    WHEN spend <= 25000 THEN 5 " +
                "    ELSE 6 " +
                "  END) AS sort_order " +
                "FROM cust_spend " +
                "GROUP BY bucket " +
                "ORDER BY sort_order ASC";

        Map<String, Long> countMap = new java.util.HashMap<>();
        jdbcTemplate.query(sql, filter.getParams(), rs -> {
            countMap.put(rs.getString("bucket"), rs.getLong("customers"));
        });

        List<String> orderedBuckets = List.of(
                "0 - 1,000",
                "1,000 - 2,500",
                "2,500 - 5,000",
                "5,000 - 10,000",
                "10,000 - 25,000",
                "25,000+"
        );

        List<com.foodanalytics.dto.CustomerSpendingResponse> results = new java.util.ArrayList<>();
        for (String b : orderedBuckets) {
            results.add(new com.foodanalytics.dto.CustomerSpendingResponse(b, countMap.getOrDefault(b, 0L)));
        }
        return results;
    }

    /**
     * Retrieves count of New (1 order) vs Repeat (>1 order) customers, filter-aware.
     */
    public List<com.foodanalytics.dto.CustomerTypeResponse> getRepeatVsNew(
            String start, String end, String city, String cuisine, String restaurant
    ) {
        QueryFilterHelper.FilterResult filter = QueryFilterHelper.build(start, end, city, cuisine, restaurant);

        String sql = "WITH cust_orders AS (" +
                "  SELECT customer_id, COUNT(*) AS cnt " +
                "  FROM orders" + filter.getWhereClause() + " " +
                "  GROUP BY customer_id" +
                ") " +
                "SELECT " +
                "  CASE WHEN cnt = 1 THEN 'New' ELSE 'Repeat' END AS cust_type, " +
                "  COUNT(*) AS customers " +
                "FROM cust_orders " +
                "GROUP BY cust_type";

        Map<String, Long> counts = new java.util.HashMap<>();
        jdbcTemplate.query(sql, filter.getParams(), rs -> {
            counts.put(rs.getString("cust_type"), rs.getLong("customers"));
        });

        return List.of(
                new com.foodanalytics.dto.CustomerTypeResponse("New", counts.getOrDefault("New", 0L)),
                new com.foodanalytics.dto.CustomerTypeResponse("Repeat", counts.getOrDefault("Repeat", 0L))
        );
    }

    /**
     * Retrieves offline K-Means customer segments summary and 600 random customer points (NOT filter-aware).
     */
    public com.foodanalytics.dto.CustomerSegmentsResponse getCustomerSegments() {
        String summarySql = "SELECT " +
                "segment, " +
                "COUNT(*) AS customers, " +
                "COALESCE(AVG(total_spend), 0.0) AS raw_avg " +
                "FROM customer_segments " +
                "GROUP BY segment " +
                "ORDER BY raw_avg DESC";

        List<com.foodanalytics.dto.CustomerSegmentsResponse.SegmentSummary> summary = jdbcTemplate.query(
                summarySql,
                Collections.emptyMap(),
                (rs, rowNum) -> {
                    String seg = rs.getString("segment");
                    long custs = rs.getLong("customers");
                    double rawAvg = rs.getDouble("raw_avg");
                    double avgSpend = BigDecimal.valueOf(rawAvg).setScale(2, RoundingMode.HALF_UP).doubleValue();
                    return new com.foodanalytics.dto.CustomerSegmentsResponse.SegmentSummary(seg, custs, avgSpend);
                }
        );

        String pointsSql = "SELECT " +
                "customer_id, " +
                "total_spend, " +
                "order_count, " +
                "segment " +
                "FROM customer_segments " +
                "ORDER BY RAND() " +
                "LIMIT 600";

        List<com.foodanalytics.dto.CustomerSegmentsResponse.SegmentPoint> points = jdbcTemplate.query(
                pointsSql,
                Collections.emptyMap(),
                (rs, rowNum) -> {
                    long cid = rs.getLong("customer_id");
                    double spend = rs.getDouble("total_spend");
                    int count = rs.getInt("order_count");
                    String seg = rs.getString("segment");
                    return new com.foodanalytics.dto.CustomerSegmentsResponse.SegmentPoint(cid, spend, count, seg);
                }
        );

        return new com.foodanalytics.dto.CustomerSegmentsResponse(summary, points);
    }
}
