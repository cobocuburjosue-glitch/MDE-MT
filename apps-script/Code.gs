/* =====================================================================
   MDE MT · Malezas y Madurantes — Apps Script de la hoja USUARIOS MDE MT
   ---------------------------------------------------------------------
   Una sola lista de usuarios para:
     · la portada MDE MT (este script)
     · Dashboard Drones, Calibración de boquillas y Catálogo de equipo
       (sus scripts leen la pestaña USUARIOS de esta hoja)

   Pestañas:
     USUARIOS  USUARIO · CONTRASEÑA · NOMBRE · CORREO · ROL ·
               DASHBOARD DRONES · CALIBRACION BOQUILLAS · CATALOGO EQUIPOS ·
               BOTONES EXTRAS CALIBRACION · ACTIVO
     ACCESOS   registro de cada ingreso a la portada
     REPORTES  historial de reportes generados (por grupo)

   Instalar: Extensiones › Apps Script › pegar este archivo › ejecutar
   "configurar" › Implementar › Nueva implementación › Aplicación web
   (Ejecutar como: Yo · Acceso: Cualquier persona) › copiar la URL /exec.
   ===================================================================== */

const TZ = 'America/Guatemala';
const TOKEN_DIAS = 30;
const MODULOS = { drones: 'DASHBOARD DRONES', calibracion: 'CALIBRACION BOQUILLAS', catalogo: 'CATALOGO EQUIPOS' };
const NOMBRE_MOD = { drones: 'Dashboard Drones', calibracion: 'Calibración de boquillas', catalogo: 'Catálogo de equipo' };

const COLS = {
  USUARIOS: ['USUARIO', 'CONTRASEÑA', 'NOMBRE', 'CORREO', 'ROL', 'DASHBOARD DRONES', 'CALIBRACION BOQUILLAS',
             'CATALOGO EQUIPOS', 'BOTONES EXTRAS CALIBRACION', 'ACTIVO'],
  ACCESOS:  ['FECHA Y HORA', 'USUARIO', 'NOMBRE', 'APELLIDO', 'CORREO', 'DISPOSITIVO'],
  REPORTES: ['FECHA Y HORA', 'GRUPO', 'MODULO', 'USUARIO', 'NOMBRE']
};

function ss_() { return SpreadsheetApp.getActiveSpreadsheet(); }
function norm_(s) { return String(s == null ? '' : s).normalize('NFD').replace(/[̀-ͯ]/g, '').toUpperCase().replace(/\s+/g, ' ').trim(); }
function ahora_() { return Utilities.formatDate(new Date(), TZ, 'yyyy-MM-dd HH:mm'); }
function json_(o) { return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON); }
function hoja_(n) {
  let sh = ss_().getSheetByName(n);
  if (!sh) { sh = ss_().insertSheet(n); sh.appendRow(COLS[n]); sh.getRange(1, 1, 1, COLS[n].length).setFontWeight('bold'); sh.setFrozenRows(1); }
  return sh;
}

/* Ejecutar una vez: crea las pestañas que falten */
function configurar() { Object.keys(COLS).forEach(hoja_); return 'Listo: ' + Object.keys(COLS).join(', '); }

/* ---------- usuarios ---------- */
function leerUsuarios_() {
  const sh = hoja_('USUARIOS');
  const v = sh.getDataRange().getValues();
  const h = v[0].map(norm_);
  const c = k => h.indexOf(norm_(k));
  const si = (r, k) => c(k) < 0 || norm_(r[c(k)]) !== 'NO';      // vacío = SI
  return v.slice(1).filter(r => String(r[c('USUARIO')]).trim()).map(r => {
    const rol = norm_(r[c('ROL')]);
    const mods = {};
    Object.keys(MODULOS).forEach(k => mods[k] = si(r, MODULOS[k]));
    return {
      usuario: String(r[c('USUARIO')]).trim(),
      clave: String(r[c('CONTRASEÑA')]).trim(),
      nombre: String(r[c('NOMBRE')] || r[c('USUARIO')]).trim(),
      correo: c('CORREO') >= 0 ? String(r[c('CORREO')]).trim() : '',
      rol: rol === 'ADMINISTRADOR' ? 'ADMINISTRADOR' : rol === 'PUBLICO' ? 'PUBLICO' : 'USUARIO',
      activo: si(r, 'ACTIVO'),
      modulos: mods
    };
  });
}

