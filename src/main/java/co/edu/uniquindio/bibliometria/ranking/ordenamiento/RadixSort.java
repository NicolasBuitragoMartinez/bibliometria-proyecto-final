package co.edu.uniquindio.bibliometria.ranking.ordenamiento;

import co.edu.uniquindio.bibliometria.ranking.Termino;

public final class RadixSort implements AlgoritmoOrdenamiento {

    private static final int BASE = 10;

    @Override
    public String nombre() {
        return "Radix Sort";
    }

    @Override
    public Termino[] ordenar(Termino[] terminos) {
        Termino[] arreglo = terminos.clone();
        int numeroDigitos = String.valueOf(AlgoritmoOrdenamiento.frecuenciaMaxima(terminos)).length();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ColaEnlazada<Termino>[] colas = new ColaEnlazada[BASE];
        for (int d = 0; d < BASE; d++) {
            colas[d] = new ColaEnlazada<>();
        }

        int divisor = 1;
        for (int i = 1; i <= numeroDigitos; i++) {
            for (Termino t : arreglo) {
                colas[(t.frecuencia() / divisor) % BASE].encolar(t);
            }
            int posicion = 0;
            for (int d = BASE - 1; d >= 0; d--) {
                while (!colas[d].estaVacia()) {
                    arreglo[posicion++] = colas[d].decolar();
                }
            }
            divisor *= BASE;
        }
        return arreglo;
    }
}
