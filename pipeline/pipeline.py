import os
import sys
from pathlib import Path
import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error
from sklearn.preprocessing import StandardScaler
from sqlalchemy import text
from textblob import TextBlob

# Ensure the pipeline directory is available for import
sys.path.append(str(Path(__file__).resolve().parent))
from db_config import get_engine


def clean_title(val):
    """
    Standardize strings to title case while trimming whitespace
    and preserving apostrophes correctly (e.g. Karim's).
    """
    if pd.isna(val):
        return val
    s = str(val).strip()
    words = s.split()
    cleaned_words = []
    for w in words:
        if "'" in w:
            sub = w.split("'", 1)
            cleaned_words.append(sub[0].capitalize() + "'" + sub[1].lower())
        else:
            cleaned_words.append(w.capitalize())
    return " ".join(cleaned_words)


def run_pipeline():
    print("==================================================")
    print("STARTING DATA PIPELINE")
    print("==================================================")

    # 1. LOAD DATA
    raw_path = Path(__file__).resolve().parent / "data" / "food_delivery_raw.csv"
    print(f"\n[Step 1] Loading raw data from: {raw_path}")
    df = pd.read_csv(raw_path)
    initial_count = len(df)
    print(f"Loaded {initial_count} rows.")

    # 2. CLEAN DATA
    print("\n[Step 2] Cleaning data...")
    # Drop exact duplicate rows
    before = len(df)
    df = df.drop_duplicates()
    print(f" - Dropped exact duplicates: {before} -> {len(df)} rows")

    # Drop duplicate order_id
    before = len(df)
    df = df.drop_duplicates(subset=["order_id"])
    print(f" - Dropped duplicate order_id: {before} -> {len(df)} rows")

    # Trim whitespace and standardize city, restaurant, cuisine to Title Case
    df["city"] = df["city"].apply(clean_title)
    df["restaurant"] = df["restaurant"].apply(clean_title)
    df["cuisine"] = df["cuisine"].apply(clean_title)
    print(" - Standardized city, restaurant, and cuisine to Title Case.")

    # Parse order_date as date
    df["order_date"] = pd.to_datetime(df["order_date"]).dt.date
    print(f" - Parsed order_date (Range: {df['order_date'].min()} to {df['order_date'].max()})")

    # Drop rows with missing or non-positive order_value
    before = len(df)
    df = df[df["order_value"].notna() & (df["order_value"] > 0)].copy()
    print(f" - Dropped non-positive or missing order_value: {before} -> {len(df)} rows")

    # Treat empty or whitespace review_text as NULL (None)
    def clean_text_field(x):
        if pd.isna(x) or x is None:
            return None
        s = str(x).strip()
        return s if s != "" else None

    df["review_text"] = df["review_text"].apply(clean_text_field)
    # Ensure rating is None when pd.isna, else integer
    df["rating"] = df["rating"].apply(lambda x: None if pd.isna(x) or x is None else int(x))
    print(f" - Total clean orders: {len(df)}")

    # 3. SENTIMENT ANALYSIS
    print("\n[Step 3] Computing sentiment analysis with TextBlob...")
    scores = []
    sentiments = []

    for text_val in df["review_text"]:
        if pd.isna(text_val) or text_val is None or str(text_val).strip() == "":
            scores.append(None)
            sentiments.append(None)
        else:
            polarity = TextBlob(str(text_val)).sentiment.polarity
            scores.append(round(polarity, 4))
            if polarity > 0.1:
                sentiments.append("positive")
            elif polarity < -0.1:
                sentiments.append("negative")
            else:
                sentiments.append("neutral")

    df["sentiment_score"] = scores
    df["sentiment"] = sentiments

    sentiment_counts = df["sentiment"].value_counts(dropna=False).to_dict()
    print(f" - Sentiment distribution: {sentiment_counts}")

    # 4. CUSTOMER SEGMENTS (K-Means)
    print("\n[Step 4] Performing Customer Segmentation...")
    # Per customer: total_spend, order_count, avg_order_value
    customer_agg = df.groupby("customer_id").agg(
        total_spend=("order_value", "sum"),
        order_count=("order_id", "count"),
        avg_order_value=("order_value", "mean")
    ).reset_index()

    customer_agg["total_spend"] = customer_agg["total_spend"].round(2)
    customer_agg["avg_order_value"] = customer_agg["avg_order_value"].round(2)

    # Scale total_spend and order_count
    scaler = StandardScaler()
    scaled_features = scaler.fit_transform(customer_agg[["total_spend", "order_count"]])

    # Run KMeans
    kmeans = KMeans(n_clusters=3, random_state=42, n_init=10)
    customer_agg["cluster"] = kmeans.fit_predict(scaled_features)

    # Rank clusters by total_spend cluster centers
    # Inverse transform cluster centers to get original scale total_spend
    cluster_centers = scaler.inverse_transform(kmeans.cluster_centers_)
    center_total_spends = cluster_centers[:, 0]
    sorted_cluster_indices = np.argsort(center_total_spends)

    # Lowest = Occasional, Middle = Regular, Highest = High Value
    cluster_mapping = {
        sorted_cluster_indices[0]: "Occasional",
        sorted_cluster_indices[1]: "Regular",
        sorted_cluster_indices[2]: "High Value"
    }
    customer_agg["segment"] = customer_agg["cluster"].map(cluster_mapping)
    df_customer_segments = customer_agg[["customer_id", "total_spend", "order_count", "avg_order_value", "segment"]].copy()

    segment_counts = df_customer_segments["segment"].value_counts().to_dict()
    print(f" - Customer segment counts: {segment_counts}")

    # 5. FORECAST
    print("\n[Step 5] Building order forecast model...")
    daily_orders = df.groupby("order_date").size().reset_index(name="orders")
    daily_orders["order_date"] = pd.to_datetime(daily_orders["order_date"])

    min_date = daily_orders["order_date"].min()
    max_date = daily_orders["order_date"].max()
    full_date_range = pd.date_range(min_date, max_date, freq="D")

    daily_orders = (
        daily_orders.set_index("order_date")
        .reindex(full_date_range, fill_value=0)
        .rename_axis("order_date")
        .reset_index()
    )

    daily_orders["day_index"] = (daily_orders["order_date"] - min_date).dt.days
    daily_orders["day_of_week"] = daily_orders["order_date"].dt.dayofweek

    # Day of week one-hot encoding (dow_0 to dow_6)
    for dow in range(7):
        daily_orders[f"dow_{dow}"] = (daily_orders["day_of_week"] == dow).astype(int)

    feature_cols = ["day_index"] + [f"dow_{dow}" for dow in range(1, 7)]  # drop first day_of_week for regression

    # Hold out last 28 days
    holdout_days = 28
    train_df = daily_orders.iloc[:-holdout_days].copy()
    test_df = daily_orders.iloc[-holdout_days:].copy()

    lr_eval = LinearRegression()
    lr_eval.fit(train_df[feature_cols], train_df["orders"])
    preds_eval = lr_eval.predict(test_df[feature_cols])
    mae = mean_absolute_error(test_df["orders"], preds_eval)
    print(f" - Holdout 28-day MAE: {mae:.2f}")

    # Retrain on full dataset
    lr_full = LinearRegression()
    lr_full.fit(daily_orders[feature_cols], daily_orders["orders"])

    # Predict next 30 days
    last_date = daily_orders["order_date"].max()
    future_dates = pd.date_range(last_date + pd.Timedelta(days=1), periods=30, freq="D")
    future_df = pd.DataFrame({"order_date": future_dates})
    future_df["day_index"] = (future_df["order_date"] - min_date).dt.days
    future_df["day_of_week"] = future_df["order_date"].dt.dayofweek
    for dow in range(7):
        future_df[f"dow_{dow}"] = (future_df["day_of_week"] == dow).astype(int)

    future_preds = lr_full.predict(future_df[feature_cols])
    future_preds_clean = np.clip(np.round(future_preds), 0, None).astype(int)

    df_forecast = pd.DataFrame({
        "forecast_date": future_df["order_date"].dt.date,
        "predicted_orders": future_preds_clean
    })
    print(f" - Forecast generated for 30 days: {df_forecast['forecast_date'].min()} to {df_forecast['forecast_date'].max()}")
    print(f" - Predicted daily orders average: {df_forecast['predicted_orders'].mean():.1f}")

    # 6. LOAD INTO MYSQL
    print("\n[Step 6] Connecting to MySQL and loading data...")
    engine = get_engine()

    create_orders_table = """
    CREATE TABLE orders (
        order_id INT PRIMARY KEY,
        order_date DATE,
        customer_id INT,
        restaurant VARCHAR(100),
        cuisine VARCHAR(50),
        city VARCHAR(50),
        order_value DECIMAL(10,2),
        rating TINYINT NULL,
        review_text TEXT NULL,
        sentiment VARCHAR(10) NULL,
        sentiment_score DOUBLE NULL,
        INDEX idx_orders_order_date (order_date),
        INDEX idx_orders_city (city),
        INDEX idx_orders_cuisine (cuisine),
        INDEX idx_orders_restaurant (restaurant),
        INDEX idx_orders_customer_id (customer_id)
    );
    """

    create_customer_segments_table = """
    CREATE TABLE customer_segments (
        customer_id INT PRIMARY KEY,
        total_spend DECIMAL(12,2),
        order_count INT,
        avg_order_value DECIMAL(10,2),
        segment VARCHAR(20)
    );
    """

    create_forecast_table = """
    CREATE TABLE forecast (
        forecast_date DATE PRIMARY KEY,
        predicted_orders INT
    );
    """

    with engine.begin() as conn:
        print(" - Dropping existing tables if any...")
        conn.execute(text("DROP TABLE IF EXISTS orders;"))
        conn.execute(text("DROP TABLE IF EXISTS customer_segments;"))
        conn.execute(text("DROP TABLE IF EXISTS forecast;"))

        print(" - Creating tables from Contract specification...")
        conn.execute(text(create_orders_table))
        conn.execute(text(create_customer_segments_table))
        conn.execute(text(create_forecast_table))

    # Insert data using pandas to_sql with if_exists="append" and chunksize
    print(" - Inserting orders into MySQL...")
    orders_to_insert = df[[
        "order_id", "order_date", "customer_id", "restaurant",
        "cuisine", "city", "order_value", "rating", "review_text",
        "sentiment", "sentiment_score"
    ]].copy()

    orders_to_insert.to_sql(
        name="orders",
        con=engine,
        if_exists="append",
        index=False,
        chunksize=1000
    )
    print(f"   -> Inserted {len(orders_to_insert)} rows into orders.")

    print(" - Inserting customer_segments into MySQL...")
    df_customer_segments.to_sql(
        name="customer_segments",
        con=engine,
        if_exists="append",
        index=False,
        chunksize=1000
    )
    print(f"   -> Inserted {len(df_customer_segments)} rows into customer_segments.")

    print(" - Inserting forecast into MySQL...")
    df_forecast.to_sql(
        name="forecast",
        con=engine,
        if_exists="append",
        index=False,
        chunksize=1000
    )
    print(f"   -> Inserted {len(df_forecast)} rows into forecast.")

    print("\n==================================================")
    print("DATA PIPELINE COMPLETED SUCCESSFULLY!")
    print("==================================================")


if __name__ == "__main__":
    run_pipeline()
