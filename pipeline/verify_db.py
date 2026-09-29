import sys
from pathlib import Path
import pandas as pd
from sqlalchemy import text

# Ensure the pipeline directory is available for import
sys.path.append(str(Path(__file__).resolve().parent))
from db_config import get_engine


def verify_database():
    print("==================================================")
    print("VERIFYING DATABASE CONTENT")
    print("==================================================")

    engine = get_engine()

    with engine.connect() as conn:
        # 1. Row counts of each table
        print("\n--- [1] Row Counts ---")
        for table in ["orders", "customer_segments", "forecast"]:
            count = conn.execute(text(f"SELECT COUNT(*) FROM {table}")).scalar()
            print(f"Table '{table}': {count} rows")

        # 2. MIN and MAX order_date in orders
        print("\n--- [2] Order Date Range ---")
        date_range = conn.execute(
            text("SELECT MIN(order_date), MAX(order_date) FROM orders")
        ).fetchone()
        print(f"MIN order_date: {date_range[0]} | MAX order_date: {date_range[1]}")

        # 3. Sentiment distribution
        print("\n--- [3] Sentiment Distribution in orders ---")
        sentiment_dist = conn.execute(
            text("""
                SELECT 
                    COALESCE(sentiment, 'NULL') AS sentiment_label, 
                    COUNT(*) AS count 
                FROM orders 
                GROUP BY sentiment_label
                ORDER BY count DESC
            """)
        ).fetchall()
        for row in sentiment_dist:
            print(f"  {row[0]}: {row[1]}")

        # 4. Customer counts per segment
        print("\n--- [4] Customer Counts per Segment ---")
        segment_dist = conn.execute(
            text("""
                SELECT segment, COUNT(*) AS count, ROUND(AVG(total_spend), 2) AS avg_spend
                FROM customer_segments
                GROUP BY segment
                ORDER BY avg_spend DESC
            """)
        ).fetchall()
        for row in segment_dist:
            print(f"  Segment: {row[0]:<12} | Customers: {row[1]:<5} | Avg Spend: INR {row[2]}")

        # 5. First 3 and Last 3 rows of forecast
        print("\n--- [5] Forecast: First 3 & Last 3 rows ---")
        forecast_first3 = pd.read_sql(
            text("SELECT * FROM forecast ORDER BY forecast_date ASC LIMIT 3"), conn
        )
        print("First 3 rows:")
        print(forecast_first3.to_string(index=False))

        forecast_last3 = pd.read_sql(
            text("SELECT * FROM forecast ORDER BY forecast_date DESC LIMIT 3"), conn
        )
        print("\nLast 3 rows:")
        print(forecast_last3.iloc[::-1].to_string(index=False))

        # 6. 3 sample rows from orders
        print("\n--- [6] 3 Sample Rows from orders ---")
        sample_orders = pd.read_sql(
            text("SELECT order_id, order_date, customer_id, restaurant, cuisine, city, order_value, rating, sentiment FROM orders LIMIT 3"),
            conn
        )
        print(sample_orders.to_string(index=False))

    print("\n==================================================")
    print("VERIFICATION COMPLETED")
    print("==================================================")


if __name__ == "__main__":
    verify_database()
