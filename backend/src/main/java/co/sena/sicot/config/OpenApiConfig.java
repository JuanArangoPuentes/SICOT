package co.sena.sicot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sicotOpenAPI() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("SICOT API")
                        .description("""
                                API del Sistema Inteligente para la Gestión y Acompañamiento de Contratos \
                                (SENA — Centro Tecnológico del Mobiliario).

                                Autenticación: obtenga un token en `POST /api/auth/login` y envíelo en el \
                                encabezado `Authorization: Bearer <token>`.""")
                        .version("0.1.0")
                        .contact(new Contact()
                                .name("SENA — Centro Tecnológico del Mobiliario")
                                .email("sicot@soy.sena.edu.co")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
