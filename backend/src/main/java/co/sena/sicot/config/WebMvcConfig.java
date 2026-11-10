package co.sena.sicot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Los interceptores de Spring MVC de la API. Hoy solo el de la hora declarada sin conexión. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final HechoSinConexionInterceptor hechoSinConexion;

    public WebMvcConfig(HechoSinConexionInterceptor hechoSinConexion) {
        this.hechoSinConexion = hechoSinConexion;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hechoSinConexion).addPathPatterns("/api/**");
    }
}
