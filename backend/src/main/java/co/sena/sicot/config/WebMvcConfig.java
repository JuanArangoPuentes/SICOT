package co.sena.sicot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Los interceptores de Spring MVC de la API.
 *
 * <p>Los dos leen una cabecera que la auditoría necesita y que no es un
 * parámetro de negocio: <b>cuándo</b> se hizo un cambio guardado sin red
 * (ADR-015) y <b>qué herramienta automática</b> dice estar actuando con la
 * cuenta de una persona. Los dos están aquí, y no en los servicios, para no
 * tener que añadir esos dos datos a la firma de cada método que registra algo.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final HechoSinConexionInterceptor hechoSinConexion;
    private final AgenteQueActuaInterceptor agenteQueActua;

    public WebMvcConfig(HechoSinConexionInterceptor hechoSinConexion,
                        AgenteQueActuaInterceptor agenteQueActua) {
        this.hechoSinConexion = hechoSinConexion;
        this.agenteQueActua = agenteQueActua;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(hechoSinConexion).addPathPatterns("/api/**");
        registry.addInterceptor(agenteQueActua).addPathPatterns("/api/**");
    }
}
