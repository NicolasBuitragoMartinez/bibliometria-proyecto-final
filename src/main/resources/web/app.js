"use strict";

const estado = { articulos: [], seleccion: new Set([11, 16]), cargado: {} };

const $ = (selector) => document.querySelector(selector);
const numero = (valor, decimales = 0) =>
  Number(valor).toLocaleString("es-CO", { minimumFractionDigits: decimales, maximumFractionDigits: decimales });
const escapar = (texto) =>
  String(texto).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const recortar = (texto, largo) => (texto.length > largo ? texto.slice(0, largo - 1) + "…" : texto);
const esIA = (enfoque) => enfoque === "IA";
const anioTexto = (articulo) => articulo.anio || "s. f.";
const CHECK = '<svg class="check" viewBox="0 0 16 16" role="img" aria-label="coincide"><path d="m3.5 8.5 3 3 6-7"/></svg>';
const CHEVRON = '<svg class="chevron" viewBox="0 0 16 16" aria-hidden="true"><path d="m6 4 4 4-4 4"/></svg>';

async function api(ruta) {
  const respuesta = await fetch(ruta);
  const datos = await respuesta.json().catch(() => ({}));
  if (!respuesta.ok) throw new Error(datos.error || `El servidor respondió ${respuesta.status}`);
  return datos;
}

function mostrarEstado(id, texto, tipo = "") {
  const elemento = $(id);
  elemento.className = "estado" + (tipo ? " " + tipo : "");
  elemento.textContent = texto;
}

// ---------- Pestañas ----------
const pestanas = [...document.querySelectorAll(".pestana")];

function moverIndicador(pestana) {
  const indicador = $(".indicador");
  indicador.style.transform = `translateX(${pestana.offsetLeft}px) scaleX(${pestana.offsetWidth})`;
  // En pantallas angostas la barra de pestañas se desplaza: la pestaña activa siempre queda a la vista.
  const barra = pestana.parentElement;
  if (pestana.offsetLeft < barra.scrollLeft || pestana.offsetLeft + pestana.offsetWidth > barra.scrollLeft + barra.clientWidth) {
    barra.scrollTo({ left: pestana.offsetLeft - 16, behavior: "smooth" });
  }
}

function abrirPanel(nombre, { actualizarEnlace = true } = {}) {
  const pestana = pestanas.find((p) => p.dataset.panel === nombre) || pestanas[0];
  pestanas.forEach((p) => p.setAttribute("aria-selected", String(p === pestana)));
  document.querySelectorAll(".panel").forEach((p) => p.classList.toggle("activo", p.id === pestana.dataset.panel));
  moverIndicador(pestana);
  if (actualizarEnlace && location.hash.slice(1).split("=")[0] !== pestana.dataset.panel) {
    history.replaceState(null, "", "#" + pestana.dataset.panel);
  }
  if (pestana.dataset.panel === "ranking" && !estado.cargado.ranking) cargarRanking();
  if (pestana.dataset.panel === "agrupamiento" && !estado.cargado.agrupamiento) cargarAgrupamiento();
}

pestanas.forEach((pestana, i) => {
  pestana.addEventListener("click", () => abrirPanel(pestana.dataset.panel));
  pestana.addEventListener("keydown", (e) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    const siguiente = pestanas[(i + (e.key === "ArrowRight" ? 1 : pestanas.length - 1)) % pestanas.length];
    siguiente.focus();
    abrirPanel(siguiente.dataset.panel);
  });
});
window.addEventListener("resize", () => moverIndicador(pestanas.find((p) => p.getAttribute("aria-selected") === "true")));

