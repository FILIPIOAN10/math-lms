package ro.mathlms;

import org.testcontainers.utility.DockerImageName;

/**
 * The images the integration tests run against: the same ones as docker-compose, in one place.
 */
public final class TestImages {

	/**
	 * Postgres with the pgvector extension: the tutor migration (V36) needs {@code CREATE EXTENSION vector}.
	 * Declared as a substitute for "postgres" so Testcontainers accepts it in a PostgreSQLContainer.
	 */
	public static final DockerImageName POSTGRES =
			DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

	private TestImages() {
	}

}
