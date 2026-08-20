# Plan de cambios: segunda ronda del escape room

> Estado: **PLAN CERRADO** — todas las decisiones tomadas. La ejecución arranca con tu OK.
> Versión actual del mod: 1.0.35.

## 1. Por qué cambiar

Algunos jugadores ya completaron el escape room y la solución se corre de boca en boca.
El problema de fondo: varias soluciones **están fijas en el código** o enterradas dentro
de cada entidad, y no se pueden cambiar sin recompilar o reconfigurar sala por sala.

La meta: que **todo lo cambiable viva en un solo `puzzles.yml`**, y que en el futuro
rotar el escape room sea editar ese archivo y hacer `/escaperoom reload`.

Importante: el escape room se juega **como competencia**, así que todas las salas
tienen siempre la misma solución y no hay nada aleatorio. Lo justo es que todos los
equipos enfrenten exactamente lo mismo.

## 2. Qué hay hoy (revisé todos los puzzles del mod)

| Puzzle / elemento | Cómo se resuelve hoy | ¿Cambiable sin recompilar? |
|---|---|---|
| Panel de código | Secuencia fija de 4 figuras | **No** — fija en el código |
| Panel de fusibles | 2 circuitos de 3 fusibles (rojo/verde/azul) | Solo comando por panel |
| Cables + interruptor | Enchufar el cable correcto (lineal/curvo) y girarlo a su rotación exacta; el interruptor solo enciende si TODOS están bien | **No** — la rotación correcta solo vive dentro de cada cable (ni comando tiene) |
| Válvulas + manivelas | Llevar la manivela del tipo correcto a su válvula y girarla hasta el estado exacto (el vapor es la única pista) | Solo comando por válvula |
| Cuadros → colgante → ganzúa | Al descolgar un cuadro cae el colgante; con el colgante se craftea la ganzúa | Se queda como está (decisión tuya) |
| Cajas (palanca) y caja de herramientas | La palanca revienta la caja; la ganzúa abre la de herramientas; sueltan su contenido | El contenido vive dentro de cada caja, sin comando |
| Reja del ducto | Se abre sola cuando hay corriente cortada + todas las válvulas resueltas | Enlaces por comando |
| Puerta del ático | Se abre con la llave del ático escondida en el mapa: la encontrás y listo | **No** — item fijo en código (cambia en este plan) |
| Ruido / Ublabla | Barra de ruido compartida; el Ublabla castiga | **Sí** — settings.yml ya existe y lo configurás vos |

Dato operativo: las 50 salas se clonan de la instancia guardada. Habrá **un único paso
de mapa tuyo** al final (colocar el panel nuevo y los números de pared, ponerle nombre a
las cajas y re-guardar la instancia); de ahí en adelante, todo se rota desde el yml.

## 3. La base de todo — `puzzles.yml`

Un solo archivo en `config/GWW/puzzles.yml` (al lado de settings.yml, que ya funciona
así y se recarga con `/escaperoom reload`). **Todo lo cambiable vive acá:**

```yml
teclados:                       # cada teclado se identifica por su nombre
  teclado_salida: "4729"        # abre las puertas metálicas (antes panel de figuras)
  teclado_atico: "8153"         # abre la puerta del ático (antes la llave)

fusibles:                       # ahora 4 por circuito (8 en total), 5 colores
  circuito_1: [rojo, amarillo, azul, verde]
  circuito_2: [violeta, verde, rojo, azul]

cables:
  rotaciones: [2, 0, 3, 1]      # rotación correcta de cada cable, en orden

valvulas:
  estados_correctos: [3, 1, 4]  # estado exacto de cada válvula, en orden

cajas:                          # cada caja se identifica por su nombre
  caja_taller: [alicate_cortacables, fusible_rojo]
  caja_deposito: [llave_atico, cable_curvo]
```

Reglas de funcionamiento:

- **Editás el yml → `/escaperoom reload` → las 50 salas quedan actualizadas.**
- Cables y válvulas se numeran solos por su posición en la sala (todas las salas son
  clones, así que el orden es el mismo en todas).
- Los teclados y las cajas se identifican **por su nombre**: se lo ponés una sola vez
  en la sala master, y ese nombre es la clave en el yml.
- Si una clave falta, la entidad usa su valor guardado: nada viejo se rompe.
- Las salas **con partida en curso mantienen su solución hasta el próximo reset/start**,
  para no cambiarle la clave a un equipo a mitad de partida.
- Si el archivo tiene un error (color inexistente, letras en el código), el mod avisa
  en consola al recargar y conserva la configuración anterior.

## 4. Cambio 1 — Codelock por números: ahora son DOS teclados

Una sola entidad nueva de teclado 0–9, y en la sala van **dos**:

- **Teclado de salida**: reemplaza en la sala al panel de figuras (que queda en el mod
  tal cual, sin tocarse). Abre las puertas metálicas que le vincules.
- **Teclado del ático (nuevo puzzle)**: la puerta del ático **deja de abrirse con la
  llave** — encontrar una llave y listo era demasiado simple. Ahora tiene su propio
  teclado con su propio código. La llave del ático queda sin uso: si querés podés
  dejarla igual en el loot como señuelo para los veteranos, no hace falta tocar nada.

Detalles (aplican a ambos):

- **El modelo 3D lo hago yo**, basándome en el panel actual (mismo estilo, geo + textura
  + hitboxes de las 10 teclas).
- Cada teclado se identifica **por su nombre** (igual que las cajas) y su código de
  **4 dígitos** se lee de `puzzles.yml`: 10.000 combinaciones cada uno.