// ---------- Corpus ----------
async function cargarCorpus() {
  const datos = await api("/api/articulos");
  estado.articulos = datos.articulos;
  const palabras = datos.articulos.reduce((suma, a) => suma + a.palabras, 0);
  const anios = datos.articulos.map((a) => a.anio).filter(Boolean);
  $("#corpus-resumen").textContent = `${datos.articulos.length} artículos publicados entre ${Math.min(...anios)} y `
    + `${Math.max(...anios)} · ${numero(palabras)} palabras en sus abstracts`;
  const cuerpo = $("#tabla-corpus tbody");
  cuerpo.innerHTML = datos.articulos
    .map((a) => `<tr class="fila" tabindex="0" aria-expanded="false" data-numero="${a.numero}">
      <td class="num">${a.numero}</td>
      <td><span class="titulo">${escapar(a.titulo)}</span>
        <span class="autores ocultar-angosto">${escapar(a.autores.join(", "))}</span>
        <span class="autores solo-angosto">${escapar(a.autoresCortos)}</span>
        <span class="detalle solo-angosto"><b>${anioTexto(a)}</b> · <b>${a.palabras}</b> palabras</span></td>
      <td class="num cifra ocultar-angosto">${anioTexto(a)}</td>
      <td class="num cifra ocultar-angosto">${a.palabras}</td>
      <td>${CHEVRON}</td></tr>`)
    .join("");
  cuerpo.querySelectorAll("tr.fila").forEach((fila) => {
    const alternar = () => {
      const abierta = fila.getAttribute("aria-expanded") === "true";
      fila.setAttribute("aria-expanded", String(!abierta));
      if (abierta) {
        fila.nextElementSibling.remove();
        return;
      }
      const articulo = estado.articulos.find((a) => a.numero === Number(fila.dataset.numero));
      fila.insertAdjacentHTML("afterend",
        `<tr class="fila-abstract"><td></td><td colspan="4"><p>${escapar(articulo.abstract)}</p></td></tr>`);
    };
    fila.addEventListener("click", alternar);
    fila.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        alternar();
      }
    });
  });
  dibujarSelector();
}

// ---------- Ranking de términos ----------
async function cargarRanking() {
  estado.cargado.ranking = true;
  try {
    const d = await api("/api/ranking");
    const c = d.cifras;
    const top = d.algoritmos[0].top;
    const maximo = top[0].frecuencia;
    const barras = top
      .map((t) => `<span class="rotulo">${escapar(t.termino)}</span>
        <span class="pista"><span class="relleno" style="width:${(t.frecuencia / maximo) * 100}%"></span></span>
        <span class="valor">${t.frecuencia}</span>`)
      .join("");
    // Una fila por posición: el término una vez y, por algoritmo, una marca si coincide con el primero.
    const filas = top
      .map((t, i) => `<tr><td class="num">${i + 1}</td><td>${escapar(t.termino)}</td><td class="num">${t.frecuencia}</td>${d.algoritmos
        .map((a) => a.top[i].termino === t.termino && a.top[i].frecuencia === t.frecuencia
          ? `<td class="centro">${CHECK}</td>`
          : `<td class="centro error">${escapar(a.top[i].termino)}</td>`)
        .join("")}</tr>`)
      .join("");
    const empatados = d.empate.terminos.map((t) => `${escapar(t.termino)} (${t.posicion}.º)`).join(", ");
    $("#ranking-resultado").className = "";
    $("#ranking-resultado").innerHTML = `
      <div class="bloque">
        <ul class="cifras">
          <li><b>${numero(c.palabras)}</b><span>palabras en los abstracts</span></li>
          <li><b>${numero(c.terminos)}</b><span>términos tras el preprocesamiento</span></li>
          <li><b>${numero(c.distintos)}</b><span>términos distintos (n)</span></li>
          <li><b>${c.maxima}</b><span>frecuencia máxima (k = ${c.k})</span></li>
          <li><b>${numero(c.unaVez)}</b><span>aparecen una sola vez</span></li>
        </ul>
      </div>
      <div class="dos-columnas" style="margin-top:16px">
        <div class="bloque">
          <div class="bloque-cabeza"><h3>Los 15 términos más frecuentes</h3>
            <span class="meta">apariciones en los 20 abstracts</span></div>
          <div class="barras">${barras}</div>
        </div>
        <div class="bloque">
          <div class="bloque-cabeza"><h3>El mismo ranking con los tres algoritmos</h3>
            <span class="estado ${d.iguales ? "ok" : "error"}">${d.iguales
              ? `Coinciden en las ${numero(c.distintos)} posiciones` : "Los rankings no coinciden"}</span></div>
          <div class="marco"><table class="tabla compacta">
            <thead><tr><th class="num">#</th><th>Término</th><th class="num">Frec.</th>${d.algoritmos.map((a) => `<th class="centro">${escapar(a.nombre.replace(" Sort", ""))}</th>`).join("")}</tr></thead>
            <tbody>${filas}</tbody></table></div>
          <p class="meta" style="margin:12px 0 0">Empate a ${d.empate.frecuencia} apariciones: ${empatados}. Los tres
            algoritmos son estables, así que conservan el orden de primera aparición.</p>
        </div>
      </div>
      <div class="bloque">
        <div class="bloque-cabeza"><h3>Tiempo por ordenamiento en este servidor</h3>
          <span class="meta">mediana de ${d.repeticiones} ejecuciones tras el calentamiento de la JVM</span></div>
        <div class="marco"><table class="tabla">
          <thead><tr><th>Algoritmo</th><th>Complejidad</th><th class="num">Tiempo (ms)</th><th class="num">Relativo</th></tr></thead>
          <tbody>${d.algoritmos.map((a) => `<tr><td>${escapar(a.nombre)}</td><td class="meta">${{
            "Counting Sort": "O(n + k)", "Radix Sort": "O(d · (n + b))", "Bucket Sort": "O(n) esperado" }[a.nombre] || ""}</td>
            <td class="num">${numero(a.msMediana, 4)}</td>
            <td class="num">${numero(a.msMediana / d.algoritmos[0].msMediana, 1)} ×</td></tr>`).join("")}</tbody>
        </table></div>
        <p class="meta" style="margin:12px 0 0">El documento técnico reporta la medición completa: 30 ejecuciones en JVM
          nuevas, los experimentos de escalabilidad y el de jerarquía de memoria.</p>
      </div>`;
  } catch (e) {
    estado.cargado.ranking = false;
    $("#ranking-resultado").innerHTML = `<span class="error">${escapar(e.message)}</span>`;
  }
}

