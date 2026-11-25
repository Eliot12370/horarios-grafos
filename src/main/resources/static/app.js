const DIAS = ["", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const DIAS_FULL = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];
const PALETA = ["#1d4ed8", "#047857", "#b45309", "#6d28d9", "#be123c", "#0f766e", "#4338ca"];

let catalogoBD = [];
// Mapa: cursoId -> { seleccionado: boolean, seccionesOrdenadas: [ { id, activa } ] }
let estadoCursos = new Map();
let cursoEditandoModalId = null;

let solucionesActuales = [];
let ultimoGrafoVisual = null;

// Estado temporal del Modal BD
let bdBloquesTemp = [];
let bdSeccionesTemp = [];

document.addEventListener("DOMContentLoaded", async () => {
    dibujarGrillaBase();

    // 1. Registrar TODOS los eventos primero (antes de llamar al servidor)
    document.getElementById("btnOptimizar").addEventListener("click", ejecutarOptimizacion);
    document.getElementById("creditosMax").addEventListener("input", actualizarCreditosUI);

    document.getElementById("inputExcel").addEventListener("change", subirArchivoExcel);
    document.getElementById("selectCiclo").addEventListener("change", renderizarListaCursosCompacta);
    document.getElementById("buscadorCursos").addEventListener("input", renderizarListaCursosCompacta);

    document.getElementById("tabVistaHorario").addEventListener("click", () => cambiarVista("horario"));
    document.getElementById("tabVistaGrafo").addEventListener("click", () => cambiarVista("grafo"));

    document.getElementById("btnCerrarModalSecciones").addEventListener("click", cerrarModalSecciones);
    document.getElementById("btnConfirmarSecciones").addEventListener("click", cerrarModalSecciones);

    document.getElementById("btnAbrirModalBD").addEventListener("click", () => document.getElementById("modalBD").classList.remove("hidden"));
    document.getElementById("btnCerrarModal").addEventListener("click", () => document.getElementById("modalBD").classList.add("hidden"));
    document.getElementById("btnBdAddBloque").addEventListener("click", agregarBloqueModalBD);
    document.getElementById("btnBdAddSeccion").addEventListener("click", agregarSeccionModalBD);
    document.getElementById("btnGuardarEnBD").addEventListener("click", guardarCursoEnBD);

    // 2. Cargar catálogo desde BD de forma segura
    try {
        await cargarCatalogoDesdeBD();
    } catch (err) {
        console.error("Error cargando catálogo inicial:", err);
    }
});

function minutesToTime(min) {
    return `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}`;
}

function timeToMinutes(hhmm) {
    const [h, m] = hhmm.split(":").map(Number);
    return h * 60 + m;
}

async function subirArchivoExcel(e) {
    const file = e.target.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);

    const resp = await fetch("/api/horarios/importar-excel", {
        method: "POST",
        body: formData
    });
    const data = await resp.json();

    if (!resp.ok) {
        alert(data.error || "Error al importar el archivo Excel.");
        e.target.value = "";
        return;
    }

    alert(data.mensaje);
    estadoCursos.clear(); // Borra el estado de los cursos predeterminados anteriores
    e.target.value = "";
    await cargarCatalogoDesdeBD();
}

async function cargarCatalogoDesdeBD() {
    const resp = await fetch("/api/horarios/catalogo");
    catalogoBD = await resp.json();

    // Poblar selector de ciclos únicos
    const ciclos = [...new Set(catalogoBD.map(c => c.ciclo || "Ciclo V"))];
    const selectCiclo = document.getElementById("selectCiclo");
    const cicloPrevio = selectCiclo.value;

    selectCiclo.innerHTML = `<option value="">-- Elegir Ciclo --</option>` +
        ciclos.map(ci => `<option value="${ci}">${ci}</option>`).join("");

    if (ciclos.includes(cicloPrevio)) {
        selectCiclo.value = cicloPrevio;
    } else if (ciclos.length > 0) {
        selectCiclo.value = ciclos[0]; // Selecciona el primer ciclo disponible
    }

    catalogoBD.forEach(curso => {
        if (!estadoCursos.has(curso.id)) {
            estadoCursos.set(curso.id, {
                seleccionado: false,
                seccionesOrdenadas: curso.secciones.map(s => ({ id: s.id, activa: true }))
            });
        }
    });

    renderizarListaCursosCompacta();
    actualizarCreditosUI();
}

