PROMPT PARA FIGMA MAKE — SICOT · Sistema Visual v3 (Identidad y Temas)

PROYECTO: SICOT — Sistema Inteligente para la Gestión y Acompañamiento de Contratos
ALCANCE: Exclusivamente visual. No modifica lógica de negocio, cálculos, condiciones de alerta ni flujos definidos en SICOT v2.1 — únicamente re-viste los componentes existentes con un nuevo sistema de diseño.
PÚBLICO OBJETIVO: Administración SENA, supervisores, asistentes, subdirectores. Debe percibirse serio, técnico y confiable — no "gamer", no plantilla SaaS genérica.

=== DIRECCIÓN ESTÉTICA ===

Concepto: "Panel de instrumento técnico de precisión" — inspirado en mesas de dibujo técnico y planos de taller, coherente con la identidad del Centro Tecnológico del Mobiliario (CTMA): precisión, medición, oficio. Esto se traduce en:

Cuadrícula fina de fondo (líneas de 1px, opacidad 4-6%) en zonas de marca, como papel milimetrado
Tipografía monoespaciada para todo dato "medible": códigos de contrato, NIT, fechas, porcentajes, montos — como si fueran lecturas de instrumento
Bordes definidos y jerarquía clara, sin degradados decorativos gratuitos
El verde de marca se usa como acento de énfasis puntual, nunca como color de relleno grande (ya establecido en v2, se mantiene)

Elemento de firma visual: la cuadrícula técnica sutil + las lecturas en mono es lo que hace reconocible a SICOT frente a cualquier otro dashboard oscuro genérico.

=== SISTEMA DE COLOR (4 TEMAS) ===

TEMA 1 — OSCURO INSTITUCIONAL (predeterminado, incluida la pantalla de login)

Rol	Hex	Uso
Fondo base (canvas)	
#0B0F14	Fondo general de la app
Superficie 1	
#141B22	Tarjetas, contenedores
Superficie 2	
#1D2530	Modales, dropdowns, tooltips
Borde / división	
#2B3542	Líneas divisorias, bordes de tarjeta
Texto primario	
#EDF1F5	Títulos, texto principal
Texto secundario	
#8D98A8	Descripciones, metadatos
Acento de marca	
#22C58B	Botones primarios, enlaces, navegación activa
Acento de énfasis	
#2EE6A0	Barra de progreso al 100%, confirmaciones, micro-highlights (identidad SICOT ya establecida)
Acento técnico/dato	
#D9A65C	Códigos de contrato, cifras, resaltado en tipografía mono

TEMA 2 — CLARO INSTITUCIONAL

Rol	Hex
Fondo base	
#F6F7F9
Superficie 1	
#FFFFFF
Superficie 2	
#EEF1F5
Borde	
#DEE3E9
Texto primario	
#1A212B
Texto secundario	
#5C6675
Acento de marca	
#147A5A
Acento de énfasis	
#1FAE79
Acento técnico	
#B8823A

TEMA 3 — PASTEL SUAVE

Rol	Hex
Fondo base	
#F5F2FA
Superficie 1	
#FFFFFF
Superficie 2	
#EDE8F5
Borde	
#E0D8EE
Texto primario	
#33303F
Texto secundario	
#7C7690
Acento de marca	
#6FA79A (verde salvia)
Acento de énfasis	
#8FC6B4
Acento técnico	
#CBA46B (dorado suave)

TEMA 4 — ALTO CONTRASTE (se conserva de v2, por accesibilidad)
Fondo 
#000000 / Texto 
#FFFFFF / Acento 
#FFD400 / Bordes 2px sólidos, sin degradados, foco de teclado siempre visible y grueso.

COLORES SEMÁNTICOS POR TEMA (críticos/pendientes/completado — mantienen su significado funcional ya definido, solo se afina el matiz por tema):

Estado	Oscuro	Claro	Pastel
Crítico	
#EF4B4B	
#D6453F	
#E38585
Pendiente	
#F2B84B	
#C98A2D	
#D9AF6B
Completado	
#22C58B	
#147A5A	
#6FA79A

=== TIPOGRAFÍA ===

Display (títulos, cifras destacadas): Space Grotesk — geométrica, con carácter técnico, se aleja de los genéricos Inter/Roboto
Cuerpo (texto general, formularios, descripciones): IBM Plex Sans — familia diseñada originalmente para identidad corporativa/institucional, muy legible, encaja con el carácter técnico-formal de SENA
Datos/mono (códigos, NIT, fechas, montos, porcentajes): IBM Plex Mono — refuerza la metáfora de "lectura de instrumento"

Escala:

H1: 32/40 Semibold (Space Grotesk)
H2: 24/32 Semibold
H3: 18/26 Medium
Cuerpo base: 15/22 Regular (IBM Plex Sans)
Cuerpo pequeño: 13/18 Regular
Dato mono: 13/20 Medium (IBM Plex Mono)

=== ELEVACIÓN, BORDES Y ESPACIADO ===

Radios: sm 6px (inputs, chips) · md 10px (tarjetas) · lg 16px (modales)
Espaciado base: escala 4 / 8 / 12 / 16 / 24 / 32 / 48px
Sombras: en tema oscuro NO usar sombra negra (invisible sobre fondo oscuro) — usar borde sutil + resplandor interior tenue del acento. En temas claro/pastel, sombra suave convencional (offset bajo, blur amplio, opacidad baja)

=== PANTALLA DE INICIO DE SESIÓN — REDISEÑO ===

Nota de coherencia: el login ocurre antes de que el sistema conozca la preferencia del usuario, así que siempre se presenta en Tema Oscuro Institucional (la cara de marca de SICOT), independientemente del tema que el usuario haya guardado. Una vez autenticado, se aplica su tema personalizado.

