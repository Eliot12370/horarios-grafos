package com.grafos.horarios.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Modela el Grafo N-Partito Ponderado G = (V, E, w) donde V = V_1 ⊔ V_2 ⊔ ... ⊔ V_N.
 */
public class GrafoNPartito {
    private final List<List<Seccion>> particiones; // [V_1, V_2, ..., V_N]
    private final List<Seccion> todosLosVertices;  // V
    private final boolean[][] matrizAdyacencia;    // E: true si {u, v} ∈ E
    private int totalAristas;                      // |E|
    private double densidadNPartita;               // ρ ∈ [0, 1]

    public GrafoNPartito(List<List<Seccion>> particiones) {
        this.particiones = particiones;
        this.todosLosVertices = new ArrayList<>();

        // 1. Calcular pesos normalizados w(v) por partición y recolectar V
        for (List<Seccion> particion : particiones) {
            int tamanoVi = particion.size();
            for (Seccion s : particion) {
                s.calcularPesoNormalizado(tamanoVi);
                todosLosVertices.add(s);
            }
        }

        int totalV = todosLosVertices.size();
        this.matrizAdyacencia = new boolean[totalV][totalV];
        construirAristasYMetricas();
    }

    private void construirAristasYMetricas() {
        int n = particiones.size();
        this.totalAristas = 0;
        long aristasMaximasPosibles = 0;

        for (int i = 0; i < n; i++) {
            List<Seccion> vi = particiones.get(i);
            for (int j = i + 1; j < n; j++) {
                List<Seccion> vj = particiones.get(j);
                aristasMaximasPosibles += (long) vi.size() * vj.size();

                for (Seccion u : vi) {
                    for (Seccion v : vj) {
                        if (u.esCompatibleCon(v)) {
                            matrizAdyacencia[u.getIdGlobal()][v.getIdGlobal()] = true;
                            matrizAdyacencia[v.getIdGlobal()][u.getIdGlobal()] = true;
                            this.totalAristas++;
                        }
                    }
                }
            }
        }

        // Fórmula corregida de densidad N-Partita (Cap. 5.1)
        this.densidadNPartita = (aristasMaximasPosibles > 0)
                ? (double) this.totalAristas / aristasMaximasPosibles
                : 0.0;
    }

    public boolean sonCompatibles(Seccion u, Seccion v) {
        return matrizAdyacencia[u.getIdGlobal()][v.getIdGlobal()];
    }

    public int getNumeroParticiones() { return particiones.size(); }
    public List<List<Seccion>> getParticiones() { return particiones; }
    public List<Seccion> getTodosLosVertices() { return todosLosVertices; }
    public int getTotalAristas() { return totalAristas; }
    public double getDensidadNPartita() { return densidadNPartita; }
}