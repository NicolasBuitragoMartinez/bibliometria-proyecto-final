package co.edu.uniquindio.bibliometria.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import co.edu.uniquindio.bibliometria.agrupamiento.AgrupamientoJerarquico.Fusion;
import co.edu.uniquindio.bibliometria.agrupamiento.AnalisisAgrupamiento;
import co.edu.uniquindio.bibliometria.busqueda.AlgoritmoBusqueda.Resultado;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus.EnArticulo;
import co.edu.uniquindio.bibliometria.busqueda.BusquedaCorpus.PorAlgoritmo;
import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.similitud.AlgoritmoSimilitud;
import co.edu.uniquindio.bibliometria.similitud.CatalogoSimilitud;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * Servidor web de la aplicación, con el HttpServer que trae el JDK. Sirve la interfaz (carpeta
 * resources/web) y una API JSON:
 *   GET /api/articulos                                   corpus con título, autores y abstract
 *   GET /api/similitud?articulos=1,5,9                   matrices de similitud de los seis algoritmos
 *   GET /api/agrupamiento?representacion=tfidf           dendrogramas y métricas de los tres criterios
 *   GET /api/busqueda?patron=learning&mayusculas=no      ocurrencias con KMP, Boyer-Moore y Rabin-Karp
 */
public final class Servidor {

    private static final int CONTEXTO_FRAGMENTO = 45;
    private static final int MAXIMO_PATRON = 200;

    private final CorpusProcesado corpus;

    public Servidor(CorpusProcesado corpus) {
        this.corpus = corpus;
    }

    public void iniciar(int puerto) throws IOException {
        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/api/articulos", ex -> responder(ex, p -> articulos()));
        servidor.createContext("/api/similitud", ex -> responder(ex, this::similitud));
        servidor.createContext("/api/agrupamiento", ex -> responder(ex, this::agrupamiento));
        servidor.createContext("/api/busqueda", ex -> responder(ex, this::busqueda));
        servidor.createContext("/", this::recursoEstatico);
        servidor.setExecutor(Executors.newFixedThreadPool(4));
        servidor.start();
        System.out.println("Aplicación disponible en http://localhost:" + puerto);
    }

    private Map<String, Object> articulos() {
        List<Object> lista = new ArrayList<>();
        for (Articulo a : corpus.articulos()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", a.numero());
            m.put("titulo", a.titulo());
            m.put("autores", a.autores());
            m.put("autoresCortos", a.autoresCortos());
            m.put("palabras", a.abstractTexto().split("\\s+").length);
            m.put("abstract", a.abstractTexto());
            lista.add(m);
        }
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("articulos", lista);
        return respuesta;
    }

    private Map<String, Object> similitud(Map<String, String> parametros) {
        List<Integer> numeros = new ArrayList<>();
        for (String parte : parametros.getOrDefault("articulos", "").split(",")) {
            if (!parte.isBlank()) {
                numeros.add(Integer.parseInt(parte.strip()));
            }
        }
        if (numeros.size() < 2) {
            throw new IllegalArgumentException("Seleccione al menos dos artículos");
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

    private Map<String, Object> busqueda(Map<String, String> parametros) {
        String patron = parametros.getOrDefault("patron", "");
        if (patron.isEmpty() || patron.length() > MAXIMO_PATRON) {
            throw new IllegalArgumentException("Escriba un patrón de 1 a " + MAXIMO_PATRON + " caracteres");
        }
        boolean ignorar = !"no".equals(parametros.get("mayusculas"));
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
                int desde = Math.max(0, posicion - CONTEXTO_FRAGMENTO);
                int hasta = Math.min(texto.length(), posicion + patron.length() + CONTEXTO_FRAGMENTO);
                fragmentos.add(Map.of("posicion", posicion,
                        "antes", (desde > 0 ? "…" : "") + texto.substring(desde, posicion),
                        "coincidencia", texto.substring(posicion, posicion + patron.length()),
                        "despues", texto.substring(posicion + patron.length(), hasta) + (hasta < texto.length() ? "…" : "")));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", articulo.numero());
            m.put("titulo", articulo.titulo());
            m.put("autoresCortos", articulo.autoresCortos());
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

    /** Atiende una consulta de la API; los errores de la petición se devuelven como {"error": ...}. */
    private void responder(HttpExchange intercambio, Function<Map<String, String>, Object> atender) throws IOException {
        int codigo = 200;
        Object cuerpo;
        try {
            cuerpo = atender.apply(parametros(intercambio));
        } catch (RuntimeException e) {
            codigo = e instanceof IllegalArgumentException ? 400 : 500;
            cuerpo = Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage());
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
            parametros.put(URLDecoder.decode(partes[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(valor, StandardCharsets.UTF_8));
        }
        return parametros;
    }
}
