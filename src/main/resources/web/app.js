"use strict";

const estado = { articulos: [], seleccion: new Set([11, 16]) };

const $ = (selector) => document.querySelector(selector);
const formato = (valor, decimales = 4) =>
  Number(valor).toLocaleString("es-CO", { minimumFractionDigits: decimales, maximumFractionDigits: decimales });
const escapar = (texto) =>
  String(texto).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

async function api(ruta) {
  const respuesta = await fetch(ruta);
  const datos = await respuesta.json();
  if (!respuesta.ok) throw new Error(datos.error || "Error " + respuesta.status);
  return datos;
}

// ---------- Pestañas ----------
document.querySelectorAll(".pestana").forEach((boton) => {
  boton.addEventListener("click", () => {
    document.querySelectorAll(".pestana").forEach((b) => b.classList.toggle("activa", b === boton));
    document.querySelectorAll(".panel").forEach((p) => p.classList.toggle("activo", p.id === boton.dataset.panel));
    if (boton.dataset.panel === "agrupamiento" && !$("#agr-resultado").dataset.cargado) cargarAgrupamiento();
  });
});

// ---------- Corpus ----------
async function cargarCorpus() {
  const datos = await api("/api/articulos");
  estado.articulos = datos.articulos;
  const cuerpo = $("#tabla-corpus tbody");
  cuerpo.innerHTML = datos.articulos
    .map((a) => `<tr data-numero="${a.numero}"><td>${a.numero}</td><td>${escapar(a.titulo)}</td>
      <td>${escapar(a.autores.join(", "))}</td><td class="num">${a.palabras}</td></tr>`)
    .join("");
  cuerpo.querySelectorAll("tr").forEach((fila) =>
    fila.addEventListener("click", () => {
      const siguiente = fila.nextElementSibling;
      if (siguiente && siguiente.classList.contains("fila-abstract")) {
        siguiente.remove();
        return;
      }
      const articulo = estado.articulos.find((a) => a.numero === Number(fila.dataset.numero));
      fila.insertAdjacentHTML("afterend",
        `<tr class="fila-abstract"><td></td><td colspan="3">${escapar(articulo.abstract)}</td></tr>`);
    }));
  dibujarSelector();
}

// ---------- Similitud ----------
function dibujarSelector() {
  $("#sim-selector").innerHTML = estado.articulos
    .map((a) => {
      const marcado = estado.seleccion.has(a.numero);
      return `<label class="${marcado ? "marcado" : ""}"><input type="checkbox" value="${a.numero}"
        ${marcado ? "checked" : ""}><span><b>A${a.numero}</b> ${escapar(a.autoresCortos)} · ${escapar(recortar(a.titulo, 70))}</span></label>`;
    })
    .join("");
  $("#sim-selector").querySelectorAll("input").forEach((casilla) =>
    casilla.addEventListener("change", () => {
      const numero = Number(casilla.value);
      casilla.checked ? estado.seleccion.add(numero) : estado.seleccion.delete(numero);
      casilla.closest("label").classList.toggle("marcado", casilla.checked);
    }));
}

const recortar = (texto, largo) => (texto.length > largo ? texto.slice(0, largo - 1) + "…" : texto);

function colorCelda(valor) {
  const v = Math.max(0, Math.min(1, valor));
  const desde = [244, 246, 251];
  const hasta = [32, 96, 192];
  const rgb = desde.map((c, i) => Math.round(c + (hasta[i] - c) * v));
  return { fondo: `rgb(${rgb.join(",")})`, texto: v > 0.55 ? "#fff" : "#1d232b" };
}

