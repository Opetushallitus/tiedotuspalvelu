package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tiedotuspalvelu.henkilo-import.enabled", havingValue = "true")
public class HenkiloImportTask {
  private static final String MANIFEST_KEY = "fulldump/henkilo/v1/manifest.json";
  private static final String HENKILO_CSV_KEY = "fulldump/henkilo/v1/henkilo.csv";

  @Value("${tiedotuspalvelu.henkilo-import.bucket-name}")
  private String bucketName;

  @Qualifier(HenkiloImportConfiguration.QUALIFIER)
  private final S3AsyncClient onrExportS3Client;

  private final HenkiloTableLoader henkiloTableLoader;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public void execute() {
    log.info("Running HenkiloImportTask");
    importIfChanged();
    log.info("Finished running HenkiloImportTask");
  }

  private void importIfChanged() {
    var manifestETag = getEtag(bucketName, MANIFEST_KEY);
    var lastETag = currentlyImportedETag();
    if (lastETag.isPresent() && lastETag.get().equals(manifestETag)) {
      log.info("Henkilo manifest unchanged (etag {}), skipping import", manifestETag);
      return;
    }
    log.info(
        "Henkilo manifest changed (was {}, now {}), importing", lastETag.orElse("-"), manifestETag);

    var manifest = readManifest(MANIFEST_KEY);
    verifyManifestHasHenkiloCsv(manifest, HENKILO_CSV_KEY);

    var rowCount = henkiloTableLoader.load(HENKILO_CSV_KEY);
    recordImport(manifestETag, rowCount);
    log.info("Imported {} henkilos (manifest etag {})", rowCount, manifestETag);
  }

  private String getEtag(String bucketName, String objectKey) {
    log.info("Fetching ETag for {}/{}", bucketName, objectKey);
    return onrExportS3Client.headObject(b -> b.bucket(bucketName).key(objectKey)).join().eTag();
  }

  private Optional<String> currentlyImportedETag() {
    return jdbcTemplate
        .queryForList("SELECT manifest_etag FROM henkilo_import WHERE id = 1", String.class)
        .stream()
        .findFirst();
  }

  private ExportManifest readManifest(String objectKey) {
    log.info("Fetching object {}/{}", bucketName, objectKey);
    try (ResponseInputStream<GetObjectResponse> stream =
        onrExportS3Client
            .getObject(
                b -> b.bucket(bucketName).key(objectKey),
                AsyncResponseTransformer.toBlockingInputStream())
            .join()) {
      return objectMapper.readValue(stream, ExportManifest.class);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read henkilo export manifest", e);
    }
  }

  private void verifyManifestHasHenkiloCsv(ExportManifest manifest, String expectedKey) {
    var hasFile = manifest.exportFiles().stream().anyMatch(f -> expectedKey.equals(f.objectKey()));
    if (!hasFile) {
      throw new RuntimeException(
          "Henkilo export manifest does not list " + HENKILO_CSV_KEY + ": " + manifest);
    }
  }

  private void recordImport(String manifestETag, long rowCount) {
    jdbcTemplate.update(
        """
        INSERT INTO henkilo_import (id, manifest_etag, row_count, imported_at)
        VALUES (1, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
          manifest_etag = EXCLUDED.manifest_etag,
          row_count = EXCLUDED.row_count,
          imported_at = EXCLUDED.imported_at
        """,
        manifestETag,
        rowCount,
        OffsetDateTime.now());
  }

  record ExportManifest(List<ExportFileDetails> exportFiles) {}

  record ExportFileDetails(String objectKey, String objectVersion) {}
}
