# GWW

Mod de servidor para Minecraft Fabric 1.21.1 con dos minijuegos:

- **Escape room**: salas con puzzles (paneles de fusibles y de código, válvulas, cables,
  cuadros, puertas), una barra de ruido compartida por sala y el **Ublabla**, que viene a
  investigar si se hace demasiado ruido y devuelve a los jugadores a la jaula.
- **Arena de cañones**: zona circular cerrada donde aparecen cañones que disparan balas a
  esquivar, con cámara cenital y puntuación.

Está pensado para correr con ~100 jugadores repartidos en muchas salas a la vez, así que
todo lo que se hace por tick va acotado a la gente que realmente está en la sala.

## Configuración

Los archivos se crean **solo en el servidor**, en `config/GWW/`, la primera vez que
arranca. Un cliente que se conecta no genera nada.

| Archivo | Qué guarda |
|---|---|
| `settings.yml` | Tiempos del Ublabla y dificultad de la barra de ruido |
| `config.yml` | Arenas del escape room (zona, jaula, instance) |
| `cannon.yml` | Arenas del minijuego de cañones |
| `instances/` | Salas capturadas con `/escaperoom save` |

`/escaperoom reload` recarga todo sin reiniciar.

## Comandos principales

| Comando | Para qué |
|---|---|
| `/escaperoom capture <radio>` / `save <nombre>` | Capturar y guardar una sala |
| `/escaperoom arena create ...` | Crear una arena |
| `/escaperoom start` / `stop` / `reset <id>` | Manejar la partida de una sala |
| `/escaperoom config ...` | Vincular entidades entre sí (paneles, puertas, cables) |
| `/cc arena create ...` / `/cc start` / `/cc stop` | Minijuego de cañones |
| `/noise toggle` / `status` | Barra de ruido a mano |

## Debug

Todo apagado por defecto: en producción no cuesta nada. Requiere permiso de operador.

| Comando | Qué hace |
|---|---|
| `/escaperoom debug` | Muestra qué está encendido |
| `/escaperoom debug info` | Estado actual: jugadores, arenas, grupos de ruido, partidas |
| `/escaperoom debug <categoria> on\|off` | Enciende una categoría de logs |
| `/escaperoom debug all on\|off` | Todas de golpe |
| `/escaperoom debug stats on` | Empieza a contar paquetes y escaneos por segundo |
| `/escaperoom debug stats` | Muestra el último segundo |
| `/escaperoom debug stats console` | Además lo vuelca a consola cada segundo |
| `/escaperoom debug stats off` | Apaga los contadores |

Categorías: `noise`, `packets`, `ublabla`, `arena`, `minigame`, `puzzle`.

Si el servidor va lento o hay desconexiones, lo primero es
`/escaperoom debug stats console` y mirar qué contador se dispara.

## Compilar

```
./gradlew build
```

El jar sale en `build/libs/`.

## Licencia

CC0.
