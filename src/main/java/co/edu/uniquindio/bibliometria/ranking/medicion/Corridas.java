package co.edu.uniquindio.bibliometria.ranking.medicion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import co.edu.uniquindio.bibliometria.ranking.RankingTerminos;

/**
 * Repite la medición en varias JVM nuevas, como se hizo para el documento: cada corrida es un
 * proceso Java independiente (así el tiempo "en frío" es realmente la primera ejecución) y al
 * final se resume cada medida con la mediana entre corridas y su rango intercuartílico (Q1 - Q3).
 */
public final class Corridas {

    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private Corridas() {
    }

    /**
     * Tiempos sobre el corpus: mediana de N corridas del tiempo en frío y del tiempo en caliente.
     */
    public static void medirTiempos(Path carpetaCorpus, Path carpetaResultados, int corridas)
            throws IOException, InterruptedException {
        Path carpeta = carpetaResultados.resolve("corridas");
        Files.createDirectories(carpeta);
        System.out.println("=== Medición de tiempos en " + corridas + " corridas (una JVM nueva por corrida) ===");
        Map<String, List<double[]>> porAlgoritmo = new LinkedHashMap<>(); // algoritmo -> [frío, caliente] por corrida
        int n = 0;
        int k = 0;
        for (int i = 1; i <= corridas; i++) {
            Path csv = carpeta.resolve("tiempos_" + i + ".csv");
            ejecutarHijo(carpetaCorpus, "--interno-tiempos", csv.toString());
            for (String[] fila : leerCsv(csv)) {
                porAlgoritmo.computeIfAbsent(fila[0], a -> new ArrayList<>())
                        .add(new double[] { Double.parseDouble(fila[3]), Double.parseDouble(fila[4]) });
                n = Integer.parseInt(fila[1]);
                k = Integer.parseInt(fila[2]);
            }
            System.out.printf("  Corrida %d de %d lista%n", i, corridas);
        }

        double referencia = mediana(porAlgoritmo.values().iterator().next().stream().mapToDouble(v -> v[1]).toArray());
        List<String> csv = new ArrayList<>();
        csv.add("algoritmo,n,k,corridas,frio_ms_mediana,frio_ms_q1,frio_ms_q3,caliente_ms_mediana,caliente_ms_q1,"
                + "caliente_ms_q3,caliente_ms_minimo,caliente_ms_maximo,relativo_counting");
        System.out.printf("%nTiempos sobre la lista de frecuencias del corpus (n = %d, k = %d)%n", n, k);
        System.out.printf("%-15s %13s %17s %24s %24s %10s%n", "Algoritmo", "En frío (ms)", "En caliente (ms)",
                "RIC en caliente (ms)", "Rango en caliente (ms)", "Relativo");
        for (Map.Entry<String, List<double[]>> e : porAlgoritmo.entrySet()) {
            double[] frio = e.getValue().stream().mapToDouble(v -> v[0]).toArray();
            double[] caliente = e.getValue().stream().mapToDouble(v -> v[1]).toArray();
            double medCaliente = mediana(caliente);
            double min = Arrays.stream(caliente).min().orElse(0);
            double max = Arrays.stream(caliente).max().orElse(0);
            System.out.printf(ES, "%-15s %13.3f %17.4f %11.4f - %-10.4f %11.4f - %-10.4f %8.1f x%n",
                    e.getKey(), mediana(frio), medCaliente, cuartil(caliente, 0.25), cuartil(caliente, 0.75),
                    min, max, medCaliente / referencia);
            csv.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.3f",
                    e.getKey(), n, k, corridas, mediana(frio), cuartil(frio, 0.25), cuartil(frio, 0.75), medCaliente,
                    cuartil(caliente, 0.25), cuartil(caliente, 0.75), min, max, medCaliente / referencia));
        }
        System.out.printf(
                "(mediana de %d corridas; en caliente = mediana de %d ordenamientos tras %d rondas de calentamiento;"
                        + " RIC = rango intercuartílico Q1 - Q3 entre corridas)%n%n",
                corridas, RankingTerminos.REPETICIONES, RankingTerminos.CALENTAMIENTO);
        escribir(carpetaResultados.resolve("tabla4_tiempos.csv"), csv);
    }

    /**
     * Escalabilidad según n y según k: mediana de varias corridas del experimento de escalabilidad.
     */
    public static void medirEscalabilidad(Path carpetaCorpus, Path carpetaResultados, int corridas)
            throws IOException, InterruptedException {
        System.out.println("=== Experimento de escalabilidad en " + corridas + " corridas ===");
        Path carpeta = carpetaResultados.resolve("corridas");
        for (int i = 1; i <= corridas; i++) {
            Path destino = carpeta.resolve("escalabilidad_" + i);
            Files.createDirectories(destino);
            ejecutarHijo(carpetaCorpus, "--interno-escalabilidad", destino.toString());
            System.out.printf("  Corrida %d de %d lista%n", i, corridas);
        }
        resumir(carpeta, "escalabilidad_", carpetaResultados, corridas, "escalabilidad_n.csv", 0,
                "Escalabilidad: nanosegundos por término según n (k <= 86)");
        resumir(carpeta, "escalabilidad_", carpetaResultados, corridas, "escalabilidad_k.csv", 0,
                "Escalabilidad: tiempo en ms según k (n = 100.000)");
    }

    /**
     * Experimento de jerarquía de memoria: mediana de varias corridas, listas contigua y dispersa.
     */
    public static void medirMemoria(Path carpetaCorpus, Path carpetaResultados, int corridas)
            throws IOException, InterruptedException {
        System.out.println("=== Experimento de jerarquía de memoria en " + corridas + " corridas ===");
        Path carpeta = carpetaResultados.resolve("corridas");
        for (int i = 1; i <= corridas; i++) {
            Path destino = carpeta.resolve("memoria_" + i);
            Files.createDirectories(destino);
            ejecutarHijo(carpetaCorpus, "--interno-memoria", destino.toString());
            System.out.printf("  Corrida %d de %d lista%n", i, corridas);
        }
        resumir(carpeta, "memoria_", carpetaResultados, corridas, "memoria.csv", 1,
                "Jerarquía de memoria: nanosegundos por término con los objetos contiguos o dispersos");

        double[] bytes = new double[corridas];
        for (int i = 1; i <= corridas; i++) {
            bytes[i - 1] = Double.parseDouble(leerCsv(carpeta.resolve("memoria_" + i).resolve("bytes_por_termino.csv"))
                    .get(0)[0]);
        }
        System.out.printf(ES, "%nBytes por término en el heap (objeto Termino + referencia en el arreglo): %.1f%n%n",
                mediana(bytes));
        escribir(carpetaResultados.resolve("memoria_bytes_por_termino.csv"),
                List.of("bytes_por_termino", String.format(Locale.ROOT, "%.2f", mediana(bytes))));
    }

    /**
     * Resume los archivos de cada corrida. Las primeras {@code columnasExtra} columnas (por ejemplo
     * la disposición en memoria) forman parte de la clave junto con algoritmo, n y k.
     */
    private static void resumir(Path carpeta, String prefijo, Path carpetaResultados, int corridas, String archivo,
            int columnasExtra, String titulo) throws IOException {
        Map<String, List<Double>> valores = new LinkedHashMap<>(); // clave -> mediana de cada corrida
        String encabezadoClave = null;
        for (int i = 1; i <= corridas; i++) {
            Path csv = carpeta.resolve(prefijo + i).resolve(archivo);
            if (encabezadoClave == null) {
                String[] encabezado = Files.readAllLines(csv, StandardCharsets.UTF_8).get(0).split(",");
                encabezadoClave = String.join(",", Arrays.copyOf(encabezado, columnasExtra + 3));
            }
            for (String[] fila : leerCsv(csv)) {
                String clave = String.join(",", Arrays.copyOf(fila, columnasExtra + 3));
                valores.computeIfAbsent(clave, c -> new ArrayList<>()).add(Double.parseDouble(fila[columnasExtra + 4]));
            }
        }
        List<String> csv = new ArrayList<>();
        csv.add(encabezadoClave + ",corridas,mediana_ms,q1_ms,q3_ms,ns_por_termino,ns_q1,ns_q3");
        System.out.println();
        System.out.println(titulo);
        System.out.printf("%-26s %12s %12s %14s %16s %22s%n", "Algoritmo", "n", "k", "Tiempo (ms)", "ns por término",
                "RIC ns por término");
        for (Map.Entry<String, List<Double>> e : valores.entrySet()) {
            String[] clave = e.getKey().split(",");
            int n = Integer.parseInt(clave[columnasExtra + 1]);
            double[] ms = e.getValue().stream().mapToDouble(Double::doubleValue).toArray();
            double mediana = mediana(ms);
            double q1 = cuartil(ms, 0.25);
            double q3 = cuartil(ms, 0.75);
            String nombre = columnasExtra == 0 ? clave[0] : clave[columnasExtra] + " (" + clave[0] + ")";
            System.out.printf(ES, "%-26s %,12d %,12d %14.4f %16.1f %10.1f - %-10.1f%n", nombre, n,
                    Integer.parseInt(clave[columnasExtra + 2]), mediana, mediana * 1e6 / n, q1 * 1e6 / n, q3 * 1e6 / n);
            csv.add(String.format(Locale.ROOT, "%s,%d,%.6f,%.6f,%.6f,%.3f,%.3f,%.3f", e.getKey(), ms.length, mediana,
                    q1, q3, mediana * 1e6 / n, q1 * 1e6 / n, q3 * 1e6 / n));
        }
        escribir(carpetaResultados.resolve(archivo.replace(".csv", "_mediana.csv")), csv);
    }

    private static void ejecutarHijo(Path carpetaCorpus, String modo, String destino)
            throws IOException, InterruptedException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> comando = List.of(java, "-Dfile.encoding=UTF-8", "-cp", System.getProperty("java.class.path"),
                RankingTerminos.class.getName(), carpetaCorpus.toString(), modo, destino);
        Process proceso = new ProcessBuilder(comando)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        int codigo = proceso.waitFor();
        if (codigo != 0) {
            throw new IllegalStateException("La corrida terminó con código " + codigo);
        }
    }

    private static List<String[]> leerCsv(Path archivo) throws IOException {
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        return lineas.subList(1, lineas.size()).stream().filter(l -> !l.isBlank()).map(l -> l.split(",")).toList();
    }

    static double mediana(double[] valores) {
        return cuartil(valores, 0.5);
    }

    /** Cuantil p con interpolación lineal entre los dos valores vecinos (método 7 de Hyndman y Fan). */
    static double cuartil(double[] valores, double p) {
        double[] v = valores.clone();
        Arrays.sort(v);
        double posicion = p * (v.length - 1);
        int abajo = (int) Math.floor(posicion);
        int arriba = (int) Math.ceil(posicion);
        return v[abajo] + (posicion - abajo) * (v[arriba] - v[abajo]);
    }

    private static void escribir(Path archivo, List<String> lineas) throws IOException {
        Files.createDirectories(archivo.toAbsolutePath().getParent());
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }
}
