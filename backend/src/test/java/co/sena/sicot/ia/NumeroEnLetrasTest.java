package co.sena.sicot.ia;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El valor en letras de las actas. Los casos incluyen los dos que el modelo
 * escribió mal en la prueba del 24-09-2026 y los valores de los documentos
 * reales del Centro (acta de inicio, notificación, certificado ESUCON).
 */
class NumeroEnLetrasTest {

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "120450000; CIENTO VEINTE MILLONES CUATROCIENTOS CINCUENTA MIL PESOS M/CTE",
            "10000000; DIEZ MILLONES DE PESOS M/CTE",
            "39552042; TREINTA Y NUEVE MILLONES QUINIENTOS CINCUENTA Y DOS MIL CUARENTA Y DOS PESOS M/CTE",
            "99067527; NOVENTA Y NUEVE MILLONES SESENTA Y SIETE MIL QUINIENTOS VEINTISIETE PESOS M/CTE",
            "86000000; OCHENTA Y SEIS MILLONES DE PESOS M/CTE",
            "1000000; UN MILLÓN DE PESOS M/CTE",
            "1; UN PESO M/CTE",
            "100; CIEN PESOS M/CTE",
            "101; CIENTO UN PESOS M/CTE",
            "1000; MIL PESOS M/CTE",
            "21000; VEINTIÚN MIL PESOS M/CTE",
            "31000000; TREINTA Y UN MILLONES DE PESOS M/CTE",
            "21021021; VEINTIÚN MILLONES VEINTIÚN MIL VEINTIÚN PESOS M/CTE",
            "1500000000; MIL QUINIENTOS MILLONES DE PESOS M/CTE",
            "2001000000; DOS MIL UN MILLONES DE PESOS M/CTE",
            "0; CERO PESOS M/CTE",
    })
    void escribeElValorComoLoEscribenLasActas(String valor, String esperado) {
        assertThat(NumeroEnLetras.pesos(new BigDecimal(valor))).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "19989620.50; DIECINUEVE MILLONES NOVECIENTOS OCHENTA Y NUEVE MIL SEISCIENTOS VEINTE PESOS CON 50/100 M/CTE",
            "18924558.1; DIECIOCHO MILLONES NOVECIENTOS VEINTICUATRO MIL QUINIENTOS CINCUENTA Y OCHO PESOS CON 10/100 M/CTE",
    })
    void losCentavosVanComoFraccion(String valor, String esperado) {
        assertThat(NumeroEnLetras.pesos(new BigDecimal(valor))).isEqualTo(esperado);
    }
}
