package co.sena.sicot.salud;

import co.sena.sicot.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lo que se le entrega a la persona que puede arreglarlo, cuando el sistema ya
 * intentó lo que sabía y no le salió.
 *
 * <h2>Por qué esto no es «otro log»</h2>
 * Porque la lección del 11-11-2026 fue justo esa. Ese día el servidor del Centro
 * llevaba seis días sin respaldo, y el sistema <b>sí lo había detectado</b>:
 * {@code VigilanciaDelRespaldo} escribió «el más reciente tiene 119 h de
 * antigüedad» en el log, dos veces, con el comando exacto que había que
 * comprobar. No sirvió de nada, porque nadie lee un log mientras todo parece
 * funcionar. Un aviso que se queda en el log es un aviso que no existe.
 *
 * <p>Así que el informe sale por tres sitios a la vez, y cada uno cubre un caso
 * distinto:
 * <ul>
 *   <li><b>El log</b>, siempre. Es lo que mira quien ya está diagnosticando.</li>
 *   <li><b>Un archivo</b> en {@code sicot.salud.informes-directorio}, uno por
 *       incidencia. Sobrevive al reinicio del contenedor, que es cuando el log
 *       de Docker se pierde: el 11-11 no se pudo saber si la vigilancia había
 *       avisado entre el 6 y el 9 de noviembre porque al recrear el contenedor
 *       desapareció todo lo anterior.</li>
 *   <li><b>Un correo</b> a {@code sicot.salud.tecnico-email}, que es el único de
 *       los tres que va a buscar a la persona en vez de esperar a que mire.</li>
 * </ul>
 *
 * <h2>Lo que este archivo tiene prohibido</h2>
 * <b>Fallar.</b> Se le llama desde el camino de error de otro componente: si
 * esto lanzara una excepción, taparía el fallo original con uno propio. Todo va
 * envuelto, y lo que no se pudo entregar se dice en el log y se sigue.
 *
 * <p>Y <b>no puede llamarse a sí mismo</b>. Si el correo del informe falla, no
 * se registra como un fallo de {@link Servicio#CORREO}: eso dispararía otro
 * informe, que intentaría otro correo, que fallaría igual. Tampoco se manda
 * correo cuando lo que está caído es el propio correo, por el mismo motivo por
 * el que no se telefonea para avisar de que el teléfono no funciona.
 */
@Component
public class InformeParaElTecnico {

    private static final Logger log = LoggerFactory.getLogger(InformeParaElTecnico.class);
    private static final DateTimeFormatter FECHA_DE_ARCHIVO =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * {@link ObjectProvider} y no {@code EmailService} a secas para romper un
     * ciclo de construcción: {@code EmailService} avisa de sus propios fallos a
     * {@code SaludDelSistema}, que necesita este informe, que necesitaría el
     * correo. Spring se niega a arrancar con ese círculo. Pedido solo en el
     * momento de enviar, el círculo no existe.
     */
    private final ObjectProvider<EmailService> correo;
    private final String destinatario;
    private final Path directorio;

    public InformeParaElTecnico(ObjectProvider<EmailService> correo,
                                @Value("${sicot.salud.tecnico-email:}") String destinatario,
                                @Value("${sicot.salud.informes-directorio:}") String directorio) {
        this.correo = correo;
        this.destinatario = destinatario == null || destinatario.isBlank() ? null : destinatario.trim();
        this.directorio = directorio == null || directorio.isBlank() ? null : Path.of(directorio);
    }

    /** El servicio se dio por perdido: informe completo. */
    public void informarCaida(Servicio servicio, SaludDelSistema.Incidencia incidencia) {
        String asunto = "SICOT: falló " + servicio.etiqueta();
        String cuerpo = redactar(servicio, incidencia);

        log.error("\n{}", cuerpo);
        guardar(servicio, cuerpo);
        enviar(servicio, asunto, cuerpo);
    }

    /** Volvió solo. Se cierra el aviso para que nadie salga a buscar un fallo que ya no está. */
    public void informarRecuperacion(Servicio servicio, SaludDelSistema.Incidencia incidencia) {
        String cuerpo = """
                SICOT: %s volvió a funcionar.

                Estuvo caído %d minuto(s), con %d fallo(s) seguidos y %d intento(s)
                de reparación automática. No hace falta hacer nada.

                Lo último que había dicho el fallo:
                  %s
                """.formatted(servicio.etiqueta(),
                incidencia.duracion().toMinutes(),
                incidencia.fallosSeguidos(),
                incidencia.reparaciones(),
                incidencia.ultimoDetalle());

        log.info("\n{}", cuerpo);
        enviar(servicio, "SICOT: " + servicio.etiqueta() + " volvió a funcionar", cuerpo);
    }

    private String redactar(Servicio servicio, SaludDelSistema.Incidencia incidencia) {
        return """
                ═══════════════════════════════════════════════════════════════
                 INFORME PARA EL TÉCNICO — %s

                 Qué falló:       %s
                 Desde:           %s (lleva %d minuto(s))
                 Fallos seguidos: %d
                 Reparaciones automáticas intentadas: %d

                 Lo que dijo el fallo, tal cual:
                   %s

                 Qué puede hacer el usuario mientras tanto:
                   %s

                 Qué comprobar en el servidor:
                %s
                ═══════════════════════════════════════════════════════════════"""
                .formatted(
                        LocalDateTime.now(),
                        servicio.etiqueta(),
                        incidencia.desde(),
                        incidencia.duracion().toMinutes(),
                        incidencia.fallosSeguidos(),
                        incidencia.reparaciones(),
                        incidencia.ultimoDetalle(),
                        servicio.mensajeParaElUsuario() == null
                                ? "Nada cambia para él: este servicio no lo usa directamente."
                                : servicio.mensajeParaElUsuario(),
                        queComprobar(servicio));
    }

    /**
     * Los comandos exactos, no una descripción de lo que habría que mirar. Es el
     * mismo criterio que ya usaban {@code VerificacionDelModeloIa} y
     * {@code VigilanciaDelRespaldo}: un aviso que obliga a buscar el comando en
     * la documentación se atiende más tarde, y a veces nunca.
     */
    private String queComprobar(Servicio servicio) {
        return switch (servicio) {
            case IA -> """
                       docker ps --filter name=sicot-ollama
                       docker logs sicot-ollama --tail 50
                       docker exec sicot-ollama ollama list     # ¿está el modelo?
                       docker compose restart ollama            # si no responde
                   El resto de SICOT funciona sin esto: no es una urgencia de noche.""";
            case CORREO -> """
                       Revisar MAIL_HOST, MAIL_PORT, MAIL_USERNAME y MAIL_PASSWORD en el .env
                       docker compose logs backend | grep -i mail
                   Si es una contraseña de aplicación caducada, hay que generar otra.""";
            case RESPALDO -> """
                       systemctl list-timers sicot-respaldo.timer
                       systemctl is-failed sicot-respaldo.service
                       tail -30 /var/log/sicot-respaldo.log
                       ls -lht <RESPALDO_DIRECTORIO> | head
                   Esto afecta al RPO de 24 h del ADR-002: conviene mirarlo hoy.""";
            case ALMACENAMIENTO -> """
                       df -h
                       docker system df
                       du -sh <carpeta de documentos>
                   Si el disco está lleno, las subidas de documentos empiezan a fallar.""";
        };
    }

    private void guardar(Servicio servicio, String cuerpo) {
        if (directorio == null) {
            return;
        }
        try {
            Files.createDirectories(directorio);
            Path archivo = directorio.resolve(
                    "%s-%s.txt".formatted(FECHA_DE_ARCHIVO.format(LocalDateTime.now()),
                            servicio.name().toLowerCase()));
            Files.writeString(archivo, cuerpo);
            log.info("Informe para el técnico escrito en {}", archivo);
        } catch (IOException | RuntimeException e) {
            // Si no se pudo escribir, el informe ya salió por el log. No se
            // propaga: esto corre en el camino de error de otro componente.
            log.warn("No se pudo escribir el informe en {}: {}", directorio, e.getMessage());
        }
    }

    private void enviar(Servicio servicio, String asunto, String cuerpo) {
        if (destinatario == null) {
            log.warn("""
                    No hay a quién mandarle este informe: falta sicot.salud.tecnico-email.
                    Mientras no esté, el aviso se queda en el log y en el archivo, que es
                    exactamente lo que hizo que el respaldo pasara seis días caído sin que
                    nadie se enterara. En el .env del despliegue:
                      SICOT_TECNICO_EMAIL=quien.lo.arregla@ejemplo.co""");
            return;
        }
        if (servicio == Servicio.CORREO) {
            log.warn("Lo caído es el correo: el informe no se manda por correo. Queda en el log y en el archivo.");
            return;
        }
        try {
            correo.getObject().enviar(destinatario, asunto, cuerpo);
            log.info("Informe para el técnico enviado a {}", destinatario);
        } catch (Exception e) {
            // A propósito NO se registra como fallo de CORREO: eso dispararía
            // otro informe, que intentaría otro correo. El log corta el ciclo.
            log.warn("No se pudo enviar el informe a {}: {}", destinatario, e.getMessage());
        }
    }
}
