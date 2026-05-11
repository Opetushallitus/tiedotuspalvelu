package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.LoggingHttpClient;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.TiedotuspalveluProperties;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.schema.AccessTokenResponse;
import fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu.oppija.schema.HenkiloDto;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class OppijanumerorekisteriClient {

  private static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

  private final ObjectMapper objectMapper;
  private final TiedotuspalveluProperties properties;
  private final LoggingHttpClient httpClient =
      new LoggingHttpClient("oppijanumerorekisteri", LoggingHttpClient.LOG_BODY_NEVER);

  public Oppija getOppija(String oid) throws ValidationException {
    var token = fetchAccessToken();
    try {
      var httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(properties.oppijanumerorekisteri().baseUrl() + "/henkilo/" + oid))
              .header("Accept", "application/json")
              .header("Authorization", "Bearer " + token)
              .GET()
              .build();
      var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        var henkiloDto = objectMapper.readValue(response.body(), HenkiloDto.class);
        return new Oppija(henkiloDto.hetu(), henkiloDto.etunimet(), henkiloDto.sukunimi());
      }
      throw new IllegalStateException(
          "Oppijanumerorekisteri call failed with status " + response.statusCode());
    } catch (IOException e) {
      throw new IllegalStateException("Oppijanumerorekisteri call failed with IO error", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Oppijanumerorekisteri call interrupted", e);
    }
  }

  private String fetchAccessToken() {
    var otuva = properties.otuva();
    var requestBody =
        buildClientCredentialsBody(otuva.oauth2ClientId(), otuva.oauth2ClientSecret());
    try {
      var httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(otuva.oauth2TokenUrl()))
              .header("Content-Type", CONTENT_TYPE_FORM)
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
              .build();
      var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Oauth2 token call failed with status " + response.statusCode());
      }

      var tokenResponse = objectMapper.readValue(response.body(), AccessTokenResponse.class);
      return tokenResponse.accessToken();
    } catch (IOException e) {
      throw new IllegalStateException("Oauth2 token call failed with IO error", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Oauth2 token call interrupted", e);
    }
  }

  private String buildClientCredentialsBody(String clientId, String clientSecret) {
    return "grant_type=client_credentials"
        + "&client_id="
        + encodeFormValue(clientId)
        + "&client_secret="
        + encodeFormValue(clientSecret);
  }

  private String encodeFormValue(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
