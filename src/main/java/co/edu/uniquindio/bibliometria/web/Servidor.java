package co.edu.uniquindio.bibliometria.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico;
import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;
import co.edu.uniquindio.bibliometria.agrupamiento.AnalisisAgrupamiento;
import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda.Resultado;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus.EnArticulo;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus.PorAlgoritmo;
import co.edu.uniquindio.bibliometria.busqueda.PasoAPaso;
import co.edu.uniquindio.bibliometria.busqueda.RabinKarp;
import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.ranking.ContadorFrecuencias;
import co.edu.uniquindio.bibliometria.ranking.RankingTerminos;
import co.edu.uniquindio.bibliometria.ranking.Termino;
import co.edu.uniquindio.bibliometria.ranking.medicion.MedidorTiempos;
import co.edu.uniquindio.bibliometria.ranking.medicion.MedidorTiempos.Medicion;
import co.edu.uniquindio.bibliometria.ranking.ordenamiento.AlgoritmoOrdenamiento;
import co.edu.uniquindio.bibliometria.similitud.AlgoritmoSimilitud;
import co.edu.uniquindio.bibliometria.similitud.CatalogoSimilitud;
import co.edu.uniquindio.bibliometria.similitud.Levenshtein;
import co.edu.uniquindio.bibliometria.similitud.NeedlemanWunsch;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Servidor web de la aplicación, con el HttpServer que trae el JDK. Sirve la interfaz (carpeta
 * resources/web) y una API JSON:
 *   GET /api/articulos                                   corpus con título, autores y abstract
 *   GET /api/ranking                                     ranking de términos con Counting, Radix y Bucket Sort
 *   GET /api/similitud?articulos=1,5,9                   matrices de similitud de los seis algoritmos
 *   GET /api/agrupamiento?representacion=tfidf           dendrogramas y métricas de los tres criterios
 *   GET /api/busqueda?patron=learning&mayusculas=no      ocurrencias con KMP, Boyer-Moore y Rabin-Karp
 *   GET /api/busqueda-multiple?patrones=learning,analysis,assessment&mayusculas=si
 *                                                        de 2 a 10 patrones: Rabin-Karp multipatrón frente a
 *                                                        KMP, Boyer-Moore y Rabin-Karp una vez por patrón
 *   GET /api/pasos?patron=analysis&articulo=18&posicion=426&q=13
 *                                                        paso a paso de los tres algoritmos en una oración
 *   GET /api/matriz?algoritmo=levenshtein&a=11&b=16&n=12 matriz de programación dinámica y camino óptimo
 */
public final class Servidor {

    private static final int CONTEXTO_FRAGMENTO = 45;
    private static final int MAXIMO_PATRON = 200;
    private static final int MINIMO_PATRONES = 2;
    private static final int MAXIMO_PATRONES = 10;
    private static final int MATRIZ_MINIMO = 2;
    private static final int MATRIZ_MAXIMO = 20;

    private final CorpusProcesado corpus;
    private Map<String, Object> ranking;

    public Servidor(CorpusProcesado corpus) {
        this.corpus = corpus;
    }

