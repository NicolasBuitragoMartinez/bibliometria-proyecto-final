"""
Genera los embeddings de los 20 abstracts con dos modelos de IA preentrenados (requerimiento 1).
La aplicación Java lee los vectores de corpus/embeddings/ y calcula ella misma la similitud del
coseno y la distancia euclidiana; este script solo vectoriza los textos.

Modelo 1 - GloVe (Pennington, Socher y Manning, 2014), vectores de palabras de 100 dimensiones
    entrenados con Wikipedia 2014 + Gigaword 5 (400.000 palabras). Es un modelo estático, de la misma
    familia que Word2Vec y FastText: cada palabra tiene un solo vector. El vector de un abstract es el
    promedio de los vectores de sus términos (mismo preprocesamiento que la aplicación Java).
Modelo 2 - all-MiniLM-L6-v2 (Sentence-Transformers; Reimers y Gurevych, 2019; Wang et al., 2020),
    un transformador de 6 capas que produce un vector de 384 dimensiones para todo el texto,
    teniendo en cuenta el contexto de cada palabra. Se ejecuta con FastEmbed (ONNX Runtime). Como el
    modelo lee como máximo 256 tokens, los abstracts más largos se dividen en fragmentos de oraciones
    completas y se promedian los vectores de los fragmentos.

Uso (desde la carpeta del proyecto):
    pip install -r ia/requirements.txt
    python ia/generar_embeddings.py
La primera vez descarga los modelos (unos 130 MB de GloVe y 90 MB de MiniLM) en ia/modelos/.
"""

import csv
import gzip
import re
import shutil
import ssl
import unicodedata
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
ABSTRACTS = RAIZ / "corpus" / "abstracts"
STOPWORDS = RAIZ / "src" / "main" / "resources" / "stopwords"
SALIDA = RAIZ / "corpus" / "embeddings"
MODELOS = Path(__file__).resolve().parent / "modelos"

GLOVE_URL = ("https://github.com/RaRe-Technologies/gensim-data/releases/download/"
             "glove-wiki-gigaword-100/glove-wiki-gigaword-100.gz")
GLOVE_ARCHIVO = MODELOS / "glove-wiki-gigaword-100.gz"
MINILM = "sentence-transformers/all-MiniLM-L6-v2"

NO_LETRAS = re.compile(r"[^a-z]+")


def normalizar(texto):
    descompuesto = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in descompuesto if not unicodedata.category(c).startswith("M")).lower()


def leer_stopwords():
    palabras = set()
    for nombre in ("en.txt", "es.txt"):
        for linea in (STOPWORDS / nombre).read_text(encoding="utf-8").splitlines():
            linea = linea.strip()
            if linea and not linea.startswith("#"):
                palabras.add(normalizar(linea))
    return palabras


def leer_abstracts():
    archivos = sorted(ABSTRACTS.glob("*.txt"), key=lambda r: int(r.name.split(".", 1)[0]))
    return [(int(a.name.split(".", 1)[0]), a.read_text(encoding="utf-8").strip()) for a in archivos]


def tokenizar(texto, stopwords):
    return [t for t in NO_LETRAS.split(normalizar(texto)) if len(t) >= 2 and t not in stopwords]


def descargar(url, destino):
    """Descarga con los certificados de certifi (el almacén de Python en Windows puede estar incompleto)."""
    try:
        import certifi
        contexto = ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        contexto = ssl.create_default_context()
    temporal = destino.with_suffix(".parcial")
    with urllib.request.urlopen(url, context=contexto) as respuesta, temporal.open("wb") as archivo:
        shutil.copyfileobj(respuesta, archivo)
    temporal.replace(destino)


