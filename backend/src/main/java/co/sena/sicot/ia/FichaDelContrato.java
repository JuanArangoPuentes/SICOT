package co.sena.sicot.ia;

import co.sena.sicot.dto.ia.ChatResponse;
import co.sena.sicot.entity.Contrato;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Responde sin modelo lo que el contrato ya dice: cuánto vale, cuándo vence,
 * cuántos días quedan, si va atrasado, quién es el contratista.
 *
 * <h2>Por qué existe</h2>
 * Hasta el 2-10-2026 estas preguntas iban al modelo, y salían mal de dos
 * maneras. Las de plazo no tenían arreglo: el prompt no llevaba la fecha de
 * hoy ni el semáforo, así que «¿cuántos días me quedan?» o «¿voy atrasado?» se
 * contestaban con una cuenta inventada que podía contradecir la alerta roja
 * que la pantalla Alertas mostraba para ese mismo contrato — la doble verdad
 * que el equipo había eliminado al dejar el cálculo solo en
 * {@code service/Cronograma.java}. Las de datos fijos tardaban entre 21 y 158 s
 * para que un modelo de 3B/7B copiara un «450000000.00» sin separadores, que
 * puede leer como 45 millones. Es la misma clase de dato que ADR-006 y ADR-008
 * decidieron no pedirle al modelo.
 *
 * <h2>De dónde sale cada cosa</h2>
 * Los datos, del contrato tal como lo diligenció Gestión; el valor, formateado
 * como en los PDF y en letras con {@link NumeroEnLetras}; «hoy», del reloj del
 * servidor en la zona horaria del Centro ({@code ZonaHoraria}); el atraso, del
 * mismo {@code Cronograma} que pinta el panel y que persiste la alerta. Lo que
 * el contrato no trae se dice «no registrado» y nunca se rellena.
 *
 * <h2>Cuándo NO responde</h2>
 * Solo con frases que preguntan por el contrato sin ambigüedad, y nunca si la
 * pregunta habla de otro documento con su propio valor o vencimiento (la
 * póliza, la factura) o es condicional: «¿cuándo vence la póliza?» o «¿qué
 * pasa si se vence?» siguen hacia el modelo.
 */
@Component
public class FichaDelContrato {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    enum Tema { RESUMEN, VALOR, FECHAS, PLAZO, ATRASO, ALERTAS, CONTRATISTA, OBJETO, SUPERVISOR, NUMERO }

    private static final Set<String> RESUMEN = PreguntaNormalizada.normalizarTodas(
            "resume el contrato", "resume este contrato", "resúmeme el contrato", "resumen del contrato",
            "resumen de este contrato", "datos del contrato", "información del contrato", "ficha del contrato",
            "detalles del contrato", "de qué trata el contrato", "de qué se trata el contrato");
    private static final Set<String> VALOR = PreguntaNormalizada.normalizarTodas(
            "cuánto vale", "valor del contrato", "valor total", "cuál es el valor", "por cuánto es el contrato",
            "de cuánto es el contrato", "monto del contrato", "cuánto cuesta el contrato", "presupuesto del contrato");
    private static final Set<String> FECHAS = PreguntaNormalizada.normalizarTodas(
            "cuándo vence", "cuándo se vence", "cuándo termina", "cuándo finaliza", "cuándo acaba",
            "fecha de terminación", "fecha de fin", "fecha final", "fecha de vencimiento", "vencimiento del contrato",
            "cuándo inicia", "cuándo empieza", "cuándo empezó", "cuándo inició", "fecha de inicio",
            "fechas del contrato", "plazo del contrato", "plazo de ejecución", "duración del contrato", "cuánto dura");
    private static final Set<String> PLAZO = PreguntaNormalizada.normalizarTodas(
            "cuántos días", "días me quedan", "días le quedan", "días quedan", "días faltan", "días restantes",
            "para que se venza", "para que venza", "para el vencimiento", "para que termine el contrato",
            "cuánto le queda al contrato", "cuánto tiempo queda", "cuánto tiempo me queda", "cuánto tiempo le queda",
            "se venció", "ya venció", "está vencido");
    private static final Set<String> ATRASO = PreguntaNormalizada.normalizarTodas(
            "atrasado", "atrasada", "atraso", "retrasado", "retrasada", "retraso", "voy a tiempo", "va a tiempo",
            "estoy a tiempo", "semáforo", "cronograma");
    /** Solo como pregunta entera: «¿cómo voy a firmar?» no pregunta por el cronograma. */
    private static final Set<String> ATRASO_COMPLETAS = PreguntaNormalizada.normalizarTodas(
            "cómo voy", "cómo va", "cómo va el contrato", "voy bien", "va bien", "cómo vamos");
    private static final Set<String> ALERTAS = PreguntaNormalizada.normalizarTodas(
            "alertas", "alerta");
    private static final Set<String> CONTRATISTA = PreguntaNormalizada.normalizarTodas(
            "quién es el contratista", "cuál es el contratista", "nombre del contratista", "datos del contratista",
            "nit del contratista", "cuál es el nit", "el nit", "representante legal", "quién es el representante");
    private static final Set<String> OBJETO = PreguntaNormalizada.normalizarTodas(
            "objeto del contrato", "cuál es el objeto", "de qué es el contrato", "qué se contrató");
    private static final Set<String> SUPERVISOR = PreguntaNormalizada.normalizarTodas(
            "quién es el supervisor", "supervisor del contrato", "quién supervisa");
    private static final Set<String> NUMERO = PreguntaNormalizada.normalizarTodas(
            "número del contrato", "número de contrato", "cuál es el número");

