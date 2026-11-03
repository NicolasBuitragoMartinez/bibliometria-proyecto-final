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
const articuloPorNumero = (n) =>
  estado.articulos.find((a) => a.numero === n) || { numero: n, titulo: "", autoresCortos: `Artículo ${n}`, anio: 0 };
// Mismos colores que usa Dendrograma.java para los grupos del corte.
const COLORES_GRUPO = ["#2060C0", "#D96028", "#289870", "#8E44AD", "#C0392B", "#B7950B", "#16A085", "#7F8C8D"];
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
    // Cruce con los metadatos: qué artículo es cada número de las matrices.
    let html = `<details class="bloque comparados" ${numeros.length <= 4 ? "open" : ""}>
      <summary>Artículos comparados (${numeros.length})</summary>
      <ul>${numeros.map((n) => articuloPorNumero(n)).map((a) => `<li><b>${a.numero}</b><span>${escapar(a.autoresCortos)}
        (${anioTexto(a)}) · ${escapar(a.titulo)}</span></li>`).join("")}</ul></details>`;
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
    if (numeros.length === 2) html += '<div class="bloque" id="matriz-dp"><span class="esqueleto"></span></div>';
    $("#sim-resultado").innerHTML = html;
    if (numeros.length === 2) cargarMatriz(numeros[0], numeros[1]);
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

// ---------- Similitud: matriz de programación dinámica paso a paso ----------
// Llena la matriz de Levenshtein o de Needleman-Wunsch celda por celda con su recurrencia y termina
// mostrando el camino óptimo. La matriz y el camino los calcula el servidor con las clases de cada algoritmo.
const matrizDp = { datos: null, algoritmo: "levenshtein", n: 12, paso: 0, a: 0, b: 0 };

async function cargarMatriz(a, b) {
  Object.assign(matrizDp, { a, b });
  try {
    const parametros = new URLSearchParams({ algoritmo: matrizDp.algoritmo, a, b, n: matrizDp.n });
    matrizDp.datos = await api("/api/matriz?" + parametros);
    matrizDp.paso = 0;
    dibujarMatriz();
  } catch (e) {
    $("#matriz-dp").innerHTML = `<div class="bloque-cabeza"><h3>Matriz paso a paso</h3></div><p class="error">${escapar(e.message)}</p>`;
  }
}

const simbolo = (s) => (s === " " ? "·" : escapar(s));
const valor = (v) => (v < 0 ? "−" + Math.abs(v) : String(v));
const veces = (cantidad, singular, plural) => `${cantidad} ${cantidad === 1 ? singular : plural}`;