async function comparar() {
  const numeros = [...estado.seleccion].sort((a, b) => a - b);
  if (numeros.length < 2) {
    $("#sim-estado").innerHTML = '<span class="error">Seleccione al menos dos artículos.</span>';
    return;
  }
  $("#sim-estado").textContent = "Calculando…";
  try {
    const datos = await api("/api/similitud?articulos=" + numeros.join(","));
    $("#sim-estado").textContent = "";
    let html = "";
    if (numeros.length === 2) {
      html += `<div class="tarjeta"><h3>A${numeros[0]} frente a A${numeros[1]}</h3>
        <div class="meta">Similitud de cada algoritmo (1 = textos idénticos)</div>
        <table class="tabla barras"><tbody>${datos.algoritmos
          .map((a) => `<tr><td>${escapar(a.nombre)}<span class="etiqueta ${a.enfoque === "IA" ? "ia" : ""}">${a.enfoque}</span></td>
            <td class="num">${formato(a.matriz[0][1])}</td>
            <td style="width:45%"><div class="barra-valor ${a.enfoque === "IA" ? "ia" : ""}"
              style="width:${Math.max(0, a.matriz[0][1]) * 100}%"></div></td>
            <td class="num">${formato(a.ms, 3)} ms</td></tr>`)
          .join("")}</tbody></table></div>`;
    }
    html += '<div class="rejilla">' + datos.algoritmos.map((a) => matrizHtml(a, numeros)).join("") + "</div>";
    $("#sim-resultado").innerHTML = html;
  } catch (e) {
    $("#sim-estado").innerHTML = `<span class="error">${escapar(e.message)}</span>`;
  }
}

function matrizHtml(algoritmo, numeros) {
  const filas = algoritmo.matriz
    .map((fila, i) => `<tr><th>A${numeros[i]}</th>${fila
      .map((v) => {
        const c = colorCelda(v);
        return `<td style="background:${c.fondo};color:${c.texto}">${formato(v, 2)}</td>`;
      })
      .join("")}</tr>`)
    .join("");
  return `<div class="tarjeta"><h3>${escapar(algoritmo.nombre)}<span class="etiqueta ${algoritmo.enfoque === "IA" ? "ia" : ""}">
    ${algoritmo.enfoque}</span></h3><div class="meta">${formato(algoritmo.ms, 3)} ms</div>
    <div class="tabla-desplazable"><table class="matriz"><thead><tr><th></th>${numeros
      .map((n) => `<th>A${n}</th>`).join("")}</tr></thead><tbody>${filas}</tbody></table></div></div>`;
}

$("#sim-comparar").addEventListener("click", comparar);
$("#sim-limpiar").addEventListener("click", () => {
  estado.seleccion.clear();
  dibujarSelector();
  $("#sim-resultado").innerHTML = "";
});
$("#sim-caso").addEventListener("click", () => {
  estado.seleccion = new Set([11, 16]);
  dibujarSelector();
  comparar();
});

// ---------- Agrupamiento ----------
async function cargarAgrupamiento(representacion) {
  $("#agr-estado").textContent = "Calculando…";
  try {
    const datos = await api("/api/agrupamiento" + (representacion ? "?representacion=" + representacion : ""));
    const selector = $("#agr-representacion");
    if (!selector.options.length) {
      selector.innerHTML = Object.entries(datos.representaciones)
        .map(([clave, nombre]) => `<option value="${clave}">${escapar(nombre)}</option>`).join("");
      selector.addEventListener("change", () => cargarAgrupamiento(selector.value));
    }
    selector.value = datos.representacion;
    $("#agr-estado").textContent = "";
    const ks = Object.keys(datos.resultados[0].silueta);
    const tablaMetricas = `<div class="tarjeta"><h3>Evaluación interna</h3>
      <div class="meta">CCC: correlación entre las distancias originales y las del dendrograma. Silueta: compacidad y
      separación de los grupos al cortar el árbol en k grupos (de −1 a 1).</div>
      <div class="tabla-desplazable"><table class="tabla"><thead><tr><th>Criterio</th><th class="num">CCC</th>
      ${ks.map((k) => `<th class="num">Silueta k=${k}</th>`).join("")}<th class="num">Mejor k</th></tr></thead><tbody>
      ${datos.resultados.map((r) => `<tr><td>${escapar(r.criterio)}${r.criterio === datos.mejorCriterio
        ? '<span class="etiqueta mejor">mayor CCC</span>' : ""}</td><td class="num">${formato(r.ccc)}</td>
        ${ks.map((k) => `<td class="num">${formato(r.silueta[k], 3)}</td>`).join("")}
        <td class="num">${r.mejorK}</td></tr>`).join("")}</tbody></table></div></div>`;
    const dendrogramas = datos.resultados
      .map((r) => `<div class="tarjeta"><h3>${escapar(r.criterio)}</h3>
        <div class="meta">CCC ${formato(r.ccc)} · corte en k = ${r.mejorK} (silueta ${formato(r.mejorSilueta, 3)}) ·
        ${formato(r.ms, 3)} ms</div><div class="dendrograma">${r.svg}</div></div>`)
      .join("");
    $("#agr-resultado").innerHTML = tablaMetricas + dendrogramas;
    $("#agr-resultado").dataset.cargado = "si";
  } catch (e) {
    $("#agr-estado").innerHTML = `<span class="error">${escapar(e.message)}</span>`;
  }
}

