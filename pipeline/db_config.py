import os
from pathlib import Path
from dotenv import load_dotenv
from sqlalchemy import create_engine
from sqlalchemy.engine import URL


def get_engine():
    """
    Loads database configuration from the .env file located at the project root
    and returns a SQLAlchemy engine configured with SSL (without certificate verification).
    """
    # Locate .env in the project root relative to this file
    project_root = Path(__file__).resolve().parent.parent
    env_path = project_root / ".env"
    load_dotenv(dotenv_path=env_path)

    db_host = os.getenv("DB_HOST", "localhost")
    db_port = os.getenv("DB_PORT", "3306")
    db_name = os.getenv("DB_NAME", "")
    db_user = os.getenv("DB_USER", "")
    db_password = os.getenv("DB_PASSWORD", "")

    connection_url = URL.create(
        drivername="mysql+pymysql",
        username=db_user,
        password=db_password,
        host=db_host,
        port=int(db_port) if db_port and db_port.isdigit() else None,
        database=db_name,
    )

    engine = create_engine(
        connection_url,
        connect_args={
            "ssl": {
                "check_hostname": False
            }
        }
    )

    return engine