function dibujarMatriz() {
  const d = matrizDp.datos;
  const lev = d.clave === "levenshtein";
  const letra = lev ? "D" : "F";
  const m = d.filas.length;
  const n = d.columnas.length;
  const formula = lev
    ? `D[i][j] = mín(D[i−1][j−1] + costo, D[i−1][j] + 1, D[i][j−1] + 1), con costo 0 si los caracteres coinciden y 1 si no`
    : `F[i][j] = máx(F[i−1][j−1] + s(aᵢ, bⱼ), F[i−1][j] − 1, F[i][j−1] − 1), con s = +1 si los términos coinciden y −1 si no`;
  const encabezado = `<tr><th scope="col">${letra}</th><th scope="col">ε</th>${d.columnas
    .map((c, j) => `<th scope="col" data-col="${j + 1}" class="${lev ? "" : "vertical"}">${simbolo(c)}</th>`).join("")}</tr>`;
  const filas = d.matriz.map((fila, i) => `<tr><th scope="row" data-fila="${i}">${i === 0 ? "ε" : simbolo(d.filas[i - 1])}</th>${fila
    .map((_, j) => `<td data-i="${i}" data-j="${j}"></td>`).join("")}</tr>`).join("");

  $("#matriz-dp").innerHTML = `
    <div class="bloque-cabeza"><h3>Matriz paso a paso</h3>
      <span class="meta">Artículo ${d.a.numero} (filas) frente a artículo ${d.b.numero} (columnas) · primeros ${m} y ${n} ${d.unidad}</span></div>
    <div class="herramientas">
      <div class="segmentado" role="radiogroup" aria-label="Algoritmo de programación dinámica">
        <button type="button" role="radio" data-algoritmo="levenshtein" aria-checked="${lev}">Levenshtein</button>
        <button type="button" role="radio" data-algoritmo="needleman" aria-checked="${!lev}">Needleman-Wunsch</button>
      </div>
      <label class="control-n">n =
        <input type="range" id="matriz-n" min="2" max="20" value="${matrizDp.n}" aria-label="Cantidad de ${d.unidad}">
        <output id="matriz-n-valor">${matrizDp.n}</output> ${d.unidad}</label>
    </div>
    <p class="formula"><code>${formula}</code></p>
    <div class="marco-dp" tabindex="0" aria-label="Matriz de programación dinámica; use las flechas para avanzar">
      <table class="matriz-dp"><thead>${encabezado}</thead><tbody>${filas}</tbody></table>
    </div>
    <ul class="leyenda-paso"><li><i class="actual"></i>Celda que se calcula</li><li><i class="fuente"></i>Celdas de las que depende</li>
      <li><i class="camino"></i>Camino óptimo (al final, en negrita)</li></ul>
    <div class="paso-control">
      <button type="button" class="boton secundario icono" id="matriz-anterior" aria-label="Paso anterior">${FLECHA_IZQ}</button>
      <input type="range" id="matriz-rango" min="0" max="${m * n + 1}" value="0" aria-label="Paso">
      <button type="button" class="boton secundario icono" id="matriz-siguiente" aria-label="Paso siguiente">${FLECHA_DER}</button>
      <button type="button" class="boton fantasma" id="matriz-final">Ver el camino óptimo</button>
      <span class="meta" id="matriz-numero"></span>
    </div>
    <p class="explicacion" id="matriz-explicacion" aria-live="polite"></p>
    <div id="matriz-alineamiento"></div>`;

  const contenedor = $("#matriz-dp");
  contenedor.querySelectorAll("[data-algoritmo]").forEach((b) => b.addEventListener("click", () => {
    if (b.dataset.algoritmo === matrizDp.algoritmo) return;
    matrizDp.algoritmo = b.dataset.algoritmo;
    cargarMatriz(matrizDp.a, matrizDp.b);
  }));
  $("#matriz-n").addEventListener("input", (e) => { $("#matriz-n-valor").textContent = e.target.value; });
  $("#matriz-n").addEventListener("change", (e) => {
    matrizDp.n = Number(e.target.value);
    cargarMatriz(matrizDp.a, matrizDp.b);
  });
  const ir = (paso) => {
    matrizDp.paso = Math.max(0, Math.min(m * n + 1, paso));
    actualizarMatriz();
  };
  $("#matriz-anterior").addEventListener("click", () => ir(matrizDp.paso - 1));
  $("#matriz-siguiente").addEventListener("click", () => ir(matrizDp.paso + 1));
  $("#matriz-final").addEventListener("click", () => ir(m * n + 1));
  $("#matriz-rango").addEventListener("input", (e) => ir(Number(e.target.value)));
  contenedor.querySelector(".marco-dp").addEventListener("keydown", (e) => {
    if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
      e.preventDefault();
      ir(matrizDp.paso + (e.key === "ArrowRight" ? 1 : -1));
    }
  });
  actualizarMatriz();
}

/** Operación de cada paso del camino: diagonal (coincidencia o sustitución), abajo o derecha. */
function operacionesDelCamino(d) {
  const lev = d.clave === "levenshtein";
  return d.camino.slice(1).map(([i, j], k) => {
    const [i0, j0] = d.camino[k];
    if (i === i0 + 1 && j === j0 + 1) {
      const igual = d.filas[i0] === d.columnas[j0];
      return { a: d.filas[i0], b: d.columnas[j0], tipo: igual ? "coincidencia" : "sustitución", marca: igual ? "=" : "≠" };
    }
    if (i === i0 + 1) return { a: d.filas[i0], b: "–", tipo: lev ? "eliminación" : "hueco en b", marca: "−" };
    return { a: "–", b: d.columnas[j0], tipo: lev ? "inserción" : "hueco en a", marca: "+" };
  });
}

