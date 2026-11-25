package com.grafos.horarios.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cursos")
public class CursoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String ciclo = "Ciclo V";

    // Agrega este constructor sobrecargado junto al que ya tienes:
    public CursoEntity(String ciclo, String codigo, String nombre, int creditos) {
        this.ciclo = ciclo;
        this.codigo = codigo;
        this.nombre = nombre;
        this.creditos = creditos;
    }

    // Y su getter y setter:
    public String getCiclo() { return ciclo; }
    public void setCiclo(String ciclo) { this.ciclo = ciclo; }

    private int creditos;

    @OneToMany(mappedBy = "curso", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SeccionEntity> secciones = new ArrayList<>();

    public CursoEntity() {}
    public CursoEntity(String codigo, String nombre, int creditos) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.creditos = creditos;
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getCreditos() { return creditos; }
    public List<SeccionEntity> getSecciones() { return secciones; }
}