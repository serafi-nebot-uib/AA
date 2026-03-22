"""
Genera un diagrama 2×3 amb els vectors de moviment de les 6 peces
sobre una graella 7×7. Exporta a 'moviments_peces.pdf' i '.png'.
"""

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyArrowPatch
import numpy as np

# ── Definició de peces ───────────────────────────────────────────────────────

PIECES = [
    {
        "name": "Cavall",
        "short": "C",
        "continuous": False,
        "moves": [(-2,-1),(-2,1),(-1,-2),(-1,2),(1,-2),(1,2),(2,-1),(2,1)],
        "color": "#4a90d9",
    },
    {
        "name": "Mussol",
        "short": "M",
        "continuous": False,
        "moves": [(-3,-1),(-3,1),(-1,-3),(-1,3),(1,-3),(1,3),(3,-1),(3,1)],
        "color": "#7b4ea0",
    },
    {
        "name": "Serp",
        "short": "S",
        "continuous": False,
        "moves": [(-1,0),(1,0),(0,-1),(0,1),(-2,-2),(-2,2),(2,-2),(2,2)],
        "color": "#2aa05a",
    },
    {
        "name": "Torre",
        "short": "T",
        "continuous": True,
        "moves": [(-1,0),(1,0),(0,-1),(0,1)],
        "color": "#d94a4a",
    },
    {
        "name": "Alfil",
        "short": "A",
        "continuous": True,
        "moves": [(-1,-1),(-1,1),(1,-1),(1,1)],
        "color": "#d9824a",
    },
    {
        "name": "Reina",
        "short": "Q",
        "continuous": True,
        "moves": [(-1,-1),(-1,0),(-1,1),(0,-1),(0,1),(1,-1),(1,0),(1,1)],
        "color": "#b8860b",
    },
]

GRID = 7
CENTER = 3  # posició central que maximitza k_max (índex 0-7)

# ── Càlcul de cel·les assolibles ─────────────────────────────────────────────

def reachable_cells(piece):
    """Retorna llista de (row, col) assolibles des del centre."""
    cells = []
    if not piece["continuous"]:
        for dr, dc in piece["moves"]:
            r, c = CENTER + dr, CENTER + dc
            if 0 <= r < GRID and 0 <= c < GRID:
                cells.append((r, c))
    else:
        for dr, dc in piece["moves"]:
            r, c = CENTER + dr, CENTER + dc
            while 0 <= r < GRID and 0 <= c < GRID:
                cells.append((r, c))
                r += dr
                c += dc
    return cells

# ── Dibuix ───────────────────────────────────────────────────────────────────

COLOR_PIECE   = "#333333"
COLOR_REACH   = None          # s'assigna per peça
COLOR_EMPTY   = "#f5f5f5"
COLOR_GRID    = "#cccccc"
ALPHA_REACH   = 0.75

fig, axes = plt.subplots(2, 3, figsize=(9, 7.5),
                         gridspec_kw={"hspace": -0.2})
fig.patch.set_facecolor("white")

for ax, piece in zip(axes.flat, PIECES):
    cells = set(reachable_cells(piece))
    color = piece["color"]

    # Fons de les cel·les
    for r in range(GRID):
        for c in range(GRID):
            if (r, c) == (CENTER, CENTER):
                fc = COLOR_PIECE
            elif (r, c) in cells:
                fc = color
                ax.add_patch(mpatches.FancyBboxPatch(
                    (c + 0.05, GRID - r - 1 + 0.05),
                    0.90, 0.90,
                    boxstyle="round,pad=0.05",
                    facecolor=fc, edgecolor="white",
                    linewidth=1.5, alpha=ALPHA_REACH,
                    zorder=2,
                ))
                continue
            else:
                fc = COLOR_EMPTY

            ax.add_patch(mpatches.Rectangle(
                (c, GRID - r - 1), 1, 1,
                facecolor=fc, edgecolor=COLOR_GRID, linewidth=0.8, zorder=1,
            ))

    # Cel·la central (peça)
    ax.add_patch(mpatches.FancyBboxPatch(
        (CENTER + 0.05, GRID - CENTER - 1 + 0.05),
        0.90, 0.90,
        boxstyle="round,pad=0.05",
        facecolor=COLOR_PIECE, edgecolor="white",
        linewidth=1.5, zorder=3,
    ))
    ax.text(CENTER + 0.5, GRID - CENTER - 0.5, piece["short"],
            ha="center", va="center", fontsize=12, fontweight="bold",
            color="white", zorder=4)

    # Nombre de moviments
    n = len(cells)
    ax.set_title(f"{piece['name']}  ($k={n}$)",
                 fontsize=11, fontweight="bold", color=color, pad=6)

    # Línies de la graella
    for i in range(GRID + 1):
        ax.axhline(i, color=COLOR_GRID, linewidth=0.8, zorder=0)
        ax.axvline(i, color=COLOR_GRID, linewidth=0.8, zorder=0)

    ax.set_xlim(0, GRID)
    ax.set_ylim(0, GRID)
    ax.set_aspect("equal")
    ax.axis("off")

plt.tight_layout()

# plt.savefig("moviments_peces.pdf", bbox_inches="tight", dpi=150)
plt.savefig("moviments_peces.png", bbox_inches="tight", dpi=200)
print("Generat: moviments_peces.pdf  i  moviments_peces.png")
