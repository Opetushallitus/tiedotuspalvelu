package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
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
  private final JdbcTemplate jdbcTemplate;
  private final DataSource dataSource;
  private final TransactionTemplate transactionTemplate;

  @Override
  public long load(String objectKey) {
    var CSV_RESOURCE = "/s3/oppijanumerorekisteri-export/" + objectKey;
    log.info("Loading henkilos from classpath resource {}", CSV_RESOURCE);
    return transactionTemplate.execute(
        status -> {
          jdbcTemplate.execute("TRUNCATE TABLE henkilo");
          var connection = DataSourceUtils.getConnection(dataSource);
          try (var stream = JdbcCopyHenkiloTableLoader.class.getResourceAsStream(CSV_RESOURCE)) {
            var copyManager = new CopyManager(connection.unwrap(BaseConnection.class));
            return copyManager.copyIn(
                "COPY henkilo (oid) FROM STDIN WITH (FORMAT CSV, HEADER TRUE)", stream);
          } catch (Exception e) {
            throw new RuntimeException(e);
          } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
          }
        });
  }
}