    /** Arranca el servidor; con puerto 0 el sistema elige uno libre (lo usan las pruebas). */
    public HttpServer iniciar(int puerto) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/api/articulos", ex -> responder(ex, p -> articulos()));
        servidor.createContext("/api/ranking", ex -> responder(ex, p -> ranking()));
        servidor.createContext("/api/similitud", ex -> responder(ex, this::similitud));
        servidor.createContext("/api/agrupamiento", ex -> responder(ex, this::agrupamiento));
        servidor.createContext("/api/busqueda", ex -> responder(ex, this::busqueda));
        servidor.createContext("/api/busqueda-multiple", ex -> responder(ex, this::busquedaMultiple));
        servidor.createContext("/api/pasos", ex -> responder(ex, this::pasos));
        servidor.createContext("/api/matriz", ex -> responder(ex, this::matriz));
        servidor.createContext("/api/", ex -> responder(ex, p -> {
            throw new NoEncontrado("No existe la consulta " + ex.getRequestURI().getPath());
        }));
        servidor.createContext("/", this::recursoEstatico);
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.start();
        System.out.println("Aplicación disponible en http://localhost:" + servidor.getAddress().getPort());
        return servidor;
    }

    /** Consulta de la API que no existe (respuesta 404). */
    private static final class NoEncontrado extends RuntimeException {
        NoEncontrado(String mensaje) {
            super(mensaje);
        }
    }

    private Map<String, Object> articulos() {
        List<Object> lista = new ArrayList<>();
        for (Articulo a : corpus.articulos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", a.numero());
            m.put("titulo", a.titulo());
            m.put("autores", a.autores());
            m.put("autoresCortos", a.autoresCortos());
            m.put("anio", a.anio());
            m.put("palabras", a.abstractTexto().split("\\s+").length);
            m.put("abstract", a.abstractTexto());
            lista.add(m);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("articulos", lista);
        return respuesta;
    }

    /**
     * Ranking de términos (capítulo 7 del documento): lista de frecuencias, top 15 de los tres
     * ordenamientos no comparativos, verificación de que coinciden y tiempos medidos en este servidor.
     * Se calcula una sola vez, en la primera consulta.
     */
    private synchronized Map<String, Object> ranking() {
        if (ranking != null) {
            return ranking;
        }
        List<List<String>> tokens = new ArrayList<>();
        int palabras = 0;
        for (int i = 0; i < corpus.tamano(); i++) {
            tokens.add(corpus.tokens(i));
            palabras += corpus.articulos().get(i).abstractTexto().split("\\s+").length;
        }
        Termino[] frecuencias = ContadorFrecuencias.contar(tokens);
        int maxima = AlgoritmoOrdenamiento.frecuenciaMaxima(frecuencias);
        int minima = AlgoritmoOrdenamiento.frecuenciaMinima(frecuencias);
        int unaVez = 0;
        for (Termino t : frecuencias) {
            unaVez += t.frecuencia() == 1 ? 1 : 0;
        }
        Map<String, Object> cifras = new LinkedHashMap<>();
        cifras.put("articulos", corpus.tamano());
        cifras.put("palabras", palabras);
        cifras.put("terminos", tokens.stream().mapToInt(List::size).sum());
        cifras.put("distintos", frecuencias.length);
        cifras.put("maxima", maxima);
        cifras.put("minima", minima);
        cifras.put("k", maxima - minima + 1);
        cifras.put("unaVez", unaVez);

        List<Termino[]> ordenados = new ArrayList<>();
        for (AlgoritmoOrdenamiento algoritmo : RankingTerminos.ALGORITMOS) {
            ordenados.add(algoritmo.ordenar(frecuencias));
        }
        boolean iguales = ordenados.stream().allMatch(o -> Arrays.equals(o, ordenados.get(0)));
        List<Medicion> mediciones = MedidorTiempos.medir(RankingTerminos.ALGORITMOS, frecuencias, 1_000, 300);

        List<Object> algoritmos = new ArrayList<>();
        for (int a = 0; a < ordenados.size(); a++) {
            List<Object> top = new ArrayList<>();
            for (int i = 0; i < RankingTerminos.TOP; i++) {
                top.add(Map.of("termino", ordenados.get(a)[i].palabra(), "frecuencia", ordenados.get(a)[i].frecuencia()));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", RankingTerminos.ALGORITMOS.get(a).nombre());
            m.put("msMediana", mediciones.get(a).medianaMs());
            m.put("top", top);
            algoritmos.add(m);
        }
        int frontera = ordenados.get(0)[RankingTerminos.TOP - 1].frecuencia();
        List<Object> empatados = new ArrayList<>();
        for (int i = 0; i < ordenados.get(0).length; i++) {
            if (ordenados.get(0)[i].frecuencia() == frontera) {
                empatados.add(Map.of("termino", ordenados.get(0)[i].palabra(), "posicion", i + 1));
            }
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("cifras", cifras);
        respuesta.put("algoritmos", algoritmos);
        respuesta.put("iguales", iguales);
        respuesta.put("empate", Map.of("frecuencia", frontera, "terminos", empatados));
        respuesta.put("repeticiones", 300);
        ranking = respuesta;
        return ranking;
    }

    private Map<String, Object> similitud(Map<String, String> parametros) {
        Set<Integer> distintos = new LinkedHashSet<>();
        for (String parte : parametros.getOrDefault("articulos", "").split(",")) {
            if (!parte.isBlank()) {
                distintos.add(entero("articulos", parte));
            }
        }
        List<Integer> numeros = new ArrayList<>(distintos);
        if (numeros.size() < 2) {
            throw new IllegalArgumentException("Seleccione al menos dos artículos distintos (por ejemplo, articulos=11,16)");
        }
        int[] indices = numeros.stream().mapToInt(corpus::indice).toArray();
        List<Object> algoritmos = new ArrayList<>();
        for (AlgoritmoSimilitud algoritmo : CatalogoSimilitud.disponibles(corpus)) {
            double[][] matriz = new double[indices.length][indices.length];
            long inicio = System.nanoTime();
            for (int i = 0; i < indices.length; i++) {
                matriz[i][i] = 1.0;
                for (int j = i + 1; j < indices.length; j++) {
                    matriz[i][j] = algoritmo.similitud(corpus, indices[i], indices[j]);
                    matriz[j][i] = matriz[i][j];
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", algoritmo.nombre());
            m.put("enfoque", algoritmo.enfoque());
            m.put("ms", (System.nanoTime() - inicio) / 1e6);
            m.put("matriz", matriz);
            algoritmos.add(m);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("articulos", numeros);
        respuesta.put("algoritmos", algoritmos);
        return respuesta;
    }

    private Map<String, Object> agrupamiento(Map<String, String> parametros) {
        Map<String, String> representaciones = AnalisisAgrupamiento.representaciones(corpus);
        String representacion = parametros.getOrDefault("representacion", "tfidf");
        if (!representaciones.containsKey(representacion)) {
            throw new IllegalArgumentException("Representación desconocida: " + representacion);
        }
        List<Object> resultados = new ArrayList<>();
        String mejor = null;
        double mejorCcc = Double.NEGATIVE_INFINITY;
        for (AnalisisAgrupamiento.Resultado r : AnalisisAgrupamiento.analizar(corpus, representacion)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("criterio", r.criterio().nombre());
            m.put("ccc", r.correlacionCofenetica());
            Map<String, Object> silueta = new LinkedHashMap<>();
            for (int k = AnalisisAgrupamiento.GRUPOS_MINIMOS; k <= AnalisisAgrupamiento.GRUPOS_MAXIMOS; k++) {
                silueta.put(String.valueOf(k), r.siluetaPorK()[k]);
            }
            m.put("silueta", silueta);
            m.put("mejorK", r.mejorK());
            m.put("mejorSilueta", r.mejorSilueta());
            m.put("ms", r.nanos() / 1e6);
            List<Object> fusiones = new ArrayList<>();
            for (Fusion f : r.fusiones()) {
                fusiones.add(Map.of("a", f.grupoA(), "b", f.grupoB(), "distancia", f.distancia(), "tamano", f.tamano()));
            }
            m.put("fusiones", fusiones);
            m.put("grupos", grupos(r.fusiones(), r.mejorK()));
            m.put("svg", r.svg());
            resultados.add(m);
            if (r.correlacionCofenetica() > mejorCcc) {
                mejorCcc = r.correlacionCofenetica();
                mejor = r.criterio().nombre();
            }
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("representaciones", representaciones);
        respuesta.put("representacion", representacion);
        respuesta.put("resultados", resultados);
        respuesta.put("mejorCriterio", mejor);
        return respuesta;
    }

    /**
     * Grupos que resultan de cortar el dendrograma en k grupos, con el número de cada artículo, en el
     * orden de la etiqueta que les da el corte (el mismo que usa el dendrograma para los colores).
     */
    private List<Object> grupos(List<Fusion> fusiones, int k) {
        int[] etiquetas = AgrupamientoJerarquico.cortar(corpus.tamano(), fusiones, k);
        List<List<Integer>> grupos = new ArrayList<>();
        for (int g = 0; g < k; g++) {
            grupos.add(new ArrayList<>());
        }
        for (int i = 0; i < etiquetas.length; i++) {
            grupos.get(etiquetas[i]).add(corpus.articulos().get(i).numero());
        }
        return new ArrayList<>(grupos);
    }

    /**
     * Paso a paso de los tres algoritmos sobre la oración del abstract que contiene la posición dada
     * (una de las ocurrencias de la búsqueda). Con q = 13 el hash de Rabin-Karp produce colisiones.
     */
    private Map<String, Object> pasos(Map<String, String> parametros) {
        String patron = parametros.getOrDefault("patron", "");
        if (patron.isEmpty() || patron.length() > PasoAPaso.MAXIMO_PATRON) {
            throw new IllegalArgumentException("Para el paso a paso el patrón debe tener de 1 a "
                    + PasoAPaso.MAXIMO_PATRON + " caracteres");
        }
        int numero = entero(parametros, "articulo", 1);
        Articulo articulo = corpus.articulos().get(corpus.indice(numero));
        String abstractTexto = articulo.abstractTexto();
        int posicion = entero(parametros, "posicion", 0);
        if (posicion < 0 || posicion > abstractTexto.length()) {
            throw new IllegalArgumentException("La posición debe estar entre 0 y " + abstractTexto.length()
                    + ", la longitud del abstract del artículo " + numero);
        }
        String q = parametros.getOrDefault("q", "pequeno");
        if (!"pequeno".equals(q) && !"grande".equals(q)) {
            throw new IllegalArgumentException("El parámetro q debe ser pequeno (q = 13) o grande (q = 1.000.000.007)");
        }
        long primo = "grande".equals(q) ? RabinKarp.PRIMO : 13;
        int[] rango = PasoAPaso.oracion(abstractTexto, posicion, patron.length());
        String texto = abstractTexto.substring(rango[0], rango[1]);

        List<Object> recorridos = new ArrayList<>();
        for (PasoAPaso.Recorrido r : PasoAPaso.recorrer(texto, patron, primo)) {
            List<Object> alineaciones = new ArrayList<>();
            for (PasoAPaso.Alineacion a : r.alineaciones()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("s", a.s());
                m.put("conocidos", a.conocidos());
                m.put("comparadas", a.comparadas());
                m.put("falla", a.falla());
                m.put("ocurrencia", a.ocurrencia());
                m.put("desplazamiento", a.desplazamiento());
                m.put("hash", a.hash());
                m.put("explicacion", a.explicacion());
                alineaciones.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", r.algoritmo());
            m.put("tablas", r.tablas());
            m.put("alineaciones", alineaciones);
            m.put("posiciones", r.posiciones());
            m.put("comparaciones", r.comparaciones());
            m.put("colisiones", r.colisiones());
            recorridos.add(m);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("articulo", resumenArticulo(articulo));
        respuesta.put("desde", rango[0]);
        respuesta.put("texto", texto);
        respuesta.put("patron", patron.toLowerCase(Locale.ROOT));
        respuesta.put("q", primo);
        respuesta.put("algoritmos", recorridos);
        return respuesta;
    }

    /**
     * Matriz de programación dinámica entre dos abstracts y su camino óptimo, para la demostración paso
     * a paso de la pestaña de similitud: Levenshtein sobre los primeros n caracteres del texto
     * normalizado y Needleman-Wunsch sobre los primeros n términos, las mismas representaciones que usa
     * el cálculo de la similitud. Las matrices y los caminos son los de las clases de cada algoritmo.
     */
    private Map<String, Object> matriz(Map<String, String> parametros) {
        String algoritmo = parametros.getOrDefault("algoritmo", "levenshtein");
        boolean levenshtein = "levenshtein".equals(algoritmo);
        if (!levenshtein && !"needleman".equals(algoritmo)) {
            throw new IllegalArgumentException("Algoritmo desconocido: " + algoritmo + " (use levenshtein o needleman)");
        }
        int n = entero(parametros, "n", 12);
        if (n < MATRIZ_MINIMO || n > MATRIZ_MAXIMO) {
            throw new IllegalArgumentException("n debe estar entre " + MATRIZ_MINIMO + " y " + MATRIZ_MAXIMO);
        }
        int a = corpus.indice(entero(parametros, "a", 11));
        int b = corpus.indice(entero(parametros, "b", 16));

        List<String> filas = new ArrayList<>();
        List<String> columnas = new ArrayList<>();
        int[][] matriz;
        List<int[]> camino;
        Map<String, Object> costos = new LinkedHashMap<>();
        if (levenshtein) {
            String textoA = corpus.textoNormalizado(a).substring(0, Math.min(n, corpus.textoNormalizado(a).length()));
            String textoB = corpus.textoNormalizado(b).substring(0, Math.min(n, corpus.textoNormalizado(b).length()));
            for (int i = 0; i < textoA.length(); i++) {
                filas.add(String.valueOf(textoA.charAt(i)));
            }
            for (int j = 0; j < textoB.length(); j++) {
                columnas.add(String.valueOf(textoB.charAt(j)));
            }
            matriz = Levenshtein.matriz(textoA, textoB);
            camino = Levenshtein.camino(textoA, textoB, matriz);
            costos.put("coincidencia", 0);
            costos.put("sustitucion", 1);
            costos.put("insercion", 1);
            costos.put("eliminacion", 1);
        } else {
            filas.addAll(corpus.tokens(a).subList(0, Math.min(n, corpus.tokens(a).size())));
            columnas.addAll(corpus.tokens(b).subList(0, Math.min(n, corpus.tokens(b).size())));
            matriz = NeedlemanWunsch.matriz(filas, columnas);
            camino = NeedlemanWunsch.camino(filas, columnas, matriz);
            costos.put("coincidencia", NeedlemanWunsch.COINCIDENCIA);
            costos.put("sustitucion", NeedlemanWunsch.SUSTITUCION);
            costos.put("hueco", NeedlemanWunsch.HUECO);
        }

        List<Object> valores = new ArrayList<>();
        for (int[] fila : matriz) {
            List<Integer> l = new ArrayList<>();
            for (int v : fila) {
                l.add(v);
            }
            valores.add(l);
        }
        List<Object> pasos = new ArrayList<>();
        for (int[] celda : camino) {
            pasos.add(List.of(celda[0], celda[1]));
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("algoritmo", levenshtein ? "Levenshtein" : "Needleman-Wunsch");
        respuesta.put("clave", algoritmo);
        respuesta.put("optimo", levenshtein ? "min" : "max");
        respuesta.put("unidad", levenshtein ? "caracteres" : "términos");
        respuesta.put("costos", costos);
        respuesta.put("a", resumenArticulo(corpus.articulos().get(a)));
        respuesta.put("b", resumenArticulo(corpus.articulos().get(b)));
        respuesta.put("filas", filas);
        respuesta.put("columnas", columnas);
        respuesta.put("matriz", valores);
        respuesta.put("camino", pasos);
        respuesta.put("resultado", matriz[filas.size()][columnas.size()]);
        return respuesta;
    }

    private static Map<String, Object> resumenArticulo(Articulo articulo) {
        return Map.of("numero", articulo.numero(), "titulo", articulo.titulo(),
                "autoresCortos", articulo.autoresCortos(), "anio", articulo.anio());
    }

    private static int entero(Map<String, String> parametros, String nombre, int porDefecto) {
        return entero(nombre, parametros.getOrDefault(nombre, String.valueOf(porDefecto)));
    }

    private static int entero(String nombre, String valor) {
        try {
            return Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El parámetro " + nombre + " debe ser un número entero (se recibió «"
                    + valor + "»)");
        }
    }

    /** Parámetro mayusculas: si (ignorar mayúsculas, por defecto) o no. */
    private static boolean ignorarMayusculas(Map<String, String> parametros) {
        String valor = parametros.getOrDefault("mayusculas", "si");
        if (!"si".equals(valor) && !"no".equals(valor)) {
            throw new IllegalArgumentException("El parámetro mayusculas debe ser si o no");
        }
        return "si".equals(valor);
    }

    private Map<String, Object> busqueda(Map<String, String> parametros) {
        String patron = parametros.getOrDefault("patron", "");
        if (patron.isEmpty() || patron.length() > MAXIMO_PATRON) {
            throw new IllegalArgumentException("Escriba un patrón de 1 a " + MAXIMO_PATRON + " caracteres");
        }
        boolean ignorar = ignorarMayusculas(parametros);
        List<PorAlgoritmo> resultados = BusquedaCorpus.buscar(corpus.articulos(), patron, ignorar);

        List<Object> algoritmos = new ArrayList<>();
        for (PorAlgoritmo r : resultados) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", r.algoritmo());
            m.put("ocurrencias", r.ocurrencias());
            m.put("comparaciones", r.comparaciones());
            m.put("ventanas", r.ventanas());
            m.put("colisiones", r.colisiones());
            m.put("msPreproceso", r.nanosPreproceso() / 1e6);
            m.put("msBusqueda", r.nanosBusqueda() / 1e6);
            algoritmos.add(m);
        }
        boolean coinciden = true;
        for (PorAlgoritmo r : resultados) {
            for (int i = 0; i < r.articulos().size(); i++) {
                coinciden &= r.articulos().get(i).resultado().posiciones()
                        .equals(resultados.get(0).articulos().get(i).resultado().posiciones());
            }
        }

        List<Object> articulos = new ArrayList<>();
        for (EnArticulo enArticulo : resultados.get(0).articulos()) {
            Resultado r = enArticulo.resultado();
            if (r.posiciones().isEmpty()) {
                continue;
            }
            Articulo articulo = corpus.articulos().get(corpus.indice(enArticulo.numeroArticulo()));
            String texto = articulo.abstractTexto();
            List<Object> fragmentos = new ArrayList<>();
            for (int posicion : r.posiciones()) {
                fragmentos.add(fragmento(texto, posicion, patron.length()));
            }
            Map<String, Object> m = new LinkedHashMap<>(resumenArticulo(articulo));
            m.put("fragmentos", fragmentos);
            articulos.add(m);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("patron", patron);
        respuesta.put("ignorarMayusculas", ignorar);
        respuesta.put("algoritmos", algoritmos);
        respuesta.put("coinciden", coinciden);
        respuesta.put("articulos", articulos);
        return respuesta;
    }

    /**
     * Varios patrones a la vez: Rabin-Karp multipatrón (una pasada por longitud distinta) frente a KMP,
     * Boyer-Moore y Rabin-Karp ejecutados una vez por patrón, y las ocurrencias de cada patrón.
     */
    private Map<String, Object> busquedaMultiple(Map<String, String> parametros) {
        boolean ignorar = ignorarMayusculas(parametros);
        Set<String> patrones = new LinkedHashSet<>();
        for (String parte : parametros.getOrDefault("patrones", "").split(",")) {
            String patron = parte.strip();
            if (patron.length() > MAXIMO_PATRON) {
                throw new IllegalArgumentException("Cada patrón debe tener como máximo " + MAXIMO_PATRON + " caracteres");
            }
            if (!patron.isEmpty()) {
                patrones.add(ignorar ? BusquedaCorpus.minusculas(patron) : patron);
            }
        }
        if (patrones.size() < MINIMO_PATRONES || patrones.size() > MAXIMO_PATRONES) {
            throw new IllegalArgumentException("Escriba de " + MINIMO_PATRONES + " a " + MAXIMO_PATRONES
                    + " patrones distintos separados por comas");
        }
        BusquedaCorpus.BusquedaMultiple r =
                BusquedaCorpus.buscarVarios(corpus.articulos(), new ArrayList<>(patrones), ignorar);

        List<Object> algoritmos = new ArrayList<>();
        for (BusquedaCorpus.FilaMultiple f : r.algoritmos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", f.algoritmo());
            m.put("pasadas", f.pasadas());
            m.put("comparaciones", f.comparaciones());
            m.put("ventanas", f.ventanas());
            m.put("colisiones", f.colisiones());
            m.put("msPreproceso", f.nanosPreproceso() / 1e6);
            m.put("msBusqueda", f.nanosBusqueda() / 1e6);
            algoritmos.add(m);
        }

        List<Object> porPatron = new ArrayList<>();
        for (String patron : r.patrones()) {
            int ocurrencias = 0;
            int conOcurrencias = 0;
            for (Map<String, List<Integer>> posiciones : r.posicionesPorArticulo()) {
                ocurrencias += posiciones.get(patron).size();
                conOcurrencias += posiciones.get(patron).isEmpty() ? 0 : 1;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("patron", patron);
            m.put("ocurrencias", ocurrencias);
            m.put("articulos", conOcurrencias);
            m.put("coinciden", r.coinciden().get(patron));
            porPatron.add(m);
        }

        List<Object> articulos = new ArrayList<>();
        for (int i = 0; i < r.posicionesPorArticulo().size(); i++) {
            Articulo articulo = corpus.articulos().get(i);
            String texto = articulo.abstractTexto();
            List<Map<String, Object>> fragmentos = new ArrayList<>();
            for (Map.Entry<String, List<Integer>> e : r.posicionesPorArticulo().get(i).entrySet()) {
                for (int posicion : e.getValue()) {
                    Map<String, Object> f = new LinkedHashMap<>(fragmento(texto, posicion, e.getKey().length()));
                    f.put("patron", e.getKey());
                    fragmentos.add(f);
                }
            }
            if (fragmentos.isEmpty()) {
                continue;
            }
            fragmentos.sort((x, y) -> Integer.compare((int) x.get("posicion"), (int) y.get("posicion")));
            Map<String, Object> m = new LinkedHashMap<>(resumenArticulo(articulo));
            m.put("fragmentos", fragmentos);
            articulos.add(m);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("patrones", r.patrones());
        respuesta.put("ignorarMayusculas", ignorar);
        respuesta.put("algoritmos", algoritmos);
        respuesta.put("coinciden", !r.coinciden().containsValue(false));
        respuesta.put("porPatron", porPatron);
        respuesta.put("articulos", articulos);
        return respuesta;
    }

    /** Ocurrencia con unos caracteres de contexto a cada lado, para mostrarla resaltada. */
    private static Map<String, Object> fragmento(String texto, int posicion, int largo) {
        int desde = Math.max(0, posicion - CONTEXTO_FRAGMENTO);
        int hasta = Math.min(texto.length(), posicion + largo + CONTEXTO_FRAGMENTO);
        return Map.of("posicion", posicion,
                "antes", (desde > 0 ? "…" : "") + texto.substring(desde, posicion),
                "coincidencia", texto.substring(posicion, posicion + largo),
                "despues", texto.substring(posicion + largo, hasta) + (hasta < texto.length() ? "…" : ""));
    }

    /**
     * Atiende una consulta de la API. Los errores se devuelven como {"error": ...}: 400 si un parámetro
     * no es válido, 404 si la consulta no existe, 405 si el método no es GET y 500 solo ante un error
     * interno inesperado, que además se registra en la consola del servidor.
     */
    private void responder(HttpExchange intercambio, Function<Map<String, String>, Object> atender) throws IOException {
        int codigo = 200;
        Object cuerpo;
        try {
            if (!"GET".equals(intercambio.getRequestMethod())) {
                codigo = 405;
                intercambio.getResponseHeaders().set("Allow", "GET");
                cuerpo = Map.of("error", "La API solo acepta consultas GET");
            } else {
                cuerpo = atender.apply(parametros(intercambio));
            }
        } catch (NoEncontrado e) {
            codigo = 404;
            cuerpo = Map.of("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            codigo = 400;
            cuerpo = Map.of("error", e.getMessage() == null ? "Parámetros no válidos" : e.getMessage());
        } catch (RuntimeException e) {
            codigo = 500;
            System.err.println("Error interno en " + intercambio.getRequestURI() + ": " + e);
            cuerpo = Map.of("error", "Error interno del servidor: " + (e.getMessage() == null ? e : e.getMessage()));
        }
        enviar(intercambio, codigo, "application/json; charset=utf-8", Json.de(cuerpo).getBytes(StandardCharsets.UTF_8));
    }

    private void recursoEstatico(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        if (ruta.equals("/")) {
            ruta = "/index.html";
        }
        if (ruta.contains("..")) {
            enviar(intercambio, 400, "text/plain; charset=utf-8", "Ruta no válida".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try (InputStream recurso = Servidor.class.getResourceAsStream("/web" + ruta)) {
            if (recurso == null) {
                enviar(intercambio, 404, "text/plain; charset=utf-8", "No encontrado".getBytes(StandardCharsets.UTF_8));
                return;
            }
            enviar(intercambio, 200, tipo(ruta), recurso.readAllBytes());
        }
    }

    private static String tipo(String ruta) {
        if (ruta.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (ruta.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (ruta.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (ruta.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private static void enviar(HttpExchange intercambio, int codigo, String tipo, byte[] cuerpo) throws IOException {
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(cuerpo);
        }
    }

    private static Map<String, String> parametros(HttpExchange intercambio) {
        Map<String, String> parametros = new LinkedHashMap<>();
        String consulta = intercambio.getRequestURI().getRawQuery();
        if (consulta == null) {
            return parametros;
        }
        for (String par : consulta.split("&")) {
            String[] partes = par.split("=", 2);
            String valor = partes.length > 1 ? partes[1] : "";
            try {
                parametros.put(URLDecoder.decode(partes[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(valor, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("La consulta tiene caracteres mal codificados: " + par);
            }
        }
        return parametros;
    }
}
