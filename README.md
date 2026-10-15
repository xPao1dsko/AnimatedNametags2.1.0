# AnimatedNametags v2

Plugin para **Paper 1.21+** que reemplaza los nametags por **TextDisplays** (sin depender de ningún plugin de hologramas).

## Características
- **MiniMessage** completo y **varias líneas** por nametag
- **Degradados / rainbow animados** por ticks (`{phase}`), con **presets** de color (`{preset:fire}`)
- **Líneas con frames**: el texto cambia solo (parpadeos, rangos rotativos, etc.)
- **Fondo animado** (transiciona entre colores), sombra, opacidad, tamaño, alineación **por formato**
- **Icono (item)** sobre el nametag con `ItemDisplay`
- **GUI de administración** (`/ant gui`): crear/editar/borrar formatos, editar líneas, ajustes globales
- **Preview** de cualquier formato sobre ti (`/ant preview <id>`)
- Oculta nametag **vanilla** y los de **TAB**
- Formatos por **permiso y prioridad**
- **PlaceholderAPI** opcional + colores legacy (`&c`, `&#ff0000`)
- Placeholders internos: `<player> <displayname> <health> <max_health> <health_bar> <level> <gamemode> <world> <ping>`
- **Lunar Client**: `/ant self` por jugador (persistente) + detección opcional de usuarios de Lunar
- Mundos deshabilitados, soporte de vanish, se atenúa al agacharse, caché de render para rendimiento

## Instalación
1. Descarga el `.jar` desde **Releases** o *Actions → Build → Artifacts*.
2. Ponlo en `plugins/` y reinicia.
3. Abre `/ant gui` (permiso `animatednametags.admin`).

> **Actualizando desde v1:** tus formatos de `config.yml` se migran solos a `formats.yml` en el primer arranque. Las claves nuevas se agregan a `config.yml` automáticamente.

## Comandos y permisos
| Comando | Permiso | Descripción |
|---|---|---|
| `/ant gui` | `animatednametags.admin` | Abre la GUI de administración |
| `/ant preview <id\|off>` | `animatednametags.admin` | Ver un formato sobre ti |
| `/ant list` | `animatednametags.admin` | Lista los formatos |
| `/ant reload` / `refresh` | `animatednametags.admin` | Recarga / reaplica |
| `/ant debug` | `animatednametags.admin` | Formato, Lunar y texto generado |
| `/ant toggle [jugador]` | `.toggle` / `.admin` | Activa/desactiva el nametag |
| `/ant self` | `animatednametags.self` | Oculta/muestra TU propio nametag |

Cada formato usa el permiso que le pongas (por defecto `animatednametags.format.<id>`). Los OP **no** los reciben solos: asígnalos con LuckPerms.

## formats.yml
```yaml
formats:
  owner:
    permission: animatednametags.format.owner
    priority: 200
    animation-speed: 0.04        # cuánto avanza {phase} por tick
    shadow: true                 # opcional, si falta usa el global
    scale: 1.2
    alignment: center            # left | center | right
    text-opacity: 100            # 10-100
    background: "40000000"       # default | none | AARRGGBB
    background-animation:        # si existe, reemplaza al fondo fijo
      colors: [80FF0000, 80FFAA00, 80FF0000]
      speed: 0.01
    icon:
      material: NETHER_STAR
      scale: 0.6
    lines:
      - frames:                  # línea animada por frames
          - "<gradient:{preset:fire}:{phase}>★ OWNER ★</gradient>"
          - "<gradient:{preset:gold}:{phase}>✦ OWNER ✦</gradient>"
        interval: 20             # ticks entre frames
      - "<gradient:{preset:fire}:{phase_rev}><player></gradient>"
      - "<health_bar>"
```

**Animación:** `{phase}` va de -1 a 1 en bucle (úsalo como último argumento de `<gradient:...>` o `<rainbow:...>`). `{phase_rev}` va en sentido contrario. `{tick}` es el contador de ticks.

**Presets:** se definen en `config.yml` (`presets:`) y se usan con `{preset:nombre}`.

## GUI
- **Lista:** click izq = editar, click der = preview, shift+der = eliminar, botón para crear.
- **Editor:** líneas (izq editar, der borrar, shift+izq subir, shift+der añadir frame, Q intervalo), permiso, prioridad, velocidad, tamaño, altura, sombra, see-through, alineación, fondo, opacidad, fondo animado, icono.
- **Ajustes:** toggles y valores de `config.yml`. Al usarlos el plugin guarda `config.yml` y recarga.
- Los textos se escriben por **chat** (escribe `cancelar` para salir).

## Lunar Client
Lunar no deja que el servidor lea el ajuste de mods "ver mi propio nametag", así que:
- Cada jugador usa `/ant self` (se guarda en `data.yml`).
- `lunar.hide-own-nametag-for-lunar-users: true` oculta el propio nametag por defecto a quienes usan Lunar (detección por el canal `lunarclient:apollo`).

## Compilar
```bash
mvn clean package
```
O sube el repo a GitHub: el workflow `.github/workflows/build.yml` compila el `.jar`. Con un tag `v*` lo adjunta a un Release.

## Notas
- Si un jugador ya está en otro team del scoreboard (por otro plugin), el ocultado vanilla no lo toca.
- Si el nametag queda muy alto o bajo, ajusta `y-offset` (global o por formato).
- Subir `update-interval-ticks` reduce tráfico en servidores grandes.
- **Montaje por paquetes (como UnlimitedNameTags, sin instalar PacketEvents):** el cliente ve el nametag montado en el jugador, pero el servidor no lo considera pasajero, así que **no bloquea ningún teleport** (Multiverse, RTP, Essentials, portales, NPCs...). Al arrancar, la consola dice si está activo; si tu versión de Paper no lo permite, el plugin usa solo un modo de seguimiento (el nametag sigue al jugador cada tick).
- No hay soporte para Folia.
