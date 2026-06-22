package fi.vm.sade.oppijanumerorekisteri.tiedotuspalvelu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

public class OpenApiDocsTest extends TiedotuspalveluApiTest {

  private static final String ERROR_RESPONSE_SCHEMA_REF = "#/components/schemas/ErrorResponse";

  @Test
  public void errorResponseSchemaIsDocumented() throws Exception {
    var schemas = apiDocs().at("/components/schemas");
    assertThat(schemas.has("ErrorResponse")).isTrue();
    assertThat(schemas.at("/ErrorResponse/properties").has("reason")).isTrue();
    assertThat(schemas.at("/ErrorResponse/properties").has("validationErrors")).isTrue();
    assertThat(schemas.has("ValidationError")).isTrue();
    assertThat(schemas.at("/ValidationError/properties").has("field")).isTrue();
    assertThat(schemas.at("/ValidationError/properties").has("error")).isTrue();
  }

  @Test
  public void kielitutkintotodistusEndpointReferencesErrorResponseSchema() throws Exception {
    var badRequestResponse =
        apiDocs()
            .path("paths")
            .path("/omat-viestit/api/v1/tiedote/kielitutkintotodistus")
            .path("post")
            .path("responses")
            .path("400");

    assertThat(badRequestResponse.findValuesAsText("$ref")).contains(ERROR_RESPONSE_SCHEMA_REF);
  }

  private JsonNode apiDocs() throws Exception {
    var json =
        mockMvc
            .perform(get("/omat-viestit/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(json);
  }
}