function renderizarListaCursosCompacta() {
    const cicloElegido = document.getElementById("selectCiclo").value;
    const textoBusq = document.getElementById("buscadorCursos").value.trim().toLowerCase();

    // 1. Sección superior: Cursos ya elegidos (pueden ser de distintos ciclos) con opción de quitar
    const seleccionados = catalogoBD.filter(c => estadoCursos.get(c.id)?.seleccionado);
    const contSel = document.getElementById("cursosSeleccionadosResumen");

    if (seleccionados.length === 0) {
        contSel.innerHTML = `<div class="hint" style="margin-bottom:0;">Aún no has añadido cursos. Elige un ciclo abajo o usa el buscador.</div>`;
    } else {
        contSel.innerHTML = seleccionados.map(c => {
            const est = estadoCursos.get(c.id);
            const activasCount = est.seccionesOrdenadas.filter(s => s.activa).length;
            const totalCount = est.seccionesOrdenadas.length;
            const cicloTag = c.ciclo || "Ciclo V";

            return `
            <div class="curso-row-card seleccionado">
                <div style="flex:1;">
                    <div><strong>${c.codigo}</strong> - ${c.nombre} <span class="badge-info" style="font-size:0.7rem;">${cicloTag}</span></div>
                    <div class="curso-meta">${c.creditos} créd. · ${activasCount} de ${totalCount} secciones activas</div>
                </div>
                <div style="display:flex; gap:5px; align-items:center;">
                    <button type="button" class="btn-elegir-sec" onclick="abrirModalSecciones(${c.id})">
                        Secciones (${activasCount})
                    </button>
                    <button type="button" onclick="toggleCurso(${c.id}, false)"
                            style="background:#fee2e2; color:#b91c1c; border:1px solid #fca5a5; border-radius:5px; padding:0.35rem 0.55rem; cursor:pointer; font-weight:bold;"
                            title="Eliminar curso de mi selección">✕</button>
                </div>
            </div>`;
        }).join("");
    }

    // 2. Sección inferior: Catálogo filtrado por Buscador Global (todos los ciclos) o por Ciclo seleccionado
    let filtrados = [];
    if (textoBusq.length > 0) {
        filtrados = catalogoBD.filter(c =>
            c.codigo.toLowerCase().includes(textoBusq) ||
            c.nombre.toLowerCase().includes(textoBusq) ||
            (c.ciclo && c.ciclo.toLowerCase().includes(textoBusq))
        );
    } else if (cicloElegido) {
        filtrados = catalogoBD.filter(c => (c.ciclo || "Ciclo V") === cicloElegido);
    }

    const cont = document.getElementById("listaCatalogoCursos");
    if (filtrados.length === 0) {
        cont.innerHTML = `<div class="hint">No se encontraron cursos para el filtro actual.</div>`;
        return;
    }

    cont.innerHTML = filtrados.map(c => {
        const est = estadoCursos.get(c.id);
        const cicloTag = c.ciclo || "Ciclo V";
        return `
        <div class="curso-row-card" style="opacity:${est.seleccionado ? '0.65' : '1'};">
            <div style="flex:1;">
                <div><strong>${c.codigo}</strong> - ${c.nombre} <small style="color:#64748b;">(${cicloTag})</small></div>
                <div class="curso-meta">${c.creditos} créd. · ${c.secciones.length} secciones disponibles</div>
            </div>
            <button type="button" class="btn-elegir-sec" ${est.seleccionado ? "disabled" : ""}
                    onclick="toggleCurso(${c.id}, true)">
                ${est.seleccionado ? "Añadido ✓" : "+ Añadir"}
            </button>
        </div>`;
    }).join("");
}

function toggleCurso(cursoId, checked) {
    const est = estadoCursos.get(cursoId);
    if (!est) return;
    est.seleccionado = checked;
    renderizarListaCursosCompacta();
    actualizarCreditosUI();
}

