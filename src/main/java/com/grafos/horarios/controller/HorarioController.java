package com.grafos.horarios.controller;

import com.grafos.horarios.algorithm.ForwardCheckingSolver;
import com.grafos.horarios.algorithm.ForwardCheckingSolver.ResultadoOptimizacion;
import com.grafos.horarios.entity.*;
import com.grafos.horarios.model.BloqueHorario;
import com.grafos.horarios.model.GrafoNPartito;
import com.grafos.horarios.model.Seccion;
import com.grafos.horarios.repository.CursoRepository;
import com.grafos.horarios.service.ExcelImportService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;

@RestController
@RequestMapping("/api/horarios")
public class HorarioController {

    private final CursoRepository cursoRepo;
    private final ExcelImportService excelImportService;

    public HorarioController(CursoRepository cursoRepo, ExcelImportService excelImportService) {
        this.cursoRepo = cursoRepo;
        this.excelImportService = excelImportService;
    }

    public record BloqueDTO(int dia, int inicioMin, int finMin) {}
    public record SeccionDTO(String codigoSeccion, String docente, int prioridad, List<BloqueDTO> bloques) {}
    public record CursoDTO(String ciclo, String codigoCurso, String nombreCurso, int creditos, List<SeccionDTO> secciones) {}
    public record SolicitudOptimizacionDTO(int creditosMaximos, int topK, List<CursoDTO> cursos) {}

    public record AristaVisualDTO(int origenId, int destinoId) {}
    public record GrafoVisualDTO(List<Seccion> vertices, List<AristaVisualDTO> aristas, int numParticiones) {}
    public record RespuestaCompletaDTO(ResultadoOptimizacion optimizacion, GrafoVisualDTO grafoVisual) {}

    @GetMapping("/catalogo")
    public List<CursoEntity> obtenerCatalogo() {
        return cursoRepo.findAll();
    }

    @PostMapping("/catalogo")
    public ResponseEntity<?> crearCursoEnBD(@RequestBody CursoDTO dto) {
        String ciclo = (dto.ciclo() == null || dto.ciclo().isBlank()) ? "Ciclo V" : dto.ciclo().trim();
        CursoEntity curso = new CursoEntity(ciclo, dto.codigoCurso(), dto.nombreCurso(), dto.creditos());
        for (SeccionDTO sDto : dto.secciones()) {
            SeccionEntity sec = new SeccionEntity(sDto.codigoSeccion(), sDto.docente(), curso);
            for (BloqueDTO bDto : sDto.bloques()) {
                sec.getBloques().add(new BloqueEntity(bDto.dia(), bDto.inicioMin(), bDto.finMin(), sec));
            }
            curso.getSecciones().add(sec);
        }
        return ResponseEntity.ok(cursoRepo.save(curso));
    }

    @PostMapping("/importar-excel")
    public ResponseEntity<?> importarExcel(@RequestParam("file") MultipartFile file) {
        try {
            int total = excelImportService.importarReemplazandoBD(file);
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Se importaron " + total + " cursos y se reemplazaron los datos anteriores."
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Error al leer el archivo Excel: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/plantilla-excel")
    public ResponseEntity<byte[]> descargarPlantillaExcel() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Horarios");
            String[] headers = {"Ciclo", "Codigo", "Curso", "Creditos", "Seccion", "Profesor", "Dia", "Hora_Inicio", "Hora_Fin"};
            Row row0 = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                row0.createCell(i).setCellValue(headers[i]);
            }

            String[][] ejemplo = {
                    {"Ciclo V", "MAT301", "Matemática Discreta II", "4", "A", "Dr. Leonhard Euler", "Lunes", "08:00", "10:00"},
                    {"Ciclo V", "MAT301", "Matemática Discreta II", "4", "A", "Dr. Leonhard Euler", "Miércoles", "08:00", "10:00"},
                    {"Ciclo VI", "IA401", "Inteligencia Artificial", "4", "01", "Dr. Alan Turing", "Martes", "10:00", "12:00"}
            };
            for (int r = 0; r < ejemplo.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < ejemplo[r].length; c++) {
                    row.createCell(c).setCellValue(ejemplo[r][c]);
                }
            }
            wb.write(out);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=plantilla_horarios.xlsx")
                    .body(out.toByteArray());
        }
    }

    @PostMapping("/optimizar")
    public ResponseEntity<?> optimizarHorario(@RequestBody SolicitudOptimizacionDTO solicitud) {
        if (solicitud.cursos() == null || solicitud.cursos().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Seleccione al menos un curso del catálogo."));
        }

        List<List<Seccion>> particiones = new ArrayList<>();
        int idGlobal = 0;

        for (CursoDTO curso : solicitud.cursos()) {
            if (curso.secciones() == null || curso.secciones().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Debe marcar al menos una sección en " + curso.nombreCurso()
                ));
            }
            List<Seccion> particionVi = new ArrayList<>();
            for (SeccionDTO secDto : curso.secciones()) {
                List<BloqueHorario> bloques = secDto.bloques().stream()
                        .map(b -> new BloqueHorario(b.dia(), b.inicioMin(), b.finMin()))
                        .toList();

                particionVi.add(new Seccion(
                        idGlobal++,
                        curso.codigoCurso(),
                        curso.nombreCurso(),
                        secDto.codigoSeccion(),
                        secDto.docente(),
                        curso.creditos(),
                        secDto.prioridad(),
                        bloques
                ));
            }
            particiones.add(particionVi);
        }

        GrafoNPartito grafo = new GrafoNPartito(particiones);
        ForwardCheckingSolver solver = new ForwardCheckingSolver(grafo);
        int k = solicitud.topK() > 0 ? solicitud.topK() : 5;
        int cMax = solicitud.creditosMaximos() > 0 ? solicitud.creditosMaximos() : 22;
        ResultadoOptimizacion resultado = solver.resolver(k, cMax);

        List<Seccion> vertices = grafo.getTodosLosVertices();
        List<AristaVisualDTO> aristas = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            for (int j = i + 1; j < vertices.size(); j++) {
                if (grafo.sonCompatibles(vertices.get(i), vertices.get(j))) {
                    aristas.add(new AristaVisualDTO(vertices.get(i).getIdGlobal(), vertices.get(j).getIdGlobal()));
                }
            }
        }

        GrafoVisualDTO grafoVisual = new GrafoVisualDTO(vertices, aristas, grafo.getNumeroParticiones());
        return ResponseEntity.ok(new RespuestaCompletaDTO(resultado, grafoVisual));
    }
}