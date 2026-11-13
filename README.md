# MDE MT · Malezas y Madurantes

Portada única del Departamento de Malezas y Madurantes (Ingenio Madre Tierra) que reúne tres apps:

| Botón | App (repositorio) | Datos |
|---|---|---|
| **DASHBOARD DRONES** | `VUELOS-DRONES` | Hoja de vuelos (BOCETO) |
| **CALIBRACIÓN DE BOQUILLAS DRONE** | `CALIBRACION-DRONES` | BASE DE DATOS CALIBRACION DRONES |
| **CATÁLOGO DE EQUIPO** | `EQUIPOS-MALEZAS` | EQUIPOS TALLER MALEZAS |
| **REPORTES** (solo administrador) | — | Historial por grupo |

Cada app sigue guardando en su propio Google Sheets y Apps Script; **no se toca nada de ellas**. MDE MT solo es la entrada.

## Cómo funciona el ingreso
1. El usuario entra con su usuario y contraseña. Se valida contra la **hoja de vuelos drones** (pestaña de usuarios del Apps Script de VUELOS-DRONES).
2. Con el mismo usuario y contraseña, MDE MT inicia sesión también en Calibración y en Catálogo y deja las tres sesiones guardadas en el navegador.
3. Al tocar un botón, la app se abre dentro de la portada ya con la sesión iniciada. El botón **‹ Portada** regresa.

Si un usuario **no existe** en la pestaña USUARIOS de Calibración o de Catálogo, la tarjeta muestra *"pedirá ingreso"* y esa app pedirá su propio usuario la primera vez.

> Esto funciona porque las cuatro páginas están en el mismo dominio
> `https://cobocuburjosue-glitch.github.io/`. Si alguna app se mueve a otro dominio (Netlify, etc.), dejará de compartir la sesión.

### Administrador
`COBO` (y cualquier usuario con rol admin en la hoja de vuelos). Solo el administrador ve el botón **REPORTES**. Para cambiarlo: `ADMINISTRADORES` al inicio del `<script>` en `index.html`.

## Reportes
Tres grupos: Dashboard Drones, Calibración de boquillas y Catálogo de equipo. **Generar reporte** abre la app y su ventana de reporte. Cada vez que se genera un PDF (botón *Imprimir* en vuelos, *Generar y enviar a mi correo* en calibración, *Descargar PDF* en catálogo) queda anotado en su grupo con fecha y usuario. El historial se guarda en el dispositivo.

## Imágenes de fondo
Ponga sus fotos en la carpeta `fondos/` con estos nombres exactos (JPG, idealmente 1200×800 o menos de 400 KB):

| Archivo | Dónde sale |
|---|---|
| `fondos/portada.jpg` | Fondo de arriba (cielo / campo) |
| `fondos/dashboard-drones.jpg` | Botón DASHBOARD DRONES |
| `fondos/calibracion-boquillas.jpg` | Botón CALIBRACIÓN DE BOQUILLAS DRONE |
| `fondos/catalogo-equipos.jpg` | Botón CATÁLOGO DE EQUIPO |
| `fondos/reportes.jpg` | Botón REPORTES |

Si falta una imagen, el botón muestra un degradado verde.

## Publicar
1. Cree el repositorio público **MDE-MT** en GitHub (usuario `cobocuburjosue-glitch`).
2. Suba todos estos archivos.
3. **Settings › Pages** › Branch `main` / root › Save.
4. Abra `https://cobocuburjosue-glitch.github.io/MDE-MT/` › en el celular, menú ⋮ › **Agregar a pantalla principal**.