// ---------- Similitud ----------
function dibujarSelector() {
  const contenedor = $("#sim-selector");
  contenedor.innerHTML = '<legend class="sr">Artículos a comparar</legend>' + estado.articulos
    .map((a) => `<label class="opcion"><input type="checkbox" value="${a.numero}" ${estado.seleccion.has(a.numero) ? "checked" : ""}>
      <span><b>${a.numero}</b>${escapar(a.autoresCortos)} (${anioTexto(a)}) · ${escapar(recortar(a.titulo, 64))}</span></label>`)
    .join("");
  contenedor.querySelectorAll("input").forEach((casilla) =>
    casilla.addEventListener("change", () => {
      const n = Number(casilla.value);
      casilla.checked ? estado.seleccion.add(n) : estado.seleccion.delete(n);
      actualizarBotonComparar();
    }));
  actualizarBotonComparar();
}

function actualizarBotonComparar() {
  const cantidad = estado.seleccion.size;
  $("#sim-comparar").disabled = cantidad < 2;
  $("#sim-comparar").textContent = cantidad < 2 ? "Elija dos o más" : `Comparar ${cantidad} artículos`;
}

function colorCelda(valor, ia) {
  const v = Math.max(0, Math.min(1, valor));
  const desde = [246, 247, 248];
  const hasta = ia ? [217, 96, 40] : [32, 96, 192];
  const rgb = desde.map((c, i) => Math.round(c + (hasta[i] - c) * v));
  return { fondo: `rgb(${rgb.join(",")})`, texto: v > 0.55 ? "#fff" : "#15171c" };
}

async function comparar() {
  const numeros = [...estado.seleccion].sort((a, b) => a - b);
  if (numeros.length < 2) return;
  mostrarEstado("#sim-estado", "Calculando…");
  $("#sim-resultado").innerHTML = '<div class="bloque"><span class="esqueleto"></span></div>';
  try {
    const d = await api("/api/similitud?articulos=" + numeros.join(","));
    mostrarEstado("#sim-estado", "");
    let html = "";
    if (numeros.length === 2) {
      html += `<div class="bloque">
        <div class="bloque-cabeza"><h3>Artículo ${numeros[0]} frente a artículo ${numeros[1]}</h3>
          <span><span class="leyenda"><i></i>Clásico</span>&nbsp;&nbsp;<span class="leyenda"><i class="ia"></i>IA</span></span></div>
        <div class="barras apiladas">${d.algoritmos.map((a) => `<span class="rotulo">${escapar(a.nombre)}</span>
          <span class="pista"><span class="relleno ${esIA(a.enfoque) ? "ia" : ""}" style="width:${Math.max(0, a.matriz[0][1]) * 100}%"></span></span>
          <span class="valor">${numero(a.matriz[0][1], 4)}</span>`).join("")}</div>
        <p class="meta" style="margin:14px 0 0">1 = textos idénticos. Cada medida tiene su propia escala: el documento
          técnico compara este par con los 190 pares del corpus.</p></div>`;
    }
    html += '<div class="rejilla" style="margin-top:16px">' + d.algoritmos.map((a) => matrizHtml(a, numeros)).join("") + "</div>";
    $("#sim-resultado").innerHTML = html;
  } catch (e) {
    mostrarEstado("#sim-estado", e.message, "error");
    $("#sim-resultado").innerHTML = "";
  }
}

