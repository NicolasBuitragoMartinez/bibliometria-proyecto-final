package co.edu.uniquindio.bibliometria.ranking.reporte;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.ranking.RankingTerminos;
import co.edu.uniquindio.bibliometria.ranking.medicion.ExperimentoMemoria.Resultado;
import co.edu.uniquindio.bibliometria.ranking.medicion.MedidorTiempos.Medicion;
import co.edu.uniquindio.bibliometria.ranking.Termino;

/** Salida por consola y archivos CSV en la carpeta de resultados. */
public final class Reporte {

    private static final int PALABRAS_MINIMAS_ESPERADAS = 50;
    private static final Locale ES = Locale.forLanguageTag("es-CO");

    private Reporte() {
    }

    private static int palabras(Articulo a) {
        return a.abstractTexto().isBlank() ? 0 : a.abstractTexto().split("\s+").length;
    }

    public static void imprimirCorpus(List<Articulo> documentos, int maxPalabras) {
        System.out.println("=== Corpus ===");
        System.out.println("Documentos: " + documentos.size());
        for (Articulo d : documentos) {
            String aviso = "";
            if (palabras(d) == 0) {
                aviso = "  <-- NO SE ENCONTRÓ EL ABSTRACT: pegarlo a mano en el .txt";
            } else if (palabras(d) < PALABRAS_MINIMAS_ESPERADAS) {
                aviso = "  <-- muy corto, revisar el .txt";
            } else if (palabras(d) >= maxPalabras) {
                aviso = "  <-- no se halló el final, revisar el .txt";
            }
            System.out.printf("  %-45s %4d palabras%s%s%n", recortar(d.archivo(), 45), palabras(d),
                    "", aviso);
        }
        System.out.println();
    }

    /** Cifras del corpus para el ranking (Tabla 21 del documento técnico). */
    public static void imprimirCifras(List<Articulo> documentos, int tokens, Termino[] frecuencias, int k) {
        int palabras = documentos.stream().mapToInt(Reporte::palabras).sum();
        int max = 0;
        int min = Integer.MAX_VALUE;
        int unaVez = 0;
        for (Termino t : frecuencias) {
            max = Math.max(max, t.frecuencia());
            min = Math.min(min, t.frecuencia());
            if (t.frecuencia() == 1) {
                unaVez++;
            }
        }
        System.out.println("Cifras del corpus después del preprocesamiento");
        System.out.printf(ES, "  %-38s %,d%n", "Artículos", documentos.size());
        System.out.printf(ES, "  %-38s %,d%n", "Palabras en los abstracts", palabras);
        System.out.printf(ES, "  %-38s %,d%n", "Términos tras el preprocesamiento", tokens);
        System.out.printf(ES, "  %-38s %,d%n", "Términos distintos (n)", frecuencias.length);
        System.out.printf(ES, "  %-38s %d / %d%n", "Frecuencia máxima / mínima", max, min);
        System.out.printf(ES, "  %-38s %d%n", "Rango de claves k = máx - mín + 1", k);
        System.out.printf(ES, "  %-38s %d (%.1f %%)%n%n", "Términos que aparecen una sola vez", unaVez,
                100.0 * unaVez / frecuencias.length);
    }

    public static void imprimirTiempos(List<Medicion> mediciones) {
        System.out.println("=== Tiempos de ejecución (ms), una corrida ===");
        System.out.printf("%-15s %12s %12s %12s %12s%n", "Algoritmo", "En frío", "Mediana", "Promedio", "Mínimo");
        for (Medicion m : mediciones) {
            System.out.printf(ES, "%-15s %12.4f %12.4f %12.4f %12.4f%n",
                    m.algoritmo(), m.frioMs(), m.medianaMs(), m.promedioMs(), m.minimoMs());
        }
        System.out.printf("(mediana, promedio y mínimo sobre %d ejecuciones tras %d de calentamiento;%n"
                + " para la medición del documento ejecute la clase MedicionCompleta o use --corridas 30)%n%n",
                mediciones.get(0).repeticiones(), RankingTerminos.CALENTAMIENTO);
    }

