package co.sena.sicot.service;

import co.sena.sicot.dto.documento.DocumentoResponse;
import co.sena.sicot.mapper.DocumentoMapper;
import co.sena.sicot.repository.DocumentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentoService {

    private final DocumentoRepository documentoRepository;
    private final ContratoService contratoService;

    public DocumentoService(DocumentoRepository documentoRepository, ContratoService contratoService) {
        this.documentoRepository = documentoRepository;
        this.contratoService = contratoService;
    }

    @Transactional(readOnly = true)
    public List<DocumentoResponse> listarPorContrato(Long contratoId) {
        contratoService.buscar(contratoId);
        return documentoRepository.findByContratoIdOrderByFechaSubidaDesc(contratoId).stream()
                .map(DocumentoMapper::toResponse)
                .toList();
    }
}
