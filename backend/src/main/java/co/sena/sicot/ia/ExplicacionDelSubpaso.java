package co.sena.sicot.ia;

import co.sena.sicot.dto.etapa.SubetapaResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Qué es, para qué sirve y de dónde sale el insumo de cada paso y de cada
 * sub-paso del proceso de supervisión, sin pasar por el modelo de IA.
 *
 * <h2>Por qué existe</h2>
 * Hasta el 17-11-2026, «explícame el paso 3.2» recibía esto:
 *
 * <blockquote>Sub-paso 3.2: Carga de evidencia fotográfica georreferenciada,
 * del paso 3: INSPECCIÓN — Monitoreo y Ejecución. <b>Está pendiente.</b>
 * Evidencia fotográfica con georreferenciación activa. Responsable:
 * Supervisor.</blockquote>
 *
 * <p>Es decir: el estado primero y, como explicación, el nombre del sub-paso
 * dicho otra vez. La descripción que {@code GcconP010Plantilla} siembra en los
 * sub-pasos 3.1 a 6.4 repite el título («Evidencia fotográfica con
 * georreferenciación activa» para «Carga de evidencia fotográfica
 * georreferenciada»), porque se escribió para rotular una lista, no para
 * explicarle el proceso a nadie. Quien preguntaba qué se hace en un sub-paso se
 * quedaba igual que antes de preguntar.
 *
 * <p>La respuesta tiene que seguir siendo del motor y no del modelo —ADR-006 y
 * {@link GuiaDelPasoActual}: un modelo pequeño contesta esto en minutos y a
 * veces sobre el paso equivocado—, así que lo que faltaba no era llamar a
 * Ollama, era tener escrito lo que el proceso ya dice de cada sub-paso.
 *
 * <h2>De dónde sale cada línea</h2>
 * De las fuentes que el proyecto ya tiene escritas, no de lo que parezca
 * razonable (regla del proyecto: no inventar el proceso contractual real):
 * <ul>
 *   <li>Pasos 1 y 2: el proceso real que documentó el supervisor
 *       (docs/producto/PROCESO_REAL_EN_SICOT.md y LISTA_CHEQUEO_ALEX.md), que
 *       es lo mismo que describe {@code GcconP010Plantilla}.</li>
 *   <li>Pasos 3 a 6: el GCCON-P-010 tal como lo resume el prompt del Copiloto
 *       ({@code CopilotoChatService.INSTRUCCIONES}) y las fichas de los
 *       documentos formales ({@link FichaDeDocumentoFormal#DOCUMENTOS}).</li>
 *   <li>Dónde se consigue un insumo: {@link FichaDelInsumo}, que ya lo dice
 *       cuando preguntan por él; aquí no se copia, se le pregunta, para que no
 *       haya dos versiones de la misma frase.</li>
 * </ul>
 *
 * <p>Lo que estas explicaciones <b>no</b> hacen es afirmar requisitos legales,
 * plazos ni porcentajes que no estén en esas fuentes: dicen qué es el sub-paso
 * y qué lugar ocupa en el proceso, que es lo que SICOT sabe. Lo que no sabe
 * —qué mirar punto por punto en una planilla o en una póliza— no se rellena
 * aquí: lo dice {@link FichaDelInsumo} cuando preguntan por el insumo, y
 * remite a la Unidad de Gestión Contractual.
 *
 * <h2>Por el nombre y no por el código</h2>
 * Igual que {@link FichaDelInsumo} y que {@code SUBPASOS_POR_NOMBRE} en la
 * guía: los contratos de antes del 15-10-2026 tienen otros sub-pasos con los
 * mismos códigos («2.3 Verificación de pólizas»), y explicarles el 2.3 del
 * proceso nuevo sería contestarles sobre un sub-paso que no tienen. Si el
 * nombre no está en esta tabla no hay explicación, y quien llama se queda con
 * la descripción que traiga el contrato.
 */
final class ExplicacionDelSubpaso {

    /**
     * @param queEs        qué se hace en el sub-paso, dicho de forma que
     *                     añada algo a su título
     * @param paraQue      qué lugar ocupa en el proceso: qué deja hecho y qué
     *                     depende de él
     * @param deDondeSale  dónde se consigue el insumo que el supervisor
     *                     verifica fuera de SICOT, o {@code null}
     */
    record Explicacion(String queEs, String paraQue, String deDondeSale) {

        Explicacion(String queEs, String paraQue) {
            this(queEs, paraQue, null);
        }
    }

    /**
     * Los 21 sub-pasos, por el trozo de su nombre (ya normalizado) que los
     * reconoce. El orden importa: se devuelve el primero que encaje.
     */
    private static final Map<String, Explicacion> POR_NOMBRE = new LinkedHashMap<>();

    /** Los 6 pasos, por el trozo de su nombre que los reconoce. */
    private static final Map<String, String> PASOS = new LinkedHashMap<>();

    static {
        // ── Paso 1: designación. No es trabajo del supervisor; le llega hecho ──
        POR_NOMBRE.put("publicacion del contrato en secop", new Explicacion(
                "La Unidad de Contratación suscribe el contrato con el contratista y lo publica en el SECOP II. "
                        + "Ocurre antes de que a usted lo designen, así que le llega hecho.",
                "Deja el contrato en firme y su expediente publicado: de ahí salen el objeto, las cantidades, las "
                        + "especificaciones y los valores que usted verifica después, y el enlace del proceso con el "
                        + "que se abre el expediente."));
        POR_NOMBRE.put("designacion del supervisor", new Explicacion(
                "El ordenador del gasto designa por escrito, en el formato GCCON-F-032, al supervisor del contrato y "
                        + "al apoyo a la supervisión.",
                "Es el acto que lo deja a usted a cargo de vigilar el contrato. Quien tenga un conflicto de interés "
                        + "debe manifestarlo por escrito a más tardar el día siguiente a la notificación."));
        POR_NOMBRE.put("registro del contrato en sicot", new Explicacion(
                "El apoyo a la supervisión carga en SICOT el acto de designación. SICOT lee del documento los datos "
                        + "del contrato, completa los que falten con los datos abiertos del SECOP II y le avisa a "
                        + "usted que fue designado.",
                "Es lo que hace que el contrato aparezca en su panel, con sus datos y con sus seis pasos armados. "
                        + "Desde aquí empieza su trabajo."));

        // ── Paso 2: arranque. El proceso real del 15-10-2026 ──────────────────
        POR_NOMBRE.put("acta de inicio", new Explicacion(
                "El Acta de Inicio (GCCON-F-018) la elabora la Unidad de Contratación y se la envía por correo; "
                        + "SICOT no la genera. Usted la firma y la devuelve respondiendo ese mismo correo.",
                "El acta trae los datos del contrato, el registro presupuestal, la aprobación de las garantías y las "
                        + "fechas de inicio y de terminación con las que arranca la ejecución. Al cerrarse este "
                        + "sub-paso el contrato queda activo en SICOT, y desde entonces corren los avisos de plazo y "
                        + "los de las entregas acordadas."));
        POR_NOMBRE.put("profesionales sst", new Explicacion(
                "Identificar a los profesionales de SST y ambiental y escribirles que el contrato inició, "
                        + "invitándolos a las entregas o actividades según las obligaciones del contrato.",
                "Son ellos quienes acompañan la parte de seguridad y salud en el trabajo y la ambiental. SICOT guarda "
                        + "sus contactos con el contrato, le propone los correos que encuentre en el estudio previo y "
                        + "le arma el borrador del correo ya dirigido a ellos.",
                "Los correos de los profesionales están en el estudio previo, que se abre entrando al proceso en el "
                        + "SECOP II con el enlace del contrato."));
        POR_NOMBRE.put("especificaciones en secop", new Explicacion(
                "Con el enlace del proceso, verificar en el SECOP II las cantidades, las especificaciones y los "
                        + "valores del contrato, y compararlos con lo que está registrado en SICOT.",
                "Es lo que le deja empezar a supervisar sobre datos ciertos: lo que reciba después se compara contra "
                        + "esto. SICOT le muestra lo que publica el SECOP y le señala lo que no coincide."));
        POR_NOMBRE.put("contacto con el contratista", new Explicacion(
                "Escribirle al contratista para presentarse como supervisor y acordar con él las fechas de entrega, "
                        + "dentro del plazo del contrato. Si es un suministro o una compra, el correo lleva las "
                        + "cantidades exactas de los bienes.",
                "De esas fechas salen los avisos que SICOT le manda antes de cada entrega y los correos con los que "
                        + "se las comparte a SST y ambiental. El sub-paso no se cierra sin al menos una fecha "
                        + "registrada."));

        // ── Pasos 3 a 6: GCCON-P-010 ─────────────────────────────────────────
        POR_NOMBRE.put("entrega en bodega", new Explicacion(
                "Estar en la entrega y comprobar en sitio lo que el contratista deja en la bodega del Centro: que "
                        + "lo que llegó es lo del contrato.",
                "Es la primera comprobación de la ejecución. Lo que vea aquí es lo que después se cuenta contra la "
                        + "ficha técnica (3.3) y lo que respalda el Informe de Supervisión (3.4)."));
        POR_NOMBRE.put("evidencia fotografica", new Explicacion(
                "Las fotos de la entrega, tomadas en el sitio. Tomadas con la cámara desde SICOT, cada foto queda con "
                        + "la fecha y el lugar de la toma; una foto elegida de la galería puede no traer el lugar.",
                "Son la prueba que queda en el expediente de qué se entregó, cuándo y dónde, y el soporte de lo que "
                        + "usted firma después en el Informe de Supervisión (GCCON-F-031)."));
        POR_NOMBRE.put("ficha tecnica", new Explicacion(
                "Contar y revisar lo entregado contra la ficha técnica del contrato: que las cantidades sean las "
                        + "pactadas y que la calidad y las especificaciones correspondan.",
                "Es la verificación que sostiene el Informe de Supervisión y, más adelante, el Acta de Recibo a "
                        + "Satisfacción. Lo que no coincida conviene dejarlo anotado antes de recibir.",
                "La ficha técnica va con el estudio previo del proceso, en el expediente del SECOP II; las cantidades "
                        + "y los valores, en la oferta económica."));
        POR_NOMBRE.put("informe de supervision", new Explicacion(
                "El informe con el que usted deja constancia del control de la ejecución: inspección física, "
                        + "novedades y avances del contrato. Lo arma SICOT con los datos del contrato y usted lo "
                        + "revisa y lo firma.",
                "Cierra el paso de inspección: deja en el expediente el respaldo de lo que verificó en la bodega, de "
                        + "las fotos y de la comparación con la ficha técnica."));
        POR_NOMBRE.put("aportes a seguridad social", new Explicacion(
                "Revisar la planilla PILA del contratista y comprobar que los aportes a seguridad social del periodo "
                        + "están pagados.",
                "Es una de las dos comprobaciones del paso de recepción —la otra es la factura— que van antes de "
                        + "firmar el Acta de Recibo a Satisfacción (4.3).",
                dondeSeConsigue("aportes a seguridad social")));
        POR_NOMBRE.put("factura electronica", new Explicacion(
                "Revisar la factura electrónica de venta del contratista y comprobar que está validada en la DIAN.",
                "Va antes del Acta de Recibo a Satisfacción (4.3) y del trámite de pago del paso 5: es la factura que "
                        + "respalda lo que se recibe.",
                dondeSeConsigue("factura electronica")));
        POR_NOMBRE.put("acta de recibo", new Explicacion(
                "El acta con la que usted recibe formalmente los bienes, con su cantidad, su calidad y sus "
                        + "especificaciones técnicas. La arma SICOT con los datos del contrato y usted la revisa y la "
                        + "firma.",
                "Es la recepción formal: deja constancia de que lo entregado se recibió a satisfacción y abre el paso "
                        + "de la certificación y el pago."));
        POR_NOMBRE.put("vigencia de garantias", new Explicacion(
                "Revisar las pólizas del contrato y comprobar que siguen vigentes.",
                "Va antes de la Certificación de cumplimiento (5.3), que es la que respalda el trámite de pago.",
                dondeSeConsigue("vigencia de garantias")));
        POR_NOMBRE.put("orden de pago", new Explicacion(
                "Revisar la orden de pago del contrato y el Certificado de Registro Presupuestal (CRP) que la "
                        + "respalda.",
                "Son los documentos presupuestales con los que avanza el trámite de pago. El Oficio de Pago "
                        + "(GRF-F-089) no lo firma usted: lo firma el Ordenador del gasto."));
        POR_NOMBRE.put("certificacion de cumplimiento", new Explicacion(
                "La certificación del supervisor que respalda el trámite de pago. La arma SICOT con los datos del "
                        + "contrato y usted la revisa y la firma. En el Centro la llaman ESUCON; no tiene un código "
                        + "de formato oficial confirmado.",
                "Con ella firmada, el trámite de pago sigue con el Ordenador del gasto."));
        POR_NOMBRE.put("cumplimiento total", new Explicacion(
                "Revisar que todo lo que el contrato pedía —su objeto y sus obligaciones— quedó cumplido.",
                "Es la comprobación con la que se cierra la supervisión, antes de firmar el Informe Final (6.3)."));
        POR_NOMBRE.put("modificaciones o adiciones", new Explicacion(
                "Revisar si el contrato tuvo adiciones, prórrogas u otras modificaciones, que quedan registradas en "
                        + "el SECOP II, y dejarlo anotado. Si no hubo ninguna, el sub-paso se marca igual.",
                "El Informe Final tiene que reflejar el contrato como quedó, no como empezó."));
        POR_NOMBRE.put("informe final", new Explicacion(
                "El informe de cierre: el cumplimiento de las obligaciones y del objeto del contrato y su estado "
                        + "financiero al final. Lo arma SICOT con los datos del contrato y usted lo revisa y lo "
                        + "firma. No es el Acta de Liquidación.",
                "Cierra formalmente su supervisión del contrato."));
        POR_NOMBRE.put("sigep", new Explicacion(
                "El cierre y el archivo del expediente digital del contrato en SIGEP. Lo hace la Unidad de "
                        + "Contratación, no usted.",
                "Deja el expediente cerrado y archivado cuando el contrato termina."));

        PASOS.put("designacion",
                "deja el contrato publicado, al supervisor designado por escrito y el contrato registrado en SICOT. "
                        + "Le llega hecho: su trabajo empieza en el paso siguiente.");
        PASOS.put("acta de inicio",
                "arranca la ejecución: usted firma y devuelve el Acta de Inicio, avisa a los profesionales de SST y "
                        + "ambiental, verifica el contrato contra el SECOP II y acuerda las entregas con el "
                        + "contratista.");
        PASOS.put("inspeccion",
                "vigila la ejecución: usted verifica la entrega en la bodega, deja la evidencia fotográfica, la "
                        + "compara con la ficha técnica y firma el Informe de Supervisión (GCCON-F-031).");
        PASOS.put("recepcion",
                "recibe formalmente lo entregado: usted verifica los aportes a seguridad social y la factura "
                        + "electrónica, y firma el Acta de Recibo a Satisfacción (GIL-F-010).");
        PASOS.put("certificacion",
                "respalda el pago: usted revisa la vigencia de las garantías y la orden de pago con su CRP, y firma "
                        + "la Certificación de cumplimiento, con la que el trámite sigue con el Ordenador del gasto.");
        PASOS.put("cierre",
                "cierra el contrato: usted verifica el cumplimiento total del objeto, evalúa las modificaciones o "
                        + "adiciones que haya habido y firma el Informe Final (GCCON-F-030); el expediente se archiva "
                        + "en SIGEP.");
    }

    private ExplicacionDelSubpaso() {
    }

    /**
     * La explicación del sub-paso, por su nombre; vacío si el contrato trae uno
     * que esta tabla no conoce (los anteriores al proceso real), en cuyo caso
     * quien llama debe quedarse con la descripción del propio sub-paso.
     */
    static Optional<Explicacion> delSubpaso(SubetapaResponse sub) {
        if (sub == null || sub.nombre() == null) {
            return Optional.empty();
        }
        String nombre = PreguntaNormalizada.normalizar(sub.nombre());
        return POR_NOMBRE.entrySet().stream()
                .filter(e -> nombre.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Dónde se consigue la factura, la planilla PILA o las pólizas lo dice
     * {@link FichaDelInsumo} cuando preguntan por el insumo. Aquí se le
     * pregunta en vez de copiar la frase, para que no haya dos versiones de la
     * misma: si cambia allí, cambia en los dos sitios a la vez. Si algún día
     * deja de encajar, la línea desaparece en vez de quedarse vieja, y la
     * prueba {@code laLineaDelInsumoSaleDeLaFicha} se pone roja.
     */
    private static String dondeSeConsigue(String claveDelSubpaso) {
        return FichaDelInsumo.dondeSeConsigue(claveDelSubpaso).orElse(null);
    }

    /** Para qué es un paso, por su nombre; vacío si no es uno de los seis. */
    static Optional<String> delPaso(String nombreDeLaEtapa) {
        if (nombreDeLaEtapa == null) {
            return Optional.empty();
        }
        String nombre = PreguntaNormalizada.normalizar(nombreDeLaEtapa);
        return PASOS.entrySet().stream()
                .filter(e -> nombre.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
