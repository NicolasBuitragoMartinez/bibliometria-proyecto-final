package co.edu.uniquindio.bibliometria.experimentos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;
import co.edu.uniquindio.bibliometria.agrupamiento.AnalisisAgrupamiento;
import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda;
import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda.Resultado;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus;
import co.edu.uniquindio.bibliometria.busqueda.RabinKarpMultiple;
import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.similitud.AlgoritmoSimilitud;
import co.edu.uniquindio.bibliometria.similitud.CatalogoSimilitud;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Mediciones del documento técnico. Cada tiempo es la mediana de varias repeticiones tras un
 * calentamiento (la JVM compila el código con el JIT en las primeras ejecuciones) y las
 * repeticiones se intercalan entre algoritmos, como en el seguimiento.
 */
public final class Experimentos {

    private static final int CALENTAMIENTO = 5;
    private static final int REPETICIONES = 15;
    /**
     * Calentamiento de la búsqueda multipatrón: el método multipatrón se llama una vez por ronda y los
     * de un patrón k veces, así que con 5 rondas el JIT aún no lo habría compilado y la comparación
     * sería injusta.
     */
    private static final int CALENTAMIENTO_MULTIPLE = 60;
    /** Patrones de longitud creciente tomados del corpus. */
    private static final String[] PATRONES = {"ai", "data", "learning", "assessment", "higher education",
            "artificial intelligence", "generative artificial intelligence", "artificial intelligence in education"};
    /** Patrones del corpus para la búsqueda multipatrón; se toman los primeros 2, 5 y 10. */
    private static final String[] PATRONES_MULTIPLES = {"learning", "analysis", "assessment", "students",
            "education", "research", "ai", "higher education", "generative", "teachers"};
    /** Diez patrones de la misma longitud (m = 8): Rabin-Karp multipatrón los busca en una sola pasada. */
    private static final String[] PATRONES_MISMA_LONGITUD = {"research", "learning", "findings", "students",
            "articles", "analysis", "teaching", "relevant", "teachers", "learners"};
    /** Veces que se repite el texto del corpus (unos 28.000 caracteres) para el experimento de tamaño. */
    private static final int[] COPIAS = {1, 10, 100};

    private static volatile long sumidero;

    private Experimentos() {
    }

    public static void ejecutar(CorpusProcesado corpus, Path carpeta) throws IOException {
        Files.createDirectories(carpeta);
        similitud(corpus, carpeta);
        agrupamiento(corpus, carpeta);
        busqueda(corpus, carpeta);
        System.out.println("Resultados guardados en " + carpeta.toAbsolutePath());
    }