function actualizarMatriz() {
  const d = matrizDp.datos;
  const lev = d.clave === "levenshtein";
  const letra = lev ? "D" : "F";
  const m = d.filas.length;
  const n = d.columnas.length;
  const k = matrizDp.paso;
  const final = k === m * n + 1;
  const [ci, cj] = k >= 1 && !final ? [Math.floor((k - 1) / n) + 1, ((k - 1) % n) + 1] : [-1, -1];
  // La celda (i, j) se calcula en el paso (i − 1)·n + j; la fila y la columna 0 son el caso base (paso 0).
  const calculada = (i, j) => i === 0 || j === 0 || final || (i - 1) * n + j <= k;
  const enCamino = new Set(final ? d.camino.map(([i, j]) => i + "," + j) : []);
  const fuentes = new Set(ci > 0 ? [`${ci - 1},${cj - 1}`, `${ci - 1},${cj}`, `${ci},${cj - 1}`] : []);

  $("#matriz-dp").querySelectorAll("td").forEach((td) => {
    const i = Number(td.dataset.i);
    const j = Number(td.dataset.j);
    td.textContent = calculada(i, j) ? valor(d.matriz[i][j]) : "";
    td.className = i === ci && j === cj ? "actual" : fuentes.has(i + "," + j) ? "fuente" : enCamino.has(i + "," + j) ? "camino" : "";
  });
  $("#matriz-dp").querySelectorAll("th.activa").forEach((th) => th.classList.remove("activa"));
  if (ci > 0) {
    $("#matriz-dp").querySelector(`th[data-fila="${ci}"]`).classList.add("activa");
    $("#matriz-dp").querySelector(`th[data-col="${cj}"]`).classList.add("activa");
  }

  let texto;
  if (k === 0) {
    texto = lev
      ? `Caso base: D[i][0] = i (borrar los primeros i caracteres de a) y D[0][j] = j (insertar los primeros j de b).`
      : `Caso base: F[i][0] = −i y F[0][j] = −j, porque alinear contra huecos cuesta −1 por término.`;
  } else if (!final) {
    const M = d.matriz;
    const x = d.filas[ci - 1];
    const y = d.columnas[cj - 1];
    const igual = x === y;
    if (lev) {
      const costo = igual ? d.costos.coincidencia : d.costos.sustitucion;
      texto = `D[${ci}][${cj}]: «${x === " " ? "espacio" : x}» y «${y === " " ? "espacio" : y}» ${igual ? "coinciden (costo 0)" : "son distintos (costo 1)"}. `
        + `mín(${igual ? "coincidencia" : "sustitución"} ${M[ci - 1][cj - 1]} + ${costo} = ${M[ci - 1][cj - 1] + costo}, `
        + `eliminación ${M[ci - 1][cj]} + 1 = ${M[ci - 1][cj] + 1}, inserción ${M[ci][cj - 1]} + 1 = ${M[ci][cj - 1] + 1}) = ${M[ci][cj]}.`;
    } else {
      const s = igual ? d.costos.coincidencia : d.costos.sustitucion;
      const h = d.costos.hueco;
      texto = `F[${ci}][${cj}]: «${x}» y «${y}» ${igual ? "coinciden (s = +1)" : "son distintos (s = −1)"}. `
        + `máx(diagonal ${valor(M[ci - 1][cj - 1])} ${s > 0 ? "+" : "−"} 1 = ${valor(M[ci - 1][cj - 1] + s)}, `
        + `hueco en b ${valor(M[ci - 1][cj])} − 1 = ${valor(M[ci - 1][cj] + h)}, `
        + `hueco en a ${valor(M[ci][cj - 1])} − 1 = ${valor(M[ci][cj - 1] + h)}) = ${valor(M[ci][cj])}.`;
    }
  } else {
    const ops = operacionesDelCamino(d);
    const cuenta = (tipo) => ops.filter((o) => o.tipo === tipo).length;
    texto = lev
      ? `Distancia de edición: D[${m}][${n}] = ${d.resultado}. El camino óptimo usa ${veces(cuenta("coincidencia"), "coincidencia", "coincidencias")}, `
        + `${veces(cuenta("sustitución"), "sustitución", "sustituciones")}, ${veces(cuenta("eliminación"), "eliminación", "eliminaciones")} `
        + `y ${veces(cuenta("inserción"), "inserción", "inserciones")}.`
      : `Puntaje del alineamiento: F[${m}][${n}] = ${valor(d.resultado)}. El camino óptimo tiene `
        + `${veces(cuenta("coincidencia"), "coincidencia", "coincidencias")}, ${veces(cuenta("sustitución"), "sustitución", "sustituciones")} `
        + `y ${veces(cuenta("hueco en a") + cuenta("hueco en b"), "hueco", "huecos")}.`;
  }
  const explicacion = $("#matriz-explicacion");
  explicacion.textContent = texto;
  explicacion.className = "explicacion" + (final ? " ok" : "");

  // Al final, el alineamiento que corresponde al camino: = coincide, ≠ sustitución, − y + huecos.
  const ops = final ? operacionesDelCamino(d) : [];
  $("#matriz-alineamiento").innerHTML = final ? `<div class="marco-dp"><table class="aux alineamiento-dp">
      <tr><th>a (art. ${d.a.numero})</th>${ops.map((o) => `<td>${simbolo(o.a)}</td>`).join("")}</tr>
      <tr><th>b (art. ${d.b.numero})</th>${ops.map((o) => `<td>${simbolo(o.b)}</td>`).join("")}</tr>
      <tr><th>operación</th>${ops.map((o) => `<td title="${o.tipo}">${o.marca}</td>`).join("")}</tr></table></div>
    <p class="meta">= coincide · ≠ sustitución · − ${lev ? "eliminación" : "hueco en b"} · + ${lev ? "inserción" : "hueco en a"}</p>` : "";

  $("#matriz-rango").value = k;
  $("#matriz-numero").textContent = k === 0 ? `Caso base · ${m * n} celdas por calcular`
    : final ? "Camino óptimo" : `Celda ${k} de ${m * n} · ${letra}[${ci}][${cj}]`;
  $("#matriz-anterior").disabled = k === 0;
  $("#matriz-siguiente").disabled = final;
}

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
        <div class="dendrograma">${r.svg}</div>${gruposHtml(r)}</div>`)
      .join("");
    $("#agr-resultado").innerHTML = tabla + dendrogramas;
  } catch (e) {
    estado.cargado.agrupamiento = false;
    mostrarEstado("#agr-estado", e.message, "error");
  }
}

/** Los artículos de cada grupo al cortar el dendrograma en el mejor k, con su título, autores y año. */
function gruposHtml(r) {
  return `<details class="grupos"><summary>Artículos de cada grupo con k = ${r.mejorK}</summary>
    ${r.grupos.map((g, i) => `<div class="grupo"><h4><i style="background:${COLORES_GRUPO[i % COLORES_GRUPO.length]}"></i>
      Grupo ${i + 1} · ${g.length} ${g.length === 1 ? "artículo" : "artículos"}</h4>
      <ul>${g.map((n) => articuloPorNumero(n)).map((a) => `<li><b>A${a.numero}</b> ${escapar(a.autoresCortos)}
        (${anioTexto(a)}) · ${escapar(a.titulo)}</li>`).join("")}</ul></div>`).join("")}</details>`;
}

// ---------- Búsqueda ----------
async function buscar(caso = {}) {
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
        ${a.fragmentos.map((f) => `<p class="fragmento"><span class="pos">car. ${f.posicion}</span>${escapar(f.antes)}<mark>${escapar(f.coincidencia)}</mark>${escapar(f.despues)}
          <button type="button" class="enlace-paso" data-articulo="${a.numero}" data-posicion="${f.posicion}">Paso a paso</button></p>`).join("")}
      </div>`).join("")}</div>` : "";
    const paso = total ? '<div class="bloque" id="pasos"><span class="esqueleto"></span></div>' : "";
    $("#bus-resultado").innerHTML = tabla + paso + ocurrencias;
    $("#bus-resultado").querySelectorAll(".enlace-paso").forEach((b) => b.addEventListener("click", async () => {
      await cargarPasos(Number(b.dataset.articulo), Number(b.dataset.posicion));
      $("#pasos").scrollIntoView({ behavior: "smooth", block: "start" });
    }));
    if (total) {
      const primero = d.articulos[0];
      cargarPasos(caso.articulo || primero.numero, caso.posicion ?? primero.fragmentos[0].posicion, caso.irAOcurrencia);
    }
  } catch (e) {
    mostrarEstado("#bus-estado", e.message, "error");
  }
}

$("#bus-formulario").addEventListener("submit", (evento) => {
  evento.preventDefault();
  buscar();
});

// ---------- Búsqueda: paso a paso ----------
// Cada alineación del patrón sobre una oración: qué se compara, dónde falla y cuánto se desplaza.
const pasos = { datos: null, algoritmo: 0, paso: 0, articulo: 0, posicion: 0, q: "pequeno" };
const FLECHA_IZQ = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="m10 3.5-4.5 4.5 4.5 4.5"/></svg>';
const FLECHA_DER = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3.5 4.5 4.5-4.5 4.5"/></svg>';

async function cargarPasos(articulo, posicion, irAOcurrencia = false) {
  Object.assign(pasos, { articulo, posicion });
  try {
    const parametros = new URLSearchParams({ patron: $("#bus-patron").value, articulo, posicion, q: pasos.q });
    pasos.datos = await api("/api/pasos?" + parametros);
    pasos.paso = 0;
    dibujarPasos();
    if (irAOcurrencia) $("#paso-evento").click();
  } catch (e) {
    $("#pasos").innerHTML = `<div class="bloque-cabeza"><h3>Paso a paso</h3></div><p class="error">${escapar(e.message)}</p>`;
  }
}

const celdas = (texto) => Array.from({ length: texto.length }, (_, i) => texto[i])
  .map((c) => `<span class="celda">${c === " " ? "·" : escapar(c)}</span>`).join("");

function tablaAuxiliar(r, patron) {
  const m = patron.length;
  const fila = (titulo, valores, clave) => `<tr><th>${titulo}</th>${valores
    .map((v, i) => `<td data-${clave}="${i}">${escapar(v)}</td>`).join("")}</tr>`;
  if (r.nombre === "Knuth-Morris-Pratt") {
    return `<table class="aux"><caption>Función de prefijos</caption>${fila("q", [...Array(m).keys()], "col")}
      ${fila("P[q]", patron.split(""), "col")}${fila("π[q]", r.tablas.prefijos, "col")}</table>`;
  }
  if (r.nombre === "Boyer-Moore") {
    const ultima = r.tablas.ultima;
    return `<table class="aux"><caption>Carácter malo</caption>
        ${fila("c", ultima.map((u) => u[0]).concat("otro"), "car")}${fila("última(c)", ultima.map((u) => u[1]).concat(-1), "car")}</table>
      <table class="aux"><caption>Sufijo bueno</caption>${fila("j", [...Array(m + 1).keys()], "j")}
        ${fila("desplazamiento[j]", r.tablas.sufijoBueno, "j")}</table>`;
  }
  const t = r.tablas;
  return `<dl class="cifras-aux"><div><dt>Base d</dt><dd>${t.base}</dd></div><div><dt>Módulo q</dt><dd>${numero(t.primo)}</dd></div>
    <div><dt>d<sup>m−1</sup> mod q</dt><dd>${numero(t.pesoMayor)}</dd></div><div><dt>h(P)</dt><dd>${numero(t.hashPatron)}</dd></div></dl>`;
}

function dibujarPasos() {
  const d = pasos.datos;
  const r = d.algoritmos[pasos.algoritmo];
  const a = d.articulo;
  const rk = r.nombre === "Rabin-Karp";
  $("#pasos").innerHTML = `
    <div class="bloque-cabeza"><h3>Paso a paso</h3>
      <span class="meta">Artículo ${a.numero} · ${escapar(a.autoresCortos)} (${anioTexto(a)}) · oración de ${d.texto.length}
        caracteres, sin distinguir mayúsculas</span></div>
    <div class="herramientas">
      <div class="segmentado" role="radiogroup" aria-label="Algoritmo">${d.algoritmos.map((x, i) =>
        `<button type="button" role="radio" data-i="${i}" aria-checked="${i === pasos.algoritmo}">${escapar(x.nombre)}</button>`).join("")}</div>
      ${rk ? `<div class="segmentado" role="radiogroup" aria-label="Módulo q">
        <button type="button" role="radio" data-q="pequeno" aria-checked="${pasos.q === "pequeno"}">q = 13</button>
        <button type="button" role="radio" data-q="grande" aria-checked="${pasos.q === "grande"}">q = 1.000.000.007</button></div>` : ""}
    </div>
    <div class="auxiliar">${tablaAuxiliar(r, d.patron)}</div>
    <div class="pista-texto" tabindex="0" aria-label="Patrón alineado sobre la oración; use las flechas para avanzar">
      <div class="regla">${Array.from({ length: d.texto.length }, (_, i) => `<span class="celda">${i % 10 === 0 ? i : ""}</span>`).join("")}</div>
      <div class="fila-texto">${celdas(d.texto)}</div>
      <div class="fila-patron">${celdas(d.patron)}</div>
    </div>
    <ul class="leyenda-paso"><li><i class="igual"></i>Coincide</li><li><i class="distinto"></i>No coincide</li>
      ${pasos.algoritmo === 0 ? '<li><i class="conocido"></i>Ya se sabe que coincide (no se compara)</li>' : ""}
      <li><i></i>Sin comparar</li></ul>
    <div class="paso-control">
      <button type="button" class="boton secundario icono" id="paso-anterior" aria-label="Alineación anterior">${FLECHA_IZQ}</button>
      <input type="range" id="paso-rango" min="0" max="${r.alineaciones.length - 1}" value="0" aria-label="Alineación">
      <button type="button" class="boton secundario icono" id="paso-siguiente" aria-label="Alineación siguiente">${FLECHA_DER}</button>
      <button type="button" class="boton fantasma" id="paso-evento">${rk ? "Próximo hash igual" : "Próxima ocurrencia"}</button>
      <span class="meta" id="paso-numero"></span>
    </div>
    <p class="explicacion" id="paso-explicacion" aria-live="polite"></p>
    <p class="meta">En esta oración: ${numero(r.alineaciones.length)} alineaciones · ${numero(r.comparaciones)} comparaciones de
      caracteres${rk ? ` · ${r.colisiones} ${r.colisiones === 1 ? "colisión" : "colisiones"}` : ""} · ocurrencia en la posición
      ${r.posiciones.join(", ") || "—"}.</p>`;

  const contenedor = $("#pasos");
  contenedor.querySelectorAll("[data-i]").forEach((b) => b.addEventListener("click", () => {
    pasos.algoritmo = Number(b.dataset.i);
    pasos.paso = 0;
    dibujarPasos();
  }));
  contenedor.querySelectorAll("[data-q]").forEach((b) => b.addEventListener("click", () => {
    if (b.dataset.q === pasos.q) return;
    pasos.q = b.dataset.q;
    cargarPasos(pasos.articulo, pasos.posicion);
  }));
  const ir = (paso) => {
    pasos.paso = Math.max(0, Math.min(r.alineaciones.length - 1, paso));
    actualizarPaso();
  };
  $("#paso-anterior").addEventListener("click", () => ir(pasos.paso - 1));
  $("#paso-siguiente").addEventListener("click", () => ir(pasos.paso + 1));
  $("#paso-rango").addEventListener("input", (e) => ir(Number(e.target.value)));
  $("#paso-evento").addEventListener("click", () => {
    const siguiente = r.alineaciones.findIndex((x, k) => k > pasos.paso && (x.ocurrencia || (rk && x.comparadas.length)));
    if (siguiente >= 0) ir(siguiente);
  });
  contenedor.querySelector(".pista-texto").addEventListener("keydown", (e) => {
    if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
      e.preventDefault();
      ir(pasos.paso + (e.key === "ArrowRight" ? 1 : -1));
    }
  });
  actualizarPaso();
}

function actualizarPaso() {
  const d = pasos.datos;
  const r = d.algoritmos[pasos.algoritmo];
  const a = r.alineaciones[pasos.paso];
  const m = d.patron.length;
  const textoCeldas = [...$("#pasos .fila-texto").children];
  const comparadas = new Set(a.comparadas);
  textoCeldas.forEach((c, i) => { c.className = "celda" + (i >= a.s && i < a.s + m ? " ventana" : ""); });
  [...$("#pasos .fila-patron").children].forEach((c, j) => {
    const clase = j < a.conocidos ? "conocido" : j === a.falla ? "distinto" : comparadas.has(j) ? "igual" : "";
    c.className = "celda" + (clase ? " " + clase : "");
    if (clase && textoCeldas[a.s + j]) textoCeldas[a.s + j].classList.add(clase);
  });
  $("#pasos .fila-patron").style.transform = `translateX(calc(${a.s} * var(--celda)))`;

  const pista = $("#pasos .pista-texto");
  const ancho = textoCeldas[0].getBoundingClientRect().width;
  const izquierda = a.s * ancho;
  if (izquierda < pista.scrollLeft || izquierda + m * ancho > pista.scrollLeft + pista.clientWidth - 24) {
    const destino = Math.max(0, izquierda - pista.clientWidth / 3);
    // Saltos largos (próxima ocurrencia, barra) sin animación; pasos cortos con desplazamiento suave.
    pista.scrollTo({ left: destino, behavior: Math.abs(destino - pista.scrollLeft) > pista.clientWidth ? "auto" : "smooth" });
  }

  // Celdas de las tablas auxiliares que usa este paso.
  $("#pasos").querySelectorAll(".aux td.activa").forEach((c) => c.classList.remove("activa"));
  const marcar = (atributo, valor) =>
    $("#pasos").querySelectorAll(`.aux td[data-${atributo}="${valor}"]`).forEach((c) => c.classList.add("activa"));
  if (r.nombre === "Knuth-Morris-Pratt") {
    if (a.ocurrencia) marcar("col", m - 1);
    else if (a.falla > 0) marcar("col", a.falla - 1);
  } else if (r.nombre === "Boyer-Moore") {
    marcar("j", a.ocurrencia ? 0 : a.falla + 1);
    if (!a.ocurrencia) {
      const c = d.texto.toLowerCase()[a.s + a.falla];
      const indice = r.tablas.ultima.findIndex((u) => u[0] === c);
      marcar("car", indice >= 0 ? indice : r.tablas.ultima.length);
    }
  }

  $("#paso-rango").value = pasos.paso;
  $("#paso-numero").textContent = `Alineación ${pasos.paso + 1} de ${r.alineaciones.length} · s = ${a.s}`
    + (r.nombre === "Rabin-Karp" ? ` · h = ${numero(a.hash)}` : "");
  const explicacion = $("#paso-explicacion");
  explicacion.textContent = a.explicacion;
  explicacion.className = "explicacion" + (a.ocurrencia ? " ok" : a.falla >= 0 && r.nombre === "Rabin-Karp" ? " aviso" : "");
  $("#paso-anterior").disabled = pasos.paso === 0;
  $("#paso-siguiente").disabled = pasos.paso === r.alineaciones.length - 1;
}

// ---------- Enlaces directos: #ranking · #similitud=11,16 · #agrupamiento=minilm · #busqueda=learning ----------
// #pasos=analysis,18,426 abre el paso a paso del caso de estudio (patrón, artículo, posición) en la ocurrencia.
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
  if (panel === "pasos" && valor) {
    const [patron, articulo, posicion] = valor.split(",");
    $("#bus-patron").value = patron;
    buscar({ articulo: Number(articulo), posicion: Number(posicion), irAOcurrencia: true });
    abrirPanel("busqueda", { actualizarEnlace: false });
    return;
  }
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