function actualizarCreditosUI() {
    const max = parseInt(document.getElementById("creditosMax").value, 10) || 22;
    let suma = 0;
    catalogoBD.forEach(c => {
        if (estadoCursos.get(c.id)?.seleccionado) suma += c.creditos;
    });
    const badge = document.getElementById("resumenCreditos");
    badge.textContent = `${suma} / ${max} créd.`;
    badge.style.background = suma > max ? "#fef3c7" : "#e0e7ff";
    badge.style.color = suma > max ? "#92400e" : "#1e3a8a";
}

// --- VENTANA FLOTANTE (MODAL) DE SECCIONES Y PRIORIDADES ---
function abrirModalSecciones(cursoId) {
    cursoEditandoModalId = cursoId;
    const curso = catalogoBD.find(c => c.id === cursoId);
    const est = estadoCursos.get(cursoId);

    document.getElementById("tituloModalSecciones").textContent = `${curso.codigo} — ${curso.nombre}`;
    const ul = document.getElementById("listaModalSecciones");

    ul.innerHTML = est.seccionesOrdenadas.map(item => {
        const sec = curso.secciones.find(s => s.id === item.id);
        const horariosTxt = sec.bloques.map(b => `${DIAS[b.dia]} ${minutesToTime(b.inicioMin)}-${minutesToTime(b.finMin)}`).join(" | ");
        return `
        <li class="seccion-item" draggable="true" data-sec-id="${sec.id}">
            <span class="drag-handle" title="Arrastrar para cambiar prioridad">☰</span>
            <input type="checkbox" ${item.activa ? "checked" : ""} onchange="sincronizarEstadoDesdeModal()">
            <div class="sec-info">
                <strong>Sección ${sec.codigoSeccion}</strong> · Prof. ${sec.docente}<br>
                <small style="color:#475569">${horariosTxt}</small>
            </div>
            <span class="sec-peso">r=1 · w=1.00</span>
        </li>`;
    }).join("");

    inicializarDragAndDropModal(ul);
    sincronizarEstadoDesdeModal();
    document.getElementById("modalSecciones").classList.remove("hidden");
}

function cerrarModalSecciones() {
    sincronizarEstadoDesdeModal();
    document.getElementById("modalSecciones").classList.add("hidden");
    cursoEditandoModalId = null;
    renderizarListaCursosCompacta();
}

function inicializarDragAndDropModal(ul) {
    let draggedItem = null;
    ul.querySelectorAll(".seccion-item").forEach(item => {
        item.addEventListener("dragstart", () => {
            draggedItem = item;
            item.classList.add("dragging");
        });
        item.addEventListener("dragend", () => {
            item.classList.remove("dragging");
            draggedItem = null;
            sincronizarEstadoDesdeModal();
        });
        item.addEventListener("dragover", e => {
            e.preventDefault();
            const afterElement = getDragAfterElement(ul, e.clientY);
            if (afterElement == null) ul.appendChild(draggedItem);
            else ul.insertBefore(draggedItem, afterElement);
        });
    });
}

function getDragAfterElement(container, y) {
    const items = [...container.querySelectorAll(".seccion-item:not(.dragging)")];
    return items.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closest.offset) return { offset, element: child };
        return closest;
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

function sincronizarEstadoDesdeModal() {
    if (cursoEditandoModalId === null) return;
    const ul = document.getElementById("listaModalSecciones");
    const items = [...ul.querySelectorAll(".seccion-item")];
    const activos = items.filter(li => li.querySelector("input[type='checkbox']").checked);
    const totalVi = activos.length;

    const nuevoOrden = [];
    let rank = 1;

    items.forEach(li => {
        const secId = Number(li.dataset.secId);
        const activa = li.querySelector("input[type='checkbox']").checked;
        const badge = li.querySelector(".sec-peso");

        nuevoOrden.push({ id: secId, activa });

        if (activa) {
            const w = (totalVi - rank + 1) / totalVi;
            badge.textContent = `r=${rank} · w=${w.toFixed(2)}`;
            badge.classList.remove("inactivo");
            rank++;
        } else {
            badge.textContent = "Omitida";
            badge.classList.add("inactivo");
        }
    });

    estadoCursos.get(cursoEditandoModalId).seccionesOrdenadas = nuevoOrden;
}

