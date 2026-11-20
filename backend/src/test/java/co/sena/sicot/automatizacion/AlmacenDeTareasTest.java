package co.sena.sicot.automatizacion;

import co.sena.sicot.PruebaDeIntegracion;
import co.sena.sicot.entity.TareaAutomatizada;
import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import co.sena.sicot.repository.TareaAutomatizadaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El mantenimiento de la cola: lo que la mantiene sana cuando nadie mira.
 *
 * <p>{@code AlmacenDeTareas} es el dueño de todas las escrituras sobre la cola y
 * lleva el comentario más cuidadoso del módulo —por qué los tres pasos de una
 * tarea no pueden compartir transacción, que es un razonamiento correcto y nada
 * obvio—. Estaba al <b>58,6 %</b> de líneas: el ciclo normal (encolar, reclamar,
 * completar) sí lo recorría {@code MotorDeAutomatizacionIntegrationTest}, y lo
 * que no se ejecutaba nunca era precisamente <b>lo que pasa cuando algo va
 * mal</b>: el reintento con espera creciente, la rendición tras agotar los
 * intentos, el rescate de lo que quedó reservado por un proceso muerto y la
 * purga que impide que la tabla crezca sin techo.
 *
 * <p>Esas cuatro cosas corren solas, de noche, en el servidor del Centro. Son
 * justo las que nadie ve fallar.
 */
class AlmacenDeTareasTest extends PruebaDeIntegracion {

    @Autowired
    private AlmacenDeTareas almacen;

    @Autowired
    private TareaAutomatizadaRepository repositorio;

    @Autowired
    private AutomatizacionProperties propiedades;

    /**
     * Para envejecer una fila. {@code fecha_actualizacion} la gobierna
     * {@code @UpdateTimestamp} y no tiene setter —correcto: su valor es «cuándo
     * se tocó de verdad» y no algo que el código elija—, así que la única forma
     * de simular «esto quedó reservado hace media hora» es por SQL. Es lo que
     * hace esta prueba y nada más.
     */
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("un fallo programa un reintento más tarde y deja el motivo escrito")
    void unFalloProgramaUnReintento() {
        TareaAutomatizada tarea = enCurso(1);
        Instant antes = tarea.getEjecutarEn();

        boolean seReintentara = almacen.registrarFallo(tarea.getId(), "el servidor de correo rechazó la conexión");

        assertThat(seReintentara).isTrue();
        TareaAutomatizada recargada = repositorio.findById(tarea.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoTareaAutomatizada.PENDIENTE);
        assertThat(recargada.getUltimoError()).contains("rechazó la conexión");
        assertThat(recargada.getEjecutarEn())
                .as("la espera creciente evita martillear un servicio que ya dijo que no")
                .isAfter(antes);
    }

    @Test
    @DisplayName("la espera crece con cada intento: no se insiste al mismo ritmo para siempre")
    void laEsperaCreceConLosIntentos() {
        Instant referencia = Instant.now();
        TareaAutomatizada primera = enCurso(1);
        TareaAutomatizada tercera = enCurso(3);

        almacen.registrarFallo(primera.getId(), "fallo");
        almacen.registrarFallo(tercera.getId(), "fallo");

        Duration esperaDeLaPrimera = Duration.between(
                referencia, repositorio.findById(primera.getId()).orElseThrow().getEjecutarEn());
        Duration esperaDeLaTercera = Duration.between(
                referencia, repositorio.findById(tercera.getId()).orElseThrow().getEjecutarEn());

        assertThat(esperaDeLaTercera).isGreaterThan(esperaDeLaPrimera);
    }

    @Test
    @DisplayName("agotados los intentos la tarea queda FALLIDA y no se vuelve a intentar")
    void agotadosLosIntentosSeRinde() {
        TareaAutomatizada tarea = enCurso(propiedades.maximoDeIntentos());

        boolean seReintentara = almacen.registrarFallo(tarea.getId(), "no hay forma");

        assertThat(seReintentara)
                .as("devolver true aquí sería un bucle infinito que además parece una cola sana")
                .isFalse();
        TareaAutomatizada recargada = repositorio.findById(tarea.getId()).orElseThrow();
        assertThat(recargada.getEstado()).isEqualTo(EstadoTareaAutomatizada.FALLIDA);
    }

    @Test
    @DisplayName("un error enorme se recorta: la cola no es un almacén de trazas")
    void elErrorSeRecorta() {
        TareaAutomatizada tarea = enCurso(1);

        almacen.registrarFallo(tarea.getId(), "x".repeat(5_000));

        String guardado = repositorio.findById(tarea.getId()).orElseThrow().getUltimoError();
        assertThat(guardado).hasSizeLessThanOrEqualTo(1_001).endsWith("…");
    }

    @Test
    @DisplayName("un fallo sobre una tarea que ya no existe no revienta")
    void unFalloSobreUnaTareaQueNoExisteNoRevienta() {
        assertThat(almacen.registrarFallo(999_999L, "la tarea se purgó entre medias")).isFalse();
    }

