"""
Genera las figuras del capítulo 8 (ranking de términos) a partir de los CSV de resultados/ranking/
(los que escribe ranking.MedicionCompleta). Las imágenes quedan en docs/figuras/ranking/.

Uso (desde la carpeta ranking-terminos):
    python docs/generar_figuras_ranking.py
Requiere matplotlib (pip install matplotlib).

Figura 1  escalabilidad_n_mediana.csv   nanosegundos por término según n
Figura 2  escalabilidad_k_mediana.csv   tiempo según el rango de frecuencias k
Figura 3  memoria_mediana.csv           objetos contiguos frente a dispersos en memoria
Figura 4  top15.csv                     frecuencia de los 15 términos más frecuentes
En las figuras 1, 2 y 3 la línea es la mediana entre corridas y la franja sombreada el rango
intercuartílico (Q1 - Q3).
"""

import csv
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.ticker import FuncFormatter  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent
RESULTADOS = RAIZ / "resultados" / "ranking"
SALIDA = RAIZ / "docs" / "figuras" / "ranking"

COLORES = {"Counting Sort": "#2060C0", "Radix Sort": "#D96028", "Bucket Sort": "#289870"}
ALGORITMOS = list(COLORES)
ANCHO, ALTO, DPI = 6.14, 3.74, 230

# Caché del equipo de medición (AMD Ryzen 7 5700U): L2 de 512 KiB por núcleo y L3 de 4 MiB por
# cada grupo de 4 núcleos (8 MiB en total). Un solo hilo usa su L2 y la L3 de su grupo.
CACHE_L2 = 512 * 1024
CACHE_L3 = 4 * 1024 * 1024

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Times New Roman", "Liberation Serif", "DejaVu Serif"],
    "font.size": 11,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.edgecolor": "#444444",
    "axes.grid": True,
    "axes.grid.axis": "y",
    "grid.color": "#dddddd",
    "grid.linewidth": 0.8,
    "xtick.color": "#333333",
    "ytick.color": "#333333",
})


def numero(valor, decimales=0):
    """Formato colombiano: punto de miles y coma decimal."""
    texto = f"{valor:,.{decimales}f}"
    return texto.replace(",", "X").replace(".", ",").replace("X", ".")


def leer(nombre):
    with (RESULTADOS / nombre).open(encoding="utf-8", newline="") as archivo:
        return list(csv.DictReader(archivo))


def serie(filas, algoritmo, x, y, disposicion=None):
    puntos = [f for f in filas if f["algoritmo"] == algoritmo
              and (disposicion is None or f["disposicion"] == disposicion)]
    puntos.sort(key=lambda f: float(f[x]))
    return ([float(f[x]) for f in puntos], [float(f[y]) for f in puntos], puntos)


def eje_log(ax, etiqueta):
    ax.set_xscale("log")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: numero(v)))
    ax.xaxis.set_minor_formatter(FuncFormatter(lambda v, _: ""))
    ax.set_xlabel(etiqueta)


def etiquetar_final(ax, x, y, texto, color, desplazamiento=0):
    ax.annotate(texto, (x, y), xytext=(8, desplazamiento), textcoords="offset points",
                va="center", ha="left", color="#222222", fontsize=10.5)


def figura1():
    filas = leer("escalabilidad_n_mediana.csv")
    fig, ax = plt.subplots(figsize=(ANCHO, ALTO), dpi=DPI)
    for algoritmo in ALGORITMOS:
        xs, ys, puntos = serie(filas, algoritmo, "n", "ns_por_termino")
        q1 = [float(p["ns_q1"]) for p in puntos]
        q3 = [float(p["ns_q3"]) for p in puntos]
        color = COLORES[algoritmo]
        ax.fill_between(xs, q1, q3, color=color, alpha=0.18, linewidth=0)
        ax.plot(xs, ys, color=color, linewidth=2.2, marker="o", markersize=4.5)
        etiquetar_final(ax, xs[-1], ys[-1], f"{algoritmo} · {numero(ys[-1], 1)} ns", color)
    eje_log(ax, "Número de términos n (escala logarítmica)")
    ax.set_ylabel("Nanosegundos por término")
    ax.set_ylim(bottom=0)
    guardar(fig, "figura1_escalabilidad_n.png")


def figura2():
    filas = leer("escalabilidad_k_mediana.csv")
    fig, ax = plt.subplots(figsize=(ANCHO, ALTO), dpi=DPI)
    finales = []
    for algoritmo in ALGORITMOS:
        xs, ys, puntos = serie(filas, algoritmo, "k", "mediana_ms")
        q1 = [float(p["q1_ms"]) for p in puntos]
        q3 = [float(p["q3_ms"]) for p in puntos]
        color = COLORES[algoritmo]
        ax.fill_between(xs, q1, q3, color=color, alpha=0.18, linewidth=0)
        ax.plot(xs, ys, color=color, linewidth=2.2, marker="o", markersize=4.5)
        finales.append((ys[-1], algoritmo))
        if algoritmo == "Radix Sort":
            for xv, yv in zip(xs, ys):
                d = len(str(int(xv)))
                # Junto a la línea k = n la etiqueta va a la izquierda para no taparla.
                dx = -16 if abs(math.log10(xv) - math.log10(int(filas[0]["n"]))) < 0.05 else 0
                ax.annotate(f"d = {d}", (xv, yv), xytext=(dx, 9), textcoords="offset points",
                            ha="center", fontsize=9.5, color="#555555")
    # Etiquetas al final: si dos quedan muy juntas, la de abajo baja y la de arriba sube.
    finales.sort()
    alto = ax.get_ylim()[1]
    desplazamientos = {algoritmo: 0 for _, algoritmo in finales}
    for (y1, a1), (y2, a2) in zip(finales, finales[1:]):
        if y2 - y1 < 0.07 * alto:
            desplazamientos[a1] -= 7
            desplazamientos[a2] += 7
    for y, algoritmo in finales:
        xs, ys, _ = serie(filas, algoritmo, "k", "mediana_ms")
        etiquetar_final(ax, xs[-1], ys[-1], f"{algoritmo} · {numero(ys[-1], 1)} ms", COLORES[algoritmo],
                        desplazamientos[algoritmo])
    n = int(filas[0]["n"])
    ax.axvline(n, color="#777777", linestyle="--", linewidth=1.2)
    ax.annotate(f"k = n = {numero(n)}", (n, alto * 0.42), xytext=(6, 0), textcoords="offset points",
                fontsize=10, color="#555555", va="center")
    eje_log(ax, "Rango de frecuencias k (escala logarítmica)")
    ax.set_ylabel("Tiempo (ms)")
    ax.set_ylim(bottom=0)
    guardar(fig, "figura2_escalabilidad_k.png")


