package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.AwsConfiguration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
@ConditionalOnProperty(name = "tiedotuspalvelu.henkilo-import.enabled", havingValue = "true")
public class HenkiloImportConfiguration {
  public static final String QUALIFIER = "onrExport";

  @Bean
  @Qualifier(QUALIFIER)
  public S3AsyncClient onrExportS3Client(
      @Qualifier(AwsConfiguration.QUALIFIER)
          AwsCredentialsProvider tiedotuspalveluCredentialsProvider) {
    return S3AsyncClient.crtBuilder()
        .credentialsProvider(tiedotuspalveluCredentialsProvider)
        .region(Region.EU_WEST_1)
        .build();
  }
}
