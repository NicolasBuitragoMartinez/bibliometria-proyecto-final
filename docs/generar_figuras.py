"""
Figuras del documento técnico a partir de los CSV de resultados/ (java -jar target/bibliometria.jar
experimentos). Se guardan en docs/figuras/.  Uso: python docs/generar_figuras.py
"""

import csv
import math
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.ticker import FuncFormatter  # noqa: E402

RAIZ = Path(__file__).resolve().parent.parent
RES = RAIZ / "resultados"
SALIDA = Path(__file__).resolve().parent / "figuras"
AZUL, NARANJA, VERDE, GRIS = "#2060C0", "#D96028", "#289870", "#666666"
DPI = 230
CASO = ("11", "16")

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Times New Roman", "Liberation Serif", "DejaVu Serif"],
    "font.size": 11,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.edgecolor": "#444444",
    "axes.grid": True,
    "axes.grid.axis": "x",
    "grid.color": "#dddddd",
})


def numero(v, d=0):
    return f"{v:,.{d}f}".replace(",", "X").replace(".", ",").replace("X", ".")


def leer(nombre):
    with (RES / nombre).open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def guardar(fig, nombre):
    SALIDA.mkdir(parents=True, exist_ok=True)
    fig.tight_layout()
    fig.savefig(SALIDA / nombre)
    plt.close(fig)
    print("Figura", SALIDA / nombre)


def similitud():
    filas = leer("similitud_pares.csv")
    algoritmos = [c for c in filas[0] if c not in ("articulo_a", "articulo_b")]
    fig, ax = plt.subplots(figsize=(6.14, 3.9), dpi=DPI)
    for y, alg in enumerate(reversed(algoritmos)):
        valores = [float(f[alg]) for f in filas]
        caso = next(float(f[alg]) for f in filas if (f["articulo_a"], f["articulo_b"]) == CASO)
        puesto = sorted(valores, reverse=True).index(caso) + 1
        color = NARANJA if "GloVe" in alg or "MiniLM" in alg else AZUL
        # Los 190 pares, con un pequeño desplazamiento vertical determinista para que no se tapen.
        ax.scatter(valores, [y + ((i * 37) % 11 - 5) * 0.035 for i in range(len(valores))], s=7, color=color,
                   alpha=0.35, linewidths=0)
        ax.scatter([caso], [y], s=60, marker="D", color="#111111", zorder=3)
        ax.annotate(f"{numero(caso, 2)} (puesto {puesto})", (caso, y), xytext=(0, 9), textcoords="offset points",
                    ha="center", fontsize=9, color="#222222")
    ax.set_yticks(range(len(algoritmos)))
    ax.set_yticklabels(list(reversed(algoritmos)), fontsize=10)
    ax.set_xlim(0, 1.02)
    ax.set_ylim(-0.6, len(algoritmos) - 0.3)
    ax.set_xlabel("Similitud (cada punto es uno de los 190 pares de abstracts)")
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: numero(v, 1)))
    ax.tick_params(axis="y", length=0)
    ax.spines["left"].set_visible(False)
    guardar(fig, "similitud_distribucion.png")


def orden_hojas(n, fusiones, grupo, orden):
    if grupo < n:
        orden.append(grupo)
        return
    a, b = fusiones[grupo - n]
    orden_hojas(n, fusiones, a, orden)
    orden_hojas(n, fusiones, b, orden)


def dendrograma(representacion, criterio, titulo_archivo):
    etiquetas = [f["etiqueta"] for f in leer("agrupamiento_etiquetas.csv")]
    filas = leer(f"fusiones_{representacion}_{criterio}.csv")
    n = len(etiquetas)
    fusiones = [(int(f["grupo_a"]), int(f["grupo_b"])) for f in filas]
    alturas = [float(f["distancia"]) for f in filas]
    orden = []
    orden_hojas(n, fusiones, 2 * n - 2, orden)
    y = {hoja: fila for fila, hoja in enumerate(orden)}
    x = {hoja: None for hoja in orden}
    inicio = max(0.0, math.floor((alturas[0] - 0.15 * (alturas[-1] - alturas[0])) / 0.05) * 0.05)
    fig, ax = plt.subplots(figsize=(6.14, 4.6), dpi=DPI)
    for p, ((a, b), h) in enumerate(zip(fusiones, alturas)):
        nuevo = n + p
        for hijo in (a, b):
            xh = inicio if hijo < n else x[hijo]
            ax.plot([xh, h], [y[hijo], y[hijo]], color=AZUL, linewidth=1.4)
        ax.plot([h, h], [y[a], y[b]], color=AZUL, linewidth=1.4)
        x[nuevo] = h
        y[nuevo] = (y[a] + y[b]) / 2
    ax.set_yticks(range(n))
    ax.set_yticklabels([etiquetas[h] for h in orden], fontsize=9)
    ax.invert_yaxis()
    ax.set_xlim(inicio, alturas[-1] + 0.02 * (alturas[-1] - inicio))
    ax.set_xlabel("Distancia de fusión (1 − coseno)" + (f"; el eje empieza en {numero(inicio, 2)}" if inicio else ""))
    ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: numero(v, 2)))
    ax.tick_params(axis="y", length=0)
    ax.spines["left"].set_visible(False)
    guardar(fig, titulo_archivo)