function matrizHtml(algoritmo, numeros) {
  const ia = esIA(algoritmo.enfoque);
  const filas = algoritmo.matriz
    .map((fila, i) => `<tr><th scope="row">${numeros[i]}</th>${fila.map((v) => {
      const c = colorCelda(v, ia);
      return `<td style="background:${c.fondo};color:${c.texto}">${numero(v, 2)}</td>`;
    }).join("")}</tr>`)
    .join("");
  return `<div class="bloque"><div class="bloque-cabeza"><h3>${escapar(algoritmo.nombre)}<span class="etiqueta ${ia ? "ia" : ""}">${algoritmo.enfoque}</span></h3>
    <span class="meta">${numero(algoritmo.ms, 2)} ms</span></div>
    <div style="overflow-x:auto"><table class="matriz"><thead><tr><th></th>${numeros.map((n) => `<th scope="col">${n}</th>`).join("")}</tr></thead>
    <tbody>${filas}</tbody></table></div></div>`;
}

$("#sim-comparar").addEventListener("click", comparar);
$("#sim-limpiar").addEventListener("click", () => {
  estado.seleccion.clear();
  dibujarSelector();
  $("#sim-resultado").innerHTML = "";
  mostrarEstado("#sim-estado", "");
});
$("#sim-caso").addEventListener("click", () => {
  estado.seleccion = new Set([11, 16]);
  dibujarSelector();
  comparar();
});

// ---------- Agrupamiento ----------
async function cargarAgrupamiento(representacion) {
  estado.cargado.agrupamiento = true;
  mostrarEstado("#agr-estado", "Calculando…");
  try {
    const d = await api("/api/agrupamiento" + (representacion ? "?representacion=" + representacion : ""));
    const segmentado = $("#agr-representacion");
    segmentado.innerHTML = Object.entries(d.representaciones)
      .map(([clave, nombre]) => `<button type="button" role="radio" data-clave="${clave}"
        aria-checked="${clave === d.representacion}">${escapar(nombre)}</button>`)
      .join("");
    segmentado.querySelectorAll("button").forEach((b) =>
      b.addEventListener("click", () => {
        if (b.getAttribute("aria-checked") === "true") return;
        history.replaceState(null, "", "#agrupamiento=" + b.dataset.clave);
        cargarAgrupamiento(b.dataset.clave);
      }));
    mostrarEstado("#agr-estado", "");
    const ks = Object.keys(d.resultados[0].silueta);
    const tabla = `<div class="bloque"><div class="bloque-cabeza"><h3>Evaluación interna</h3>
      <span class="meta">CCC: fidelidad del dendrograma a las distancias. Silueta: grupos compactos y separados (−1 a 1).</span></div>
      <div class="marco"><table class="tabla"><thead><tr><th>Criterio</th><th class="num">CCC</th>
      ${ks.map((k) => `<th class="num ocultar-movil">k = ${k}</th>`).join("")}<th class="num">Mejor k</th></tr></thead><tbody>
      ${d.resultados.map((r) => `<tr><td>${escapar(r.criterio)}${r.criterio === d.mejorCriterio ? ' <span class="mejor">· mayor CCC</span>' : ""}</td>
        <td class="num ${r.criterio === d.mejorCriterio ? "fuerte" : ""}">${numero(r.ccc, 3)}</td>
        ${ks.map((k) => `<td class="num ocultar-movil ${String(r.mejorK) === k ? "fuerte" : ""}">${numero(r.silueta[k], 3)}</td>`).join("")}
        <td class="num">${r.mejorK}</td></tr>`).join("")}</tbody></table></div></div>`;
    const dendrogramas = d.resultados
      .map((r) => `<div class="bloque"><div class="bloque-cabeza"><h3>${escapar(r.criterio)}</h3>
        <span class="meta">CCC ${numero(r.ccc, 3)} · corte en k = ${r.mejorK} · ${numero(r.ms, 2)} ms</span></div>
        <div class="dendrograma">${r.svg}</div></div>`)
      .join("");
    $("#agr-resultado").innerHTML = tabla + dendrogramas;
  } catch (e) {
    estado.cargado.agrupamiento = false;
    mostrarEstado("#agr-estado", e.message, "error");
  }
}

