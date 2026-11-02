package co.edu.uniquindio.bibliometria.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import co.edu.uniquindio.bibliometria.modelo.Articulo;

/**
 * Lee los embeddings generados por ia/generar_embeddings.py. Cada modelo tiene un archivo
 * {@code embeddings/<modelo>.csv} con una fila por artículo: el número del artículo y las d
 * componentes del vector. Los vectores se devuelven en el mismo orden de la lista de artículos.
 */
public final class CargadorEmbeddings {

    public static final List<String> MODELOS = List.of("glove", "minilm");

    private CargadorEmbeddings() {
    }

    public static Map<String, double[][]> cargar(Path carpetaCorpus, List<Articulo> articulos) throws IOException {
        Map<String, double[][]> embeddings = new LinkedHashMap<>();
        for (String modelo : MODELOS) {
            Path archivo = carpetaCorpus.resolve("embeddings").resolve(modelo + ".csv");
            if (!Files.exists(archivo)) {
                continue;
            }
            Map<Integer, double[]> porNumero = new LinkedHashMap<>();
            List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
            for (String linea : lineas.subList(1, lineas.size())) {
                if (linea.isBlank()) {
                    continue;
                }
                String[] campos = linea.split(",");
                double[] vector = new double[campos.length - 1];
                for (int k = 1; k < campos.length; k++) {
                    vector[k - 1] = Double.parseDouble(campos[k]);
                }
                porNumero.put(Integer.parseInt(campos[0]), vector);
            }
            double[][] vectores = new double[articulos.size()][];
            for (int i = 0; i < articulos.size(); i++) {
                vectores[i] = porNumero.get(articulos.get(i).numero());
                if (vectores[i] == null) {
                    throw new IllegalStateException("Falta el embedding del artículo " + articulos.get(i).numero()
                            + " en " + archivo);
                }
            }
            embeddings.put(modelo, vectores);
        }
        return embeddings;
    }
}