    @Test
    @DisplayName("lo que quedó reservado por un proceso muerto vuelve a la cola")
    void seRescataLoAbandonado() {
        // La fuga clásica de una cola basada en estado: matar el backend a
        // mitad de una tarea la deja EN_PROCESO para siempre y nadie la vuelve
        // a mirar. Sin este rescate, un reinicio desafortunado se come un aviso
        // de vencimiento y nada lo dice.
        TareaAutomatizada abandonada = enCurso(1);
        envejecer(abandonada.getId(), propiedades.abandonoTrasVer());

        int liberadas = almacen.liberarAbandonadas();

        assertThat(liberadas).isEqualTo(1);
        assertThat(repositorio.findById(abandonada.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTareaAutomatizada.PENDIENTE);
    }

    @Test
    @DisplayName("una tarea reservada hace un instante NO se le quita a quien la está ejecutando")
    void noSeRescataLoQueTodaviaSeEstaEjecutando() {
        TareaAutomatizada enMarcha = enCurso(1);

        assertThat(almacen.liberarAbandonadas()).isZero();
        assertThat(repositorio.findById(enMarcha.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTareaAutomatizada.EN_PROCESO);
    }

    @Test
    @DisplayName("la purga borra las resueltas viejas y NUNCA las fallidas")
    void laPurgaConservaLasFallidas() {
        TareaAutomatizada vieja = guardar("purga-completada", EstadoTareaAutomatizada.COMPLETADA);
        TareaAutomatizada fallida = guardar("purga-fallida", EstadoTareaAutomatizada.FALLIDA);
        TareaAutomatizada reciente = guardar("purga-reciente", EstadoTareaAutomatizada.COMPLETADA);
        envejecer(vieja.getId(), propiedades.retencionDeTareas());
        envejecer(fallida.getId(), propiedades.retencionDeTareas());

        int borradas = almacen.purgarResueltas();

        assertThat(borradas).isEqualTo(1);
        assertThat(repositorio.findById(vieja.getId())).isEmpty();
        assertThat(repositorio.findById(reciente.getId())).isPresent();
        assertThat(repositorio.findById(fallida.getId()))
                .as("una tarea fallida es la evidencia de un problema y se queda hasta que alguien la mire")
                .isPresent();
    }

    @Test
    @DisplayName("el listado tiene techo y se puede filtrar por estado")
    void elListadoTieneTechoYFiltra() {
        guardar("listado-1", EstadoTareaAutomatizada.PENDIENTE);
        guardar("listado-2", EstadoTareaAutomatizada.COMPLETADA);

        assertThat(almacen.listar(null)).hasSize(2);
        assertThat(almacen.listar(EstadoTareaAutomatizada.COMPLETADA)).hasSize(1);
        assertThat(almacen.contar(EstadoTareaAutomatizada.PENDIENTE)).isEqualTo(1);
        assertThat(AlmacenDeTareas.MAX_TAREAS_LISTADO).isPositive();
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /**
     * Una tarea reservada y con {@code intentos} intentos ya contados, llegando
     * ahí por el CAMINO REAL: reclamar incrementa el contador y registrar el
     * fallo la devuelve a la cola. No hay setter de {@code intentos} y está
     * bien que no lo haya —el contador es del ciclo, no de quien llama—, así
     * que la prueba recorre el ciclo en vez de falsear el estado.
     */
    private TareaAutomatizada enCurso(int intentos) {
        TareaAutomatizada tarea = guardar("en-curso-" + intentos + "-" + System.nanoTime(),
                EstadoTareaAutomatizada.PENDIENTE);
        for (int i = 0; i < intentos; i++) {
            // Por el servicio y no por el repositorio: `reclamar` es
            // `@Modifying` y necesita una transacción, y esta prueba no es
            // transacional a propósito (ver PruebaDeIntegracion: el rollback
            // cambiaría la semántica de lo que se está comprobando).
            assertThat(almacen.reclamar(tarea.getId())).isPresent();
            if (i < intentos - 1) {
                almacen.registrarFallo(tarea.getId(), "intento " + (i + 1));
            }
        }
        TareaAutomatizada reservada = repositorio.findById(tarea.getId()).orElseThrow();
        assertThat(reservada.getIntentos()).isEqualTo(intentos);
        return reservada;
    }

    private TareaAutomatizada guardar(String clave, EstadoTareaAutomatizada estado) {
        TareaAutomatizada tarea = new TareaAutomatizada();
        tarea.setRegla("ReglaDePrueba");
        tarea.setTipo(TipoTareaAutomatizada.ENVIAR_CORREO);
        tarea.setClaveIdempotencia(clave);
        tarea.setPayload("{}");
        tarea.setEjecutarEn(Instant.now());
        tarea.setEstado(estado);
        return repositorio.saveAndFlush(tarea);
    }

    /** Deja la fila como si se hubiera tocado por última vez antes del plazo dado. */
    private void envejecer(Long id, Duration plazo) {
        jdbc.update("UPDATE tareas_automatizadas SET fecha_actualizacion = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(plazo).minusSeconds(60)), id);
    }
}
