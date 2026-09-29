# Contract

## Database tables (MySQL)

orders
- order_id INT PRIMARY KEY
- order_date DATE
- customer_id INT
- restaurant VARCHAR(100)
- cuisine VARCHAR(50)
- city VARCHAR(50)
- order_value DECIMAL(10,2)
- rating TINYINT NULL
- review_text TEXT NULL
- sentiment VARCHAR(10) NULL   (positive / neutral / negative; NULL when there is no review)
- sentiment_score DOUBLE NULL  (TextBlob polarity)
Indexes on: order_date, city, cuisine, restaurant, customer_id.

customer_segments
- customer_id INT PRIMARY KEY
- total_spend DECIMAL(12,2)
- order_count INT
- avg_order_value DECIMAL(10,2)
- segment VARCHAR(20)   (High Value / Regular / Occasional)

forecast
- forecast_date DATE PRIMARY KEY
- predicted_orders INT

## Filters (all optional query parameters on filter-aware endpoints)
start (yyyy-MM-dd), end (yyyy-MM-dd), city, cuisine, restaurant. An empty or missing value means "all".

## API endpoints (Spring Boot, JSON)
GET /api/health -> {"status":"ok"}
GET /api/filters -> {"cities":[],"cuisines":[],"restaurants":[],"minDate":"","maxDate":""}
GET /api/kpis -> {"totalOrders":0,"totalRevenue":0,"avgOrderValue":0,"totalCustomers":0}   (filter-aware)
GET /api/trend?granularity=month|week -> [{"period":"","orders":0,"revenue":0}]   (filter-aware)
GET /api/restaurants/top?limit=10 -> [{"restaurant":"","orders":0,"revenue":0}]   (filter-aware)
GET /api/regions -> [{"city":"","orders":0,"revenue":0}]   (filter-aware)
GET /api/cuisines -> [{"cuisine":"","orders":0,"revenue":0}]   (filter-aware)
GET /api/customers/spending -> [{"bucket":"","customers":0}]   (filter-aware)
GET /api/customers/repeat-vs-new -> [{"type":"New","customers":0},{"type":"Repeat","customers":0}]   (filter-aware; a customer with exactly 1 order in the filtered data is New, more than 1 is Repeat)
GET /api/customers/segments -> {"summary":[{"segment":"","customers":0,"avgSpend":0}],"points":[{"customerId":0,"totalSpend":0,"orderCount":0,"segment":""}]}   (NOT filter-aware; points limited to 600 random customers)
GET /api/sentiment/summary -> [{"sentiment":"","count":0}]   (filter-aware; ignores orders without a review)
GET /api/sentiment/by-restaurant -> [{"restaurant":"","positive":0,"neutral":0,"negative":0}]   (filter-aware; top 10 restaurants by review count)
GET /api/sentiment/by-cuisine -> [{"cuisine":"","positive":0,"neutral":0,"negative":0}]   (filter-aware)
GET /api/forecast -> {"history":[{"date":"","orders":0}],"forecast":[{"date":"","predictedOrders":0}]}   (NOT filter-aware; history = last 120 days of daily orders)

## Frontend
Static files in backend/src/main/resources/static: index.html (Overview), restaurants.html, customers.html, reviews.html, forecast.html, js/common.js, css/style.css. Bootstrap 5 and Chart.js 4 loaded from CDN. Currency is INR, formatted with the en-IN locale.