- Compatibilidad: una puerta de ático sin teclado vinculado sigue aceptando la llave,
  así nada viejo se rompe.
- **Cada tecla suena con un tono distinto, estilo teléfono** (variación de pitch del
  sonido actual: cero archivos nuevos, cero tráfico extra).
- **Números de pared: entidad nueva también** (la de figuras no se toca). Es siempre
  la misma entidad y muestra **cualquier número del 0 al 99**: al hacer clic cambia la
  textura, sin trabajo extra tuyo. En modo creativo, **clic = +1** y **agachado +
  clic = +10**, así llegás rápido a cualquier número. Las colocás **donde quieras y en
  la cantidad que quieras**, y quedan guardadas en la instancia. Yo hago la entidad,
  el modelo y la textura de los dígitos; **qué número muestra cada una y dónde van las
  demás pistas lo manejás vos** — incluida la pista del código del ático (por ejemplo,
  esconderla donde antes estaba la llave).

## 5. Cambio 2 — Panel de fusibles: 8 slots y 2 fusibles nuevos

- El panel pasa de 6 a **8 slots: 4 por circuito**. Modifico el modelo actual del panel
  (slots, hitboxes y luces siguen el mismo estilo).
- **2 fusibles nuevos: amarillo y violeta**, con forma propia (items nuevos + piezas
  alternativas por slot en el modelo, para que se noten por forma y no solo por color).
- Con 5 colores y 4 slots, cada circuito pasa de 27 a **625 combinaciones**.
- La solución de ambos circuitos se lee de `puzzles.yml`.
- Migración incluida: las salas guardadas con el panel de 6 no se rompen.

## 6. Cambio 3 — Cables, válvulas y loot de cajas al yml

- **Cables**: la rotación correcta de cada cable (hoy imposible de cambiar, ni comando
  tiene) pasa al yml (`cables.rotaciones`), y de paso se puede variar qué ranura pide
  cable lineal y cuál curvo.
- **Válvulas**: el estado correcto de cada válvula pasa al yml
  (`valvulas.estados_correctos`).
- **Loot de las cajas**: el contenido de cada caja (alicate, llave del ático, fusibles,
  cables) pasa al yml, identificando cada caja por su nombre. Los veteranos se saben de
  memoria dónde está cada item: con esto lo rotás cuando quieras.

## 7. Lo que queda como está (decidido)

- **Cuadros y colgante**: siguen igual — todos los cuadros sueltan el colgante.
- **Modo aleatorio**: descartado. Es una competencia y sería injusto que cada equipo
  enfrente una solución distinta.
- **Dificultad del ruido**: la configurás vos con `settings.yml`, que ya existe y se
  recarga en caliente (umbral del Ublabla, decaída, ruido por acción).
- **Panel de energía, reja, escalera, palanca**: mecánicas físicas sin secreto
  memorizable; rotarlas no aporta contra spoilers. (La puerta del ático ya no está en
  esta lista: ahora lleva su propio teclado.)
- **Receta de la ganzúa**: el cuello de botella real es conseguir el colgante.

## 8. Sonido (análisis SFX)

Todo lo nuevo queda cubierto con los sonidos existentes: acierto/fallo
(`CODE_CORRECT`/`CODE_WRONG`), bloqueo (`PANEL_LOCKED`/`PANEL_READY`), fusibles
(`FUSE_INSERT`/`FUSE_REMOVE`/`CIRCUIT_COMPLETE`). Los números de pared son decorativos:
no necesitan sonido. **Ningún archivo de audio nuevo.**

Lo único nuevo: el **beep por tecla estilo teléfono** del teclado (decidido), que es
variación de tono del sonido de botón actual.

## 9. Red y rendimiento (100 jugadores)

- El yml se lee **una vez** al arrancar/recargar; los paneles consultan memoria, nunca
  el disco por tick.
- Estado nuevo viaja por la sincronización de entidades existente: **solo cuando algo
  cambia**, nunca por tick, y solo a quien tiene la entidad cargada.
- Riesgo nuevo de lag o saturación de red: **ninguno**.

## 10. Fases y versiones

| Fase | Qué | Versión |
|---|---|---|
| 1 | `puzzles.yml` + los dos teclados 0–9 (salida y ático, con beep teléfono) + números de pared | 1.0.36 |
| 2 | Panel de fusibles a 8 slots + fusibles amarillo y violeta | 1.0.37 |
| 3 | Cables, válvulas y loot de cajas al yml | 1.0.38 |

Cada fase se compila y prueba por separado, con su subida de versión.

Al terminar, **tu paso de mapa** (una sola vez): colocar los dos teclados y los números
de pared en la sala master, ponerles nombre a teclados y cajas, re-guardar la instancia
y preparar las pistas con sus imágenes (incluida la del código del ático).

## 11. Decisiones finales (todas tomadas)

- Teclado 0–9, entidad nueva, sin reemplazar el panel de figuras. Modelo 3D: yo.
- **Dos teclados en la sala**: el de salida y el del ático (la puerta del ático deja
  de abrirse con la llave).
- Códigos de 4 dígitos, uno por teclado, identificados por nombre en el yml.
- Beep por tecla estilo teléfono: sí.
- Números de pared: entidad nueva que muestra cualquier número del 0 al 99, colocables
  donde y cuantas quieras (en creativo: clic +1, agachado + clic +10). Pistas: vos.
- Fusibles: 8 slots (4 por circuito) + amarillo y violeta.
- Cables, válvulas y loot de cajas (por nombre de caja) al yml.
- Cuadros: quedan igual. Modo aleatorio: no (competencia). Ruido: lo configurás vos.
- Sin parche previo por comandos: se va directo a las fases.
