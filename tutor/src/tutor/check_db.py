"""Can we reach the LMS database, and does it have the pgvector extension and the tutor table?

Run:  uv run python -m tutor.check_db
"""

import psycopg

from tutor.config import get_settings


def main() -> None:
    dsn = get_settings().psycopg_dsn
    with psycopg.connect(dsn, autocommit=True) as conn:
        print("SELECT 1            ->", conn.execute("SELECT 1").fetchone()[0])

        row = conn.execute("SELECT extversion FROM pg_extension WHERE extname = 'vector'").fetchone()
        if row is None:
            print("pgvector extension  -> MISSING (is the database the pgvector image, and did Flyway run V36?)")
            raise SystemExit(1)
        print("pgvector extension  ->", row[0])

        # Sanity check that vector math works, not only that the extension exists.
        distance = conn.execute("SELECT '[1,0]'::vector <=> '[0,1]'::vector").fetchone()[0]
        print("cosine distance     ->", distance, "(orthogonal vectors should be 1)")

        table = conn.execute("SELECT to_regclass('public.tutor_chunks')").fetchone()[0]
        print("tutor_chunks table  ->", table or "MISSING (start the backend once so Flyway applies V36)")


if __name__ == "__main__":
    main()
