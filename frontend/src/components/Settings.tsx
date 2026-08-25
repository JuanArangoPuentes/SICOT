import { useState } from 'react'
import { AVATARS, FONT_OPTIONS, PRESETS, usePrefs, type PresetId, type Prefs } from '@/prefs'
import { Field, Modal } from './ui'
import { AvatarIcon } from './icons'

type Section = 'presets' | 'manual' | 'copiloto'

export default function Settings({ open, onClose, initialSection = 'presets' }: {
  open: boolean
  onClose: () => void
  initialSection?: Section
}) {
  const { prefs, patch, reset } = usePrefs()
  const [section, setSection] = useState<Section>(initialSection)

  if (!open) return null

  const applyPreset = (id: PresetId) => patch({ preset: id, ...PRESETS[id].patch })

  const colorField = (label: string, key: keyof Prefs) => (
    <Field label={label}>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <input type="color" value={prefs[key] as string} onChange={e => patch({ [key]: e.target.value } as Partial<Prefs>)} style={{ width: 52 }} />
        <input type="text" value={prefs[key] as string} onChange={e => patch({ [key]: e.target.value } as Partial<Prefs>)}
          style={{ flex: 1, padding: '8px 10px', fontFamily: 'var(--font-mono)', fontSize: 12 }} />
      </div>
    </Field>
  )

  const sectionTitle = (t: string) => (
    <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.12em', color: 'var(--accent)', margin: '18px 0 10px' }}>{t}</div>
  )

  return (
    <Modal title="Configuración" onClose={onClose} width={620}>
      <div style={{ display: 'flex', gap: 6, marginBottom: 4, flexWrap: 'wrap' }}>
        {([['presets', 'Presets'], ['manual', 'Personalización manual'], ['copiloto', 'Mi Copiloto IA']] as const).map(([id, label]) => (
          <button key={id} onClick={() => setSection(id)}
            className={section === id ? 'btn-green' : 'btn-ghost'}
            style={{ padding: '6px 12px', fontSize: 12 }}>{label}</button>
        ))}
      </div>

      {section === 'presets' && (
        <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
          {(Object.keys(PRESETS) as PresetId[]).map(id => (
            <button key={id} onClick={() => applyPreset(id)}
              style={{
                textAlign: 'left', background: prefs.preset === id ? 'var(--accent-soft)' : 'transparent',
                border: `1px solid ${prefs.preset === id ? 'var(--accent)' : 'var(--border)'}`,
                borderRadius: 10, padding: '12px 14px', cursor: 'pointer', color: 'var(--text-primary)', fontFamily: 'var(--font-ui)',
              }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ width: 12, height: 12, borderRadius: 3, background: PRESETS[id].patch.colorPrimary ?? 'var(--accent)' }} />
                <strong style={{ fontSize: 13 }}>{PRESETS[id].label}</strong>
                {prefs.preset === id && <span style={{ fontSize: 10, color: 'var(--accent)' }}>ACTIVO</span>}
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 4 }}>{PRESETS[id].desc}</div>
            </button>
          ))}
        </div>
      )}

      {section === 'manual' && (
        <div>
          {sectionTitle('1. COLORES')}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '0 14px' }}>
            {colorField('Acento primario', 'colorPrimary')}
            {colorField('Acento énfasis', 'colorEmphasis')}
            {colorField('Datos / mono', 'colorAccentTech')}
            {colorField('Fondo base', 'colorBackground')}
            {colorField('Superficie (tarjetas)', 'colorBgCard')}
            {colorField('Superficie 2 (modales)', 'colorBgSurface')}
            {colorField('Borde', 'colorBorder')}
            {colorField('Texto principal', 'colorText')}
            {colorField('Texto secundario', 'colorTextSecondary')}
            {colorField('Texto tenue', 'colorTextMuted')}
            {colorField('Alerta leve', 'colorAlertLeve')}
            {colorField('Alerta crítica', 'colorAlertCritica')}
          </div>

          {sectionTitle('2. TIPOGRAFÍA')}
          <Field label="Fuente principal">
            <select value={prefs.fontFamily} onChange={e => patch({ fontFamily: e.target.value })}>
              {FONT_OPTIONS.map(f => <option key={f} value={f}>{f}</option>)}
            </select>
          </Field>
          <Field label={`Tamaño base — ${prefs.fontSize}px`}>
            <input type="range" min={12} max={18} value={prefs.fontSize} onChange={e => patch({ fontSize: Number(e.target.value) })} />
          </Field>
          <Field label="Peso de fuente">
            <div style={{ display: 'flex', gap: 14, fontSize: 13, color: 'var(--text-secondary)' }}>
              {([[400, 'Normal'], [600, 'Semibold'], [700, 'Bold']] as const).map(([w, l]) => (
                <label key={w} style={{ display: 'flex', gap: 6, alignItems: 'center', cursor: 'pointer' }}>
                  <input type="radio" name="fw" checked={prefs.fontWeight === w} onChange={() => patch({ fontWeight: w })} />{l}
                </label>
              ))}
            </div>
          </Field>

          {sectionTitle('3. ANIMACIONES')}
          <Field label={`Velocidad de transiciones — ${prefs.transitionMs}ms`}>
            <input type="range" min={0} max={400} step={10} value={prefs.transitionMs} onChange={e => patch({ transitionMs: Number(e.target.value) })} />
          </Field>
          <Toggle label="Parpadeo en alertas" value={prefs.blinkAlerts} onChange={v => patch({ blinkAlerts: v })} />
          <Toggle label="Efectos hover" value={prefs.hoverEffects} onChange={v => patch({ hoverEffects: v })} />

          {sectionTitle('4. NOTIFICACIONES')}
          <Field label={`Duración de alertas — ${prefs.alertDurationS}s`}>
            <input type="range" min={3} max={10} value={prefs.alertDurationS} onChange={e => patch({ alertDurationS: Number(e.target.value) })} />
          </Field>
          <Field label="Posición">
            <div style={{ display: 'flex', gap: 14, fontSize: 13, color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
              {([['top-right', 'Arriba derecha'], ['top-center', 'Arriba centro'], ['bottom-right', 'Abajo derecha']] as const).map(([p, l]) => (
                <label key={p} style={{ display: 'flex', gap: 6, alignItems: 'center', cursor: 'pointer' }}>
                  <input type="radio" name="pos" checked={prefs.alertPosition === p} onChange={() => patch({ alertPosition: p })} />{l}
                </label>
              ))}
            </div>
          </Field>
          <Toggle label="Sonido de notificación" value={prefs.sound} onChange={v => patch({ sound: v })} />

          {/* Las preferencias se guardan solas en cuanto cambian (ver prefs.tsx),
              así que no hay un botón "Guardar": tenerlo sugeriría que sin pulsarlo
              se pierden. Antes existía y mostraba "✓ Cambios guardados" sin
              guardar absolutamente nada. */}
          <div style={{ display: 'flex', gap: 10, marginTop: 22, alignItems: 'center' }}>
            <span style={{ flex: 2, fontSize: 12, color: 'var(--text-muted)' }}>
              Los cambios se guardan automáticamente en este equipo.
            </span>
            <button className="btn-ghost" style={{ flex: 1, padding: '10px 0', fontSize: 13 }} onClick={reset}>↶ Resetear</button>
          </div>
        </div>
      )}

      {section === 'copiloto' && (
        <div>
          {sectionTitle('GALERÍA DE AVATARES')}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 8 }}>
            {AVATARS.map(a => (
              <button key={a.id} onClick={() => patch({ avatarId: a.id })}
                style={{
                  background: prefs.avatarId === a.id ? 'var(--accent-soft)' : 'transparent',
                  border: `1px solid ${prefs.avatarId === a.id ? 'var(--accent)' : 'var(--border)'}`,
                  borderRadius: 10, padding: '14px 10px', cursor: 'pointer', textAlign: 'center',
                  color: 'var(--text-primary)', fontFamily: 'var(--font-ui)',
                }}>
                <div style={{
                  width: 44, height: 44, margin: '0 auto', borderRadius: '50%', color: 'var(--accent)',
                  background: 'var(--bg-card)', border: '1.5px solid var(--accent-line)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  <AvatarIcon id={a.id} size={22} />
                </div>
                <div style={{ fontSize: 12, fontWeight: 600, marginTop: 8 }}>{a.label}</div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 2, lineHeight: 1.35 }}>{a.desc}</div>
              </button>
            ))}
          </div>

          {sectionTitle('IDENTIDAD')}
          <Field label="Nombre del asistente">
            <input type="text" value={prefs.avatarName} onChange={e => patch({ avatarName: e.target.value })}
              style={{ width: '100%', padding: '9px 10px' }} />
          </Field>
          <Field label="Tono de comunicación">
            <div style={{ display: 'flex', gap: 14, fontSize: 13, color: 'var(--text-secondary)', flexWrap: 'wrap' }}>
              {([['formal', 'Formal y directo'], ['amable', 'Amable y detallado'], ['tecnico', 'Conciso y técnico']] as const).map(([t, l]) => (
                <label key={t} style={{ display: 'flex', gap: 6, alignItems: 'center', cursor: 'pointer' }}>
                  <input type="radio" name="tono" checked={prefs.avatarTone === t} onChange={() => patch({ avatarTone: t })} />{l}
                </label>
              ))}
            </div>
          </Field>

          {sectionTitle('MODO DE PRESENCIA')}
          <div style={{ display: 'grid', gap: 8 }}>
            {([
              ['ghost', 'Ghost', 'Oculto. Solo aparece en el panel Copiloto o durante un tutorial.'],
              ['follower', 'Follower', 'Avatar flotante en la esquina inferior derecha; clic para abrir el chat.'],
              ['guide', 'Guide', 'Tutorial asistido: el avatar se posiciona junto a cada elemento y lo explica.'],
            ] as const).map(([m, label, desc]) => (
              <button key={m} onClick={() => patch({ avatarMode: m })}
                style={{
                  textAlign: 'left', background: prefs.avatarMode === m ? 'var(--accent-soft)' : 'transparent',
                  border: `1px solid ${prefs.avatarMode === m ? 'var(--accent)' : 'var(--border)'}`,
                  borderRadius: 10, padding: '10px 12px', cursor: 'pointer', color: 'var(--text-primary)', fontFamily: 'var(--font-ui)',
                }}>
                <strong style={{ fontSize: 13 }}>{label}</strong>
                <div style={{ fontSize: 12, color: 'var(--text-secondary)', marginTop: 3 }}>{desc}</div>
              </button>
            ))}
          </div>
        </div>
      )}
    </Modal>
  )
}

function Toggle({ label, value, onChange }: { label: string; value: boolean; onChange: (v: boolean) => void }) {
  return (
    <label style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', fontSize: 13, color: 'var(--text-secondary)', cursor: 'pointer' }}>
      {label}
      <button type="button" onClick={() => onChange(!value)}
        style={{
          width: 40, height: 22, borderRadius: 999, border: '1px solid var(--border)', cursor: 'pointer',
          background: value ? 'var(--accent)' : 'var(--bg-input)', position: 'relative', transition: 'background var(--t)',
        }}>
        <span style={{
          position: 'absolute', top: 2, left: value ? 20 : 2, width: 16, height: 16, borderRadius: '50%',
          background: value ? '#041007' : 'var(--text-muted)', transition: 'left var(--t)',
        }} />
      </button>
    </label>
  )
}
