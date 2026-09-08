package co.sena.sicot.entity;

import co.sena.sicot.entity.enums.EstadoTareaAutomatizada;
import co.sena.sicot.entity.enums.TipoTareaAutomatizada;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Una unidad de trabajo producida por una regla de automatización y pendiente
 * de ejecutarse.
 *
 * <h2>Por qué las reglas producen filas y no efectos</h2>
 * Una regla podría crear la alerta o mandar el correo directamente. No lo hace,
 * y esa es la decisión estructural del módulo: la regla <b>decide</b> y devuelve
 * lo que hay que hacer; el ejecutor <b>hace</b>. De ahí salen tres propiedades
 * que no se pueden conseguir de otra forma.
 *
 * <p><b>Las reglas se vuelven funciones puras.</b> Se prueban dándoles un
 * contrato y comparando la lista de tareas que devuelven, sin levantar Spring,
 * sin base de datos y sin servidor de correo.
 *
 * <p><b>Los reintentos viven en un solo sitio.</b> Si cada regla mandara su
 * propio correo, cada regla tendría que acordarse de reintentar, de esperar
 * entre intentos y de rendirse en algún momento. Aquí eso se escribe una vez.
 *
 * <p><b>El trabajo sobrevive al reinicio.</b> Un despliegue a media tarde con
 * la cola en memoria se llevaría por delante todo lo pendiente, y en silencio.
 *
 * <h2>La clave de idempotencia</h2>
 * Identifica el <b>hecho</b>, no el momento: {@code vencimiento:contrato=7:umbral=30}.
 * Las reglas de calendario se evalúan cada día, así que sin esto un contrato al
 * que le quedan treinta días generaría esa misma alerta cada mañana hasta
 * vencer. La restricción UNIQUE en la base convierte ese error en imposible en
 * vez de en algo que hay que acordarse de evitar.
 *
 * <h2>Por qué no lleva {@code lock_version}</h2>
 * Ya tiene control de concurrencia, y más fuerte para este caso: un trabajador
 * reclama la tarea con un UPDATE condicionado al estado, de modo que no existe
 * ventana entre comprobar y reservar. Ver el encabezado de
 * {@code V15__motor_de_automatizaciones.sql}.
 */
@Entity
@Table(name = "tareas_automatizadas")
public class TareaAutomatizada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Código de la regla que la produjo. Es lo que permite responder «¿de dónde
     * salió esta alerta?» meses después, y apagar una regla ruidosa sin tener
     * que adivinar cuáles de las tareas en cola son suyas.
     */
    @Column(nullable = false, length = 80)
    private String regla;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoTareaAutomatizada tipo;

    /**
     * Contrato al que pertenece el trabajo, si pertenece a alguno. Nulable
     * porque hay eventos de sistema sin contrato asociado (una firma revocada,
     * por ejemplo, que `RegistroService` registra con contrato nulo).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 200)
    private String claveIdempotencia;

    /**
     * Datos del efecto, en JSON. Es un TEXT y no columnas por tipo de tarea
     * porque cada tipo necesita campos distintos: un correo tiene destinatario
     * y asunto, una alerta tiene prioridad. Con columnas, la tabla acumularía
     * una por cada campo de cada tipo, casi todas nulas casi siempre.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTareaAutomatizada estado = EstadoTareaAutomatizada.PENDIENTE;

    @Column(nullable = false)
    private int intentos = 0;

    /**
     * Momento a partir del cual la tarea vuelve a ser elegible. Lo mueve hacia
     * adelante cada reintento: volver a intentar de inmediato contra un servicio
     * caído es una forma eficaz de que siga caído.
     */
    @Column(name = "ejecutar_en", nullable = false)
    private Instant ejecutarEn = Instant.now();

    @Column(name = "ultimo_error", columnDefinition = "TEXT")
    private String ultimoError;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRegla() { return regla; }
    public void setRegla(String regla) { this.regla = regla; }

    public TipoTareaAutomatizada getTipo() { return tipo; }
    public void setTipo(TipoTareaAutomatizada tipo) { this.tipo = tipo; }

    public Contrato getContrato() { return contrato; }
    public void setContrato(Contrato contrato) { this.contrato = contrato; }

    public String getClaveIdempotencia() { return claveIdempotencia; }
    public void setClaveIdempotencia(String claveIdempotencia) { this.claveIdempotencia = claveIdempotencia; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public EstadoTareaAutomatizada getEstado() { return estado; }
    public void setEstado(EstadoTareaAutomatizada estado) { this.estado = estado; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }

    public Instant getEjecutarEn() { return ejecutarEn; }
    public void setEjecutarEn(Instant ejecutarEn) { this.ejecutarEn = ejecutarEn; }

    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }

    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
}
