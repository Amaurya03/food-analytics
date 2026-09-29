import os
import random
from datetime import datetime, timedelta
from pathlib import Path
import numpy as np
import pandas as pd


def generate_dataset():
    # Set random seeds for reproducibility
    random.seed(42)
    np.random.seed(42)

    output_dir = Path(__file__).resolve().parent / "data"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_file = output_dir / "food_delivery_raw.csv"

    # Cities and 5 restaurants per city with assigned cuisines
    cities = [
        "Mumbai", "Delhi", "Bengaluru", "Hyderabad",
        "Chennai", "Pune", "Kolkata", "Ahmedabad"
    ]

    city_restaurants = {
        "Mumbai": [
            ("Bombay Canteen", "North Indian"),
            ("Mahesh Lunch Home", "South Indian"),
            ("Ling's Pavilion", "Chinese"),
            ("Pizza Express Fort", "Italian"),
            ("Jumbo King Burger", "Fast Food")
        ],
        "Delhi": [
            ("Karim's Old Delhi", "Biryani"),
            ("Bukhara Grills", "North Indian"),
            ("Berco's Connaught", "Chinese"),
            ("Big Chill Cafe", "Italian"),
            ("Nirula's Treats", "Desserts")
        ],
        "Bengaluru": [
            ("Vidyarthi Bhavan", "South Indian"),
            ("Empire Restaurant", "Biryani"),
            ("Meghana Foods", "North Indian"),
            ("Glen's Bakehouse", "Desserts"),
            ("EatFit Indiranagar", "Healthy")
        ],
        "Hyderabad": [
            ("Paradise Biryani", "Biryani"),
            ("Bawarchi Cross Roads", "Biryani"),
            ("Chutneys Banjara", "South Indian"),
            ("Mainland China", "Chinese"),
            ("Subway Hitec City", "Fast Food")
        ],
        "Chennai": [
            ("Murugan Idli Shop", "South Indian"),
            ("Saravana Bhavan", "South Indian"),
            ("Tuscana Pizzeria", "Italian"),
            ("Copper Chimney", "North Indian"),
            ("The Belgian Waffle", "Desserts")
        ],
        "Pune": [
            ("Vaishali Cafe", "South Indian"),
            ("German Bakery", "Desserts"),
            ("George Restaurant", "North Indian"),
            ("Chung Fa Chinese", "Chinese"),
            ("Salad Days FC Road", "Healthy")
        ],
        "Kolkata": [
            ("Arsalan Park Circus", "Biryani"),
            ("Peter Cat Park Street", "North Indian"),
            ("Mocambo Dining", "Italian"),
            ("Tung Fong", "Chinese"),
            ("Flurys Tearoom", "Desserts")
        ],
        "Ahmedabad": [
            ("Agashiye Heritage", "North Indian"),
            ("Gordhan Thal", "North Indian"),
            ("Tomato's Diner", "Fast Food"),
            ("Little Italy Navrangpura", "Italian"),
            ("Super Salads Bodakdev", "Healthy")
        ]
    }

    # Cuisine price distributions (min, max, mean, std) in INR
    cuisine_pricing = {
        "Fast Food": (120, 480, 240, 60),
        "Desserts": (120, 500, 260, 70),
        "South Indian": (140, 580, 280, 80),
        "Healthy": (200, 650, 360, 90),
        "Chinese": (250, 850, 480, 120),
        "Biryani": (280, 980, 520, 130),
        "North Indian": (300, 1050, 560, 150),
        "Italian": (350, 1200, 680, 180)
    }

    # Review text templates
    positive_reviews = [
        "The food was absolutely delicious and arrived piping hot! Excellent packaging.",
        "Loved the flavours and prompt delivery. Definitely ordering again.",
        "Generous portion sizes and great value for money. Very satisfied.",
        "Super fast delivery and fresh ingredients. The taste was spot on!",
        "One of the best meals I've had recently! Courteous delivery driver too.",
        "Delicious, authentic taste and sturdy spill-proof container.",
        "Always consistent quality and taste. Highly recommended for family dinners."
    ]

    neutral_reviews = [
        "Food was decent but delivery took slightly longer than expected.",
        "Average taste and portion size, okay for the price.",
        "Packaging was neat but the meal was a bit bland. Fair overall.",
        "Not bad, but could use more seasoning. Delivery was on time.",
        "Standard meal quality. Met expectations but nothing extraordinary."
    ]

    negative_reviews = [
        "Terrible experience, food was cold and delivered over an hour late.",
        "Completely overpriced for such tiny portions, very disappointed.",
        "Spilled gravy in the bag and missing items. Would not recommend.",
        "Taste was stale and lacked any fresh flavour. Poor quality.",
        "Rude delivery partner and mediocre, soggy food."
    ]

    # Customers setup: 1500 customers
    num_customers = 1500
    customer_ids = [1000 + i for i in range(1, num_customers + 1)]
    # Assign home city to each customer
    customer_home_cities = {
        cid: np.random.choice(cities, p=[0.18, 0.18, 0.16, 0.12, 0.10, 0.10, 0.08, 0.08])
        for cid in customer_ids
    }

    # Skewed order activity using Pareto distribution
    activity_weights = np.random.pareto(a=1.4, size=num_customers) + 0.1
    activity_probs = activity_weights / activity_weights.sum()

    # Generate dates from 2024-10-01 to 2026-09-28
    start_date = datetime(2024, 10, 1)
    end_date = datetime(2026, 9, 28)
    total_days = (end_date - start_date).days + 1
    date_list = [start_date + timedelta(days=i) for i in range(total_days)]

    # Calculate day weights with growth trend, weekend boost, and seasonal bumps
    day_weights = []
    for d in date_list:
        day_index = (d - start_date).days
        # Growth trend: ~40% growth over 2 years
        growth = 1.0 + (day_index / total_days) * 0.40
        # Weekend boost (Friday=4, Saturday=5, Sunday=6)
        weekday = d.weekday()
        if weekday in (5, 6):
            dow_factor = 1.45
        elif weekday == 4:
            dow_factor = 1.20
        else:
            dow_factor = 1.00
        # Festive / seasonal bumps (Oct/Nov Diwali/festivals, Dec/Jan New Year)
        month = d.month
        day = d.day
        seasonal = 1.0
        if month in (10, 11):
            seasonal = 1.15
        elif month == 12 and day >= 20:
            seasonal = 1.25
        elif month == 1 and day <= 5:
            seasonal = 1.20

        weight = growth * dow_factor * seasonal
        day_weights.append(weight)

    day_weights = np.array(day_weights)
    date_probs = day_weights / day_weights.sum()

    target_base_orders = 19800
    chosen_dates = np.random.choice(date_list, size=target_base_orders, p=date_probs)
    # Sort dates chronologically for natural order flow
    chosen_dates = sorted(chosen_dates)

    chosen_customers = np.random.choice(customer_ids, size=target_base_orders, p=activity_probs)

    orders_data = []

    for i in range(target_base_orders):
        order_id = 100001 + i
        order_date = chosen_dates[i].strftime("%Y-%m-%d")
        cid = chosen_customers[i]
        home_city = customer_home_cities[cid]

        # 94% order from home city, 6% order while traveling to another city
        if np.random.rand() < 0.94:
            city = home_city
        else:
            other_cities = [c for c in cities if c != home_city]
            city = np.random.choice(other_cities)

        # Pick one restaurant in that city
        rest_name, cuisine = random.choice(city_restaurants[city])

        # Generate order value based on cuisine
        min_p, max_p, mean_p, std_p = cuisine_pricing[cuisine]
        val = np.random.normal(loc=mean_p, scale=std_p)
        order_val = float(np.clip(val, min_p, max_p))
        order_value = round(order_val, 2)

        # 70% have rating & review, 30% empty
        if np.random.rand() < 0.70:
            # Rating distribution skewed positive (e.g. 5: 45%, 4: 30%, 3: 13%, 2: 7%, 1: 5%)
            rating = int(np.random.choice([1, 2, 3, 4, 5], p=[0.05, 0.07, 0.13, 0.30, 0.45]))
            if rating in (4, 5):
                review_text = random.choice(positive_reviews)
            elif rating in (1, 2):
                review_text = random.choice(negative_reviews)
            else:
                review_text = random.choice(neutral_reviews)
        else:
            rating = None
            review_text = None

        orders_data.append({
            "order_id": order_id,
            "order_date": order_date,
            "customer_id": cid,
            "restaurant": rest_name,
            "cuisine": cuisine,
            "city": city,
            "order_value": order_value,
            "rating": rating,
            "review_text": review_text
        })

    df = pd.DataFrame(orders_data)

    # Introduce deliberate mess:
    # 1. About 1% exact duplicate rows (around 198 rows)
    num_duplicates = int(len(df) * 0.01)
    dup_indices = np.random.choice(df.index, size=num_duplicates, replace=False)
    dup_rows = df.loc[dup_indices].copy()

    # 2. About 1% messy city values (wrong casing, extra spaces)
    num_messy_cities = int(len(df) * 0.01)
    messy_indices = np.random.choice(df.index, size=num_messy_cities, replace=False)
    for idx in messy_indices:
        orig = df.at[idx, "city"]
        variants = [
            f" {orig.lower()} ",
            orig.upper(),
            f"  {orig}  ",
            orig.lower(),
            f"{orig}   "
        ]
        df.at[idx, "city"] = random.choice(variants)

    # 3. About 0.5% zero or negative order_value (around 100 rows)
    num_invalid_values = int(len(df) * 0.005)
    invalid_indices = np.random.choice(df.index, size=num_invalid_values, replace=False)
    for idx in invalid_indices:
        df.at[idx, "order_value"] = float(random.choice([0.0, -25.50, -100.0, 0.0]))

    # Append duplicate rows to create exact duplicate rows
    df = pd.concat([df, dup_rows], ignore_index=True)

    # Save to CSV
    df.to_csv(output_file, index=False)

    # Print summary
    print(f"Data generation complete!")
    print(f"Total rows: {len(df)}")
    print(f"Date range: {df['order_date'].min()} to {df['order_date'].max()}")
    print(f"Unique customers: {df['customer_id'].nunique()}")
    print(f"Saved raw file to: {output_file}")


if __name__ == "__main__":
    generate_dataset()
