package co.sena.sicot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   @Value("${sicot.swagger.public:true}") boolean swaggerPublico)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/**").permitAll();
                    // Swagger queda público solo en desarrollo (sicot.swagger.public=true, el
                    // default). En "prod" (application-prod.properties lo fija en false) exigir
                    // JWT válido para verla — no se apaga del todo porque sigue siendo útil para
                    // depurar en el servidor real, pero no se expone el contrato completo de la
                    // API a cualquiera en internet.
                    if (swaggerPublico) {
                        auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.requestMatchers("/actuator/health").permitAll();
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    // ── Pre-filtro de rol por ruta ──────────────────────────
                    // Spring MVC resuelve y valida los argumentos del método
                    // (@Valid @RequestBody) ANTES de invocarlo, y por tanto
                    // antes de que actúe @PreAuthorize. El efecto es que un
                    // usuario sin permiso para una ruta recibía 400 con el
                    // mapa completo de campos obligatorios en vez de 403: se
                    // le filtraba el contrato de la API de un endpoint que
                    // tiene prohibido, y el código de estado además mentía
                    // sobre el motivo del rechazo.
                    //
                    // Estas reglas corren en la cadena de filtros, mucho antes
                    // de que exista un cuerpo que validar, así que el 403 llega
                    // primero. Son un PRE-FILTRO, no un reemplazo: los
                    // @PreAuthorize de los controladores siguen siendo la
                    // autoridad y se siguen evaluando. Por eso cada regla de
                    // aquí es deliberadamente igual o MÁS PERMISIVA que la del
                    // método correspondiente — si alguna vez divergen, la del
                    // método sigue cerrando el paso y no se abre nada.
                    auth.requestMatchers(HttpMethod.GET, "/api/firmas/mia").authenticated();
                    auth.requestMatchers("/api/firmas/**").hasRole("ADMINISTRADOR");
                    // El listado amplía a GESTION; todo lo demás bajo
                    // /api/usuarios es solo ADMINISTRADOR (@PreAuthorize de
                    // clase). Se separan por método en vez de poner la unión de
                    // ambos: una regla única y más laxa dejaría pasar a GESTION
                    // hasta la validación del cuerpo, que es justo lo que estas
                    // reglas existen para evitar.
                    auth.requestMatchers(HttpMethod.GET, "/api/usuarios")
                            .hasAnyRole("ADMINISTRADOR", "GESTION");
                    auth.requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.GET, "/api/registros").hasRole("ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.GET, "/api/alertas").hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.PATCH, "/api/alertas/*/leida")
                            .hasAnyRole("SUPERVISOR", "GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.PATCH, "/api/subetapas/*/estado")
                            .hasAnyRole("SUPERVISOR", "GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/formatos").hasRole("ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/formatos/*").hasRole("ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/ia/**").hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/contratos").hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.PUT, "/api/contratos/*").hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.PATCH, "/api/contratos/*/supervisor")
                            .hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.PATCH, "/api/contratos/*/estado")
                            .hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/contratos/*/documentos")
                            .hasAnyRole("GESTION", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/contratos/*/documentos/generar")
                            .hasAnyRole("SUPERVISOR", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/contratos/*/documentos/*/firmar")
                            .hasAnyRole("SUPERVISOR", "ADMINISTRADOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/contratos/*/copiloto/chat")
                            .hasAnyRole("SUPERVISOR", "ADMINISTRADOR");

                    auth.anyRequest().authenticated();
                })
                // Spring Security ya añade por defecto nosniff, X-Frame-Options
                // y no-cache. Se completan aquí las que no trae y que sí
                // corresponden a una API: HSTS para que un cliente que llegue
                // una vez por HTTPS no vuelva a intentarlo en claro, y una
                // política de referente que impide filtrar la ruta consultada
                // a un tercero.
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // CSP máximamente restrictiva, pero SOLO en /api/**.
                        // Esas rutas devuelven JSON y archivos que siempre bajan
                        // con Content-Disposition: attachment, así que nada de
                        // lo que sirven necesita ejecutar scripts ni ser
                        // embebido. Aplicarla a todo el backend rompería
                        // Swagger UI, que es una página real servida desde este
                        // mismo origen y que necesita cargar su propio
                        // JavaScript — de ahí el writer delegado en vez de
                        // .contentSecurityPolicy(), que la pondría en todas
                        // las respuestas.
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                request -> request.getRequestURI().startsWith("/api/"),
                                new StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'; sandbox"))))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"status":403,"error":"Forbidden","message":"No tiene permisos para realizar esta operación."}
                    """);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${sicot.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        // trim() y filtrado de vacíos: la comparación de orígenes es exacta,
        // así que un espacio después de una coma en CORS_ALLOWED_ORIGINS
        // ("http://a, http://b") producía el origen literal " http://b", que no
        // coincide con ninguno y deja el navegador bloqueando peticiones sin
        // ningún error en el servidor. Era el fallo más difícil de diagnosticar
        // de toda la configuración: todo parece bien puesto y nada funciona.
        List<String> origenes = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList();
        if (origenes.isEmpty()) {
            throw new IllegalStateException(
                    "sicot.cors.allowed-origins quedó vacío: el frontend no podría hablar con esta API. "
                            + "Defina CORS_ALLOWED_ORIGINS con la URL real del frontend.");
        }
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