// --- ENVÍO AL SOLVER EN JAVA ---
async function ejecutarOptimizacion() {
    const alerta = document.getElementById("alertaError");
    alerta.classList.add("hidden");

    const cursosPayload = [];
    catalogoBD.forEach(curso => {
        const est = estadoCursos.get(curso.id);
        if (!est || !est.seleccionado) return;

        const seccionesActivas = [];
        let prioridad = 1;
        est.seccionesOrdenadas.forEach(item => {
            if (item.activa) {
                const secObj = curso.secciones.find(s => s.id === item.id);
                seccionesActivas.push({
                    codigoSeccion: secObj.codigoSeccion,
                    docente: secObj.docente,
                    prioridad: prioridad++,
                    bloques: secObj.bloques.map(b => ({ dia: b.dia, inicioMin: b.inicioMin, finMin: b.finMin }))
                });
            }
        });

        cursosPayload.push({
            ciclo: curso.ciclo || "Ciclo V",
            codigoCurso: curso.codigo,
            nombreCurso: curso.nombre,
            creditos: curso.creditos,
            secciones: seccionesActivas
        });
    });

    const payload = {
        creditosMaximos: parseInt(document.getElementById("creditosMax").value, 10),
        topK: parseInt(document.getElementById("topK").value, 10),
        cursos: cursosPayload
    };

    const resp = await fetch("/api/horarios/optimizar", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });
    const data = await resp.json();

    if (!resp.ok) {
        alerta.textContent = data.error || "Error al optimizar.";
        alerta.classList.remove("hidden");
        return;
    }

    const opt = data.optimizacion;
    ultimoGrafoVisual = data.grafoVisual;

    document.getElementById("metOmega").textContent = opt.omegaAlcanzado === opt.cursosSolicitados
        ? `Completo: ${opt.omegaAlcanzado} de ${opt.cursosSolicitados} cursos`
        : `Máximo posible: ${opt.omegaAlcanzado} de ${opt.cursosSolicitados} cursos`;
    document.getElementById("metDensidad").textContent = `${(opt.densidadGrafo * 100).toFixed(1)}% (${opt.totalAristas} aristas)`;
    document.getElementById("metPoda").textContent = `${opt.nodosEvaluados} eval. / ${opt.ramasPodadas} podadas`;
    document.getElementById("metTiempo").textContent = `${opt.tiempoEjecucionMs.toFixed(3)} ms`;

    solucionesActuales = opt.mejoresHorarios || [];
    renderizarTabsSoluciones();

    if (solucionesActuales.length > 0) {
        mostrarSolucion(0);
    } else {
        dibujarGrillaBase();
        dibujarGrafoSVG(ultimoGrafoVisual, []);
        document.getElementById("detalleAptitud").textContent = "Sin clique transversal válido (todos los cursos exceden C_max o tienen cruces).";
    }
}

function renderizarTabsSoluciones() {
    document.getElementById("tabsSoluciones").innerHTML = solucionesActuales.map((_, idx) =>
        `<button class="tab-btn ${idx === 0 ? 'active' : ''}" onclick="mostrarSolucion(${idx})">#${idx + 1}</button>`
    ).join("");
}