    /** Explica el empate en la frontera del top (en el corpus: cuatro términos con 15 apariciones). */
    public static void imprimirEmpate(Termino[] ordenado, int top) {
        if (ordenado.length <= top) {
            return;
        }
        int frontera = ordenado[top - 1].frecuencia();
        if (ordenado[top].frecuencia() != frontera) {
            return;
        }
        List<String> empatados = new ArrayList<>();
        int ultimaPosicion = top;
        for (int i = 0; i < ordenado.length; i++) {
            if (ordenado[i].frecuencia() == frontera) {
                empatados.add(ordenado[i].palabra() + " (pos. " + (i + 1) + ")");
                ultimaPosicion = i + 1;
            }
        }
        System.out.printf("Empate en el límite del top %d: %d términos con %d apariciones: %s.%n",
                top, empatados.size(), frontera, String.join(", ", empatados));
        String fuera = ultimaPosicion == top + 1 ? "la posición " + (top + 1) + " queda"
                : "las posiciones " + (top + 1) + " a " + ultimaPosicion + " quedan";
        System.out.printf("Se resuelve por orden de primera aparición en el corpus (los tres algoritmos son estables);"
                + " %s fuera del top %d.%n%n", fuera, top);
    }

    public static void imprimirTop(List<String> nombres, List<Termino[]> ordenados, int top) {
        System.out.println("Los " + top + " términos más frecuentes del corpus según cada algoritmo");
        StringBuilder encabezado = new StringBuilder(String.format("%-4s", "#"));
        nombres.forEach(nombre -> encabezado.append(String.format("%-28s", nombre)));
        System.out.println(encabezado);
        for (int i = 0; i < top && i < ordenados.get(0).length; i++) {
            StringBuilder fila = new StringBuilder(String.format("%-4d", i + 1));
            for (Termino[] lista : ordenados) {
                fila.append(String.format("%-28s", lista[i].palabra() + " (" + lista[i].frecuencia() + ")"));
            }
            System.out.println(fila);
        }
        System.out.println();
    }

    public static void guardarTiempos(Path archivo, List<Medicion> mediciones) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("algoritmo,n,k,frio_ms,mediana_ms,promedio_ms,minimo_ms,repeticiones");
        for (Medicion m : mediciones) {
            lineas.add(String.format(Locale.ROOT, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%d", m.algoritmo(), m.n(), m.k(),
                    m.frioMs(), m.medianaMs(), m.promedioMs(), m.minimoMs(), m.repeticiones()));
        }
        escribir(archivo, lineas);
    }

    public static void guardarMemoria(Path archivo, List<Resultado> resultados) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("disposicion,algoritmo,n,k,frio_ms,mediana_ms,promedio_ms,minimo_ms,repeticiones");
        for (Resultado r : resultados) {
            Medicion m = r.medicion();
            lineas.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%d", r.disposicion(), m.algoritmo(),
                    m.n(), m.k(), m.frioMs(), m.medianaMs(), m.promedioMs(), m.minimoMs(), m.repeticiones()));
        }
        escribir(archivo, lineas);
    }

    public static void guardarTop(Path archivo, List<String> nombres, List<Termino[]> ordenados, int top)
            throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder encabezado = new StringBuilder("posicion");
        nombres.forEach(nombre -> encabezado.append(',').append(nombre).append(" termino,")
                .append(nombre).append(" frecuencia"));
        lineas.add(encabezado.toString());
        for (int i = 0; i < top && i < ordenados.get(0).length; i++) {
            StringBuilder fila = new StringBuilder(String.valueOf(i + 1));
            for (Termino[] lista : ordenados) {
                fila.append(',').append(lista[i].palabra()).append(',').append(lista[i].frecuencia());
            }
            lineas.add(fila.toString());
        }
        escribir(archivo, lineas);
    }

    public static void guardarRanking(Path archivo, Termino[] ordenado) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("posicion,termino,frecuencia");
        for (int i = 0; i < ordenado.length; i++) {
            lineas.add((i + 1) + "," + ordenado[i].palabra() + "," + ordenado[i].frecuencia());
        }
        escribir(archivo, lineas);
    }

    public static void guardarCorpus(Path archivo, List<Articulo> documentos) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("archivo,palabras_abstract");
        documentos.forEach(d -> lineas.add("\"" + d.archivo() + "\"," + palabras(d)));
        escribir(archivo, lineas);
    }

    private static void escribir(Path archivo, List<String> lineas) throws IOException {
        Files.createDirectories(archivo.getParent());
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 3) + "...";
    }
}