def busqueda():
    filas = [f for f in leer("busqueda_tiempos.csv") if f["experimento"] == "tamano_texto"]
    colores = {"Knuth-Morris-Pratt": AZUL, "Boyer-Moore": VERDE, "Rabin-Karp": NARANJA}
    marcas = {"Knuth-Morris-Pratt": "o", "Boyer-Moore": "s", "Rabin-Karp": "^"}
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(6.14, 3.0), dpi=DPI)
    for alg, color in colores.items():
        puntos = sorted((int(f["n"]), float(f["busqueda_ms"]), float(f["comparaciones"])) for f in filas
                        if f["algoritmo"] == alg)
        ax1.plot([p[0] for p in puntos], [p[1] for p in puntos], marker=marcas[alg], color=color, linewidth=1.8,
                 markersize=5, label=alg)
        ax2.plot([p[0] for p in puntos], [p[2] for p in puntos], marker=marcas[alg], color=color, linewidth=1.8,
                 markersize=5, label=alg)
    for ax, titulo in ((ax1, "Tiempo de búsqueda (ms)"), (ax2, "Comparaciones de caracteres")):
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.set_title(titulo, fontsize=10.5)
        ax.set_xlabel("Caracteres del texto (n)", fontsize=10)
        ax.grid(axis="both", color="#e5e5e5")
        ax.xaxis.set_major_formatter(FuncFormatter(lambda v, _: numero(v)))
        ax.yaxis.set_major_formatter(FuncFormatter(lambda v, _: numero(v, 0) if v >= 1 else numero(v, 1)))
        ax.tick_params(labelsize=8.5)
    ax1.legend(fontsize=8.5, frameon=False, loc="upper left")
    ax2.legend(fontsize=8.5, frameon=False, loc="upper left")
    guardar(fig, "busqueda_escalabilidad.png")


def arquitectura():
    from matplotlib.patches import FancyBboxPatch

    fig, ax = plt.subplots(figsize=(6.14, 5.0), dpi=DPI)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 91)
    ax.axis("off")

    def caja(x, y, w, h, titulo, detalle, color):
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.4,rounding_size=1.5", linewidth=1.2,
                                    edgecolor=color, facecolor="white"))
        ax.text(x + w / 2, y + h - 2.4, titulo, ha="center", va="top", fontsize=9.5, fontweight="bold", color=color)
        ax.text(x + w / 2, y + h - 6.4, detalle, ha="center", va="top", fontsize=7.6, color="#222222",
                linespacing=1.3)

    def flecha(x1, y1, x2, y2, texto=""):
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                    arrowprops=dict(arrowstyle="-|>", color="#555555", linewidth=1.1))
        if texto:
            ax.text((x1 + x2) / 2 + 1.5, (y1 + y2) / 2, texto, fontsize=7.2, color="#555555", va="center")

    caja(30, 80, 40, 10, "Navegador", "index.html · app.js · estilos.css", GRIS)
    salto = "\n"
    caja(26, 59, 48, 14.5, "web", salto.join(["Servidor (HttpServer del JDK)", "API JSON: /api/articulos, /api/ranking,",
                                              "/api/similitud, /api/agrupamiento, /api/busqueda"]), AZUL)
    caja(1, 33, 23, 19, "ranking", salto.join(["Counting Sort", "Radix Sort", "Bucket Sort", "Medición de tiempos"]), AZUL)
    caja(26, 33, 23, 19, "similitud", salto.join(["Levenshtein", "Needleman-Wunsch", "Jaccard · TF-IDF",
                                                  "GloVe y MiniLM"]), AZUL)
    caja(51, 33, 23, 19, "agrupamiento", salto.join(["Single, complete y", "average linkage", "CCC y silueta",
                                                     "Dendrograma SVG"]), AZUL)
    caja(76, 33, 23, 19, "busqueda", salto.join(["Knuth-Morris-Pratt", "Boyer-Moore", "Rabin-Karp"]), AZUL)
    caja(26, 15.5, 48, 11.5, "texto", salto.join(["Preprocesador · CorpusProcesado",
                                                  "(texto normalizado, tokens, TF-IDF)"]), VERDE)
    caja(1, 0.5, 54, 11.5, "corpus", salto.join(["LectorPdf (PDFBox) · DelimitadorAbstract",
                                                 "ExtractorMetadatos · CargadorEmbeddings"]), VERDE)
    caja(59, 0.5, 40, 11.5, "Archivos del corpus", salto.join(["pdfs/ · abstracts/ · metadatos/",
                                                               "embeddings/ (ia/generar_embeddings.py)"]), NARANJA)

    flecha(50, 79.6, 50, 74.0, "HTTP (JSON)")
    for origen, destino in ((36, 12.5), (45, 37.5), (55, 62.5), (64, 87.5)):
        flecha(origen, 58.6, destino, 52.6)
    for origen, destino in ((12.5, 34), (37.5, 45), (62.5, 55), (87.5, 66)):
        flecha(origen, 32.6, destino, 27.6)
    flecha(40, 15.1, 28, 12.6)
    flecha(58.6, 6.2, 55.6, 6.2)
    guardar(fig, "arquitectura.png")


if __name__ == "__main__":
    arquitectura()
    similitud()
    dendrograma("tfidf", "simple", "dendrograma_tfidf_simple.png")
    dendrograma("tfidf", "completo", "dendrograma_tfidf_completo.png")
    dendrograma("tfidf", "promedio", "dendrograma_tfidf_promedio.png")
    dendrograma("minilm", "promedio", "dendrograma_minilm_promedio.png")
    busqueda()
