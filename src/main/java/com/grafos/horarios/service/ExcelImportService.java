package com.grafos.horarios.service;

import com.grafos.horarios.entity.*;
import com.grafos.horarios.repository.CursoRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Service
public class ExcelImportService {

    private final CursoRepository cursoRepo;

    public ExcelImportService(CursoRepository cursoRepo) {
        this.cursoRepo = cursoRepo;
    }

    @Transactional
    public int importarReemplazandoBD(MultipartFile archivo) throws Exception {
        // 1. Borra los cursos predeterminados o la carga anterior
        cursoRepo.deleteAll();
        cursoRepo.flush();

        Map<String, CursoEntity> mapaCursos = new LinkedHashMap<>();
        Map<String, SeccionEntity> mapaSecciones = new LinkedHashMap<>();

        try (InputStream is = archivo.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet hoja = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) continue;

                // Columnas: 0:Ciclo | 1:Codigo | 2:Curso | 3:Creditos | 4:Seccion | 5:Profesor | 6:Dia | 7:Inicio | 8:Fin
                String ciclo = formatter.formatCellValue(fila.getCell(0)).trim();
                String codigo = formatter.formatCellValue(fila.getCell(1)).trim().toUpperCase();
                String nombre = formatter.formatCellValue(fila.getCell(2)).trim();
                if (codigo.isEmpty() || nombre.isEmpty()) continue;

                int creditos = Integer.parseInt(formatter.formatCellValue(fila.getCell(3)).trim());
                String codSeccion = formatter.formatCellValue(fila.getCell(4)).trim();
                String profesor = formatter.formatCellValue(fila.getCell(5)).trim();
                int dia = parsearDia(formatter.formatCellValue(fila.getCell(6)).trim());
                int inicioMin = parsearHoraAMinutos(formatter.formatCellValue(fila.getCell(7)).trim());
                int finMin = parsearHoraAMinutos(formatter.formatCellValue(fila.getCell(8)).trim());

                String cicloFinal = ciclo.isEmpty() ? "General" : ciclo;
                CursoEntity curso = mapaCursos.computeIfAbsent(codigo,
                        k -> new CursoEntity(cicloFinal, codigo, nombre, creditos));

                String claveSeccion = codigo + "-" + codSeccion;
                SeccionEntity seccion = mapaSecciones.computeIfAbsent(claveSeccion, k -> {
                    SeccionEntity s = new SeccionEntity(codSeccion, profesor, curso);
                    curso.getSecciones().add(s);
                    return s;
                });

                seccion.getBloques().add(new BloqueEntity(dia, inicioMin, finMin, seccion));
            }
        }

        cursoRepo.saveAll(mapaCursos.values());
        return mapaCursos.size();
    }

    private int parsearDia(String textoDia) {
        String d = textoDia.toLowerCase();
        if (d.startsWith("1") || d.startsWith("lun")) return 1;
        if (d.startsWith("2") || d.startsWith("mar")) return 2;
        if (d.startsWith("3") || d.startsWith("mi")) return 3;
        if (d.startsWith("4") || d.startsWith("jue")) return 4;
        if (d.startsWith("5") || d.startsWith("vie")) return 5;
        if (d.startsWith("6") || d.startsWith("sáb") || d.startsWith("sab")) return 6;
        return 7;
    }

    private int parsearHoraAMinutos(String hhmm) {
        String[] partes = hhmm.split(":");
        return Integer.parseInt(partes[0].trim()) * 60 + Integer.parseInt(partes[1].trim());
    }
}