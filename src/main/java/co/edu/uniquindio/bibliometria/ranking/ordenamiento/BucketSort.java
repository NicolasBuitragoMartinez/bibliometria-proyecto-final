package co.edu.uniquindio.bibliometria.ranking.ordenamiento;

import java.util.ArrayList;
import java.util.List;

import co.edu.uniquindio.bibliometria.ranking.Termino;

public final class BucketSort implements AlgoritmoOrdenamiento {

    @Override
    public String nombre() {
        return "Bucket Sort";
    }

    @Override
    public Termino[] ordenar(Termino[] terminos) {
        int n = terminos.length;
        Termino[] salida = new Termino[n];
        if (n == 0) {
            return salida;
        }
        int min = AlgoritmoOrdenamiento.frecuenciaMinima(terminos);
        long rango = (long) AlgoritmoOrdenamiento.frecuenciaMaxima(terminos) - min + 1;

        // 1. Distribucion en cubetas
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Termino>[] cubetas = new List[n];
        for (Termino t : terminos) {
            int indice = (int) ((t.frecuencia() - min) * (long) n / rango);
            if (cubetas[indice] == null) {
                cubetas[indice] = new ArrayList<>();
            }
            cubetas[indice].add(t);
        }

        // 2. Ordenar cada cubeta y concatenar desde la cubeta de frecuencias mas altas
        int posicion = 0;
        for (int i = n - 1; i >= 0; i--) {
            List<Termino> cubeta = cubetas[i];
            if (cubeta != null) {
                insercionDescendente(cubeta);
                for (Termino t : cubeta) {
                    salida[posicion++] = t;
                }
            }
        }
        return salida;
    }

    private static void insercionDescendente(List<Termino> cubeta) {
        for (int i = 1; i < cubeta.size(); i++) {
            Termino actual = cubeta.get(i);
            int j = i - 1;
            while (j >= 0 && cubeta.get(j).frecuencia() < actual.frecuencia()) {
                cubeta.set(j + 1, cubeta.get(j));
                j--;
            }
            cubeta.set(j + 1, actual);
        }
    }
}
