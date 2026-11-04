package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda.Resultado;
import co.edu.uniquindio.bibliometria.modelo.Articulo;

/**
 * Requerimiento 3: busca el patrón en los 20 abstracts con los tres algoritmos y comprueba que los
 * tres encuentren exactamente las mismas posiciones; buscarVarios compara Rabin-Karp multipatrón
 * con los algoritmos de un patrón ejecutados una vez por patrón.
 */
public final class BusquedaCorpus {

    public static final List<AlgoritmoBusqueda> ALGORITMOS =
            List.of(new KnuthMorrisPratt(), new BoyerMoore(), new RabinKarp());

    /** Ocurrencias de un algoritmo en un artículo. */
    public record EnArticulo(int numeroArticulo, Resultado resultado) {
    }

    /** Resultado de un algoritmo sobre todo el corpus, con los totales. */
    public record PorAlgoritmo(String algoritmo, List<EnArticulo> articulos, int ocurrencias, long comparaciones,
            long ventanas, long colisiones, long nanosPreproceso, long nanosBusqueda) {
    }

    /** Totales de un algoritmo al buscar varios patrones en todo el corpus. */
    public record FilaMultiple(String algoritmo, int pasadas, long comparaciones, long ventanas, long colisiones,
            long nanosPreproceso, long nanosBusqueda) {
    }

    /**
     * Búsqueda de varios patrones: los patrones distintos que se buscaron, los totales de Rabin-Karp
     * multipatrón (primera fila) y de cada algoritmo de un patrón ejecutado una vez por patrón, las
     * posiciones de cada patrón en cada artículo y, por patrón, si todos encontraron las mismas.
     */
    public record BusquedaMultiple(List<String> patrones, List<FilaMultiple> algoritmos,
            List<Map<String, List<Integer>>> posicionesPorArticulo, Map<String, Boolean> coinciden) {
    }

    private BusquedaCorpus() {
    }

    public static List<PorAlgoritmo> buscar(List<Articulo> articulos, String patron, boolean ignorarMayusculas) {
        String patronBuscado = ignorarMayusculas ? minusculas(patron) : patron;
        List<String> textos = textos(articulos, ignorarMayusculas);
        List<PorAlgoritmo> resultados = new ArrayList<>();
        for (AlgoritmoBusqueda algoritmo : ALGORITMOS) {
            List<EnArticulo> porArticulo = new ArrayList<>();
            int ocurrencias = 0;
            long comparaciones = 0;
            long ventanas = 0;
            long colisiones = 0;
            long preproceso = 0;
            long busqueda = 0;
            for (int i = 0; i < articulos.size(); i++) {
                Resultado r = algoritmo.buscar(textos.get(i), patronBuscado);
                porArticulo.add(new EnArticulo(articulos.get(i).numero(), r));
                ocurrencias += r.posiciones().size();
                comparaciones += r.comparaciones();
                ventanas += r.ventanas();
                colisiones += r.colisiones();
                preproceso += r.nanosPreproceso();
                busqueda += r.nanosBusqueda();
            }
            resultados.add(new PorAlgoritmo(algoritmo.nombre(), porArticulo, ocurrencias, comparaciones, ventanas,
                    colisiones, preproceso, busqueda));
        }
        return resultados;
    }

    /**
     * Busca varios patrones en los abstracts: Rabin-Karp multipatrón recorre cada texto una vez por
     * longitud distinta, mientras KMP, Boyer-Moore y Rabin-Karp se ejecutan una vez por patrón. Los
     * patrones repetidos (también los que solo difieren en mayúsculas, si se ignoran) se buscan una vez.
     */
    public static BusquedaMultiple buscarVarios(List<Articulo> articulos, List<String> patrones,
            boolean ignorarMayusculas) {
        Set<String> unicos = new LinkedHashSet<>();
        for (String patron : patrones) {
            unicos.add(ignorarMayusculas ? minusculas(patron) : patron);
        }
        List<String> distintos = new ArrayList<>(unicos);
        List<String> textos = textos(articulos, ignorarMayusculas);
        RabinKarpMultiple multiple = new RabinKarpMultiple();
        long[][] totales = new long[ALGORITMOS.size() + 1][6];
        List<Map<String, List<Integer>>> porArticulo = new ArrayList<>();
        Map<String, Boolean> coinciden = new LinkedHashMap<>();
        distintos.forEach(p -> coinciden.put(p, true));
        for (String texto : textos) {
            RabinKarpMultiple.Resultado r = multiple.buscar(texto, distintos);
            acumular(totales[0], r.pasadas(), r.comparaciones(), r.ventanas(), r.colisiones(), r.nanosPreproceso(),
                    r.nanosBusqueda());
            porArticulo.add(r.posiciones());
            for (int a = 0; a < ALGORITMOS.size(); a++) {
                for (String patron : distintos) {
                    Resultado unico = ALGORITMOS.get(a).buscar(texto, patron);
                    acumular(totales[a + 1], unico.ventanas() > 0 ? 1 : 0, unico.comparaciones(), unico.ventanas(),
                            unico.colisiones(), unico.nanosPreproceso(), unico.nanosBusqueda());
                    if (!unico.posiciones().equals(r.posiciones().get(patron))) {
                        coinciden.put(patron, false);
                    }
                }
            }
        }
        List<FilaMultiple> filas = new ArrayList<>();
        filas.add(fila(multiple.nombre(), totales[0], textos.size()));
        for (int a = 0; a < ALGORITMOS.size(); a++) {
            filas.add(fila(ALGORITMOS.get(a).nombre(), totales[a + 1], textos.size()));
        }
        return new BusquedaMultiple(distintos, filas, porArticulo, coinciden);
    }

    private static void acumular(long[] total, long... valores) {
        for (int i = 0; i < valores.length; i++) {
            total[i] += valores[i];
        }
    }

    /** Las pasadas se acumulan por artículo; se reportan por texto (la cuenta de un abstract típico). */
    private static FilaMultiple fila(String nombre, long[] t, int textos) {
        return new FilaMultiple(nombre, (int) Math.round((double) t[0] / textos), t[1], t[2], t[3], t[4], t[5]);
    }

    private static List<String> textos(List<Articulo> articulos, boolean ignorarMayusculas) {
        List<String> textos = new ArrayList<>();
        for (Articulo a : articulos) {
            textos.add(ignorarMayusculas ? minusculas(a.abstractTexto()) : a.abstractTexto());
        }
        return textos;
    }

    /** Pasa el texto a minúsculas carácter por carácter (equivalente a ignorar mayúsculas). */
    public static String minusculas(String texto) {
        char[] caracteres = new char[texto.length()];
        for (int i = 0; i < texto.length(); i++) {
            caracteres[i] = Character.toLowerCase(texto.charAt(i));
        }
        return new String(caracteres);
    }
}
