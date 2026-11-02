package co.edu.uniquindio.bibliometria.busqueda;

import java.util.ArrayList;
import java.util.List;

import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda.Resultado;
import co.edu.uniquindio.bibliometria.modelo.Articulo;

/**
 * Requerimiento 3: busca el patrón en los 20 abstracts con los tres algoritmos y comprueba que los
 * tres encuentren exactamente las mismas posiciones.
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

    private BusquedaCorpus() {
    }

    public static List<PorAlgoritmo> buscar(List<Articulo> articulos, String patron, boolean ignorarMayusculas) {
        String patronBuscado = ignorarMayusculas ? minusculas(patron) : patron;
        List<String> textos = new ArrayList<>();
        for (Articulo a : articulos) {
            textos.add(ignorarMayusculas ? minusculas(a.abstractTexto()) : a.abstractTexto());
        }
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

    /** Pasa el texto a minúsculas carácter por carácter (equivalente a ignorar mayúsculas). */
    public static String minusculas(String texto) {
        char[] caracteres = new char[texto.length()];
        for (int i = 0; i < texto.length(); i++) {
            caracteres[i] = Character.toLowerCase(texto.charAt(i));
        }
        return new String(caracteres);
    }
}
