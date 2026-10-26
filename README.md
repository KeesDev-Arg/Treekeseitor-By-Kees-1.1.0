# Treekeseitor by Kees

Plugin de Paper para **Minecraft 26.3** (Java 25) que tala el árbol completo al romper un tronco con un hacha.
Pensado para convivir con el trabajo **Woodcutter (talador) de Jobs**.

## Compilar

Requiere JDK 25 y Gradle 9.1 o superior.

```
gradle wrapper      # una sola vez, genera ./gradlew
./gradlew build
```

El jar queda en `build/libs/Treekeseitor-1.1.0.jar`. Cópialo a `plugins/` y reinicia el servidor.

> Paper 26.3 sigue en beta. El `build.gradle.kts` fija `26.3.build.143-beta`; si hay un build más nuevo,
> cambia esa línea a `26.3.build.+`.

## Cómo funciona con Jobs

- Cada tronco extra se rompe con `Player#breakBlock`, que dispara un **`BlockBreakEvent` real** por bloque.
  Jobs lo recibe igual que si el jugador hubiera picado ese tronco, así que **cada tronco cuenta y paga como un Break del Woodcutter**.
- Esos mismos eventos pasan por WorldGuard, claims y demás protecciones: si no puede romper ahí, no se tala.
- Guardia anti-recursión: los eventos que genera el propio plugin no vuelven a disparar otro talado.
- Se talan **solo árboles naturales**: tiene que haber un mínimo de hojas no colocadas por jugadores (`min-leaves`).
  Así nadie construye una torre de troncos para cobrar de más.
- `softdepend: [Jobs]` hace que Jobs cargue antes.

### Ajuste necesario en tu `jobs/woodcutter.yml`

Tu Woodcutter solo paga roble, abeto, abedul, jungla, acacia y roble oscuro (y sus versiones sin corteza).
**No paga** cerezo, manglar, roble pálido ni los troncos del Nether, así que talar esos árboles no daría dinero.
Agrega esto dentro de `Break:` → `materials:` (mantén la misma indentación que las líneas que ya existen):

```yaml
    - cherry_log;1.0;1.0;1.0
    - mangrove_log;1.0;1.0;1.0
    - pale_oak_log;1.0;1.0;1.0
    - crimson_stem;1.0;1.0;1.0
    - warped_stem;1.0;1.0;1.0
    - stripped_cherry_log;0.75;0.75;0.25
    - stripped_mangrove_log;0.75;0.75;0.25
    - stripped_pale_oak_log;0.75;0.75;0.25
    - stripped_crimson_stem;0.75;0.75;0.25
    - stripped_warped_stem;0.75;0.75;0.25
```

Luego `/jobs reload`.

### Ojo con la economía

Como cada tronco paga, un árbol grande con Treekeseitor da muchas veces lo de un hachazo normal
(hasta `max-logs`, por defecto 200). Si te parece mucho, baja `max-logs` o los ingresos del Woodcutter.

## Comandos y permisos

| Comando | Permiso | Por defecto |
|---|---|---|
| `/treekeseitor`, `/tk` o `/tc` (alterna) | `treekeseitor.toggle` | todos |
| `/treekeseitor effects` (alterna sonidos y partículas) | `treekeseitor.effects` | todos |
| `/treekeseitor reload` | `treekeseitor.reload` | op |
| (usar el talado) | `treekeseitor.use` | todos |

Por defecto tala todo el árbol salvo que vayas **agachado** (`activation-mode` en `config.yml`).

## Novedades 1.1.0

- **Hojas**: al terminar los troncos, las hojas del árbol caen de golpe con sus drops normales
  (se disparan como `LeavesDecayEvent`, así que las protecciones pueden cancelarlas). Solo se quitan
  las hojas que dependían de ese árbol; las de un árbol vecino se quedan.
- **Drops juntos** (`drops.mode`): `ORIGIN` suelta todo en pilas completas en el tronco base,
  `PLAYER` lo manda al inventario, `NATURAL` es el comportamiento anterior. Además de ordenar,
  baja mucho la cantidad de entidades de ítem.

- **Efectos** (`effects` en la config): un sonido y un estallido de partículas por tanda, solo para
  jugadores cercanos. Cada jugador los apaga o prende con `/treekeseitor effects`
  (permiso `treekeseitor.effects`); el servidor los puede apagar del todo con `effects.enabled: false`.

## Qué no hace (todavía)

- No soporta Folia.
