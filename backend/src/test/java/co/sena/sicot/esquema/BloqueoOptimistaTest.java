package co.sena.sicot.esquema;

import co.sena.sicot.entity.Contrato;
import co.sena.sicot.repository.ContratoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprueba que el bloqueo optimista de {@code V12__bloqueo_optimista.sql}
 * realmente impida una actualización perdida.
 *
 * <p>Antes de la columna {@code lock_version}, dos transacciones que leían el
 * mismo contrato y lo guardaban una tras otra terminaban ambas con éxito y el
 * cambio de la primera desaparecía: ningún error, ninguna traza, el dato
 * simplemente ya no estaba. Esta prueba reproduce exactamente esa secuencia y
 * exige que ahora la segunda falle.
 *
 * <p>Se usan dos {@link EntityManager} independientes en vez de dos hilos
 * porque el escenario no necesita paralelismo real, solo dos contextos de
 * persistencia que leyeron la misma versión. Con hilos, la prueba dependería
 * del azar del planificador; así es determinista.
 */
@SpringBootTest
@ActiveProfiles("test")
class BloqueoOptimistaTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void laEscrituraSobreUnaVersionVencidaFallaEnVezDePisarLaAnterior() {
        Long contratoId = crearContratoDePrueba();

        EntityManager sesionA = entityManagerFactory.createEntityManager();
        EntityManager sesionB = entityManagerFactory.createEntityManager();
        try {
            sesionA.getTransaction().begin();
            sesionB.getTransaction().begin();

            // Las dos leen el MISMO estado: aquí es donde nacía la pérdida.
            Contrato leidoPorA = sesionA.find(Contrato.class, contratoId);
            Contrato leidoPorB = sesionB.find(Contrato.class, contratoId);

            leidoPorA.setObjeto("Objeto corregido por la Unidad de Gestión");
            sesionA.getTransaction().commit();

            leidoPorB.setObjeto("Objeto corregido por el Supervisor");
            assertThatThrownBy(() -> sesionB.getTransaction().commit())
                    .as("la segunda escritura debe fallar en vez de sobrescribir en silencio")
                    .isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);
        } finally {
            cerrar(sesionA);
            cerrar(sesionB);
        }

        // El dato que sobrevive es el de quien llegó primero, y el de la segunda
        // no se perdió en silencio: su autor recibió un 409 y puede reintentar.
        assertThat(contratoRepository.findById(contratoId).orElseThrow().getObjeto())
                .isEqualTo("Objeto corregido por la Unidad de Gestión");
    }

    @Test
    void cadaEscrituraExitosaIncrementaLaVersionDeLaFila() {
        Long contratoId = crearContratoDePrueba();

        assertThat(versionDe(contratoId)).as("versión inicial de una fila recién insertada").isZero();

        new TransactionTemplate(transactionManager).executeWithoutResult(estado -> {
            Contrato contrato = contratoRepository.findById(contratoId).orElseThrow();
            contrato.setLugarEjecucion("Centro Tecnológico del Mobiliario");
        });

        assertThat(versionDe(contratoId))
                .as("Hibernate debe incrementar lock_version en cada UPDATE")
                .isEqualTo(1L);
    }

    private Long crearContratoDePrueba() {
        return new TransactionTemplate(transactionManager).execute(estado -> {
            Contrato contrato = new Contrato();
            // Número único por invocación: la tabla tiene UNIQUE(numero_contrato)
            // y estas pruebas comparten la base en memoria con el resto de la suite.
            contrato.setNumeroContrato("BLOQ-" + System.nanoTime());
            contrato.setObjeto("Contrato para verificar el bloqueo optimista");
            contrato.setValor(new BigDecimal("1000000.00"));
            contrato.setFechaInicio(LocalDate.of(2026, 1, 1));
            contrato.setFechaFin(LocalDate.of(2026, 12, 31));
            return contratoRepository.save(contrato).getId();
        });
    }

    /**
     * Lee {@code lock_version} con SQL nativo: la entidad no expone getter a
     * propósito, porque es un campo de Hibernate y no del dominio.
     */
    private Long versionDe(Long contratoId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            Object version = em
                    .createNativeQuery("SELECT lock_version FROM contratos WHERE id = :id")
                    .setParameter("id", contratoId)
                    .getSingleResult();
            return ((Number) version).longValue();
        } finally {
            cerrar(em);
        }
    }

    private static void cerrar(EntityManager em) {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }
}