    /**
     * Otros documentos con valor o vencimiento propios. «¿Cuándo vence la
     * póliza?» no es la fecha del contrato, y contestarla con ella sería
     * afirmar algo falso con toda seguridad.
     */
    private static final Set<String> OTROS_OBJETOS = Set.of("poliza", "polizas", "garantia", "garantias",
            "factura", "facturas", "planilla", "pila", "rut", "certificado", "camara", "anticipo", "cuota", "pago",
            "pagos", "orden", "item", "items", "bien", "bienes", "firma", "acta", "informe");

    private final Clock reloj;

    public FichaDelContrato(Clock reloj) {
        this.reloj = reloj;
    }

    /** Los temas del contrato que pregunta el mensaje; vacío si no es para esta clase. */
    Set<Tema> reconocer(String pregunta) {
        PreguntaNormalizada p = PreguntaNormalizada.de(pregunta);
        if (p.estaVacia() || p.largo() > 200 || p.tienePalabra("si")
                || OTROS_OBJETOS.stream().anyMatch(p::tienePalabra)) {
            return EnumSet.noneOf(Tema.class);
        }
        EnumSet<Tema> temas = EnumSet.noneOf(Tema.class);
        if (p.contieneAlgunaFrase(RESUMEN)) {
            temas.add(Tema.RESUMEN);
        }
        if (p.contieneAlgunaFrase(VALOR)) {
            temas.add(Tema.VALOR);
        }
        if (p.contieneAlgunaFrase(FECHAS)) {
            temas.add(Tema.FECHAS);
        }
        if (p.contieneAlgunaFrase(PLAZO)) {
            temas.add(Tema.PLAZO);
        }
        if (p.contieneAlgunaFrase(ATRASO) || p.esExactamenteAlguna(ATRASO_COMPLETAS)) {
            temas.add(Tema.ATRASO);
        }
        if (p.contieneAlgunaFrase(ALERTAS)) {
            temas.add(Tema.ALERTAS);
        }
        if (p.contieneAlgunaFrase(CONTRATISTA)) {
            temas.add(Tema.CONTRATISTA);
        }
        if (p.contieneAlgunaFrase(OBJETO)) {
            temas.add(Tema.OBJETO);
        }
        if (p.contieneAlgunaFrase(SUPERVISOR)) {
            temas.add(Tema.SUPERVISOR);
        }
        if (p.contieneAlgunaFrase(NUMERO)) {
            temas.add(Tema.NUMERO);
        }
        return temas;
    }

    /** ¿Este trozo de una pregunta compuesta ya lo contesta la ficha? */
    boolean cubre(String trozo) {
        return !reconocer(trozo).isEmpty();
    }

