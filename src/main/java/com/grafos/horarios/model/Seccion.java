package com.grafos.horarios.model;

import java.util.List;

/**
 * Representa un vértice v ∈ V_i (una sección específica de un curso C_i).
 */
public class Seccion {
    private final int idGlobal;          // Índice único en V = V_1 ⊔ ... ⊔ V_N
    private final String codigoCurso;    // Identificador del curso C_i
    private final String nombreCurso;
    private final String codigoSeccion;  // Ej. "01", "A"
    private final String docente;
    private final int creditos;          // c(C_i)
    private final int prioridad;         // r(v) ∈ {1, ..., |V_i|} (1 = máxima prioridad)
    private double pesoNormalizado;      // w(v) ∈ (0, 1]
    private final List<BloqueHorario> bloques; // H(s)

    public Seccion(int idGlobal, String codigoCurso, String nombreCurso,
                   String codigoSeccion, String docente, int creditos,
                   int prioridad, List<BloqueHorario> bloques) {
        this.idGlobal = idGlobal;
        this.codigoCurso = codigoCurso;
        this.nombreCurso = nombreCurso;
        this.codigoSeccion = codigoSeccion;
        this.docente = docente;
        this.creditos = creditos;
        this.prioridad = prioridad;
        this.bloques = bloques;
    }

    /**
     * Aplica la fórmula de utilidad normalizada (Cap. 3.1.2):
     * w(v) = (|V_i| - r(v) + 1) / |V_i|
     */
    public void calcularPesoNormalizado(int cardinalidadParticion) {
        int r = Math.max(1, Math.min(this.prioridad, cardinalidadParticion));
        this.pesoNormalizado = (double) (cardinalidadParticion - r + 1) / cardinalidadParticion;
    }

    /**
     * Condición de compatibilidad horaria (Cap. 3.1.1):
     * H(u) ∩ H(v) = ∅
     */
    public boolean esCompatibleCon(Seccion otra) {
        if (this.codigoCurso.equals(otra.codigoCurso)) {
            return false; // V_i es un conjunto independiente por definición
        }
        for (BloqueHorario b1 : this.bloques) {
            for (BloqueHorario b2 : otra.bloques) {
                if (b1.seCruzaCon(b2)) {
                    return false;
                }
            }
        }
        return true;
    }

    // Getters
    public int getIdGlobal() { return idGlobal; }
    public String getCodigoCurso() { return codigoCurso; }
    public String getNombreCurso() { return nombreCurso; }
    public String getCodigoSeccion() { return codigoSeccion; }
    public String getDocente() { return docente; }
    public int getCreditos() { return creditos; }
    public int getPrioridad() { return prioridad; }
    public double getPesoNormalizado() { return pesoNormalizado; }
    public List<BloqueHorario> getBloques() { return bloques; }
}