def embeddings_glove(abstracts, stopwords):
    MODELOS.mkdir(parents=True, exist_ok=True)
    if not GLOVE_ARCHIVO.exists():
        print("Descargando GloVe (unos 130 MB)...")
        descargar(GLOVE_URL, GLOVE_ARCHIVO)
    tokens = [tokenizar(texto, stopwords) for _, texto in abstracts]
    vocabulario = {t for lista in tokens for t in lista}
    vectores = {}
    with gzip.open(GLOVE_ARCHIVO, "rt", encoding="utf-8") as archivo:
        next(archivo)  # encabezado "400000 100" del formato word2vec
        for linea in archivo:
            palabra, resto = linea.split(" ", 1)
            if palabra in vocabulario:
                vectores[palabra] = [float(x) for x in resto.split()]
    dimension = len(next(iter(vectores.values())))
    resultado = []
    cubiertos = 0
    total = 0
    for (numero, _), lista in zip(abstracts, tokens):
        presentes = [vectores[t] for t in lista if t in vectores]
        cubiertos += len(presentes)
        total += len(lista)
        promedio = [sum(v[k] for v in presentes) / len(presentes) for k in range(dimension)]
        resultado.append((numero, promedio))
    print(f"GloVe: {dimension} dimensiones; {cubiertos} de {total} términos tienen vector "
          f"({100 * cubiertos / total:.1f} %)")
    return resultado


def fragmentos(texto, tokenizador, maximo):
    """Agrupa oraciones completas en fragmentos de hasta `maximo` tokens del modelo."""
    oraciones = re.split(r"(?<=[.!?])\s+", texto)
    grupos, actual, tokens_actual = [], [], 0
    for oracion in oraciones:
        n = len(tokenizador.encode(oracion).ids)
        if actual and tokens_actual + n > maximo:
            grupos.append((" ".join(actual), tokens_actual))
            actual, tokens_actual = [], 0
        actual.append(oracion)
        tokens_actual += n
    if actual:
        grupos.append((" ".join(actual), tokens_actual))
    return grupos


def embeddings_minilm(abstracts):
    """
    all-MiniLM-L6-v2 solo lee 256 tokens; 13 de los 20 abstracts son más largos. Para no perder el
    final del texto, cada abstract se divide en fragmentos de oraciones completas de hasta 256 tokens,
    se calcula el embedding de cada fragmento y se promedian, ponderando por el número de tokens.
    """
    from fastembed import TextEmbedding

    modelo = TextEmbedding(model_name=MINILM, cache_dir=str(MODELOS))
    tokenizador = modelo.model.tokenizer
    maximo = tokenizador.truncation["max_length"] if tokenizador.truncation else 256
    tokenizador.no_truncation()
    resultado = []
    divididos = 0
    for numero, texto in abstracts:
        grupos = fragmentos(texto, tokenizador, maximo)
        divididos += len(grupos) > 1
        vectores = list(modelo.embed([g for g, _ in grupos]))
        pesos = [t for _, t in grupos]
        dimension = len(vectores[0])
        promedio = [sum(float(v[k]) * w for v, w in zip(vectores, pesos)) / sum(pesos) for k in range(dimension)]
        norma = sum(x * x for x in promedio) ** 0.5
        resultado.append((numero, [x / norma for x in promedio]))
    print(f"MiniLM: {dimension} dimensiones; {divididos} abstracts divididos en fragmentos de hasta {maximo} tokens")
    return resultado


def guardar(nombre, filas):
    SALIDA.mkdir(parents=True, exist_ok=True)
    with (SALIDA / f"{nombre}.csv").open("w", encoding="utf-8", newline="") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["articulo"] + [f"v{k + 1}" for k in range(len(filas[0][1]))])
        for numero, vector in filas:
            escritor.writerow([numero] + [f"{x:.8f}" for x in vector])
    print("Guardado", SALIDA / f"{nombre}.csv")


def main():
    abstracts = leer_abstracts()
    stopwords = leer_stopwords()
    guardar("glove", embeddings_glove(abstracts, stopwords))
    guardar("minilm", embeddings_minilm(abstracts))


if __name__ == "__main__":
    main()