function mostrarSolucion(indice) {
    document.querySelectorAll(".tab-btn").forEach((b, i) => b.classList.toggle("active", i === indice));
    const sol = solucionesActuales[indice];

    const credUsados = sol.secciones.reduce((acc, s) => acc + s.creditos, 0);
    const cursosIncluidos = new Set(sol.secciones.map(s => s.codigoCurso));
    const omitidos = ultimoGrafoVisual.vertices
        .map(v => v.codigoCurso)
        .filter((c, i, arr) => arr.indexOf(c) === i && !cursosIncluidos.has(c));

    const avisoOmitidos = omitidos.length > 0
        ? ` | Cursos fuera (por C_max o cruce): ${omitidos.join(", ")}`
        : "";

    document.getElementById("detalleAptitud").textContent =
        `Opción #${indice + 1} (${sol.secciones.length} cursos · ${credUsados} créd.) · Aptitud F(K) = ${sol.aptitudBase.toFixed(3)} | Horas muertas: ${sol.minutosMuertos} min | F*(K) = ${sol.aptitudExtendida.toFixed(5)}${avisoOmitidos}`;

    dibujarGrillaBase();
    const grilla = document.getElementById("grillaSemanal");
    sol.secciones.forEach((sec, idxCurso) => {
        const color = PALETA[idxCurso % PALETA.length];
        sec.bloques.forEach(b => {
            const filaInicio = Math.floor((b.inicioMin - 420) / 60) + 2;
            const filaFin = Math.ceil((b.finMin - 420) / 60) + 2;
            const div = document.createElement("div");
            div.className = "course-block";
            div.style.backgroundColor = color;
            div.style.gridColumn = b.dia + 1;
            div.style.gridRow = `${Math.max(2, filaInicio)} / ${Math.min(17, filaFin)}`;
            div.innerHTML = `<strong>${sec.codigoCurso} (Sec ${sec.codigoSeccion})</strong><br>${sec.docente}<br><small>${minutesToTime(b.inicioMin)}-${minutesToTime(b.finMin)} · w=${sec.pesoNormalizado.toFixed(2)}</small>`;
            grilla.appendChild(div);
        });
    });

    const idsClique = sol.secciones.map(s => s.idGlobal);
    dibujarGrafoSVG(ultimoGrafoVisual, idsClique);
}

function dibujarGrafoSVG(grafoVisual, idsClique) {
    const svg = document.getElementById("svgGrafo");
    if (!grafoVisual || grafoVisual.vertices.length === 0) {
        svg.innerHTML = "";
        return;
    }

    const cliqueSet = new Set(idsClique);
    const grupos = [];
    const mapCursos = new Map();
    grafoVisual.vertices.forEach(v => {
        if (!mapCursos.has(v.codigoCurso)) {
            const arr = [];
            mapCursos.set(v.codigoCurso, arr);
            grupos.push(arr);
        }
        mapCursos.get(v.codigoCurso).push(v);
    });

    const N = grupos.length;
    const width = 960;
    const height = 520;
    const colSpacing = width / (N + 1);
    const coords = new Map();
    let svgHtml = "";

    grupos.forEach((particion, i) => {
        const x = colSpacing * (i + 1);
        const rowSpacing = (height - 90) / (particion.length + 1);
        svgHtml += `<text x="${x}" y="28" text-anchor="middle" font-size="12" font-weight="bold" fill="#1e3a8a">V${i + 1} (${particion[0].codigoCurso})</text>`;
        svgHtml += `<rect x="${x - 52}" y="40" width="104" height="${height - 60}" rx="12" fill="none" stroke="#e2e8f0" stroke-dasharray="4"/>`;

        particion.forEach((v, j) => {
            const y = 45 + rowSpacing * (j + 1);
            coords.set(v.idGlobal, { x, y, v });
        });
    });

    grafoVisual.aristas.forEach(e => {
        const p1 = coords.get(e.origenId);
        const p2 = coords.get(e.destinoId);
        const enClique = cliqueSet.has(e.origenId) && cliqueSet.has(e.destinoId);
        if (!enClique) {
            svgHtml += `<line x1="${p1.x}" y1="${p1.y}" x2="${p2.x}" y2="${p2.y}" stroke="#cbd5e1" stroke-width="1.3"/>`;
        }
    });

    grafoVisual.aristas.forEach(e => {
        const p1 = coords.get(e.origenId);
        const p2 = coords.get(e.destinoId);
        const enClique = cliqueSet.has(e.origenId) && cliqueSet.has(e.destinoId);
        if (enClique) {
            svgHtml += `<line x1="${p1.x}" y1="${p1.y}" x2="${p2.x}" y2="${p2.y}" stroke="#16a34a" stroke-width="3.2"/>`;
        }
    });

    coords.forEach(({ x, y, v }) => {
        const enClique = cliqueSet.has(v.idGlobal);
        const fill = enClique ? "#16a34a" : "#ffffff";
        const stroke = enClique ? "#14532d" : "#2563eb";
        const textFill = enClique ? "#ffffff" : "#0f172a";

        svgHtml += `
            <g>
                <circle cx="${x}" cy="${y}" r="24" fill="${fill}" stroke="${stroke}" stroke-width="2.5"/>
                <text x="${x}" y="${y - 2}" text-anchor="middle" font-size="11" font-weight="bold" fill="${textFill}">Sec ${v.codigoSeccion}</text>
                <text x="${x}" y="${y + 11}" text-anchor="middle" font-size="9" fill="${textFill}">w=${v.pesoNormalizado.toFixed(2)}</text>
            </g>`;
    });

    svg.innerHTML = svgHtml;
}

