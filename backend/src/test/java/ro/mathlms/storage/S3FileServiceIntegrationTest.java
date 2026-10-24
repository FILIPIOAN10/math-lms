package ro.mathlms.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.FileNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F11 against a real S3 API (Adobe S3Mock in Docker; the MinIO images are not pullable here): the same contract as
 * the disk provider — names, not URLs; the folder becomes the key prefix; a crafted name never reaches another object.
 */
@Testcontainers
class S3FileServiceIntegrationTest {

    private static final String BUCKET = "mathlms-test";

    @Container
    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:3.12.0")
            .withEnv("initialBuckets", BUCKET)
            .withExposedPorts(9090);

    private static S3FileService service;

    @BeforeAll
    static void connect() {
        MinioClient client = MinioClient.builder()
                .endpoint("http://" + S3.getHost() + ":" + S3.getMappedPort(9090))
                .region("eu-central-1")
                .credentials("test", "test")
                .build();
        service = new S3FileService(client, BUCKET);
    }

    private static MockMultipartFile photo(String content) {
        return new MockMultipartFile("file", "rezolvare.jpg", "image/jpeg", content.getBytes());
    }

    @Test
    void storesUnderTheFolderAndHandsBackOnlyTheName() throws Exception {
        String name = service.uploadImage("uploads/quiz-photos", photo("poza"));

        assertThat(name).endsWith(".jpg").doesNotContain("/").doesNotStartWith("http");
        Resource loaded = service.loadImage("uploads/quiz-photos", name);
        assertThat(loaded.getContentAsByteArray()).isEqualTo("poza".getBytes());
        assertThat(loaded.getFilename()).isEqualTo(name);
        // another folder is another prefix: the same name is not found there
        assertThatThrownBy(() -> service.loadImage("uploads/question-images", name))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void copiesAndDeletes() throws Exception {
        String name = service.uploadImage("uploads/question-images", photo("figura"));

        String copy = service.copyImage("uploads/question-images", name);
        assertThat(copy).isNotEqualTo(name);
        assertThat(service.loadImage("uploads/question-images", copy).getContentAsByteArray()).isEqualTo("figura".getBytes());

        service.deleteImage("uploads/question-images", name);
        assertThatThrownBy(() -> service.loadImage("uploads/question-images", name)).isInstanceOf(FileNotFoundException.class);
        assertThatCode(() -> service.deleteImage("uploads/question-images", name)).doesNotThrowAnyException(); // already gone
    }

    @Test
    void aCraftedNameIsRefused() {
        assertThatThrownBy(() -> service.loadImage("uploads/quiz-photos", "../ai-pages/x.jpg"))
                .isInstanceOf(FileNotFoundException.class);
        assertThatThrownBy(() -> service.deleteImage("uploads/quiz-photos", "a/b.jpg"))
                .isInstanceOf(FileNotFoundException.class);
    }

    @Test
    void theKeyIgnoresLeadingAndTrailingSlashes() throws Exception {
        assertThat(S3FileService.key("./uploads/quiz-photos/", "a.jpg")).isEqualTo("uploads/quiz-photos/a.jpg");
        assertThat(S3FileService.key("/app/uploads/x", "a.jpg")).isEqualTo("app/uploads/x/a.jpg");
    }
}
