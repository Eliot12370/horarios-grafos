package com.grafos.horarios.algorithm;

import com.grafos.horarios.model.GrafoNPartito;
import com.grafos.horarios.model.Seccion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ForwardCheckingSolver {

    public record ResultadoOptimizacion(
            boolean esFactible,
            int omegaAlcanzado,
            int cursosSolicitados,
            double densidadGrafo,
            int totalVertices,
            int totalAristas,
            long nodosEvaluados,
            long ramasPodadas,
            double tiempoEjecucionMs,
            List<SolucionHorario> mejoresHorarios
    ) {}

    private final GrafoNPartito grafo;
    private long nodosEvaluados;
    private long ramasPodadas;
    private int maxCursosAlcanzado;
    private final List<SolucionHorario> solucionesValidas;

    public ForwardCheckingSolver(GrafoNPartito grafo) {
        this.grafo = grafo;
        this.solucionesValidas = new ArrayList<>();
    }

    public ResultadoOptimizacion resolver(int topK, int creditosMaximos) {
        this.nodosEvaluados = 0;
        this.ramasPodadas = 0;
        this.maxCursosAlcanzado = 0;
        this.solucionesValidas.clear();

        int n = grafo.getNumeroParticiones();
        if (n == 0) {
            return new ResultadoOptimizacion(false, 0, 0, 0, 0, 0, 0, 0, 0.0, List.of());
        }

        List<List<Seccion>> dominiosIniciales = new ArrayList<>();
        for (List<Seccion> particion : grafo.getParticiones()) {
            dominiosIniciales.add(new ArrayList<>(particion));
        }

        boolean[] procesado = new boolean[n];
        List<Seccion> cliqueActual = new ArrayList<>(n);

        long inicioNano = System.nanoTime();
        backtrackMaxCliqueRelajado(0, n, procesado, cliqueActual, 0, creditosMaximos, dominiosIniciales);
        long finNano = System.nanoTime();

        double tiempoMs = (finNano - inicioNano) / 1_000_000.0;

        // 1º: Mayor cantidad de cursos |V(K)|; 2º: Mayor aptitud extendida F*(K)
        List<SolucionHorario> topResultados = solucionesValidas.stream()
                .filter(s -> s.getSecciones().size() == maxCursosAlcanzado)
                .sorted(Comparator
                        .comparingInt((SolucionHorario s) -> s.getSecciones().size()).reversed()
                        .thenComparing(SolucionHorario::compareTo))
                .limit(topK)
                .toList();

        boolean factible = maxCursosAlcanzado > 0;

        return new ResultadoOptimizacion(
                factible,
                maxCursosAlcanzado,
                n,
                grafo.getDensidadNPartita(),
                grafo.getTodosLosVertices().size(),
                grafo.getTotalAristas(),
                nodosEvaluados,
                ramasPodadas,
                tiempoMs,
                topResultados
        );
    }

    private void backtrackMaxCliqueRelajado(int particionesRevisadas, int n, boolean[] procesado,
                                            List<Seccion> cliqueActual, int creditosActuales,
                                            int creditosMaximos, List<List<Seccion>> dominios) {
        this.nodosEvaluados++;

        int cursosEnClique = cliqueActual.size();
        int particionesRestantes = n - particionesRevisadas;

        // Poda Branch & Bound: si ni tomando todas las restantes alcanzamos el mejor ω(G) ya hallado
        if (cursosEnClique + particionesRestantes < this.maxCursosAlcanzado) {
            this.ramasPodadas++;
            return;
        }

        // Caso base: ya se decidió sobre todas las particiones
        if (particionesRevisadas == n) {
            if (cursosEnClique > 0 && cursosEnClique >= this.maxCursosAlcanzado) {
                if (cursosEnClique > this.maxCursosAlcanzado) {
                    this.maxCursosAlcanzado = cursosEnClique;
                    this.solucionesValidas.clear();
                }
                this.solucionesValidas.add(new SolucionHorario(cliqueActual));
            }
            return;
        }

        // Heurística MRV: elegir la partición no procesada con menor dominio residual
        int idxParticion = seleccionarParticionMRV(n, procesado, dominios);
        List<Seccion> candidatos = dominios.get(idxParticion);
        procesado[idxParticion] = true;

        // RAMA 1: Intentar incluir una sección de este curso (si no excede C_max)
        if (!candidatos.isEmpty()) {
            int creditosCurso = candidatos.get(0).getCreditos();
            if (creditosActuales + creditosCurso <= creditosMaximos) {
                for (Seccion v : candidatos) {
                    List<List<Seccion>> nuevosDominios = new ArrayList<>(n);
                    for (int k = 0; k < n; k++) {
                        nuevosDominios.add(new ArrayList<>(dominios.get(k)));
                    }

                    // Propagación de restricciones (Forward Checking) sobre dominios futuros
                    for (int j = 0; j < n; j++) {
                        if (!procesado[j]) {
                            List<Seccion> filtrado = new ArrayList<>();
                            for (Seccion u : nuevosDominios.get(j)) {
                                if (grafo.sonCompatibles(v, u)) {
                                    filtrado.add(u);
                                }
                            }
                            nuevosDominios.set(j, filtrado);
                        }
                    }

                    cliqueActual.add(v);
                    backtrackMaxCliqueRelajado(particionesRevisadas + 1, n, procesado,
                            cliqueActual, creditosActuales + creditosCurso, creditosMaximos, nuevosDominios);
                    cliqueActual.remove(cliqueActual.size() - 1);
                }
            } else {
                this.ramasPodadas++;
            }
        }

        // RAMA 2: Omitir este curso (permite hallar ω(G) < N cuando hay exceso de créditos o cruces inevitables)
        if (cursosEnClique + (particionesRestantes - 1) >= this.maxCursosAlcanzado) {
            backtrackMaxCliqueRelajado(particionesRevisadas + 1, n, procesado,
                    cliqueActual, creditosActuales, creditosMaximos, dominios);
        } else {
            this.ramasPodadas++;
        }

        procesado[idxParticion] = false;
    }

    private int seleccionarParticionMRV(int n, boolean[] procesado, List<List<Seccion>> dominios) {
        int mejorIndice = -1;
        int menorTamano = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (!procesado[i]) {
                int tam = dominios.get(i).size();
                if (tam < menorTamano) {
                    menorTamano = tam;
                    mejorIndice = i;
                }
            }
        }
        return mejorIndice;
    }
}