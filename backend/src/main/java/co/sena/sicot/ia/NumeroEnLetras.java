package co.sena.sicot.ia;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valor de un contrato en letras, como lo escriben las actas del SENA:
 * «DIEZ MILLONES DE PESOS M/CTE».
 *
 * <h2>Por qué lo hace el código y no el modelo</h2>
 * El 24-09-2026, en la prueba integral, el modelo escribió $120.450.000 como
 * «ciento veinticuatro millones quinientos diez mil» en el Acta de Recibo y
 * como «Bs 120.450.000» —bolívares— en la Certificación de cumplimiento. Un
 * valor en letras que no coincide con la cifra invalida el documento, y
 * convertir un número a palabras es un algoritmo cerrado: pedírselo a un
 * modelo es pagar minutos de CPU por un resultado peor.
 *
 * <p>Cubre hasta 999.999.999.999 pesos, más centavos («CON 50/100»), que es
 * de sobra para un contrato de un Centro.
 */
public final class NumeroEnLetras {

    private static final String[] UNIDADES = {"", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE",
            "OCHO", "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE",
            "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO",
            "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"};
    private static final String[] DECENAS = {"", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA",
            "SETENTA", "OCHENTA", "NOVENTA"};
    private static final String[] CENTENAS = {"", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS",
            "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"};

    private NumeroEnLetras() {
    }

    /** «CIENTO VEINTE MILLONES CUATROCIENTOS CINCUENTA MIL PESOS M/CTE». */
    public static String pesos(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        BigDecimal redondeado = valor.setScale(2, RoundingMode.HALF_UP);
        long enteros = redondeado.longValue();
        int centavos = redondeado.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();
        if (enteros < 0 || enteros > 999_999_999_999L) {
            throw new IllegalArgumentException("Valor fuera de rango para escribirlo en letras: " + valor);
        }
        // Delante de «PESOS» el «UNO» también se apocopa: «CIENTO UN PESOS».
        String letras = enteros == 0 ? "CERO" : apocopar(entero(enteros));
        // «UN MILLÓN DE PESOS», «DOS MILLONES DE PESOS»: tras millón/millones
        // exactos va «DE»; con cualquier otra terminación no.
        boolean millonesExactos = enteros >= 1_000_000 && enteros % 1_000_000 == 0;
        String moneda = (millonesExactos ? " DE" : "") + (enteros == 1 ? " PESO" : " PESOS");
        String sufijoCentavos = centavos > 0 ? " CON %02d/100".formatted(centavos) : "";
        return letras + moneda + sufijoCentavos + " M/CTE";
    }

    static String entero(long n) {
        if (n == 0) {
            return "";
        }
        long milesDeMillon = n / 1_000_000_000L;
        long millones = (n / 1_000_000L) % 1000;
        long miles = (n / 1000) % 1000;
        long resto = n % 1000;

        StringBuilder sb = new StringBuilder();
        // Los «miles de millones» se escriben como millones: 1.500.000.000 es
        // «MIL QUINIENTOS MILLONES», no «UN MIL QUINIENTOS…».
        long millonesTotales = milesDeMillon * 1000 + millones;
        if (millonesTotales > 0) {
            if (millonesTotales == 1) {
                sb.append("UN MILLÓN");
            } else {
                sb.append(apocopar(hastaMillon(millonesTotales))).append(" MILLONES");
            }
        }
        if (miles > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(miles == 1 ? "MIL" : apocopar(tresCifras((int) miles)) + " MIL");
        }
        if (resto > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(tresCifras((int) resto));
        }
        return sb.toString();
    }

    /** Hasta 999.999, para contar millones («MIL QUINIENTOS» millones). */
    private static String hastaMillon(long n) {
        long miles = n / 1000;
        int resto = (int) (n % 1000);
        StringBuilder sb = new StringBuilder();
        if (miles > 0) {
            sb.append(miles == 1 ? "MIL" : apocopar(tresCifras((int) miles)) + " MIL");
        }
        if (resto > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(tresCifras(resto));
        }
        return sb.toString();
    }

    static String tresCifras(int n) {
        if (n == 100) {
            return "CIEN";
        }
        int c = n / 100;
        int du = n % 100;
        StringBuilder sb = new StringBuilder(CENTENAS[c]);
        if (du > 0) {
            if (!sb.isEmpty()) sb.append(' ');
            if (du < 30) {
                sb.append(UNIDADES[du]);
            } else {
                sb.append(DECENAS[du / 10]);
                if (du % 10 > 0) sb.append(" Y ").append(UNIDADES[du % 10]);
            }
        }
        return sb.toString();
    }

    /**
     * Delante de «MIL» y «MILLONES» el «UNO» final se apocopa: «VEINTIÚN MIL»,
     * «TREINTA Y UN MILLONES».
     */
    private static String apocopar(String s) {
        if (s.endsWith("VEINTIUNO")) {
            return s.substring(0, s.length() - "VEINTIUNO".length()) + "VEINTIÚN";
        }
        if (s.endsWith("UNO")) {
            return s.substring(0, s.length() - 3) + "UN";
        }
        return s;
    }
}
