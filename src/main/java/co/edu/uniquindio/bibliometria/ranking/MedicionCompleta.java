package co.edu.uniquindio.bibliometria.ranking;

/**
 * Atajo para ejecutar desde el IDE la medición completa del ranking (capítulo 8 del documento):
 * 30 corridas en JVM nuevas para los tiempos, 20 del experimento de escalabilidad y 20 del de
 * jerarquía de memoria. Equivale a RankingTerminos corpus --corridas 30 --escalabilidad --memoria.
 */
public final class MedicionCompleta {

    private MedicionCompleta() {
    }

    public static void main(String[] args) throws Exception {
        RankingTerminos.main(new String[] {"corpus", "--corridas", "30", "--escalabilidad", "--memoria"});
    }
}