// ---------- Búsqueda ----------
$("#bus-formulario").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  const patron = $("#bus-patron").value;
  if (!patron) return;
  $("#bus-estado").textContent = "Buscando…";
  try {
    const parametros = new URLSearchParams({ patron, mayusculas: $("#bus-mayusculas").checked ? "si" : "no" });
    const datos = await api("/api/busqueda?" + parametros);
    const total = datos.algoritmos[0].ocurrencias;
    $("#bus-estado").textContent = `${total} ocurrencias en ${datos.articulos.length} abstracts · los tres algoritmos `
      + (datos.coinciden ? "encontraron las mismas posiciones" : "NO coinciden");
    const tabla = `<div class="tarjeta"><h3>Comparación de los algoritmos</h3>
      <div class="meta">Comparaciones de caracteres, alineaciones examinadas, colisiones de hash y tiempos
      (preprocesamiento del patrón y búsqueda en los 20 abstracts).</div>
      <div class="tabla-desplazable"><table class="tabla"><thead><tr><th>Algoritmo</th><th class="num">Ocurrencias</th>
      <th class="num">Comparaciones</th><th class="num">Alineaciones</th><th class="num">Colisiones</th>
      <th class="num">Preproceso (ms)</th><th class="num">Búsqueda (ms)</th></tr></thead><tbody>
      ${datos.algoritmos.map((a) => `<tr><td>${escapar(a.nombre)}</td><td class="num">${a.ocurrencias}</td>
        <td class="num">${a.comparaciones.toLocaleString("es-CO")}</td><td class="num">${a.ventanas.toLocaleString("es-CO")}</td>
        <td class="num">${a.colisiones}</td><td class="num">${formato(a.msPreproceso, 4)}</td>
        <td class="num">${formato(a.msBusqueda, 4)}</td></tr>`).join("")}</tbody></table></div></div>`;
    const ocurrencias = datos.articulos
      .map((a) => `<div class="tarjeta"><h3>A${a.numero} · ${escapar(a.titulo)}</h3>
        <div class="meta">${escapar(a.autoresCortos)} · ${a.fragmentos.length} ocurrencia(s)</div>
        ${a.fragmentos.map((f) => `<div class="fragmento">[${f.posicion}] ${escapar(f.antes)}<mark>${escapar(f.coincidencia)}</mark>${escapar(f.despues)}</div>`).join("")}
      </div>`)
      .join("");
    $("#bus-resultado").innerHTML = tabla + ocurrencias;
  } catch (e) {
    $("#bus-estado").innerHTML = `<span class="error">${escapar(e.message)}</span>`;
  }
});

cargarCorpus().catch((e) => {
  $("#tabla-corpus tbody").innerHTML = `<tr><td colspan="4" class="error">${escapar(e.message)}</td></tr>`;
});
