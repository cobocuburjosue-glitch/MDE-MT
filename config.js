/* =====================================================================
   MDE MT · configuración compartida (la usan la portada y las 3 apps)
   MDE_API: URL /exec del Apps Script de la hoja "USUARIOS MDE MT".
   ===================================================================== */
window.MDE_API = 'https://script.google.com/macros/s/AKfycbyg1l-joKgkvRaM5yPalcxpp1IxwOt1koMuO3TFJsc17NKvPguxCRE05qzffJEVqB7HzQ/exec';

/* Dispositivo desde el que se entra (para el historial de accesos) */
window.mdeDispositivo = function(){
  var ua = navigator.userAgent;
  if (window.AppAndroid || window.AndroidApp || /MDEMT|CalibracionMTApp|; wv\)/.test(ua)) return 'App Android';
  if (/iPhone|iPad/i.test(ua)) return 'Navegador iPhone';
  if (/Android/i.test(ua)) return 'Navegador Android';
  return 'Computadora';
};

/* Anota en el historial una entrada DIRECTA a una app (sin pasar por la portada).
   Dentro de la portada no hace nada: ahí la portada ya lo anota. */
window.mdeRegistrarAcceso = function(modulo, usuario, clave, extra){
  try {
    if (window.top !== window) return;
    if (!/^https:\/\/script\.google\.com\//.test(window.MDE_API || '')) return;
    var cuerpo = { action:'login', soloRegistro:true, modulo:modulo, usuario:usuario, clave:clave, dispositivo:window.mdeDispositivo() };
    if (extra) for (var k in extra) cuerpo[k] = extra[k];
    fetch(window.MDE_API, { method:'POST', body:JSON.stringify(cuerpo) }).catch(function(){});
  } catch(e){}
};
