package co.edu.uniquindio.bibliometria.busqueda;

import java.util.List;

/**
 * Búsqueda exacta de un patrón en un texto. Las implementaciones recorren los caracteres con
 * charAt y estructuras propias; no usan indexOf, contains, matches ni expresiones regulares.
 */
public interface AlgoritmoBusqueda {

    /**
     * Resultado de una búsqueda: posiciones (base 0) donde empieza cada ocurrencia, número de
     * comparaciones de caracteres, número de alineaciones (ventanas) examinadas, coincidencias
     * espurias de hash (solo Rabin-Karp) y tiempos de preprocesamiento y de búsqueda en nanosegundos.
     */
    record Resultado(List<Integer> posiciones, long comparaciones, long ventanas, long colisiones,
            long nanosPreproceso, long nanosBusqueda) {
    }

    String nombre();

    Resultado buscar(String texto, String patron);
}