    /** Matriz 20 x 20 de cada algoritmo y tiempo para calcular los 190 pares. */
    private static void similitud(CorpusProcesado corpus, Path carpeta) throws IOException {
        List<AlgoritmoSimilitud> algoritmos = CatalogoSimilitud.disponibles(corpus);
        int n = corpus.tamano();
        List<String> pares = new ArrayList<>();
        StringBuilder encabezado = new StringBuilder("articulo_a,articulo_b");
        algoritmos.forEach(a -> encabezado.append(',').append(a.nombre().replace(",", "")));
        pares.add(encabezado.toString());
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                StringBuilder fila = new StringBuilder(corpus.articulos().get(i).numero() + ","
                        + corpus.articulos().get(j).numero());
                for (AlgoritmoSimilitud a : algoritmos) {
                    fila.append(String.format(Locale.ROOT, ",%.6f", a.similitud(corpus, i, j)));
                }
                pares.add(fila.toString());
            }
        }
        escribir(carpeta.resolve("similitud_pares.csv"), pares);

        double[][] tiempos = new double[algoritmos.size()][REPETICIONES];
        for (int r = -CALENTAMIENTO; r < REPETICIONES; r++) {
            for (int a = 0; a < algoritmos.size(); a++) {
                long inicio = System.nanoTime();
                double suma = 0;
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        suma += algoritmos.get(a).similitud(corpus, i, j);
                    }
                }
                long nanos = System.nanoTime() - inicio;
                sumidero += (long) suma;
                if (r >= 0) {
                    tiempos[a][r] = nanos / 1e6;
                }
            }
        }
        List<String> csv = new ArrayList<>();
        csv.add("algoritmo,enfoque,pares,mediana_ms,q1_ms,q3_ms,ms_por_par");
        int cantidadPares = n * (n - 1) / 2;
        System.out.println("Similitud: tiempo para los " + cantidadPares + " pares");
        for (int a = 0; a < algoritmos.size(); a++) {
            double mediana = cuantil(tiempos[a], 0.5);
            csv.add(String.format(Locale.ROOT, "%s,%s,%d,%.4f,%.4f,%.4f,%.5f", algoritmos.get(a).nombre().replace(",", ""),
                    algoritmos.get(a).enfoque(), cantidadPares, mediana, cuantil(tiempos[a], 0.25),
                    cuantil(tiempos[a], 0.75), mediana / cantidadPares));
            System.out.printf(Locale.ROOT, "  %-32s %10.3f ms%n", algoritmos.get(a).nombre(), mediana);
        }
        escribir(carpeta.resolve("similitud_tiempos.csv"), csv);
    }

    private static void agrupamiento(CorpusProcesado corpus, Path carpeta) throws IOException {
        List<String> resumen = new ArrayList<>();
        resumen.add("representacion,criterio,ccc,mejor_k,silueta_mejor_k,silueta_k2,silueta_k3,silueta_k4,silueta_k5,"
                + "silueta_k6,silueta_k7,silueta_k8");
        List<String> etiquetas = corpus.articulos().stream().map(Articulo::etiqueta).toList();
        for (String representacion : AnalisisAgrupamiento.representaciones(corpus).keySet()) {
            for (AnalisisAgrupamiento.Resultado r : AnalisisAgrupamiento.analizar(corpus, representacion)) {
                double[] s = r.siluetaPorK();
                resumen.add(String.format(Locale.ROOT, "%s,%s,%.4f,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
                        representacion, r.criterio().nombre(), r.correlacionCofenetica(), r.mejorK(), r.mejorSilueta(),
                        s[2], s[3], s[4], s[5], s[6], s[7], s[8]));
                List<String> fusiones = new ArrayList<>();
                fusiones.add("paso,grupo_a,grupo_b,distancia,tamano");
                int paso = 1;
                for (Fusion f : r.fusiones()) {
                    fusiones.add(String.format(Locale.ROOT, "%d,%d,%d,%.6f,%d", paso++, f.grupoA(), f.grupoB(),
                            f.distancia(), f.tamano()));
                }
                String base = "fusiones_" + representacion + "_" + r.criterio().name().toLowerCase(Locale.ROOT);
                escribir(carpeta.resolve(base + ".csv"), fusiones);
                Files.writeString(carpeta.resolve(base + ".svg"), r.svg(), StandardCharsets.UTF_8);
            }
        }
        List<String> hojas = new ArrayList<>();
        hojas.add("indice,etiqueta");
        for (int i = 0; i < etiquetas.size(); i++) {
            hojas.add(i + ",\"" + etiquetas.get(i) + "\"");
        }
        escribir(carpeta.resolve("agrupamiento_etiquetas.csv"), hojas);
        escribir(carpeta.resolve("agrupamiento_metricas.csv"), resumen);
        System.out.println("Agrupamiento: métricas en agrupamiento_metricas.csv");
    }

    /**
     * Tiempos de preprocesamiento y de búsqueda de los tres algoritmos según la longitud del patrón
     * (texto: los 20 abstracts unidos) y según el tamaño del texto (patrón "learning").
     */
    private static void busqueda(CorpusProcesado corpus, Path carpeta) throws IOException {
        StringBuilder unido = new StringBuilder();
        for (Articulo a : corpus.articulos()) {
            unido.append(BusquedaCorpus.minusculas(a.abstractTexto())).append(' ');
        }
        String texto = unido.toString();
        List<String> csv = new ArrayList<>();
        csv.add("experimento,algoritmo,n,m,ocurrencias,comparaciones,ventanas,colisiones,preproceso_us,busqueda_ms,"
                + "comparaciones_por_caracter");
        for (String patron : PATRONES) {
            medirBusqueda("longitud_patron", texto, patron, csv);
        }
        for (int copias : COPIAS) {
            medirBusqueda("tamano_texto", texto.repeat(copias), "learning", csv);
        }
        escribir(carpeta.resolve("busqueda_tiempos.csv"), csv);
        System.out.println("Búsqueda: tiempos en busqueda_tiempos.csv");
    }

    private static void medirBusqueda(String experimento, String texto, String patron, List<String> csv) {
        List<AlgoritmoBusqueda> algoritmos = BusquedaCorpus.ALGORITMOS;
        double[][] preproceso = new double[algoritmos.size()][REPETICIONES];
        double[][] busqueda = new double[algoritmos.size()][REPETICIONES];
        Resultado[] ultimo = new Resultado[algoritmos.size()];
        for (int r = -CALENTAMIENTO; r < REPETICIONES; r++) {
            for (int a = 0; a < algoritmos.size(); a++) {
                Resultado resultado = algoritmos.get(a).buscar(texto, patron);
                sumidero += resultado.posiciones().size();
                if (r >= 0) {
                    preproceso[a][r] = resultado.nanosPreproceso() / 1e3;
                    busqueda[a][r] = resultado.nanosBusqueda() / 1e6;
                }
                ultimo[a] = resultado;
            }
        }
        for (int a = 0; a < algoritmos.size(); a++) {
            Resultado r = ultimo[a];
            csv.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%d,%.3f,%.4f,%.4f", experimento,
                    algoritmos.get(a).nombre(), texto.length(), patron.length(), r.posiciones().size(),
                    r.comparaciones(), r.ventanas(), r.colisiones(), cuantil(preproceso[a], 0.5),
                    cuantil(busqueda[a], 0.5), (double) r.comparaciones() / texto.length()));
        }
    }

    /**
     * Rabin-Karp multipatrón frente a KMP, Boyer-Moore y Rabin-Karp ejecutados una vez por patrón, con
     * 2, 5 y 10 patrones del corpus de distintas longitudes y con 10 de la misma longitud, sobre los 20
     * abstracts unidos (copiados 1 y 10 veces). Se ejecuta aparte (modo multipatron) para no volver a generar los demás archivos de resultados.
     */
    public static void busquedaMultiple(CorpusProcesado corpus, Path carpeta) throws IOException {
        Files.createDirectories(carpeta);
        StringBuilder unido = new StringBuilder();
        for (Articulo a : corpus.articulos()) {
            unido.append(BusquedaCorpus.minusculas(a.abstractTexto())).append(' ');
        }
        List<String> csv = new ArrayList<>();
        csv.add("conjunto,copias,n,patrones,longitudes,algoritmo,pasadas,ocurrencias,comparaciones,ventanas,colisiones,"
                + "total_ms");
        RabinKarpMultiple multiple = new RabinKarpMultiple();
        for (int copias : new int[] {1, 10}) {
            String texto = unido.toString().repeat(copias);
            // 2, 5 y 10 patrones de distintas longitudes; con 0, los diez de la misma longitud.
            for (int cantidad : new int[] {2, 5, 10, 0}) {
                String conjunto = cantidad > 0 ? "distintas_longitudes" : "misma_longitud";
                List<String> patrones = cantidad > 0 ? List.of(PATRONES_MULTIPLES).subList(0, cantidad)
                        : List.of(PATRONES_MISMA_LONGITUD);
                int k = patrones.size();
                long longitudes = patrones.stream().mapToInt(String::length).distinct().count();
                double[][] ms = new double[BusquedaCorpus.ALGORITMOS.size() + 1][REPETICIONES];
                long[][] cifras = new long[BusquedaCorpus.ALGORITMOS.size() + 1][5];
                for (int r = -CALENTAMIENTO_MULTIPLE; r < REPETICIONES; r++) {
                    RabinKarpMultiple.Resultado rm = multiple.buscar(texto, patrones);
                    int ocurrencias = 0;
                    for (List<Integer> p : rm.posiciones().values()) {
                        ocurrencias += p.size();
                    }
                    cifras[0] = new long[] {rm.pasadas(), ocurrencias, rm.comparaciones(), rm.ventanas(), rm.colisiones()};
                    if (r >= 0) {
                        ms[0][r] = (rm.nanosPreproceso() + rm.nanosBusqueda()) / 1e6;
                    }
                    for (int a = 0; a < BusquedaCorpus.ALGORITMOS.size(); a++) {
                        long[] c = new long[5];
                        long nanos = 0;
                        for (String patron : patrones) {
                            Resultado res = BusquedaCorpus.ALGORITMOS.get(a).buscar(texto, patron);
                            c[0]++;
                            c[1] += res.posiciones().size();
                            c[2] += res.comparaciones();
                            c[3] += res.ventanas();
                            c[4] += res.colisiones();
                            nanos += res.nanosPreproceso() + res.nanosBusqueda();
                        }
                        cifras[a + 1] = c;
                        if (r >= 0) {
                            ms[a + 1][r] = nanos / 1e6;
                        }
                    }
                    sumidero += ocurrencias;
                }
                for (int a = 0; a <= BusquedaCorpus.ALGORITMOS.size(); a++) {
                    String nombre = a == 0 ? multiple.nombre() : BusquedaCorpus.ALGORITMOS.get(a - 1).nombre();
                    long[] c = cifras[a];
                    csv.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%.4f", conjunto, copias, texto.length(), k,
                            longitudes, nombre, c[0], c[1], c[2], c[3], c[4], cuantil(ms[a], 0.5)));
                }
            }
        }
        escribir(carpeta.resolve("busqueda_multiple.csv"), csv);
        System.out.println("Búsqueda multipatrón: resultados en busqueda_multiple.csv");
    }

    static double cuantil(double[] valores, double p) {
        double[] v = valores.clone();
        Arrays.sort(v);
        double posicion = p * (v.length - 1);
        int abajo = (int) Math.floor(posicion);
        int arriba = (int) Math.ceil(posicion);
        return v[abajo] + (posicion - abajo) * (v[arriba] - v[abajo]);
    }

    private static void escribir(Path archivo, List<String> lineas) throws IOException {
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }
}