function login_(p) {
  const u = leerUsuarios_().find(x => norm_(x.usuario) === norm_(p.usuario));
  if (!u || u.clave !== String(p.clave || '').trim()) return { ok: false, error: 'Usuario o contraseña incorrectos.' };
  if (!u.activo) return { ok: false, error: 'Este usuario está desactivado. Consulte al administrador.' };
  if (u.rol === 'PUBLICO') {
    if (!p.nombre || !p.apellido) return { ok: false, falta: 'correo', error: 'Escriba su nombre y apellido.' };
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(p.correo || '')) return { ok: false, falta: 'correo', error: 'Escriba un correo válido.' };
  }
  hoja_('ACCESOS').appendRow([ahora_(), u.usuario, p.nombre || u.nombre, p.apellido || '', p.correo || u.correo, p.dispositivo || '']);
  const props = PropertiesService.getScriptProperties();
  limpiarTokens_(props);
  const token = Utilities.getUuid();
  props.setProperty('tok_' + token, JSON.stringify({ u: u.usuario, exp: Date.now() + TOKEN_DIAS * 864e5 }));
  return { ok: true, token: token, usuario: u.usuario, nombre: u.nombre, rol: u.rol, modulos: u.modulos };
}

function conSesion_(token, soloAdmin, fn) {
  const raw = token && PropertiesService.getScriptProperties().getProperty('tok_' + token);
  if (!raw) return { ok: false, sesion: false, error: 'La sesión venció. Vuelva a entrar.' };
  const t = JSON.parse(raw);
  if (t.exp < Date.now()) return { ok: false, sesion: false, error: 'La sesión venció. Vuelva a entrar.' };
  const u = leerUsuarios_().find(x => norm_(x.usuario) === norm_(t.u));
  if (!u || !u.activo) return { ok: false, sesion: false, error: 'Usuario dado de baja.' };
  if (soloAdmin && u.rol !== 'ADMINISTRADOR') return { ok: false, error: 'Solo el administrador puede hacer esto.' };
  return fn(u);
}

function limpiarTokens_(props) {
  const all = props.getProperties(), now = Date.now();
  Object.keys(all).forEach(k => {
    if (k.indexOf('tok_') !== 0) return;
    try { if (JSON.parse(all[k]).exp < now) props.deleteProperty(k); } catch (e) { props.deleteProperty(k); }
  });
}

/* ---------- reportes ---------- */
function registrarReporte_(u, modulo) {
  if (!MODULOS[modulo]) return { ok: false, error: 'Grupo de reporte desconocido.' };
  hoja_('REPORTES').appendRow([ahora_(), NOMBRE_MOD[modulo], modulo, u.usuario, u.nombre]);
  return { ok: true };
}
function reportes_() {
  const sh = hoja_('REPORTES');
  if (sh.getLastRow() < 2) return { ok: true, reportes: [] };
  const v = sh.getRange(2, 1, sh.getLastRow() - 1, 5).getDisplayValues();
  return { ok: true, reportes: v.reverse().slice(0, 500).map(r => ({ fecha: r[0], mod: r[2], usuario: r[4] || r[3] })) };
}

/* ---------- web app ---------- */
function doGet() { return json_({ ok: true, app: 'MDE MT' }); }
function doPost(e) {
  try {
    const p = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    switch (p.action) {
      case 'ping':     return json_({ ok: true });
      case 'login':    return json_(login_(p));
      case 'logout':   PropertiesService.getScriptProperties().deleteProperty('tok_' + p.token); return json_({ ok: true });
      case 'reporte':  return json_(conSesion_(p.token, false, u => registrarReporte_(u, p.modulo)));
      case 'reportes': return json_(conSesion_(p.token, true, () => reportes_()));
      default:         return json_({ ok: false, error: 'Acción desconocida: ' + p.action });
    }
  } catch (err) { return json_({ ok: false, error: String(err && err.message || err) }); }
}

/* =====================================================================
   PARA LOS OTROS SCRIPTS (Vuelos, Calibración, Catálogo)
   Copiar esta función en cada uno, poner el ID de esta hoja y usarla
   en lugar de su pestaña USUARIOS local. modulo: 'DASHBOARD DRONES',
   'CALIBRACION BOQUILLAS' o 'CATALOGO EQUIPOS'.
   ---------------------------------------------------------------------
   const ID_USUARIOS_MDE = 'ID_DE_ESTA_HOJA';
   function usuariosMDE_(modulo) { ... }   (ver Calibración o Catálogo)
   ===================================================================== */
