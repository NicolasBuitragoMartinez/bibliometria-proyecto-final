# Análisis de algoritmos en el contexto de la bibliometría — Proyecto final

Universidad del Quindío · Análisis de Algoritmos 2026-2 · Docente: Sergio Augusto Cardona Torres
Nicolas Buitrago Martinez · Juan David Martinez Valencia

Aplicación web que procesa los abstracts de los 20 artículos del corpus (inteligencia artificial en
la educación) y ofrece los tres requerimientos funcionales del enunciado:

| Requerimiento | Algoritmos (implementados desde cero) | Paquete |
|---|---|---|
| 1. Similitud textual | Levenshtein, Needleman-Wunsch, Jaccard, coseno TF-IDF; coseno entre embeddings de GloVe y de all-MiniLM-L6-v2 | `similitud` |
| 2. Agrupamiento jerárquico | Single, complete y average linkage (Lance-Williams), dendrogramas SVG, correlación cofenética y silueta | `agrupamiento` |
| 3. Búsqueda de patrones | Knuth-Morris-Pratt, Boyer-Moore (carácter malo + sufijo bueno), Rabin-Karp (hash rodante) | `busqueda` |
| 4. Despliegue | Servidor HTTP del JDK + interfaz web; imagen Docker | `web`, `Dockerfile` |

Ningún algoritmo usa funciones de alto nivel que lo resuelvan: la búsqueda no usa `indexOf`,
`contains` ni expresiones regulares; la similitud y el agrupamiento se calculan con arreglos, listas
y tablas hash. Los modelos de IA solo se usan para **vectorizar** los abstracts (script de Python);
la similitud del coseno y la distancia euclidiana entre los vectores las calcula la aplicación Java.

## Requisitos

- Java 17 o superior y Maven 3.9 (o Visual Studio Code con el *Extension Pack for Java*).
- Python 3.9+ solo para regenerar los embeddings (ya están en `corpus/embeddings/`).

## Ejecutar

```
mvn package
java -jar target/bibliometria.jar                 # servidor en http://localhost:8080
java -jar target/bibliometria.jar experimentos    # CSV de tiempos y métricas en resultados/
java -jar target/bibliometria.jar trazas          # demostraciones paso a paso en resultados/trazas/
```

En VS Code: abrir la carpeta, esperar la importación de Maven y pulsar **Run** sobre `App.main`.

## Corpus

- `corpus/pdfs/`: los 20 artículos entregados por el docente.
- `corpus/abstracts/`: abstract extraído automáticamente de cada PDF (mismo extractor del seguimiento).
- `corpus/metadatos/`: título (primera línea) y autores (segunda línea) extraídos de cada PDF. Se
  combinan tres fuentes y se elige la más completa: metadatos XMP (`dc:creator`), diccionario Info del
  PDF y la línea que sigue al título en la primera página; si el título de los metadatos no es válido
  (por ejemplo `ET-04-2023-0117_proof`), se toman las líneas escritas con la letra más grande.
- `corpus/embeddings/`: vectores de los dos modelos de IA (`glove.csv`, 100 dimensiones;
  `minilm.csv`, 384 dimensiones).

Si se borran las carpetas `abstracts/` o `metadatos/`, el programa las vuelve a generar desde los PDF.

## Embeddings (modelos de IA)

```
pip install -r ia/requirements.txt
python ia/generar_embeddings.py
```

- **GloVe** (glove-wiki-gigaword-100): promedio de los vectores de los términos de cada abstract.
- **all-MiniLM-L6-v2** (Sentence-Transformers, ejecutado con FastEmbed/ONNX): un vector por abstract.

## Despliegue

La imagen Docker compila el proyecto, ejecuta las pruebas y arranca el servidor en el puerto de la
variable `PORT`:

```
docker build -t bibliometria .
docker run -p 8080:8080 bibliometria
```

En Render basta con crear un *Blueprint* a partir de este repositorio (`render.yaml`).

## Estructura

```
src/main/java/co/edu/uniquindio/bibliometria/
├── App.java              Punto de entrada (servidor, experimentos, trazas)
├── corpus/               Lectura de PDF, abstract, título y autores, embeddings
├── texto/                Preprocesamiento y representaciones (texto normalizado, tokens, TF-IDF)
├── similitud/            Requerimiento 1
├── agrupamiento/         Requerimiento 2
├── busqueda/             Requerimiento 3
├── web/                  Servidor HTTP y JSON
└── experimentos/         Mediciones y trazas del documento técnico
src/main/resources/web/   Interfaz (HTML, CSS y JavaScript sin frameworks)
src/test/java/            Pruebas JUnit (mvn test)
ia/                       Generación de embeddings (Python)
```
