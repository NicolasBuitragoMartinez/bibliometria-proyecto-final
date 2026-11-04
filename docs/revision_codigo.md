# Revisión del código antes de la entrega

Revisión final del repositorio (noviembre de 2026), incluidos los cambios recientes de la vista
«Matriz paso a paso» (`GET /api/matriz`) y la nueva búsqueda de varios patrones. El objetivo fue dejar
el proyecto más limpio sin cambiar ningún resultado numérico de los algoritmos.

## Qué se revisó

- Todo el código Java de `src/main` y `src/test` (13 paquetes, 45 clases), la interfaz
  (`index.html`, `app.js`, `estilos.css`), `pom.xml`, `Dockerfile`, `.dockerignore`, `.gitignore`,
  `render.yaml` y el README.
- Referencias de cada método, constante, clase, import, clase CSS e identificador HTML, para
  encontrar código sin uso.
- Cada consulta de la API con parámetros válidos e inválidos.
- La interfaz en escritorio y a 375 px de ancho, con teclado y con la consola del navegador.
- Los comandos del README.

## Qué se eliminó o unificó

| Cambio | Por qué |
|---|---|
| `CorpusProcesado.matrizTfIdf()` y su import de `HashMap` | No tenía ninguna referencia. |
| `NeedlemanWunsch.alinear` reutiliza `camino()` | El recorrido hacia atrás estaba escrito dos veces. Se comprobó que la matriz de similitud de los 20 artículos es idéntica antes y después del cambio. |
| Comentario «Tabla 1 del documento» en `Reporte`; `imprimirTabla1` pasa a `imprimirCifras` | Era la numeración del seguimiento; en el documento final esas cifras están en la Tabla 21. |

No se borraron `corpus/` ni `resultados/`, que cita el documento técnico.

## Qué se agregó

| Cambio | Por qué |
|---|---|
| Validación de parámetros en todas las consultas | Antes, `articulos=11,abc` respondía `For input string: "abc"`, y valores inválidos de `mayusculas`, `q` o `posicion` se aceptaban en silencio. Ahora responden 400 con un mensaje claro; las consultas inexistentes, 404 en JSON; los métodos distintos de GET, 405. El 500 queda solo para errores internos, que se registran en la consola. |
| `ServidorTest` (3 pruebas) | Arranca el servidor en un puerto libre con un corpus de tres artículos y comprueba 15 parámetros inválidos, el 404, el 405 y consultas válidas. |
| Pruebas de casos borde | Texto o patrón vacíos, mayúsculas y caracteres fuera de ASCII en la búsqueda; secuencias vacías en Levenshtein y Needleman-Wunsch. |
| `RabinKarpMultiple`, `GET /api/busqueda-multiple` y su vista | Demuestra la conclusión del documento técnico: Rabin-Karp conviene cuando se buscan muchos patrones a la vez. 4 pruebas nuevas. |
| `.gitignore` y `.dockerignore` | Faltaban las carpetas de IDE (`.idea`, `.vscode`, Eclipse), `.claude/`, la caché de Python, los temporales de Word y los archivos del sistema operativo; Docker copiaba `.git` al contexto. |
| Accesibilidad | Pestañas con `aria-controls`, tabulación solo en la activa y teclas Inicio y Fin; enlace «Saltar al contenido»; nombre accesible para los dendrogramas y los botones «Paso a paso»; foco del buscador con más contraste. |
| Pantallas angostas | La columna de preprocesamiento se oculta en celular en las tablas de búsqueda. A 375 px no hay desplazamiento horizontal de la página. |
| Mensaje de la búsqueda | Decía «los tres algoritmos encontraron las mismas posiciones» aunque no coincidieran; ahora depende del resultado. |

Resultado: `mvn package` ejecuta **52 pruebas sin fallas** (antes, 43).

## Comandos del README

| Comando | Resultado |
|---|---|
| `mvn package` | Compila y pasan las 52 pruebas. |
| `java -jar target/bibliometria.jar` | Servidor en http://localhost:8080. |
| `java -jar target/bibliometria.jar multipatron` | Genera `resultados/busqueda_multiple.csv`. |
| `java -jar target/bibliometria.jar experimentos` y `trazas` | No se ejecutaron: sobrescribirían los CSV y las trazas que cita el documento técnico. |
| `python ...` (verificación, figuras, embeddings) | No se ejecutaron: el equipo de la revisión no tiene Python instalado. |
| `docker build -t bibliometria .` | Pendiente (ver abajo). |

