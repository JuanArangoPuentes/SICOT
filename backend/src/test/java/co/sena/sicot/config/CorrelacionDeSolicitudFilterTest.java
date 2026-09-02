package co.sena.sicot.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * El filtro que marca cada petición con un identificador propio.
 *
 * <p>Se prueba con cuidado porque falla de forma silenciosa: si el MDC no se
 * limpia, el identificador de una petición aparece en las líneas de log de la
 * siguiente —los hilos de Tomcat se reutilizan— y la traza queda mezclada justo
 * cuando se necesita para diagnosticar algo. Nada en la aplicación se rompe;
 * solo el registro deja de ser fiable, que es la peor manera de romperse.
 */
class CorrelacionDeSolicitudFilterTest {

    private final CorrelacionDeSolicitudFilter filtro = new CorrelacionDeSolicitudFilter();

    @AfterEach
    void limpiarContexto() {
        MDC.clear();
    }

    @Test
    void generaUnIdentificadorCuandoElClienteNoManda() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, mock(FilterChain.class));

        String id = respuesta.getHeader(CorrelacionDeSolicitudFilter.CABECERA);
        assertThat(id).isNotBlank();
    }

    @Test
    void respeta_el_identificador_que_manda_el_cliente() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        peticion.addHeader(CorrelacionDeSolicitudFilter.CABECERA, "trazado-externo-123");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, mock(FilterChain.class));

        assertThat(respuesta.getHeader(CorrelacionDeSolicitudFilter.CABECERA))
                .isEqualTo("trazado-externo-123");
    }

    /**
     * El valor del cliente termina escrito en el log. Un salto de línea ahí
     * permitiría inyectar líneas falsas —hacer que un registro inventado
     * parezca real— así que se sanea antes de usarlo.
     */
    @Test
    void saneaCaracteresQuePermitirianFalsificarLineasDeLog() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        peticion.addHeader(CorrelacionDeSolicitudFilter.CABECERA,
                "abc\n2026-01-01 ERROR linea inventada\r\t");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, mock(FilterChain.class));

        String id = respuesta.getHeader(CorrelacionDeSolicitudFilter.CABECERA);
        assertThat(id).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t")
                .doesNotContain(" ");
    }

    @Test
    void recortaUnIdentificadorDesmesurado() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        peticion.addHeader(CorrelacionDeSolicitudFilter.CABECERA, "x".repeat(500));
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, mock(FilterChain.class));

        assertThat(respuesta.getHeader(CorrelacionDeSolicitudFilter.CABECERA).length())
                .isLessThanOrEqualTo(64);
    }

    @Test
    void generaUnoPropioSiElDelClienteQuedaVacioAlSanearlo() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        // Solo caracteres que el saneado descarta: no queda nada aprovechable.
        peticion.addHeader(CorrelacionDeSolicitudFilter.CABECERA, "!!!@@@###");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, mock(FilterChain.class));

        assertThat(respuesta.getHeader(CorrelacionDeSolicitudFilter.CABECERA)).isNotBlank();
    }

    /** Durante la petición el identificador tiene que estar disponible para el log. */
    @Test
    void elIdentificadorEstaEnElContextoDeLogDuranteLaPeticion() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        peticion.addHeader(CorrelacionDeSolicitudFilter.CABECERA, "durante-la-peticion");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        String[] visto = new String[1];
        FilterChain cadena = mock(FilterChain.class);
        doAnswer(inv -> {
            visto[0] = MDC.get(CorrelacionDeSolicitudFilter.CLAVE_MDC);
            return null;
        }).when(cadena).doFilter(any(), any());

        filtro.doFilter(peticion, respuesta, cadena);

        assertThat(visto[0]).isEqualTo("durante-la-peticion");
    }

    /**
     * Lo que de verdad importa: al terminar, el contexto queda limpio. Los hilos
     * de Tomcat se reutilizan, y sin esto la siguiente petición atendida por el
     * mismo hilo heredaría el identificador de la anterior.
     */
    @Test
    void limpiaElContextoAlTerminar() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        filtro.doFilter(peticion, respuesta, mock(FilterChain.class));

        assertThat(MDC.get(CorrelacionDeSolicitudFilter.CLAVE_MDC)).isNull();
    }

    /** Y también cuando la petición revienta: si no, la fuga es peor. */
    @Test
    void limpiaElContextoAunqueLaPeticionFalle() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/contratos");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        FilterChain cadena = mock(FilterChain.class);
        doAnswer(inv -> {
            throw new IllegalStateException("algo explotó dentro de la petición");
        }).when(cadena).doFilter(any(), any());

        assertThatThrownBy(() -> filtro.doFilter(peticion, respuesta, cadena))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelacionDeSolicitudFilter.CLAVE_MDC)).isNull();
    }
}
