package co.edu.uniquindio.bibliometria.modelo;

import java.util.List;

/**
 * Un artículo del corpus: su número (1 a 20, el del nombre del archivo), el título, los autores y el
 * año extraídos del PDF (0 si no se encontró el año) y el texto del abstract, que es lo que procesan
 * los algoritmos.
 */
public record Articulo(int numero, String archivo, String titulo, List<String> autores, int anio,
        String abstractTexto) {

    /** "Laupichler et al.", "Zhang y Aslan" o "Lo": forma corta para tablas y etiquetas. */
    public String autoresCortos() {
        if (autores.isEmpty()) {
            return "Sin autores";
        }
        String primero = apellido(autores.get(0));
        if (autores.size() == 1) {
            return primero;
        }
        if (autores.size() == 2) {
            return primero + " y " + apellido(autores.get(1));
        }
        return primero + " et al.";
    }

    /** Etiqueta corta para los dendrogramas y las matrices: "A7 Alfredo et al.". */
    public String etiqueta() {
        return "A" + numero + " " + autoresCortos();
    }

    private static String apellido(String nombreCompleto) {
        String[] partes = nombreCompleto.strip().split("\\s+");
        return partes[partes.length - 1];
    }
}