// ---------- Búsqueda ----------
async function buscar() {
  const patron = $("#bus-patron").value;
  if (!patron.trim()) {
    mostrarEstado("#bus-estado", "Escriba un patrón para buscar.", "error");
    return;
  }
  mostrarEstado("#bus-estado", "Buscando…");
  try {
    const parametros = new URLSearchParams({ patron, mayusculas: $("#bus-mayusculas").checked ? "si" : "no" });
    const d = await api("/api/busqueda?" + parametros);
    history.replaceState(null, "", "#busqueda=" + encodeURIComponent(patron));
    const total = d.algoritmos[0].ocurrencias;
    mostrarEstado("#bus-estado", total
      ? `${total} ocurrencias en ${d.articulos.length} abstracts; los tres algoritmos encontraron las mismas posiciones`
      : "Sin ocurrencias en los 20 abstracts", d.coinciden && total ? "ok" : "");
    const tabla = `<div class="bloque"><div class="bloque-cabeza"><h3>Comparación de los algoritmos</h3>
      <span class="meta">preprocesamiento del patrón y búsqueda en los 20 abstracts</span></div>
      <div class="marco"><table class="tabla compacta"><thead><tr><th>Algoritmo</th><th class="num">Comparaciones</th>
      <th class="num">Alineaciones</th><th class="num">Colisiones</th><th class="num">Preproceso (ms)</th>
      <th class="num">Búsqueda (ms)</th></tr></thead><tbody>
      ${d.algoritmos.map((a) => `<tr><td>${escapar(a.nombre)}</td><td class="num">${numero(a.comparaciones)}</td>
        <td class="num">${numero(a.ventanas)}</td><td class="num">${a.colisiones}</td>
        <td class="num">${numero(a.msPreproceso, 4)}</td><td class="num">${numero(a.msBusqueda, 4)}</td></tr>`).join("")}
      </tbody></table></div></div>`;
    const ocurrencias = d.articulos.length ? `<div class="bloque">${d.articulos
      .map((a) => `<div class="ocurrencia"><h3>${a.numero}. ${escapar(a.titulo)}</h3>
        <span class="meta">${escapar(a.autoresCortos)} (${anioTexto(a)}) · ${a.fragmentos.length} ${a.fragmentos.length === 1 ? "ocurrencia" : "ocurrencias"}</span>
        ${a.fragmentos.map((f) => `<p class="fragmento"><span class="pos">car. ${f.posicion}</span>${escapar(f.antes)}<mark>${escapar(f.coincidencia)}</mark>${escapar(f.despues)}</p>`).join("")}
      </div>`).join("")}</div>` : "";
    $("#bus-resultado").innerHTML = tabla + ocurrencias;
  } catch (e) {
    mostrarEstado("#bus-estado", e.message, "error");
  }
}

$("#bus-formulario").addEventListener("submit", (evento) => {
  evento.preventDefault();
  buscar();
});

// ---------- Enlaces directos: #ranking · #similitud=11,16 · #agrupamiento=minilm · #busqueda=learning ----------
function abrirDesdeEnlace() {
  const [panel, valor] = decodeURIComponent(location.hash.slice(1)).split("=");
  if (!panel) {
    abrirPanel("corpus", { actualizarEnlace: false });
    return;
  }
  if (panel === "similitud" && valor) {
    estado.seleccion = new Set(valor.split(",").map(Number).filter(Boolean));
    dibujarSelector();
    comparar();
  }
  if (panel === "agrupamiento") cargarAgrupamiento(valor);
  if (panel === "busqueda" && valor) {
    $("#bus-patron").value = valor;
    buscar();
  }
  abrirPanel(panel, { actualizarEnlace: false });
}

window.addEventListener("hashchange", abrirDesdeEnlace);

moverIndicador(pestanas[0]);
cargarCorpus()
  .then(abrirDesdeEnlace)
  .catch((e) => {
    $("#tabla-corpus tbody").innerHTML = `<tr><td colspan="5" class="error">${escapar(e.message)}</td></tr>`;
  });
