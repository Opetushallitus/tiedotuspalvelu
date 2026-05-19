package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "tiedotuspalvelu.henkilo-import.loader", havingValue = "jdbc-copy")
public class JdbcCopyHenkiloTableLoader implements HenkiloTableLoader {

  @Value("${tiedotuspalvelu.henkilo-import.local-fs-root}")
  private String localFsRoot;

  private final JdbcTemplate jdbcTemplate;
  private final DataSource dataSource;
  private final TransactionTemplate transactionTemplate;

  @Override
  public long load(String bucketName, String objectKey) {
    var path = Path.of(localFsRoot, bucketName, objectKey);
    log.info("Loading henkilos from local file {}", path);
    return transactionTemplate.execute(
        status -> {
          jdbcTemplate.execute("TRUNCATE TABLE henkilo");
          var connection = DataSourceUtils.getConnection(dataSource);
          try (InputStream stream = Files.newInputStream(path)) {
            var copyManager = new CopyManager(connection.unwrap(BaseConnection.class));
            return copyManager.copyIn(
                "COPY henkilo (oid) FROM STDIN WITH (FORMAT CSV, HEADER TRUE)", stream);
          } catch (IOException | java.sql.SQLException e) {
            throw new IllegalStateException("Failed to copy henkilos into database", e);
          } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
          }
        });
  }
}
