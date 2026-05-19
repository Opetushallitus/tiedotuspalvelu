package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluApiTest;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
    properties = {
      "tiedotuspalvelu.henkilo-import.enabled=true",
      "tiedotuspalvelu.henkilo-import.bucket-name=oppijanumerorekisteri-export",
      "tiedotuspalvelu.henkilo-import.loader=jdbc-copy"
    })
public class HenkiloImportTaskTest extends TiedotuspalveluApiTest {

  private static final Path S3_ROOT = classpathS3Root();
  private static final String BUCKET = "oppijanumerorekisteri-export";
  private static final String MANIFEST_KEY = "fulldump/henkilo/v1/manifest.json";

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("tiedotuspalvelu.henkilo-import.local-fs-root", () -> S3_ROOT.toString());
  }

  @Autowired private HenkiloImportTask henkiloImportTask;

  @MockitoBean(name = "onrExportS3Client")
  private S3AsyncClient onrExportS3Client;

  @BeforeEach
  public void setup() {
    jdbc.execute("TRUNCATE TABLE henkilo");
    jdbc.execute("DELETE FROM henkilo_import");
    wireS3Mock();
  }

  @Test
  public void importsHenkilosFromCsv() throws Exception {
    henkiloImportTask.execute();

    var oids = jdbc.queryForList("SELECT oid FROM henkilo ORDER BY oid", String.class);
    assertThat(oids)
        .containsExactly(
            "1.2.246.562.24.00000000001",
            "1.2.246.562.24.00000000002",
            "1.2.246.562.24.00000000003");

    var rowCount =
        jdbc.queryForObject("SELECT row_count FROM henkilo_import WHERE id = 1", Long.class);
    assertThat(rowCount).isEqualTo(3L);

    var storedEtag =
        jdbc.queryForObject("SELECT manifest_etag FROM henkilo_import WHERE id = 1", String.class);
    assertThat(storedEtag).isEqualTo(manifestEtag());
  }

  @Test
  public void skipsImportWhenManifestEtagUnchanged() {
    henkiloImportTask.execute();
    var firstImportedAt =
        jdbc.queryForObject(
            "SELECT imported_at FROM henkilo_import WHERE id = 1", java.time.OffsetDateTime.class);

    henkiloImportTask.execute();

    var secondImportedAt =
        jdbc.queryForObject(
            "SELECT imported_at FROM henkilo_import WHERE id = 1", java.time.OffsetDateTime.class);
    assertThat(secondImportedAt).isEqualTo(firstImportedAt);
  }

  @Test
  public void reimportsWhenStoredEtagDoesNotMatch() {
    henkiloImportTask.execute();
    jdbc.update("UPDATE henkilo_import SET manifest_etag = 'stale' WHERE id = 1");

    henkiloImportTask.execute();

    var storedEtag =
        jdbc.queryForObject("SELECT manifest_etag FROM henkilo_import WHERE id = 1", String.class);
    assertThat(storedEtag).isEqualTo(manifestEtag());
  }

  @Test
  public void taskEmitsAlarmLogLine(CapturedOutput output) {
    henkiloImportTask.execute();
    assertThat(output).contains("Finished running HenkiloImportTask");
  }

  @SuppressWarnings("unchecked")
  private void wireS3Mock() {
    when(onrExportS3Client.headObject(any(Consumer.class)))
        .thenAnswer(
            invocation -> {
              var builder = HeadObjectRequest.builder();
              ((Consumer<HeadObjectRequest.Builder>) invocation.getArgument(0)).accept(builder);
              var req = builder.build();
              var bytes = Files.readAllBytes(S3_ROOT.resolve(req.bucket()).resolve(req.key()));
              return CompletableFuture.completedFuture(
                  HeadObjectResponse.builder().eTag("\"" + md5Hex(bytes) + "\"").build());
            });
    when(onrExportS3Client.getObject(any(Consumer.class), any(AsyncResponseTransformer.class)))
        .thenAnswer(
            invocation -> {
              var builder = GetObjectRequest.builder();
              ((Consumer<GetObjectRequest.Builder>) invocation.getArgument(0)).accept(builder);
              var req = builder.build();
              var bytes = Files.readAllBytes(S3_ROOT.resolve(req.bucket()).resolve(req.key()));
              var response = GetObjectResponse.builder().contentLength((long) bytes.length).build();
              var stream =
                  new ResponseInputStream<>(
                      response, AbortableInputStream.create(new ByteArrayInputStream(bytes)));
              return CompletableFuture.completedFuture(stream);
            });
  }

  private static String manifestEtag() {
    try {
      return md5Hex(Files.readAllBytes(S3_ROOT.resolve(BUCKET).resolve(MANIFEST_KEY)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static Path classpathS3Root() {
    try {
      var url = HenkiloImportTaskTest.class.getResource("/s3");
      if (url == null) {
        throw new IllegalStateException("Missing classpath resource /s3");
      }
      return Path.of(url.toURI());
    } catch (java.net.URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  private static String md5Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
