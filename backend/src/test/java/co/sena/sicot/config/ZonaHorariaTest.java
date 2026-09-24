package co.sena.sicot.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La zona horaria del Centro (MDL-214): el reloj que decide qué día es «hoy» y
 * la zona en la que se leen las tareas programadas.
 */
class ZonaHorariaTest {

    @Test
    void elRelojLlevaLaZonaConfigurada() {
        assertThat(new ZonaHoraria().reloj("America/Bogota").getZone()).isEqualTo(ZoneId.of("America/Bogota"));
    }

    /**
     * {@code TimeZone.getTimeZone} convierte una zona que no conoce en GMT sin
     * decir nada, que es exactamente el fallo que este reloj viene a quitar. Una
     * zona mal escrita en el .env tiene que detener el arranque.
     */
    @Test
    void unaZonaInvalidaNoCaeEnSilencioAUtc() {
        assertThatThrownBy(() -> new ZonaHoraria().reloj("America/Bogta"))
                .isInstanceOf(DateTimeException.class);
    }

    /**
     * Toda tarea {@code @Scheduled(cron = …)} del backend declara la zona del
     * Centro. Una sin zona se lee en la de la JVM (UTC en el contenedor) y
     * corre cinco horas antes de lo que dice su comentario. Esta prueba recorre
     * el paquete entero para que la próxima tarea que se añada tampoco pueda
     * olvidarla.
     */
    @Test
    void todaTareaConCronDeclaraLaZonaDelCentro() throws Exception {
        ClassPathScanningCandidateComponentProvider escaner = new ClassPathScanningCandidateComponentProvider(false);
        escaner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        List<String> conCron = new ArrayList<>();
        List<String> sinZona = new ArrayList<>();
        for (BeanDefinition definicion : escaner.findCandidateComponents("co.sena.sicot")) {
            for (Method metodo : Class.forName(definicion.getBeanClassName()).getDeclaredMethods()) {
                Scheduled programada = metodo.getAnnotation(Scheduled.class);
                if (programada == null || programada.cron().isEmpty()) {
                    continue;
                }
                String nombre = metodo.getDeclaringClass().getSimpleName() + "." + metodo.getName();
                conCron.add(nombre);
                if (!ZonaHoraria.PROPIEDAD.equals(programada.zone())) {
                    sinZona.add(nombre);
                }
            }
        }

        // Si el recorrido no encontrara ninguna, la prueba pasaría sin comprobar nada.
        assertThat(conCron).contains(
                "PlanificadorDeAutomatizaciones.evaluarCalendario",
                "PlanificadorDeAutomatizaciones.purgarLaCola",
                "VigilanciaDeAlmacenamiento.medirCadaDia",
                "VigilanciaDelRespaldo.comprobarCadaDia");
        assertThat(sinZona).as("tareas con cron que no declaran la zona del Centro").isEmpty();
    }
}