function cambiarVista(vista) {
    const esHorario = vista === "horario";
    document.getElementById("vistaHorario").classList.toggle("hidden", !esHorario);
    document.getElementById("vistaGrafo").classList.toggle("hidden", esHorario);
    document.getElementById("tabVistaHorario").classList.toggle("active", esHorario);
    document.getElementById("tabVistaGrafo").classList.toggle("active", !esHorario);
}

function dibujarGrillaBase() {
    const grilla = document.getElementById("grillaSemanal");
    grilla.innerHTML = `<div class="grid-header">Hora</div>` + DIAS_FULL.slice(1).map(d => `<div class="grid-header">${d}</div>`).join("");
    for (let h = 7; h < 22; h++) {
        const row = h - 7 + 2;
        const lbl = document.createElement("div");
        lbl.className = "time-label";
        lbl.style.gridRow = row;
        lbl.style.gridColumn = 1;
        lbl.textContent = `${String(h).padStart(2, "0")}:00`;
        grilla.appendChild(lbl);

        for (let d = 1; d <= 6; d++) {
            const cell = document.createElement("div");
            cell.className = "grid-cell";
            cell.style.gridRow = row;
            cell.style.gridColumn = d + 1;
            grilla.appendChild(cell);
        }
    }
}

// Funciones del Modal para insertar manualmente en BD Local
function agregarBloqueModalBD() {
    const dia = Number(document.getElementById("bdDia").value);
    const inicioMin = timeToMinutes(document.getElementById("bdInicio").value);
    const finMin = timeToMinutes(document.getElementById("bdFin").value);
    if (inicioMin >= finMin) return alert("Hora inicio debe ser menor a fin.");
    bdBloquesTemp.push({ dia, inicioMin, finMin });
    document.getElementById("bdListaBloques").innerHTML = bdBloquesTemp.map(b =>
        `<li>${DIAS[b.dia]} ${minutesToTime(b.inicioMin)}-${minutesToTime(b.finMin)}</li>`
    ).join("");
}

function agregarSeccionModalBD() {
    const codigoSeccion = document.getElementById("bdSecCodigo").value.trim() || "01";
    const docente = document.getElementById("bdSecDocente").value.trim() || "Docente Asignado";
    if (bdBloquesTemp.length === 0) return alert("Agregue al menos un bloque horario.");
    bdSeccionesTemp.push({ codigoSeccion, docente, prioridad: 1, bloques: [...bdBloquesTemp] });
    bdBloquesTemp = [];
    document.getElementById("bdListaBloques").innerHTML = "";
    document.getElementById("bdSecCodigo").value = "";
    document.getElementById("bdSecDocente").value = "";
    document.getElementById("bdResumenSecciones").textContent = `Secciones listas para guardar: ${bdSeccionesTemp.length}`;
}

async function guardarCursoEnBD() {
    const ciclo = document.getElementById("bdCiclo").value.trim() || "Ciclo V";
    const codigoCurso = document.getElementById("bdCodigo").value.trim().toUpperCase();
    const nombreCurso = document.getElementById("bdNombre").value.trim();
    const creditos = Number(document.getElementById("bdCreditos").value);
    if (!codigoCurso || !nombreCurso || bdSeccionesTemp.length === 0) {
        return alert("Complete los datos del curso y añada al menos una sección.");
    }

    await fetch("/api/horarios/catalogo", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ciclo, codigoCurso, nombreCurso, creditos, secciones: bdSeccionesTemp })
    });

    bdSeccionesTemp = [];
    document.getElementById("bdCodigo").value = "";
    document.getElementById("bdNombre").value = "";
    document.getElementById("bdResumenSecciones").textContent = "";
    document.getElementById("modalBD").classList.add("hidden");
    await cargarCatalogoDesdeBD();
}