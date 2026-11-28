package ro.mathlms.recap;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Audit 2026-11-27 L2: each file of the collection's zip had a cap, the whole did not - 3000 drawings of 4 MB each
 * (a few MB on the wire, zeros compress well) would have been held in memory at once.
 */
class RecapImportServiceTest {

    private static MockMultipartFile zipOf(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "culegere.zip", "application/zip", bytes.toByteArray());
    }

    @Test
    void whatTheZipUnpacksToAltogetherIsCapped() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("culegere.json", new byte[100]);
        entries.put("figures/a.png", new byte[600]);
        entries.put("figures/b.png", new byte[600]);
        MockMultipartFile zip = zipOf(entries);

        assertThat(RecapImportService.unzip(zip, 2_000)).containsOnlyKeys("culegere.json", "figures/a.png", "figures/b.png");
        assertThatThrownBy(() -> RecapImportService.unzip(zip, 1_000))
                .isInstanceOf(InvalidRecapException.class)
                .hasMessageContaining("prea mare");
    }

    @Test
    void theRealCapLeavesRoomForTheWholeBook() {
        // 387 exercises with a drawing of up to ~200 KB each and a 20 MB culegere.json fit many times over
        assertThat(RecapImportService.MAX_TOTAL_BYTES).isGreaterThanOrEqualTo(100L * 1024 * 1024);
        assertThat(RecapImportService.MAX_TOTAL_BYTES).isLessThan(RecapImportService.MAX_ENTRIES * RecapImportService.MAX_IMAGE_BYTES);
    }
}
