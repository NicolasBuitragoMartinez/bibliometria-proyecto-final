package co.edu.uniquindio.bibliometria.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import co.edu.uniquindio.bibliometria.modelo.Articulo;
import co.edu.uniquindio.bibliometria.texto.CorpusProcesado;

/**
 * La API responde 400 con un mensaje claro ante parámetros inválidos (nunca 500), 404 si la consulta
 * no existe y 405 si el método no es GET. Usa un corpus de tres artículos y un puerto libre.
 */
class ServidorTest {

    private static HttpServer servidor;
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();

    @BeforeAll
    static void iniciar() throws IOException {
        List<Articulo> articulos = List.of(
                new Articulo(1, "1.pdf", "Uno", List.of("Ana Uno"), 2024,
                        "Learning analytics support learning and assessment in higher education."),
                new Articulo(2, "2.pdf", "Dos", List.of("Beto Dos"), 2023,
                        "Generative artificial intelligence changes assessment and learning."),
                new Articulo(3, "3.pdf", "Tres", List.of("Carla Tres"), 2022,
                        "A content analysis of research on teachers and students."));
        servidor = new Servidor(new CorpusProcesado(articulos, Map.of())).iniciar(0);
    }

    @AfterAll
    static void detener() {
        servidor.stop(0);
    }

    private static HttpResponse<String> consultar(String metodo, String ruta) throws Exception {
        URI uri = URI.create("http://localhost:" + servidor.getAddress().getPort() + ruta);
        HttpRequest peticion = HttpRequest.newBuilder(uri).method(metodo, HttpRequest.BodyPublishers.noBody()).build();
        return CLIENTE.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void parametrosInvalidosResponden400ConMensaje() throws Exception {
        Map<String, String> casos = Map.ofEntries(
                Map.entry("/api/similitud?articulos=1,abc", "número entero"),
                Map.entry("/api/similitud?articulos=1,1", "dos artículos distintos"),
                Map.entry("/api/similitud?articulos=1,99", "No existe el artículo 99"),
                Map.entry("/api/busqueda", "patrón de 1 a"),
                Map.entry("/api/busqueda?patron=ai&mayusculas=quiza", "mayusculas debe ser si o no"),
                Map.entry("/api/busqueda-multiple?patrones=learning", "de 2 a 10 patrones"),
                Map.entry("/api/busqueda-multiple?patrones=learning,LEARNING", "de 2 a 10 patrones"),
                Map.entry("/api/busqueda-multiple?patrones=a,b,c,d,e,f,g,h,i,j,k", "de 2 a 10 patrones"),
                Map.entry("/api/pasos?patron=learning&articulo=1&posicion=-1", "posición debe estar entre 0"),
                Map.entry("/api/pasos?patron=learning&articulo=1&posicion=0&q=raro", "q debe ser pequeno"),
                Map.entry("/api/pasos?patron=&articulo=1", "de 1 a"),
                Map.entry("/api/matriz?a=1&b=2&n=1", "n debe estar entre 2 y 20"),
                Map.entry("/api/matriz?algoritmo=otro&a=1&b=2", "Algoritmo desconocido"),
                Map.entry("/api/matriz?a=x", "número entero"),
                Map.entry("/api/agrupamiento?representacion=otra", "Representación desconocida"));
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            HttpResponse<String> r = consultar("GET", caso.getKey());
            assertEquals(400, r.statusCode(), caso.getKey() + " -> " + r.body());
            assertTrue(r.body().startsWith("{\"error\":"), caso.getKey());
            assertTrue(r.body().contains(caso.getValue()), caso.getKey() + " -> " + r.body());
        }
    }

    @Test
    void consultaInexistenteYMetodoNoPermitido() throws Exception {
        HttpResponse<String> r = consultar("GET", "/api/no-existe");
        assertEquals(404, r.statusCode());
        assertTrue(r.body().contains("No existe la consulta"));
        r = consultar("POST", "/api/busqueda?patron=ai");
        assertEquals(405, r.statusCode());
        assertEquals("GET", r.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void consultasValidasResponden200() throws Exception {
        HttpResponse<String> r = consultar("GET", "/api/busqueda-multiple?patrones=learning,%20assessment,,quantum");
        assertEquals(200, r.statusCode(), r.body());
        assertTrue(r.body().contains("\"patrones\":[\"learning\",\"assessment\",\"quantum\"]"), r.body());
        assertTrue(r.body().contains("\"coinciden\":true"), r.body());
        assertEquals(200, consultar("GET", "/api/similitud?articulos=1,2,2,3").statusCode());
        assertEquals(200, consultar("GET", "/api/matriz?algoritmo=needleman&a=1&b=2&n=5").statusCode());
        assertEquals(200, consultar("GET", "/api/pasos?patron=learning&articulo=1&posicion=0&q=grande").statusCode());
    }
}
