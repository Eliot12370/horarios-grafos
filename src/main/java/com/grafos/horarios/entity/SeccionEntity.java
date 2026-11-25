package com.grafos.horarios.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "secciones")
public class SeccionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String codigoSeccion;
    private String docente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id")
    @JsonIgnore
    private CursoEntity curso;

    @OneToMany(mappedBy = "seccion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BloqueEntity> bloques = new ArrayList<>();

    public SeccionEntity() {}
    public SeccionEntity(String codigoSeccion, String docente, CursoEntity curso) {
        this.codigoSeccion = codigoSeccion;
        this.docente = docente;
        this.curso = curso;
    }

    public Long getId() { return id; }
    public String getCodigoSeccion() { return codigoSeccion; }
    public String getDocente() { return docente; }
    public CursoEntity getCurso() { return curso; }
    public List<BloqueEntity> getBloques() { return bloques; }
}