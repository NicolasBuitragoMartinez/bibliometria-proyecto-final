# Análisis de algoritmos en el contexto de la bibliometría — Proyecto final

Universidad del Quindío · Análisis de Algoritmos 2026-2 · Docente: Sergio Augusto Cardona Torres
Nicolas Buitrago Martinez · Juan David Martinez Valencia

**Aplicación desplegada:** https://bibliometria-proyecto-final.onrender.com
(plan gratuito de Render: si nadie la ha usado en los últimos 15 minutos, la primera carga tarda
unos 50 segundos mientras el servidor arranca).

Aplicación web que procesa los abstracts de los 20 artículos del corpus (inteligencia artificial en
la educación) y ofrece los tres requerimientos funcionales del enunciado, además del ranking de
términos del seguimiento con las correcciones del docente:

| Requerimiento | Algoritmos (implementados desde cero) | Paquete |
|---|---|---|
| 1. Similitud textual | Levenshtein, Needleman-Wunsch, Jaccard, coseno TF-IDF; coseno entre embeddings de GloVe y de all-MiniLM-L6-v2 | `similitud` |
| 2. Agrupamiento jerárquico | Single, complete y average linkage (Lance-Williams), dendrogramas SVG, correlación cofenética y silueta | `agrupamiento` |
| 3. Búsqueda de patrones | Knuth-Morris-Pratt, Boyer-Moore (carácter malo + sufijo bueno), Rabin-Karp (hash rodante) | `busqueda` |
| 4. Despliegue | Servidor HTTP del JDK + interfaz web; imagen Docker | `web`, `Dockerfile` |
| Ranking de términos | Counting Sort, Radix Sort (LSD, colas enlazadas) y Bucket Sort; medición en JVM nuevas y experimento de memoria | `ranking` |

Ningún algoritmo usa funciones de alto nivel que lo resuelvan: la búsqueda no usa `indexOf`,
`contains` ni expresiones regulares; la similitud y el agrupamiento se calculan con arreglos, listas
y tablas hash. Los modelos de IA solo se usan para **vectorizar** los abstracts (script de Python);
la similitud del coseno y la distancia euclidiana entre los vectores las calcula la aplicación Java.

## Interfaz y API

La interfaz tiene cinco pestañas: Corpus (título, autores, año y abstract de cada artículo), Ranking
de términos, Similitud, Agrupamiento (con los artículos de cada grupo) y Búsqueda de patrones, que
incluye un **paso a paso** de KMP, Boyer-Moore y Rabin-Karp sobre la oración de cada ocurrencia.

| Consulta | Respuesta |
|---|---|
| `GET /api/articulos` | Número, título, autores, año, palabras y abstract |
| `GET /api/ranking` | Cifras del corpus, top 15 de los tres ordenamientos y tiempos en el servidor |
| `GET /api/similitud?articulos=11,16` | Matriz y tiempo de los seis algoritmos |
| `GET /api/agrupamiento?representacion=tfidf` | Fusiones, CCC, silueta, grupos del mejor corte y dendrogramas |
| `GET /api/busqueda?patron=learning` | Ocurrencias y estadísticas de los tres algoritmos |
| `GET /api/pasos?patron=analysis&articulo=18&posicion=426&q=pequeno` | Cada alineación de los tres algoritmos |

Enlaces directos: `#similitud=11,16`, `#agrupamiento=minilm`, `#busqueda=analysis`,
`#pasos=analysis,18,426` (caso de estudio paso a paso) y `#ranking`.

## Requisitos

- Java 17 o superior y Maven 3.9 (o Visual Studio Code con el *Extension Pack for Java*).
- Python 3.9+ solo para regenerar los embeddings (ya están en `corpus/embeddings/`).

## Ejecutar

```
mvn package
java -jar target/bibliometria.jar                 # servidor en http://localhost:8080
java -jar target/bibliometria.jar experimentos    # CSV de tiempos y métricas en resultados/
java -jar target/bibliometria.jar trazas          # demostraciones paso a paso en resultados/trazas/
java -cp target/bibliometria.jar co.edu.uniquindio.bibliometria.ranking.RankingTerminos corpus --corridas 30 --escalabilidad --memoria
python verificacion/verificar_conteo.py           # verificación independiente del ranking
python docs/generar_figuras_ranking.py            # figuras del ranking a partir de resultados/ranking/
```

La medición completa del ranking tarda varios minutos: cada una de las 30 corridas se hace en una JVM
nueva. `MedicionCompleta` es un atajo para lanzarla desde el IDE.

En VS Code: abrir la carpeta, esperar la importación de Maven y pulsar **Run** sobre `App.main`.

## Corpus

- `corpus/pdfs/`: los 20 artículos entregados por el docente.
- `corpus/abstracts/`: abstract extraído automáticamente de cada PDF (mismo extractor del seguimiento).
- `corpus/metadatos/`: título (primera línea), autores (segunda línea) y año (tercera línea) extraídos
  de cada PDF. Para los autores se combinan tres fuentes y se elige la más completa: metadatos XMP
  (`dc:creator`), diccionario Info del PDF y la línea que sigue al título en la primera página; si el
  título de los metadatos no es válido (por ejemplo `ET-04-2023-0117_proof`), se toman las líneas
  escritas con la letra más grande. El año sale de la línea de cita de la revista en la primera página
  (`3 (2022) 100101`, `(2024) 21:40`, `2024, 14, 636.`, `2024;59:e12663` o `Vol. 66 No. 6, 2024`) y,
  si no aparece, del aviso de derechos de autor.
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

La aplicación está desplegada en Render como *Web Service* con entorno Docker, creado a partir de
la URL pública de este repositorio (plan Free, región Virginia). Para volver a desplegar después de
subir cambios se usa **Manual Deploy → Deploy latest commit** en el panel del servicio. También se
puede crear el servicio como *Blueprint* a partir de `render.yaml`.

## Estructura

```
src/main/java/co/edu/uniquindio/bibliometria/
├── App.java              Punto de entrada (servidor, experimentos, trazas)
├── corpus/               Lectura de PDF, abstract, título y autores, embeddings
├── texto/                Preprocesamiento y representaciones (texto normalizado, tokens, TF-IDF)
├── similitud/            Requerimiento 1
├── agrupamiento/         Requerimiento 2
├── busqueda/             Requerimiento 3 y su paso a paso (PasoAPaso)
├── ranking/              Ranking de términos: ordenamientos, medición y reporte
├── web/                  Servidor HTTP y JSON
└── experimentos/         Mediciones y trazas del documento técnico
src/main/resources/web/   Interfaz (HTML, CSS y JavaScript sin frameworks)
src/test/java/            Pruebas JUnit (mvn test)
ia/                       Generación de embeddings (Python)
verificacion/             Verificación independiente del conteo y del ranking (Python)
resultados/               CSV de las mediciones y trazas que cita el documento técnico
docs/                     Documento técnico, declaración de uso de IA y scripts de las figuras
```

## Documentos

- [Documento técnico](docs/Proyecto_Final_Analisis_de_Algoritmos.pdf): arquitectura, casos de estudio
  paso a paso y análisis crítico de cada requerimiento y del ranking de términos.
- [Declaración de uso de IA](docs/Declaracion_Uso_IA_Proyecto_Final.pdf): herramienta, usos, prompts y
  forma de validación.
