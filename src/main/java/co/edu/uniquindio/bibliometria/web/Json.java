package co.edu.uniquindio.bibliometria.web;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * Serializador JSON mínimo para las respuestas de la API (cadenas, números, booleanos, listas,
 * arreglos y mapas). Evita depender de una biblioteca externa para algo tan pequeño.
 */
public final class Json {

    private Json() {
    }

    public static String de(Object valor) {
        StringBuilder sb = new StringBuilder();
        escribir(sb, valor);
        return sb.toString();
    }

    private static void escribir(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            cadena(sb, s);
        } else if (valor instanceof Double d) {
            sb.append(Double.isFinite(d) ? String.format(Locale.ROOT, "%.6g", d) : "null");
        } else if (valor instanceof Float f) {
            escribir(sb, (double) f);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primero = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primero) {
                    sb.append(',');
                }
                cadena(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escribir(sb, e.getValue());
                primero = false;
            }
            sb.append('}');
        } else if (valor instanceof Collection<?> lista) {
            sb.append('[');
            boolean primero = true;
            for (Object elemento : lista) {
                if (!primero) {
                    sb.append(',');
                }
                escribir(sb, elemento);
                primero = false;
            }
            sb.append(']');
        } else if (valor instanceof double[] arreglo) {
            sb.append('[');
            for (int i = 0; i < arreglo.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                escribir(sb, arreglo[i]);
            }
            sb.append(']');
        } else if (valor instanceof double[][] matriz) {
            sb.append('[');
            for (int i = 0; i < matriz.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                escribir(sb, matriz[i]);
            }
            sb.append(']');
        } else {
            cadena(sb, valor.toString());
        }
    }

    private static void cadena(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
