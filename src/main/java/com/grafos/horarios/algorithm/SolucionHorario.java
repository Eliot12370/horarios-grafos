package com.grafos.horarios.algorithm;

import com.grafos.horarios.model.BloqueHorario;
import com.grafos.horarios.model.Seccion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Representa un subgrafo completo (clique transversal) K ⊆ G
 * con su función de aptitud F(K) y desempate por horas muertas F*(K).
 */
public class SolucionHorario implements Comparable<SolucionHorario> {
    private static final double EPSILON = 1e-6; // Factor ε << 1 (Cap. 6.1 Q3)

    private final List<Seccion> secciones;
    private final double aptitudBase;      // F(K) = ∑ w(v)
    private final int minutosMuertos;      // Hm(K)
    private final double aptitudExtendida; // F*(K) = F(K) - ε * Hm(K)

    public SolucionHorario(List<Seccion> secciones) {
        this.secciones = List.copyOf(secciones);
        this.aptitudBase = secciones.stream()
                .mapToDouble(Seccion::getPesoNormalizado)
                .sum();
        this.minutosMuertos = calcularMinutosMuertos(secciones);
        this.aptitudExtendida = this.aptitudBase - (EPSILON * this.minutosMuertos);
    }

    /**
     * Calcula Hm(K): suma de minutos libres entre bloques consecutivos del mismo día.
     */
    private int calcularMinutosMuertos(List<Seccion> clique) {
        int totalMuertos = 0;
        for (int dia = 1; dia <= 7; dia++) {
            final int d = dia;
            List<BloqueHorario> bloquesDia = new ArrayList<>();
            for (Seccion s : clique) {
                for (BloqueHorario b : s.getBloques()) {
                    if (b.dia() == d) {
                        bloquesDia.add(b);
                    }
                }
            }
            bloquesDia.sort(Comparator.comparingInt(BloqueHorario::inicioMin));
            for (int i = 0; i < bloquesDia.size() - 1; i++) {
                totalMuertos += bloquesDia.get(i).minutosMuertosCon(bloquesDia.get(i + 1));
            }
        }
        return totalMuertos;
    }

    @Override
    public int compareTo(SolucionHorario otra) {
        // Ordenamiento descendente por F*(K)
        return Double.compare(otra.aptitudExtendida, this.aptitudExtendida);
    }

    public List<Seccion> getSecciones() { return secciones; }
    public double getAptitudBase() { return aptitudBase; }
    public int getMinutosMuertos() { return minutosMuertos; }
    public double getAptitudExtendida() { return aptitudExtendida; }
}