    /**
     * La respuesta, con los datos del contrato y, si pregunta por el plazo o
     * el atraso, el cálculo del cronograma y el botón de Alertas.
     *
     * @param cronograma el mensaje de {@code Cronograma}; solo se calcula si
     *                   la pregunta lo necesita
     */
    public Optional<ChatResponse> responder(String pregunta, Contrato contrato, Supplier<Optional<String>> cronograma) {
        Set<Tema> temas = reconocer(pregunta);
        if (temas.isEmpty() || contrato == null) {
            return Optional.empty();
        }
        if (temas.contains(Tema.RESUMEN)) {
            temas = EnumSet.of(Tema.RESUMEN, Tema.NUMERO, Tema.OBJETO, Tema.VALOR, Tema.FECHAS, Tema.PLAZO,
                    Tema.CONTRATISTA, Tema.SUPERVISOR, Tema.ATRASO);
        }
        LocalDate hoy = LocalDate.now(reloj);
        List<String> lineas = new ArrayList<>();
        if (temas.contains(Tema.NUMERO)) {
            lineas.add("Contrato Nro.: %s.".formatted(oNoRegistrado(contrato.getNumeroContrato())));
        }
        if (temas.contains(Tema.OBJETO)) {
            lineas.add("Objeto: %s.".formatted(sinPuntoFinal(oNoRegistrado(contrato.getObjeto()))));
        }
        if (temas.contains(Tema.VALOR)) {
            lineas.add("Valor del contrato: %s.".formatted(valor(contrato.getValor())));
        }
        if (temas.contains(Tema.FECHAS) || temas.contains(Tema.PLAZO)) {
            lineas.add("Fecha de inicio: %s.".formatted(fecha(contrato.getFechaInicio())));
            lineas.add("Fecha de terminación: %s.".formatted(fecha(contrato.getFechaFin())));
        }
        if (temas.contains(Tema.FECHAS) || temas.contains(Tema.PLAZO) || temas.contains(Tema.ATRASO)) {
            lineas.add(diasRestantes(contrato.getFechaInicio(), contrato.getFechaFin(), hoy));
        }
        if (temas.contains(Tema.CONTRATISTA)) {
            lineas.add("Contratista: %s. NIT: %s. Representante legal: %s.".formatted(
                    sinPuntoFinal(oNoRegistrado(contrato.getContratista())),
                    oNoRegistrado(contrato.getContratistaNit()),
                    sinPuntoFinal(oNoRegistrado(contrato.getRepresentanteLegal()))));
        }
        if (temas.contains(Tema.SUPERVISOR)) {
            lineas.add("Supervisor: %s.".formatted(contrato.getSupervisor() == null
                    ? "sin asignar" : oNoRegistrado(contrato.getSupervisor().getNombre())));
        }
        boolean conCronograma = temas.contains(Tema.PLAZO) || temas.contains(Tema.ATRASO)
                || temas.contains(Tema.ALERTAS);
        if (conCronograma) {
            cronograma.get().ifPresent(m -> lineas.add("Cronograma: " + m));
        }
        if (temas.contains(Tema.ALERTAS)) {
            // No dice si hay o no alertas: no las lee, y afirmar que no hay
            // ninguna fue justo lo que el modelo podía inventar.
            lineas.add("Las alertas de este contrato están en la pestaña Alertas; con el botón de abajo la abre.");
        }
        String texto = String.join("\n", lineas);
        return Optional.of(conCronograma
                ? ChatResponse.delSistema(texto, AccionesDelCopiloto.mostrarAlertas())
                : ChatResponse.delSistema(texto));
    }

    /**
     * El valor como lo escriben los PDF de SICOT: «$10.000.000,00 (DIEZ
     * MILLONES DE PESOS M/CTE)». También lo usa el prompt del modelo, que
     * recibía el {@code BigDecimal} crudo.
     */
    static String valor(BigDecimal valor) {
        if (valor == null) {
            return "no registrado";
        }
        return "%s (%s)".formatted(RedactorDeDocumentos.pesos(valor, false), NumeroEnLetras.pesos(valor));
    }

    static String fecha(LocalDate fecha) {
        return fecha == null ? "no registrada" : fecha.format(FECHA);
    }

    /** Días calendario hasta la terminación, contados desde «hoy» en la zona del Centro. */
    static String diasRestantes(LocalDate inicio, LocalDate fin, LocalDate hoy) {
        String hoyTexto = "Hoy es %s".formatted(hoy.format(FECHA));
        if (fin == null) {
            return hoyTexto + "; sin fecha de terminación registrada no puedo calcular cuántos días quedan.";
        }
        long dias = ChronoUnit.DAYS.between(hoy, fin);
        if (dias < 0) {
            return hoyTexto + ": el plazo terminó hace %s.".formatted(dias(-dias));
        }
        if (dias == 0) {
            return hoyTexto + ": el plazo termina hoy.";
        }
        String restantes = hoyTexto + ": %s hasta la fecha de terminación."
                .formatted(dias == 1 ? "queda 1 día calendario" : "quedan %d días calendario".formatted(dias));
        if (inicio != null && inicio.isAfter(hoy)) {
            restantes += " El contrato todavía no ha iniciado: empieza en %s.".formatted(
                    dias(ChronoUnit.DAYS.between(hoy, inicio)));
        }
        return restantes;
    }

    private static String dias(long n) {
        return n == 1 ? "1 día" : n + " días";
    }

    private static String oNoRegistrado(String valor) {
        return valor == null || valor.isBlank() ? "no registrado" : valor.strip();
    }

    private static String sinPuntoFinal(String texto) {
        return texto.endsWith(".") ? texto.substring(0, texto.length() - 1) : texto;
    }
}
