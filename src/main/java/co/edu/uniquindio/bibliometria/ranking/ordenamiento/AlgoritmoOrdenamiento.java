package co.edu.uniquindio.bibliometria.ranking.ordenamiento;

import co.edu.uniquindio.bibliometria.ranking.Termino;

/**
 * Ordena términos por frecuencia de mayor a menor sin comparar elementos entre sí:
 * la frecuencia se usa directamente como índice (clave entera). Los tres algoritmos son
 * estables (los empates conservan el orden de entrada) y no modifican el arreglo recibido.
 */
public interface AlgoritmoOrdenamiento {

    String nombre();

    Termino[] ordenar(Termino[] terminos);

    static int frecuenciaMaxima(Termino[] terminos) {
        int max = 0;
        for (Termino t : terminos) {
            if (t.frecuencia() > max) {
                max = t.frecuencia();
            }
        }
        return max;
    }

    static int frecuenciaMinima(Termino[] terminos) {
        int min = Integer.MAX_VALUE;
        for (Termino t : terminos) {
            if (t.frecuencia() < min) {
                min = t.frecuencia();
            }
        }
        return min;
    }
}
