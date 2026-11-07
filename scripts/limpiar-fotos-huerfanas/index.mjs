// Borra de Firebase Storage las fotos de fotos_proyectos/ que ningún proyecto de Firestore usa.
// Por defecto solo muestra lo que borraría; con --borrar las elimina de verdad.
//
//   node index.mjs --clave ./cuenta-servicio.json            (revisar)
//   node index.mjs --clave ./cuenta-servicio.json --borrar   (borrar)
//
// Opciones: --horas-minimas N (24 por defecto) deja intactos los archivos más recientes,
// por si una subida todavía no terminó de escribir su URL en Firestore.

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { initializeApp, cert } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';

const COLECCION = 'proyectos_mina';
const PREFIJO = 'fotos_proyectos/';
const CAMPOS_FOTO = ['fotoUri', 'fotoMiniaturaUri'];

const aqui = dirname(fileURLToPath(import.meta.url));

function leerOpciones(argv) {
  const opciones = { borrar: false, horasMinimas: 24, clave: process.env.GOOGLE_APPLICATION_CREDENTIALS };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--borrar') opciones.borrar = true;
    else if (arg === '--clave') opciones.clave = argv[++i];
    else if (arg === '--horas-minimas') opciones.horasMinimas = Number(argv[++i]);
    else throw new Error(`Opción desconocida: ${arg}`);
  }
  if (!opciones.clave) {
    throw new Error('Falta la clave de la cuenta de servicio: --clave ./cuenta-servicio.json');
  }
  if (!Number.isFinite(opciones.horasMinimas) || opciones.horasMinimas < 0) {
    throw new Error('--horas-minimas debe ser un número mayor o igual a 0');
  }
  return opciones;
}

function leerJson(ruta) {
  return JSON.parse(readFileSync(ruta, 'utf8'));
}

/** Ruta del objeto dentro del bucket para las tres formas de URL que puede tener una foto. */
function rutaDesdeUrl(url) {
  if (typeof url !== 'string') return null;
  const descarga = url.match(/\/o\/([^?#]+)/);
  if (descarga) return decodeURIComponent(descarga[1]);
  const gs = url.match(/^gs:\/\/[^/]+\/(.+)$/);
  if (gs) return gs[1];
  const publica = url.match(/^https:\/\/storage\.googleapis\.com\/[^/]+\/([^?#]+)/);
  if (publica) return decodeURIComponent(publica[1]);
  return null;
}

/** Mismo id corto que SubirFotoWorker pone al final del nombre de la carpeta. */
function idCorto(idProyecto) {
  return idProyecto.replaceAll('-', '').slice(0, 8);
}

function carpetaDe(ruta) {
  return ruta.slice(0, ruta.lastIndexOf('/'));
}

function formatearTamano(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
}

async function main() {
  const opciones = leerOpciones(process.argv.slice(2));

  const servicios = leerJson(resolve(aqui, '../../app/google-services.json'));
  const proyectoFirebase = servicios.project_info.project_id;
  const bucket = servicios.project_info.storage_bucket;

  const clave = leerJson(resolve(opciones.clave));
  if (clave.project_id !== proyectoFirebase) {
    throw new Error(`La clave es del proyecto "${clave.project_id}", pero la app usa "${proyectoFirebase}"`);
  }

  initializeApp({ credential: cert(clave), storageBucket: bucket });

  const documentos = (await getFirestore().collection(COLECCION).get()).docs;
  if (documentos.length === 0) {
    throw new Error(`"${COLECCION}" no tiene documentos; no se borra nada para no vaciar el bucket por error`);
  }

  const rutasEnUso = new Set();
  const carpetasProtegidas = new Set();
  for (const doc of documentos) {
    for (const campo of CAMPOS_FOTO) {
      const valor = doc.get(campo);
      if (!valor) continue;
      const ruta = rutaDesdeUrl(valor);
      if (ruta) rutasEnUso.add(ruta);
      // file:// = subida pendiente en el teléfono; su carpeta puede tener archivos aún sin URL en Firestore
      else if (valor.startsWith('file://')) carpetasProtegidas.add(idCorto(doc.id));
    }
  }

  const [archivos] = await getStorage().bucket().getFiles({ prefix: PREFIJO });
  const limite = Date.now() - opciones.horasMinimas * 3600 * 1000;

  const huerfanos = [];
  let recientes = 0;
  let pendientes = 0;
  for (const archivo of archivos) {
    if (archivo.name.endsWith('/') || rutasEnUso.has(archivo.name)) continue;

    const carpeta = carpetaDe(archivo.name);
    if ([...carpetasProtegidas].some((id) => carpeta.endsWith(`_${id}`))) {
      pendientes++;
      continue;
    }
    if (new Date(archivo.metadata.updated).getTime() > limite) {
      recientes++;
      continue;
    }
    huerfanos.push(archivo);
  }

  const existentes = new Set(archivos.map((a) => a.name));
  const rotas = [...rutasEnUso].filter((r) => r.startsWith(PREFIJO) && !existentes.has(r));

  const porCarpeta = new Map();
  for (const archivo of huerfanos) {
    const carpeta = carpetaDe(archivo.name);
    if (!porCarpeta.has(carpeta)) porCarpeta.set(carpeta, []);
    porCarpeta.get(carpeta).push(archivo);
  }
  for (const [carpeta, lista] of [...porCarpeta].sort(([a], [b]) => a.localeCompare(b))) {
    console.log(`\n${carpeta}/`);
    for (const archivo of lista) {
      console.log(`  ${archivo.name.slice(carpeta.length + 1)}  (${formatearTamano(Number(archivo.metadata.size))})`);
    }
  }

  const bytes = huerfanos.reduce((total, a) => total + Number(a.metadata.size), 0);
  console.log(`
Proyectos en Firestore:         ${documentos.length}
Archivos en ${PREFIJO}:   ${archivos.length}
Fotos en uso:                   ${rutasEnUso.size}
Huérfanas a borrar:             ${huerfanos.length} (${formatearTamano(bytes)}) en ${porCarpeta.size} carpetas
Omitidas por subida pendiente:  ${pendientes}
Omitidas por recientes (<${opciones.horasMinimas} h):  ${recientes}`);

  if (rotas.length > 0) {
    console.log(`\nAviso: ${rotas.length} foto(s) usadas en Firestore ya no existen en Storage (se ven rotas en la app):`);
    rotas.forEach((r) => console.log(`  ${r}`));
  }

  if (!opciones.borrar) {
    if (huerfanos.length > 0) console.log('\nModo revisión: no se borró nada. Repite con --borrar para eliminarlas.');
    return;
  }

  let borradas = 0;
  for (const archivo of huerfanos) {
    try {
      await archivo.delete({ ignoreNotFound: true });
      borradas++;
    } catch (e) {
      console.error(`No se pudo borrar ${archivo.name}: ${e.message}`);
    }
  }
  console.log(`\nBorradas ${borradas} de ${huerfanos.length}.`);
}

main().catch((e) => {
  console.error(`Error: ${e.message}`);
  process.exitCode = 1;
});
