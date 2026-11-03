"""
Verificación independiente del conteo de términos (capítulo 8 del documento técnico).

Repite en Python, sin reutilizar el código Java, el preprocesamiento y el conteo de
frecuencias sobre los 20 abstracts de corpus/abstracts/ y compara el resultado con el
ranking que generó la aplicación Java (resultados/ranking/ranking_completo.csv).

Reglas de preprocesamiento (las mismas del documento, escritas de nuevo aquí):
  1. Normalización Unicode NFKD y eliminación de las marcas diacríticas (tildes, ligaduras).
  2. Conversión a minúsculas.
  3. Tokenización: cualquier carácter que no sea una letra a-z separa tokens.
  4. Eliminación de stopwords (listas en inglés y español del proyecto) y de tokens de una letra.

El ranking se obtiene aquí con sorted() de Python (Timsort, un ordenamiento por comparación y
estable), de modo que también se comprueba el orden que entregan los algoritmos no comparativos.

Uso (desde la carpeta del proyecto, solo necesita Python 3.8 o superior):
    python verificacion/verificar_conteo.py
Termina con código 0 si todo coincide y con código 1 si hay alguna diferencia.
"""

import csv
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
CARPETA_ABSTRACTS = RAIZ / "corpus" / "abstracts"
CARPETA_STOPWORDS = RAIZ / "src" / "main" / "resources" / "stopwords"
RANKING_JAVA = RAIZ / "resultados" / "ranking" / "ranking_completo.csv"

NO_LETRAS = re.compile(r"[^a-z]+")
# Java separa las palabras con \s+, que solo incluye los espacios ASCII.
ESPACIOS_ASCII = re.compile(r"[ \t\n\x0b\f\r]+")
LONGITUD_MINIMA = 2
TOP = 15


def normalizar(texto):
    descompuesto = unicodedata.normalize("NFKD", texto)
    sin_marcas = "".join(c for c in descompuesto if not unicodedata.category(c).startswith("M"))
    return sin_marcas.lower()


def leer_stopwords():
    palabras = set()
    for nombre in ("en.txt", "es.txt"):
        for linea in (CARPETA_STOPWORDS / nombre).read_text(encoding="utf-8").splitlines():
            linea = linea.strip()
            if linea and not linea.startswith("#"):
                palabras.add(normalizar(linea))
    return palabras


def numero_de_articulo(ruta):
    return int(ruta.name.split(".", 1)[0])


def tokenizar(texto, stopwords):
    return [t for t in NO_LETRAS.split(normalizar(texto))
            if len(t) >= LONGITUD_MINIMA and t not in stopwords]


def leer_ranking_java():
    with RANKING_JAVA.open(encoding="utf-8", newline="") as archivo:
        return [(fila["termino"], int(fila["frecuencia"])) for fila in csv.DictReader(archivo)]


def main():
    stopwords = leer_stopwords()
    archivos = sorted(CARPETA_ABSTRACTS.glob("*.txt"), key=numero_de_articulo)

    palabras = 0
    frecuencias = Counter()  # conserva el orden de primera aparición (Python 3.7+)
    for archivo in archivos:
        texto = archivo.read_text(encoding="utf-8").strip()
        palabras += len(ESPACIOS_ASCII.split(texto)) if texto else 0
        frecuencias.update(tokenizar(texto, stopwords))

    tokens = sum(frecuencias.values())
    ranking = sorted(frecuencias.items(), key=lambda par: -par[1])  # estable: empates por primera aparición
    maxima = ranking[0][1]
    minima = ranking[-1][1]
    una_vez = sum(1 for _, f in ranking if f == 1)

    print("Verificación independiente en Python")
    print(f"  Artículos                              {len(archivos)}")
    print(f"  Palabras en los abstracts              {palabras:,}".replace(",", "."))
    print(f"  Términos tras el preprocesamiento      {tokens:,}".replace(",", "."))
    print(f"  Términos distintos (n)                 {len(ranking)}")
    print(f"  Frecuencia máxima / mínima             {maxima} / {minima}")
    print(f"  Rango de claves k = máx - mín + 1      {maxima - minima + 1}")
    porcentaje = f"{100 * una_vez / len(ranking):.1f}".replace(".", ",")
    print(f"  Términos que aparecen una sola vez     {una_vez} ({porcentaje} %)")
    print()
    print(f"Top {TOP} (sorted de Python):")
    for posicion, (termino, frecuencia) in enumerate(ranking[:TOP], start=1):
        print(f"  {posicion:2d}. {termino} ({frecuencia})")
    print()

    if not RANKING_JAVA.exists():
        print(f"No existe {RANKING_JAVA}; ejecute primero RankingTerminos para generar el ranking.")
        return 1
    ranking_java = leer_ranking_java()
    mismos_pares = dict(ranking) == dict(ranking_java)
    mismo_orden = ranking == ranking_java
    diferencias = [(i + 1, p, j) for i, (p, j) in enumerate(zip(ranking, ranking_java)) if p != j]

    print("Comparación con resultados/ranking/ranking_completo.csv (aplicación Java):")
    print(f"  {'Términos en Python / en Java':<38} {len(ranking)} / {len(ranking_java)}")
    print(f"  {'Mismos términos y frecuencias':<38} {'SÍ' if mismos_pares else 'NO'}")
    print(f"  {f'Mismo orden en las {len(ranking)} posiciones':<38} {'SÍ' if mismo_orden else 'NO'}")
    for posicion, python, java in diferencias[:10]:
        print(f"    posición {posicion}: Python {python} - Java {java}")
    return 0 if mismos_pares and mismo_orden else 1


if __name__ == "__main__":
    sys.exit(main())
