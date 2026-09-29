import sys
from pathlib import Path
from sqlalchemy import text

# Ensure the pipeline directory is available in the module search path
sys.path.append(str(Path(__file__).resolve().parent))

from db_config import get_engine


def test_connection():
    engine = get_engine()
    with engine.connect() as connection:
        version_result = connection.execute(text("SELECT VERSION()")).scalar()
        print(f"Connected. MySQL version: {version_result}")

        cipher_result = connection.execute(text("SHOW STATUS LIKE 'Ssl_cipher'")).fetchone()
        if cipher_result:
            cipher_value = cipher_result[1]
            print(f"SSL cipher in use: {cipher_value}")
        else:
            print("Could not retrieve SSL cipher status.")


if __name__ == "__main__":
    test_connection()
