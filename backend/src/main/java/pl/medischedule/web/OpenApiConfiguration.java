package pl.medischedule.web;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.*;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenAPI apiDefinition() {
    return new OpenAPI()
        .info(
            new Info()
                .title("MediSchedule API")
                .version("1.0.0")
                .description(
                    "Rezerwacje wizyt, grafiki lekarzy i administracja. Logowanie używa sesji"
                        + " JSESSIONID. Najpierw wykonaj GET /api/auth/csrf, następnie POST"
                        + " /api/auth/login. Po zalogowaniu ponownie pobierz CSRF. Swagger dodaje"
                        + " nagłówek X-XSRF-TOKEN z cookie. Możesz też zalogować się w aplikacji i"
                        + " otworzyć Swagger pod tym samym adresem."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "session",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")))
        .addSecurityItem(new SecurityRequirement().addList("session"));
  }

  @Bean
  OpenApiCustomizer publicAuthAndLogout() {
    return api -> {
      for (String path : List.of("/api/auth/csrf", "/api/auth/login", "/api/auth/register")) {
        PathItem item = api.getPaths().get(path);
        if (item != null)
          item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
      }
      api.path(
          "/api/auth/logout",
          new PathItem()
              .post(
                  new Operation()
                      .operationId("logout")
                      .summary("Wylogowanie i unieważnienie sesji")
                      .tags(List.of("Logowanie"))
                      .responses(
                          new ApiResponses()
                              .addApiResponse(
                                  "204", new ApiResponse().description("Sesja zakończona"))
                              .addApiResponse(
                                  "403",
                                  new ApiResponse().description("Nieprawidłowy token CSRF")))));
      Schema<?> errorSchema = new ObjectSchema();
      errorSchema.addProperty("message", new StringSchema());
      errorSchema.setRequired(List.of("message"));
      api.getComponents().addSchemas("ApiError", errorSchema);
      api.getPaths()
          .values()
          .forEach(
              item ->
                  item.readOperations()
                      .forEach(
                          operation -> {
                            for (String code : List.of("400", "401", "403", "409", "429")) {
                              ApiResponse response = operation.getResponses().get(code);
                              if (response != null)
                                response.setContent(
                                    new Content()
                                        .addMediaType(
                                            "application/json",
                                            new MediaType()
                                                .schema(
                                                    new Schema<>()
                                                        .$ref("#/components/schemas/ApiError"))));
                            }
                          }));
    };
  }
}
