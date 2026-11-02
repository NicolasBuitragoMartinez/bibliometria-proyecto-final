package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.List;

/**
 * Rabin-Karp con hash rodante (rolling hash). Cada ventana de m caracteres se interpreta como un
 * número en base d y se reduce módulo un primo q:
 *   h(T[s..s+m-1]) = (T[s]·d^(m-1) + T[s+1]·d^(m-2) + ... + T[s+m-1]) mod q
 * La ventana siguiente se obtiene en O(1) quitando el primer carácter y añadiendo el nuevo:
 *   h(s+1) = (d · (h(s) - T[s]·d^(m-1)) + T[s+m]) mod q
 * Si los hash coinciden se verifica carácter por carácter, porque dos cadenas distintas pueden tener
 * el mismo hash (coincidencia espuria o colisión). Preprocesamiento O(m); búsqueda O(n + m) esperada
 * y O(n·m) en el peor caso (todas las ventanas colisionan).
 */
public final class RabinKarp implements AlgoritmoBusqueda {

    public static final int BASE = 256;
    public static final long PRIMO = 1_000_000_007L;

    private final long primo;

    public RabinKarp() {
        this(PRIMO);
    }

    /** Con un primo pequeño (por ejemplo 101) las colisiones son frecuentes: útil para la demostración. */
    public RabinKarp(long primo) {
        this.primo = primo;
    }

    @Override
    public String nombre() {
        return "Rabin-Karp";
    }

    /** Hash de los primeros m caracteres de la cadena (método de Horner). */
    public long hash(String cadena, int m) {
        long h = 0;
        for (int i = 0; i < m; i++) {
            h = (BASE * h + cadena.charAt(i)) % primo;
        }
        return h;
    }

    /** d^(m-1) mod q, el peso del carácter que sale de la ventana. */
    public long pesoMayor(int m) {
        long h = 1;
        for (int i = 0; i < m - 1; i++) {
            h = (h * BASE) % primo;
        }
        return h;
    }

    /** Siguiente hash: quita el carácter que sale y agrega el que entra. */
    public long rodar(long hashActual, char sale, char entra, long pesoMayor) {
        long sinPrimero = (hashActual - sale * pesoMayor % primo + primo) % primo;
        return (sinPrimero * BASE + entra) % primo;
    }

    @Override
    public Resultado buscar(String texto, String patron) {
        long inicio = System.nanoTime();
        int n = texto.length();
        int m = patron.length();
        long hashPatron = m > 0 ? hash(patron, m) : 0;
        long peso = pesoMayor(m);
        long finPreproceso = System.nanoTime();

        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        long colisiones = 0;
        long ventanas = 0;
        if (m > 0 && m <= n) {
            long hashVentana = hash(texto, m);
            for (int s = 0; s <= n - m; s++) {
                ventanas++;
                if (hashVentana == hashPatron) {
                    int j = 0;
                    while (j < m) {
                        comparaciones++;
                        if (texto.charAt(s + j) != patron.charAt(j)) {
                            break;
                        }
                        j++;
                    }
                    if (j == m) {
                        posiciones.add(s);
                    } else {
                        colisiones++;
                    }
                }
                if (s < n - m) {
                    hashVentana = rodar(hashVentana, texto.charAt(s), texto.charAt(s + m), peso);
                }
            }
        }
        long fin = System.nanoTime();
        return new Resultado(posiciones, comparaciones, ventanas, colisiones, finPreproceso - inicio,
                fin - finPreproceso);
    }
}
