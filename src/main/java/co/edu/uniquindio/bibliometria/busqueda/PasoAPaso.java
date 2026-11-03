package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Demostración paso a paso de KMP, Boyer-Moore y Rabin-Karp sobre una oración de un abstract: registra
 * cada alineación del patrón sobre el texto (qué posiciones del patrón se comparan, dónde falla, cuánto
 * se desplaza y por qué). Usa las mismas tablas y funciones que las implementaciones (función de
 * prefijos, carácter malo, sufijo bueno, hash de Horner y hash rodante) y recorre el texto con los mismos
 * ciclos, de modo que las posiciones y las comparaciones coinciden con las de cada algoritmo; las pruebas
 * lo verifican. La interfaz dibuja estas alineaciones sobre el texto.
 */
public final class PasoAPaso {

    public static final int MAXIMO_TEXTO = 240;
    public static final int MAXIMO_PATRON = 40;

    /**
     * Una alineación del patrón en la posición s del texto. conocidos: caracteres que se sabe que
     * coinciden sin compararlos (KMP); comparadas: posiciones j del patrón comparadas, en orden; falla:
     * la j donde hubo diferencia (-1 si no la hubo); desplazamiento: cuánto avanza el patrón después; hash: el de la ventana en Rabin-Karp (-1 en los otros algoritmos).
     */
    public record Alineacion(int s, int conocidos, List<Integer> comparadas, int falla, boolean ocurrencia,
            int desplazamiento, long hash, String explicacion) {
    }

    /** Recorrido completo de un algoritmo: sus tablas auxiliares, las alineaciones y los totales. */
    public record Recorrido(String algoritmo, Map<String, Object> tablas, List<Alineacion> alineaciones,
            List<Integer> posiciones, long comparaciones, long colisiones) {
    }

    private PasoAPaso() {
    }

    /**
     * Oración del abstract que contiene la posición dada (entre el punto anterior y el siguiente),
     * recortada a MAXIMO_TEXTO caracteres alrededor de la posición sin partir palabras.
     */
    public static int[] oracion(String abstractTexto, int posicion, int largoPatron) {
        int n = abstractTexto.length();
        int desde = posicion;
        while (desde > 0 && !(abstractTexto.charAt(desde - 1) == ' ' && desde >= 2 && abstractTexto.charAt(desde - 2) == '.')) {
            desde--;
        }
        int hasta = Math.min(n, posicion + largoPatron);
        while (hasta < n && abstractTexto.charAt(hasta - 1) != '.') {
            hasta++;
        }
        if (hasta - desde > MAXIMO_TEXTO) {
            int margen = (MAXIMO_TEXTO - largoPatron) / 2;
            if (posicion - desde > margen) {
                desde = posicion - margen;
                while (desde < posicion && abstractTexto.charAt(desde - 1) != ' ') {
                    desde++;
                }
            }
            if (hasta - desde > MAXIMO_TEXTO) {
                hasta = desde + MAXIMO_TEXTO;
                while (hasta > posicion + largoPatron && abstractTexto.charAt(hasta) != ' ') {
                    hasta--;
                }
            }
        }
        return new int[] {desde, hasta};
    }

    public static List<Recorrido> recorrer(String texto, String patron, long primo) {
        String t = texto.toLowerCase(Locale.ROOT);
        String p = patron.toLowerCase(Locale.ROOT);
        return List.of(kmp(t, p), boyerMoore(t, p), rabinKarp(t, p, primo));
    }

    static Recorrido kmp(String texto, String patron) {
        int n = texto.length();
        int m = patron.length();
        int[] pi = KnuthMorrisPratt.funcionPrefijos(patron);
        List<Alineacion> alineaciones = new ArrayList<>();
        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        int s = 0;
        int q = 0;
        // Igual que KMP, el recorrido sigue mientras quede texto por leer en la posición s + q.
        while (s + q < n) {
            List<Integer> comparadas = new ArrayList<>();
            int j = q;
            int falla = -1;
            while (j < m && s + j < n) {
                comparadas.add(j);
                comparaciones++;
                if (texto.charAt(s + j) != patron.charAt(j)) {
                    falla = j;
                    break;
                }
                j++;
            }
            int conocidos = q;
            if (j == m) {
                posiciones.add(s);
                q = pi[m - 1];
                alineaciones.add(new Alineacion(s, conocidos, comparadas, -1, true, m - q, -1, String.format(
                        "Ocurrencia en la posición %d. π[%d] = %d: el patrón avanza m − π[m−1] = %d y conserva %d "
                                + "%s ya comparados.", s, m - 1, q, m - q, q, q == 1 ? "carácter" : "caracteres")));
                s += m - q;
            } else if (falla < 0) {
                alineaciones.add(new Alineacion(s, conocidos, comparadas, -1, false, 0, -1,
                        "Se acabó el texto antes de completar el patrón."));
                break;
            } else if (falla == 0) {
                alineaciones.add(new Alineacion(s, conocidos, comparadas, 0, false, 1, -1, String.format(
                        "'%c' ≠ P[0] = '%c': ningún prefijo del patrón sirve, avanza 1.", texto.charAt(s), patron.charAt(0))));
                s += 1;
                q = 0;
            } else {
                q = pi[falla - 1];
                alineaciones.add(new Alineacion(s, conocidos, comparadas, falla, false, falla - q, -1, String.format(
                        "'%c' ≠ P[%d] = '%c' tras %d coincidencias. π[%d] = %d: avanza %d − %d = %d sin volver a "
                                + "leer el texto y conserva %d %s.", texto.charAt(s + falla), falla, patron.charAt(falla),
                        falla, falla - 1, q, falla, q, falla - q, q, q == 1 ? "carácter" : "caracteres")));
                s += falla - q;
            }
        }
        Map<String, Object> tablas = new TreeMap<>();
        tablas.put("prefijos", lista(pi));
        return new Recorrido("Knuth-Morris-Pratt", tablas, alineaciones, posiciones,
                comparaciones, 0);
    }

