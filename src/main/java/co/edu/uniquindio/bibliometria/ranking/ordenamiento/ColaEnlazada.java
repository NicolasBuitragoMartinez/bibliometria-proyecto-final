package co.edu.uniquindio.bibliometria.ranking.ordenamiento;

final class ColaEnlazada<T> {

    private static final class Nodo<T> {
        private final T valor;
        private Nodo<T> siguiente;

        Nodo(T valor) {
            this.valor = valor;
        }
    }

    private Nodo<T> inicio;
    private Nodo<T> fin;

    void encolar(T valor) {
        Nodo<T> nuevo = new Nodo<>(valor);
        if (inicio == null) {
            inicio = nuevo;
        } else {
            fin.siguiente = nuevo;
        }
        fin = nuevo;
    }

    T decolar() {
        T valor = inicio.valor;
        inicio = inicio.siguiente;
        if (inicio == null) {
            fin = null;
        }
        return valor;
    }

    boolean estaVacia() {
        return inicio == null;
    }
}
