import { describe, expect, it } from 'vitest'
import { nombreConExtension } from './documentoService'

// Cada caso es un archivo que bajó mal antes del 28 de septiembre de 2026: la
// descarga le pegaba «.pdf» a todo lo que no terminara en .pdf.
describe('nombreConExtension', () => {
  it('le pone .pdf a un documento generado que no la trae', () => {
    expect(nombreConExtension('Acta de Inicio — CO1.PCCNTR.7788991', 'application/pdf')).toBe(
      'Acta de Inicio — CO1.PCCNTR.7788991.pdf',
    )
  })

  it('no convierte una foto en «.jpg.pdf»', () => {
    expect(nombreConExtension('Evidencia fotográfica 3.2 — 1000000021.jpg', 'image/jpeg')).toBe(
      'Evidencia fotográfica 3.2 — 1000000021.jpg',
    )
    expect(nombreConExtension('foto.JPEG', 'image/jpeg')).toBe('foto.JPEG')
  })

  it('respeta los archivos de Word y Excel que carga Gestión', () => {
    expect(
      nombreConExtension('Acta.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'),
    ).toBe('Acta.docx')
    expect(
      nombreConExtension('Lista de chequeo', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'),
    ).toBe('Lista de chequeo.xlsx')
  })

  it('deja el nombre tal cual si el tipo no se conoce', () => {
    expect(nombreConExtension('soporte', 'application/octet-stream')).toBe('soporte')
  })
})
