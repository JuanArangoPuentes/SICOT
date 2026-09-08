package co.sena.sicot.service;

import co.sena.sicot.automatizacion.FotoDelContrato;
import co.sena.sicot.automatizacion.LectorDeContratos;
import co.sena.sicot.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Sirve el estado de cronograma de un contrato a quien lo pida por la API.
 *
 * <h2>Por qué reutiliza el lector del motor</h2>
 * {@link LectorDeContratos} ya resuelve en dos consultas agrupadas exactamente
 * los datos que hace falta leer: fechas, conteo de subetapas y etapa en curso.
 * Escribir aquí una segunda lectura equivalente reintroduciría, en la capa de
 * lectura, el mismo problema que este cambio viene a eliminar en la capa de
 * cálculo: dos caminos hacia el mismo dato que pueden divergir.
 *
 * <p>Sí comprueba el acceso, que el motor no necesita: aquí sí hay un usuario
 * autenticado detrás, y un SUPERVISOR solo puede ver el cronograma del contrato
 * que tiene asignado.
 */
@Service
public class CronogramaService {

    private final LectorDeContratos lectorDeContratos;
    private final ContratoService contratoService;

    public CronogramaService(LectorDeContratos lectorDeContratos, ContratoService contratoService) {
        this.lectorDeContratos = lectorDeContratos;
        this.contratoService = contratoService;
    }

    @Transactional(readOnly = true)
    public Cronograma de(Long contratoId) {
        // buscar() aplica verificarAccesoAlContrato: lanza 404 si el contrato no
        // es de este supervisor, sin distinguirlo de "no existe".
        contratoService.buscar(contratoId);

        FotoDelContrato foto = lectorDeContratos.porId(contratoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Contrato", contratoId));
        return foto.cronograma(LocalDate.now());
    }
}
