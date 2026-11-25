package com.grafos.horarios.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "bloques_horario")
public class BloqueEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int dia;       // 1..7
    private int inicioMin; // Minutos desde 00:00
    private int finMin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seccion_id")
    @JsonIgnore
    private SeccionEntity seccion;

    public BloqueEntity() {}
    public BloqueEntity(int dia, int inicioMin, int finMin, SeccionEntity seccion) {
        this.dia = dia;
        this.inicioMin = inicioMin;
        this.finMin = finMin;
        this.seccion = seccion;
    }

    public Long getId() { return id; }
    public int getDia() { return dia; }
    public int getInicioMin() { return inicioMin; }
    public int getFinMin() { return finMin; }
}