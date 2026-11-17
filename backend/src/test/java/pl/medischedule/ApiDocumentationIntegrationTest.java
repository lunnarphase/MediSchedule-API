package pl.medischedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class ApiDocumentationIntegrationTest extends ApiIntegrationTestSupport {
  @Test
  void openApiDescribesRealEndpointsSessionAndPagedResponse() throws Exception {
    var spec = body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn());
    assertThat(spec.at("/info/title").asText()).isEqualTo("MediSchedule API");
    assertThat(spec.at("/components/securitySchemes/session/name").asText())
        .isEqualTo("JSESSIONID");
    assertThat(spec.at("/paths/~1api~1appointments/post/responses/201").isMissingNode()).isFalse();
    assertThat(spec.at("/paths/~1api~1admin~1emails/get/parameters").toString())
        .contains("page", "size", "status", "q");
    assertThat(spec.at("/paths/~1api~1auth~1logout/post/responses/204").isMissingNode()).isFalse();
    assertThat(spec.at("/paths/~1api~1auth~1login/post/security").toString()).isEqualTo("[]");
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void securityApiUsesViewsWithoutExposingPersistenceFields() throws Exception {
    mvc.perform(get("/api/admin/security").session(admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.attempts[0].email").exists())
        .andExpect(jsonPath("$.attempts[0].success").isBoolean())
        .andExpect(jsonPath("$.attempts[0].password").doesNotExist());
  }
}