    static Recorrido boyerMoore(String texto, String patron) {
        int n = texto.length();
        int m = patron.length();
        BoyerMoore.TablaCaracterMalo malo = BoyerMoore.tablaCaracterMalo(patron);
        int[] sufijo = BoyerMoore.tablaSufijoBueno(patron);
        List<Alineacion> alineaciones = new ArrayList<>();
        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        int s = 0;
        while (m > 0 && s <= n - m) {
            List<Integer> comparadas = new ArrayList<>();
            int j = m - 1;
            while (j >= 0) {
                comparadas.add(j);
                comparaciones++;
                if (patron.charAt(j) != texto.charAt(s + j)) {
                    break;
                }
                j--;
            }
            if (j < 0) {
                posiciones.add(s);
                alineaciones.add(new Alineacion(s, 0, comparadas, -1, true, sufijo[0], -1, String.format(
                        "Ocurrencia en la posición %d. Avanza desplazamiento[0] = %d (sufijo bueno).", s, sufijo[0])));
                s += sufijo[0];
            } else {
                char c = texto.charAt(s + j);
                int porMalo = j - malo.ultima(c);
                int porSufijo = sufijo[j + 1];
                int avance = Math.max(porSufijo, porMalo);
                String regla = porMalo >= porSufijo ? "carácter malo" : "sufijo bueno";
                alineaciones.add(new Alineacion(s, 0, comparadas, j, false, avance, -1, String.format(
                        "'%c' ≠ P[%d] = '%c' tras %d coincidencias desde la derecha. Carácter malo: %d − última('%c') = "
                                + "%d − (%d) = %d; sufijo bueno: desplazamiento[%d] = %d. Avanza %d (%s).", c, j,
                        patron.charAt(j), m - 1 - j, j, c, j, malo.ultima(c), porMalo, j + 1, porSufijo, avance, regla)));
                s += avance;
            }
        }
        Map<Character, Integer> ultimas = new TreeMap<>();
        for (int i = 0; i < m; i++) {
            ultimas.put(patron.charAt(i), malo.ultima(patron.charAt(i)));
        }
        Map<String, Object> tablas = new TreeMap<>();
        List<Object> filas = new ArrayList<>();
        ultimas.forEach((c, u) -> filas.add(List.of(String.valueOf(c), u)));
        tablas.put("ultima", filas);
        tablas.put("sufijoBueno", lista(sufijo));
        return new Recorrido("Boyer-Moore", tablas, alineaciones, posiciones,
                comparaciones, 0);
    }

    static Recorrido rabinKarp(String texto, String patron, long primo) {
        RabinKarp rk = new RabinKarp(primo);
        int n = texto.length();
        int m = patron.length();
        long hashPatron = rk.hash(patron, m);
        long peso = rk.pesoMayor(m);
        List<Alineacion> alineaciones = new ArrayList<>();
        List<Integer> posiciones = new ArrayList<>();
        long comparaciones = 0;
        long colisiones = 0;
        if (m > 0 && m <= n) {
            long hashVentana = rk.hash(texto, m);
            for (int s = 0; s <= n - m; s++) {
                List<Integer> comparadas = new ArrayList<>();
                String explicacion;
                boolean ocurrencia = false;
                int falla = -1;
                if (hashVentana == hashPatron) {
                    int j = 0;
                    while (j < m) {
                        comparadas.add(j);
                        comparaciones++;
                        if (texto.charAt(s + j) != patron.charAt(j)) {
                            break;
                        }
                        j++;
                    }
                    if (j == m) {
                        posiciones.add(s);
                        ocurrencia = true;
                        explicacion = String.format("h = %d = h(P): se verifican los %d caracteres y coinciden. "
                                + "Ocurrencia en la posición %d.", hashVentana, m, s);
                    } else {
                        colisiones++;
                        falla = j;
                        explicacion = String.format("h = %d = h(P), pero P[%d] ≠ '%c': colisión espuria, el texto "
                                + "es distinto aunque el hash coincide.", hashVentana, j, texto.charAt(s + j));
                    }
                } else {
                    explicacion = String.format("h = %d ≠ h(P) = %d: la ventana se descarta sin comparar caracteres.",
                            hashVentana, hashPatron);
                }
                if (s < n - m) {
                    long siguiente = rk.rodar(hashVentana, texto.charAt(s), texto.charAt(s + m), peso);
                    explicacion += String.format(" Hash rodante: sale '%c', entra '%c' → %d.", texto.charAt(s),
                            texto.charAt(s + m), siguiente);
                    alineaciones.add(new Alineacion(s, 0, comparadas, falla, ocurrencia, 1, hashVentana, explicacion));
                    hashVentana = siguiente;
                } else {
                    alineaciones.add(new Alineacion(s, 0, comparadas, falla, ocurrencia, 0, hashVentana, explicacion));
                }
            }
        }
        Map<String, Object> tablas = new TreeMap<>();
        tablas.put("base", RabinKarp.BASE);
        tablas.put("primo", primo);
        tablas.put("pesoMayor", peso);
        tablas.put("hashPatron", hashPatron);
        return new Recorrido("Rabin-Karp", tablas, alineaciones, posiciones, comparaciones, colisiones);
    }

    private static List<Integer> lista(int[] valores) {
        List<Integer> l = new ArrayList<>();
        for (int v : valores) {
            l.add(v);
        }
        return l;
    }
}
