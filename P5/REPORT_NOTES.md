# Notes per a la Memoria de P5

## Seccio d'Analisi dels Solvers

Objectiu: comparar les dues variants de programacio dinamica que resolen la mateixa recurrencia.

- `TopDownDPSolver`: DP recursiva amb memoitzacio. Calcula nomes els estats accessibles des de la configuracio demanada.
- `BottomUpDPSolver`: DP iterativa. Omple tota la taula `total x lastPlayed x turn` de `limit - 1` fins a `0`.

## Recurrencia

Estat:

```text
S = (total, lastPlayed, turn)
```

Valor de l'estat:

```text
loser[S] = jugador que perd si tots juguen optimament des de S
```

Per cada moviment legal `m`:

```text
nextTotal = total + m
nextTurn = (turn + 1) mod playerCount
```

Si `nextTotal >= limit`, el moviment fa perdre immediatament el jugador `turn`.

Si existeix un moviment segur amb:

```text
loser[nextTotal, m, nextTurn] != turn
```

es tria el primer moviment d'aquest tipus en ordre ascendent. Si no existeix, el perdedor de l'estat es `turn`.

## Complexitat Teorica

Definicions:

- `L`: limit de perdua.
- `K = width * height`: nombre de tecles.
- `P`: nombre de jugadors.
- `B`: maxim nombre de moviments legals per estat.
- Per `lastPlayed = 0`, `B = K`.
- Per `lastPlayed > 0`, `B = width + height - 2`.

Nombre maxim d'estats:

```text
L * (K + 1) * P
```

Top-down DP:

```text
Temps:  O(R * B)
Memoria: O(R)
```

on `R` es el nombre d'estats realment accessibles des de l'estat inicial.

Bottom-up DP:

```text
Temps:  O(L * (K + 1) * P * B)
Memoria: O(L * (K + 1) * P)
```

## Mesures Experimentals

Hi ha un punt d'entrada per generar CSV:

```bash
mvn exec:java -Dexec.mainClass="com.serafinebot.p5.BenchmarkMain"
```

Arguments opcionals:

```bash
mvn exec:java -Dexec.mainClass="com.serafinebot.p5.BenchmarkMain" -Dexec.args="3 10"
```

On `3` son warmups i `10` son repeticions mesurades.

Columnes del CSV:

- `solver`: `TOP_DOWN_DP` o `BOTTOM_UP_DP`.
- `width`, `height`, `keys`, `limit`, `players`: mida de l'entrada.
- `branch_upper_bound`: maxim de moviments considerats per estat.
- `full_states`: mida teorica de la taula completa.
- `computed_states`: estats memoitzats o entrades de taula calculades.
- `wall_ns`: temps real mitja en nanosegons.
- `cpu_ns`: temps de CPU mitja del thread, si la JVM ho suporta.
- `heap_delta_bytes`: diferencia aproximada d'us de heap.

## Taules Recomanades

- Comparacio per mida de teclat amb `limit = 31`, `players = 2`.
- Comparacio per `limit` amb teclat `3x3`, `players = 2`.
- Comparacio per nombre de jugadors amb teclat `3x3`, `limit = 31`.
- Comparacio de `computed_states` entre top-down i bottom-up.

## Conclusions Esperades

- Bottom-up calcula sempre tota la taula i te un cost mes previsible.
- Top-down sol consumir menys memoria i temps quan l'estat inicial nomes arriba a una part de l'espai d'estats.
- Amb limits petits, la diferencia pot ser baixa perque l'espai d'estats total tambe es petit.
- Amb `K`, `L` o `P` creixents, bottom-up creix directament amb `L * (K + 1) * P`, mentre que top-down depen dels estats realment visitats.
