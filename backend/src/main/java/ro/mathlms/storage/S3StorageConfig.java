package ro.mathlms.storage;

import io.minio.MinioClient;
import io.minio.credentials.IamAwsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The S3 client, created only when {@code file.storage.provider=s3} (so dev and tests never need AWS to boot). On the
 * EC2 server no keys are configured: the instance role's temporary credentials are used, refreshed by the client.
 * Static keys ({@code S3_ACCESS_KEY}/{@code S3_SECRET_KEY}) are for running against another S3 (a local S3 mock).
 */
@Configuration
@ConditionalOnProperty(name = "file.storage.provider", havingValue = "s3")
public class S3StorageConfig {

    @Bean
    MinioClient s3Client(@Value("${app.storage.s3.endpoint:https://s3.amazonaws.com}") String endpoint,
                         @Value("${app.storage.s3.region:eu-central-1}") String region,
                         @Value("${app.storage.s3.access-key:}") String accessKey,
                         @Value("${app.storage.s3.secret-key:}") String secretKey) {
        MinioClient.Builder builder = MinioClient.builder().endpoint(endpoint).region(region);
        if (!accessKey.isBlank()) {
            return builder.credentials(accessKey, secretKey).build();
        }
        return builder.credentialsProvider(new IamAwsProvider(null, null)).build();
    }
}
