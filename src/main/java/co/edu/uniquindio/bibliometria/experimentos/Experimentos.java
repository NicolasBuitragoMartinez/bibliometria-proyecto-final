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
    /** Patrones de longitud creciente tomados del corpus. */
    private static final String[] PATRONES = {"ai", "data", "learning", "assessment", "higher education",
            "artificial intelligence", "generative artificial intelligence", "artificial intelligence in education"};
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
