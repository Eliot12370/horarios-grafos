package com.grafos.horarios.model;

/**
 * Representa un intervalo semiabierto {dia} x [inicioMin, finMin) en T = [0, 1440).
 * Resuelve formalmente los bordes adyacentes: [8:00, 10:00) y [10:00, 12:00) no se cruzan.
 */
public record BloqueHorario(int dia, int inicioMin, int finMin) {

    public BloqueHorario {
        if (dia < 1 || dia > 7) {
            throw new IllegalArgumentException("El día debe pertenecer a D = {1..7}");
        }
        if (inicioMin < 0 || finMin > 1440 || inicioMin >= finMin) {
            throw new IllegalArgumentException("Intervalo inválido en T = [0, 1440)");
        }
    }

    /**
     * Evalúa si dos intervalos semiabiertos tienen intersección no vacía.
     * [a, b) ∩ [c, d) != ∅  <=>  a < d && c < b
     */
    public boolean seCruzaCon(BloqueHorario otro) {
        if (this.dia != otro.dia) {
            return false;
        }
        return this.inicioMin < otro.finMin && otro.inicioMin < this.finMin;
    }

    /**
     * Calcula los minutos muertos entre dos bloques del mismo día sin cruce.
     */
    public int minutosMuertosCon(BloqueHorario otro) {
        if (this.dia != otro.dia || this.seCruzaCon(otro)) {
            return 0;
        }
        if (this.finMin <= otro.inicioMin) {
            return otro.inicioMin - this.finMin;
        }
        return this.inicioMin - otro.finMin;
    }
}