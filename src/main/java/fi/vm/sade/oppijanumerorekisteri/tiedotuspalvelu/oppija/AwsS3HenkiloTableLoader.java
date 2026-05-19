package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.regions.Region;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "tiedotuspalvelu.henkilo-import.loader",
    havingValue = "aws-s3",
    matchIfMissing = true)
public class AwsS3HenkiloTableLoader implements HenkiloTableLoader {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  @Override
  public long load(String bucketName, String objectKey) {
    return transactionTemplate.execute(
        status -> {
          jdbcTemplate.execute("TRUNCATE TABLE henkilo");
          var importResult =
              jdbcTemplate.queryForObject(
                  "SELECT aws_s3.table_import_from_s3(?, ?, ?, aws_commons.create_s3_uri(?, ?, ?))",
                  String.class,
                  "henkilo",
                  "oid",
                  "(FORMAT CSV, HEADER true)",
                  bucketName,
                  objectKey,
                  Region.EU_WEST_1.id());
          log.info("aws_s3.table_import_from_s3 result: {}", importResult);
          return jdbcTemplate.queryForObject("SELECT count(*) FROM henkilo", Long.class);
        });
  }
}
