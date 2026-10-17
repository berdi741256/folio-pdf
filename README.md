# Folio PDF

Lector y editor de PDF que funciona **sin conexión**. Se instala como app en Android, iPhone/iPad y PC.

## Funciones
- Leer PDF con zoom y contador de páginas.
- Anotar: texto, dibujo a mano alzada (firmas), resaltado, recuadros y "Tapar" para ocultar datos.
- Mover y borrar anotaciones, deshacer y rehacer.
- Organizar páginas: girar, reordenar, duplicar, eliminar, agregar páginas en blanco y unir varios PDF.
- Guardar una copia nueva (`nombre-editado.pdf`). El original no se modifica.
- En Windows, Mac y Chromebook: se registra como app para abrir PDF y puede ser el lector predeterminado.
- En Android, abrir un PDF desde **Compartir → Folio PDF**.

Todo se procesa en el dispositivo; ningún archivo se envía a internet.

## Publicar en GitHub Pages
1. Crea un repositorio (por ejemplo `folio-pdf`) y sube todo el contenido de esta carpeta a la raíz.
2. En el repositorio: **Settings → Pages → Source: Deploy from a branch → main / (root) → Save**.
3. Abre `https://<tu-usuario>.github.io/folio-pdf/` y usa **Instalar** (o en el navegador: menú → *Instalar app* / *Agregar a pantalla de inicio*).

Tras abrirla una vez con internet, funciona sin conexión.

## Actualizar
Cambia `VERSION` en `sw.js` (por ejemplo `folio-v2`) cada vez que modifiques archivos, para que los dispositivos instalados descarguen la versión nueva.

## Licencias
pdf.js (Apache 2.0), pdf-lib (MIT), fuentes Instrument Sans y JetBrains Mono (OFL 1.1). Ver `lib/LICENCIAS.txt`.
