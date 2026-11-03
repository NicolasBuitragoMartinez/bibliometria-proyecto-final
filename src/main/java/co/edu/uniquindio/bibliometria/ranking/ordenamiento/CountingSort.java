package co.edu.uniquindio.bibliometria.ranking.ordenamiento;

import co.edu.uniquindio.bibliometria.ranking.Termino;

public final class CountingSort implements AlgoritmoOrdenamiento {

    @Override
    public String nombre() {
        return "Counting Sort";
    }

    @Override
    public Termino[] ordenar(Termino[] terminos) {
        int n = terminos.length;
        Termino[] salida = new Termino[n];
        if (n == 0) {
            return salida;
        }
        int min = AlgoritmoOrdenamiento.frecuenciaMinima(terminos);
        int max = AlgoritmoOrdenamiento.frecuenciaMaxima(terminos);

        // 1. Histograma: cuantos terminos tienen cada frecuencia
        int[] conteo = new int[max - min + 1];
        for (Termino t : terminos) {
            conteo[t.frecuencia() - min]++;
        }

        // 2. Posicion inicial de cada frecuencia en la salida de la mayor a la menor
        int posicion = 0;
        for (int v = conteo.length - 1; v >= 0; v--) {
            int cantidad = conteo[v];
            conteo[v] = posicion;
            posicion += cantidad;
        }

        // 3. Colocacion recorriendo la entrada en orden
        for (Termino t : terminos) {
            salida[conteo[t.frecuencia() - min]++] = t;
        }
        return salida;
    }
}