┌──────────────────────────────────────────┬───────────────────────┐
│  PANEL DE IDENTIDAD (60%)                 │  ACCESO (40%)         │
│  Fondo #0B0F14 + cuadrícula técnica sutil │  Fondo Superficie 1   │
│  animada (líneas 1px, opacidad 4-6%,      │                       │
│  pulso lento en intersecciones)           │   [Ícono / Logo]      │
│                                            │   SICOT               │
│   SICOT                                   │   Sistema de Gestión  │
│   Sistema Inteligente de Gestión          │   y Supervisión de    │
│   y Acompañamiento de Contratos           │   Contratos — CTMA    │
│                                            │                       │
│   "Precisión en cada etapa del contrato"  │   Correo institucional│
│                                            │   [________________] │
│   ┌──────────────┐ ┌──────────────┐       │   Contraseña          │
│   │ 24 contratos │ │ 98% cumplim. │       │   [________________] │
│   │ activos      │ │ documental   │       │                       │
│   └──────────────┘ └──────────────┘       │   [   Ingresar   ]    │
│   (lectura en tipografía mono,            │                       │
│    tono ambiental — usar datos reales     │   ¿Olvidaste tu       │
│    si el backend los provee)              │   contraseña?         │
└──────────────────────────────────────────┴───────────────────────┘

En viewport móvil: el panel de identidad se colapsa a una franja superior de 120px (logo + wordmark), formulario ocupa el resto en pantalla completa.

=== IDENTIDAD DE MARCA: LOGO Y NOMBRE PERSONALIZABLE ===

Ubicación: Personalización → "Identidad de Marca" (nueva subsección, separada de Apariencia)

Editable por el usuario/administrador:

Símbolo/ícono: subir imagen propia, o elegir entre una galería de 6 símbolos geométricos abstractos coherentes con el motivo técnico (escuadra, compás, capa, cuadrícula, etc.)
Color de acento del símbolo: tomado de la paleta del tema activo
Subtítulo institucional opcional (ej. "CTMA", "Regional Antioquia"), máximo 24 caracteres, se muestra debajo o junto al wordmark

Fijo, no editable:

El texto "SICOT" debe permanecer siempre visible, en la tipografía Display definida, tanto en el encabezado principal como en la pantalla de login
El campo de nombre del sistema debe bloquearse (mostrarse en gris/disabled) para impedir que un usuario lo reemplace u oculte por error

=== PERSONALIZACIÓN — PRESETS ACTUALIZADOS (reemplaza la sección de presets de v2) ===

Preset	Base	Cuándo usarlo
Oscuro Institucional (predeterminado)	Tema 1	Uso general, coincide con el login
Claro Institucional	Tema 2	Oficinas con mucha luz, preferencia personal
Pastel Suave	Tema 3	Uso prolongado, menor fatiga visual
Alto Contraste	Tema 4	Accesibilidad

El panel de personalización manual (colores, tipografía, animaciones — ya definido en v2) sigue disponible y ahora parte del preset elegido como punto de partida, con vista previa en vivo.

=== REFINAMIENTO VISUAL DE COMPONENTES CLAVE (sin alterar su lógica) ===

Barra de progreso lineal: extremo con resplandor sutil del color de estado activo; el porcentaje se muestra en IBM Plex Mono
Tarjetas de documento: iconografía outline, contorno que se ilumina levemente en hover, franja de estado a la izquierda (3px) en vez de solo texto de color
Alertas: en tema oscuro, anillo de resplandor tenue alrededor del ícono en vez de fondo sólido saturado; en claro/pastel, formato de chip con borde de color
Panel del Copiloto: burbujas de chat con esquinas asimétricas (más redondeadas del lado del emisor), avatar con el símbolo de marca por defecto

=== ICONOGRAFÍA ===
Librería: Phosphor Icons, trazo 1.5px, variante Regular para estado inactivo y Bold para estado activo/seleccionado — consistencia total en todos los módulos.

=== MICROINTERACCIONES Y MOVIMIENTO ===

Hover en tarjetas: elevación de 2px + borde se ilumina (150ms)
Botones: escala 0.98 al presionar
Transiciones generales: 200-250ms (ya definido en v2, se mantiene)
Parpadeo de alertas: 0.8s (ya definido, se mantiene)
Foco de teclado: anillo de 2px del acento de marca, con 2px de separación — siempre visible
Respetar "prefers-reduced-motion": si está activo, desactivar parpadeos y transiciones de posición, dejar solo cambios de color

=== ACCESIBILIDAD ===

Contraste mínimo AA: 4.5:1 en texto de cuerpo, 3:1 en texto grande
El toggle "Habilitar Parpadeo en Alertas" (ya definido en v2) sigue disponible para quienes lo necesiten
Alto Contraste como tema completo, no solo ajuste de opacidad

=== INSTRUCCIONES TÉCNICAS PARA FIGMA MAKE ===

Crear en este orden:

Página de Sistema de Diseño (tokens: color, tipografía, espaciado, iconografía) — maestro de referencia
Pantalla de Login (Tema Oscuro Institucional, panel dividido)
Dashboard Supervisor — reskin visual sobre la estructura ya definida en v2.1
Personalización → Apariencia (selector de 4 temas con preview en vivo)
Personalización → Identidad de Marca (logo + subtítulo)
Tarjeta de Documento — refinamiento visual
Panel de Alertas y Copiloto — refinamiento visual
Validación en modo Alto Contraste

Variables de Figma: definir los 4 temas como modos de una misma colección de variables de color, para poder alternar sin duplicar componentes.

Guardar como: "SICOT-v3-Sistema-Visual-[FechaHoy]"

=== FIN DEL PROMPT ===