package co.sena.sicot.mapper;

import co.sena.sicot.dto.chequeo.ListaChequeoDetalle;
import co.sena.sicot.dto.chequeo.ListaChequeoResumen;

public final class ListaChequeoMapper {

    private ListaChequeoMapper() {
    }

    public static ListaChequeoResumen toResumen(ListaChequeoDetalle l) {
        return new ListaChequeoResumen(
                l.codigo(),
                l.nombre(),
                l.version(),
                l.proceso(),
                l.tipo(),
                l.alcance(),
                l.etapas().size(),
                l.totalItems(),
                l.advertencias().size());
    }
}
