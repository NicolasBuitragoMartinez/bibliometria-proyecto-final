package co.edu.uniquindio.bibliometria;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import co.edu.uniquindio.bibliometria.corpus.CargadorCorpus;
import co.edu.uniquindio.bibliometria.corpus.CargadorEmbeddings;
import co.edu.uniquindio.bibliometria.experimentos.Experimentos;
import co.edu.uniquindio.bibliometria.experimentos.Trazas;
import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;
import co.edu.uniquindio.bibliometria.web.Servidor;

/**
 * Proyecto final: análisis de algoritmos en el contexto de la bibliometría.
 *
 * Uso: java -jar bibliometria.jar [servidor | experimentos | multipatron | trazas] [carpetaCorpus]
 *  - servidor (por defecto): aplicación web en el puerto de la variable PORT (8080 si no existe).
 *  - experimentos: mide los algoritmos de los tres requerimientos y guarda los CSV en resultados/.
 *  - multipatron: mide solo la búsqueda de varios patrones (resultados/busqueda_multiple.csv).
 *  - trazas: genera en resultados/trazas/ las demostraciones paso a paso del documento técnico.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws IOException {
        String modo = args.length > 0 ? args[0] : "servidor";
        Path carpetaCorpus = Path.of(args.length > 1 ? args[1] : "corpus");
        CorpusProcesado corpus = cargar(carpetaCorpus);
        System.out.printf("Corpus: %d artículos; embeddings disponibles: %s%n", corpus.tamano(),
                CargadorEmbeddings.MODELOS.stream().filter(corpus::tieneEmbeddings).toList());

        switch (modo) {
            case "experimentos" -> Experimentos.ejecutar(corpus, Path.of("resultados"));
            case "trazas" -> Trazas.generar(corpus, Path.of("resultados", "trazas"));
            case "multipatron" -> Experimentos.busquedaMultiple(corpus, Path.of("resultados"));
            default -> new Servidor(corpus).iniciar(puerto());
        }
    }

    public static CorpusProcesado cargar(Path carpetaCorpus) throws IOException {
        List<Articulo> articulos = CargadorCorpus.cargar(carpetaCorpus);
        Map<String, double[][]> embeddings = CargadorEmbeddings.cargar(carpetaCorpus, articulos);
        return new CorpusProcesado(articulos, embeddings);
    }

    private static int puerto() {
        String variable = System.getenv("PORT");
        return variable == null || variable.isBlank() ? 8080 : Integer.parseInt(variable.strip());
    }
}