## Pendiente

- Construir la imagen con `docker build` en un equipo con Docker en ejecución. El `Dockerfile` no
  cambió y Render lo vuelve a construir en cada despliegue, así que el despliegue sirve como prueba.
- Las URL con un `%` mal codificado las rechaza el propio servidor del JDK con un 400 en HTML, antes
  de llegar al código de la aplicación.
- Las consultas de búsqueda de la interfaz miden una sola ejecución, sin calentar la JVM; la página lo
  indica, y las cifras del documento salen del modo `multipatron` (mediana de 15 ejecuciones).

## Verificación frente al enunciado

| Requisito | Dónde se cumple en la aplicación | Dónde está en el documento técnico | Estado |
|---|---|---|---|
| Req. 1: cuatro algoritmos clásicos de similitud | Pestaña Similitud; paquete `similitud` (Levenshtein, Needleman-Wunsch, Jaccard, coseno TF-IDF) | 4.2 a 4.5 | Cumple |
| Req. 1: dos algoritmos de IA | Pestaña Similitud; coseno entre embeddings de GloVe y de all-MiniLM-L6-v2 (`ia/generar_embeddings.py`) | 4.6 | Cumple |
| Req. 1: paso a paso del caso de estudio | Artículos 11 y 16; vista «Matriz paso a paso» de Levenshtein y Needleman-Wunsch | 4.1 a 4.6 (Tablas 4 a 7) y final de 4.3 | Cumple |
| Req. 2: tres enlaces implementados desde cero | Pestaña Agrupamiento; `AgrupamientoJerarquico` (simple, completo y promedio con Lance-Williams) | 5.2 | Cumple |
| Req. 2: dendrogramas | Pestaña Agrupamiento (SVG propio en `Dendrograma`) | 5.3 (Figuras 4 a 7) | Cumple |
| Req. 2: métrica interna | Correlación cofenética y silueta (`EvaluacionInterna`) | 5.4 (Tabla 12) | Cumple |
| Req. 3: KMP, Boyer-Moore y Rabin-Karp sin búsqueda nativa | Pestaña Búsqueda; paquete `busqueda`, solo con `charAt` y estructuras propias | 6.2 a 6.4 | Cumple |
| Req. 3: paso a paso graficado | Vista «Paso a paso» de la pestaña Búsqueda (`PasoAPaso`) | 6.1 a 6.4 (Tablas 13 a 18) y 6.6 (Figura 9) | Cumple |
| Req. 3: O grande comparada con las mediciones | `Experimentos` (`resultados/busqueda_tiempos.csv`) y modo `multipatron` | 6.5 (Tablas 19 y 20, Figura 8) | Cumple |
| Req. 4: despliegue | https://bibliometria-proyecto-final.onrender.com (Docker en Render) | 8 | Cumple; falta volver a desplegar el último commit |
| Req. 4: documentación | README, comentarios de cada clase, documento técnico y este informe | 8 | Cumple |
| Cruce con título y autores | Tabla del corpus, artículos comparados en Similitud, grupos del dendrograma y ocurrencias de la búsqueda | 2.3, 3.1 y Anexo A | Cumple |
| Declaración de uso de IA | `docs/Declaracion_Uso_IA_Proyecto_Final.pdf` | 10 | Cumple (filas 7 y 8 agregadas) |

La verificación se hizo contra los requisitos tal como los resume el documento técnico y el pedido de
esta revisión; el PDF del enunciado no estaba disponible en el equipo de la revisión, así que conviene
contrastar esta tabla con él antes de la entrega.

### Coherencia entre la aplicación, el README, el documento y la presentación

| Dato | Aplicación | README | Documento | Presentación |
|---|---|---|---|---|
| Pruebas unitarias | 52 (`mvn package`) | — | 52 (cap. 8) | 52 (diapositivas 14 y 15) |
| Consultas de la API | 8 | 8 | 8 (Tabla 2) | — |
| Levenshtein y Needleman-Wunsch, 11 y 16 con n = 12 | 10 y −7 | — | 10 y −7 (4.3) | 10 y −7 (diapositiva 6) |
| Multipatrón, 10 patrones de 8 letras | `busqueda_multiple.csv` | — | 3,99 / 12,88 / 5,92 / 31,59 ms (6.5) | igual (diapositiva 11) |
| Tres autores | Pie de la interfaz | Encabezado | Portada | Portada y cierre (Luisa faltaba; se agregó) |