def figura4():
    filas = leer("top15.csv")
    columna_termino = [c for c in filas[0] if c.endswith("termino")][0]
    columna_frecuencia = [c for c in filas[0] if c.endswith("frecuencia")][0]
    terminos = [f[columna_termino] for f in filas][::-1]
    frecuencias = [int(f[columna_frecuencia]) for f in filas][::-1]
    fig, ax = plt.subplots(figsize=(6.0, 4.125), dpi=DPI)
    ax.barh(terminos, frecuencias, color=COLORES["Counting Sort"], height=0.62)
    for y, f in enumerate(frecuencias):
        ax.annotate(str(f), (f, y), xytext=(5, 0), textcoords="offset points", va="center", fontsize=11)
    ax.grid(axis="x", color="#dddddd")
    ax.grid(axis="y", visible=False)
    ax.spines["left"].set_visible(False)
    ax.tick_params(axis="y", length=0)
    ax.set_xlabel("Número de apariciones en los 20 abstracts")
    ax.set_xlim(0, max(frecuencias) * 1.1)
    guardar(fig, "figura4_top15.png")


def figura3():
    filas = leer("memoria_mediana.csv")
    bytes_termino = float(leer("memoria_bytes_por_termino.csv")[0]["bytes_por_termino"])
    # Datos que recorre Counting Sort por término: el objeto y su referencia (medidos) más la
    # referencia en el arreglo de salida (4 bytes).
    por_termino = bytes_termino + 4
    limite_l2 = CACHE_L2 / por_termino
    limite_l3 = CACHE_L3 / por_termino

    fig, ejes = plt.subplots(1, 3, figsize=(ANCHO, 3.2), dpi=DPI, sharex=True)
    for ax, algoritmo in zip(ejes, ALGORITMOS):
        color = COLORES[algoritmo]
        maximo = 0
        for disposicion, estilo in (("contigua", "-"), ("dispersa", "--")):
            xs, ys, puntos = serie(filas, algoritmo, "n", "ns_por_termino", disposicion)
            q1 = [float(p["ns_q1"]) for p in puntos]
            q3 = [float(p["ns_q3"]) for p in puntos]
            ax.fill_between(xs, q1, q3, color=color, alpha=0.15, linewidth=0)
            ax.plot(xs, ys, color=color, linestyle=estilo, linewidth=1.9, marker="o" if estilo == "-" else "s",
                    markersize=3.2, label=disposicion.capitalize())
            maximo = max(maximo, max(q3))
        ax.set_xscale("log")
        ax.set_ylim(0, maximo * 1.12)
        xmin, xmax = 800, 2.5e6
        ax.set_xlim(xmin, xmax)
        ax.axvspan(limite_l2, limite_l3, color="#f2f2f2", zorder=0)
        ax.axvspan(limite_l3, xmax, color="#e4e4e4", zorder=0)
        ax.set_title(algoritmo, fontsize=11)
        ax.xaxis.set_major_formatter(FuncFormatter(
            lambda v, _: {1e3: "1 mil", 1e4: "10 mil", 1e5: "100 mil", 1e6: "1 millón"}.get(v, "")))
        ax.xaxis.set_minor_formatter(FuncFormatter(lambda v, _: ""))
        ax.tick_params(axis="both", labelsize=9)
        alto = ax.get_ylim()[1]
        ax.text(math.sqrt(xmin * limite_l2), alto * 0.97, "L2", ha="center", va="top", fontsize=9, color="#555555")
        ax.text(math.sqrt(limite_l2 * limite_l3), alto * 0.97, "L3", ha="center", va="top", fontsize=9,
                color="#555555")
        ax.text(math.sqrt(limite_l3 * xmax), alto * 0.97, "RAM", ha="center", va="top", fontsize=9, color="#555555")
    ejes[0].set_ylabel("Nanosegundos por término")
    ejes[1].set_xlabel("Número de términos n (escala logarítmica)")
    ejes[0].legend(loc="center left", fontsize=8.5, frameon=False, bbox_to_anchor=(0.0, 0.62))
    fig.tight_layout(w_pad=1.0)
    guardar(fig, "figura3_memoria.png")


def guardar(fig, nombre):
    SALIDA.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(SALIDA / nombre)
    plt.close(fig)
    print("Figura guardada en", SALIDA / nombre)


if __name__ == "__main__":
    figura1()
    figura2()
    figura3()
    figura4()
