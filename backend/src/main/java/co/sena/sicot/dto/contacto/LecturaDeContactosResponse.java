package co.sena.sicot.dto.contacto;

import co.sena.sicot.entity.enums.RolDeContacto;

import java.util.List;

/**
 * Los correos que SICOT encontró en el estudio previo que cargó el supervisor.
 * Es una propuesta: nada se guarda hasta que el supervisor elige cuáles son
 * los profesionales de SST y ambiental.
 *
 * @param encontrados primero los que tienen un rol sugerido
 * @param aviso       por qué no hay nada que proponer (documento escaneado,
 *                    sin correos), o {@code null} si hay propuestas
 */
public record LecturaDeContactosResponse(List<Encontrado> encontrados, String aviso) {

    /**
     * @param rolSugerido el rol mencionado más cerca antes del correo, o
     *                    {@code null}: es una sugerencia, no una lectura segura
     * @param contexto    el fragmento del documento donde aparece el correo,
     *                    para que el supervisor reconozca de quién es
     */
    public record Encontrado(String correo, RolDeContacto rolSugerido, String contexto) {
    }
}
