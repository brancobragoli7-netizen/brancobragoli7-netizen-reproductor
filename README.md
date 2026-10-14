# Música

Reproductor para Android que lee la música guardada en el teléfono, sin internet.
Negro y blanco, tipografía tipo Times New Roman, bordes rectos y animaciones que
siguen el ritmo de lo que suena (barras, onda y anillo — tocá la animación para cambiar).

## Conseguir el APK

**Opción A — GitHub (sin instalar nada):**
1. Creá un repositorio en github.com y subí esta carpeta completa.
2. Entrá a la pestaña **Actions** → "Compilar APK". Se ejecuta solo con cada cambio
   (o tocá *Run workflow*). Tarda unos 5 minutos.
3. Abrí la ejecución terminada y descargá **Musica-apk**: es un .zip con el APK adentro.

**Opción B — Android Studio en una computadora:**
1. *File → Open* y elegí esta carpeta. Esperá a que termine de sincronizar.
2. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
3. El APK queda en `app/build/outputs/apk/`.

## Instalar en el teléfono

Pasá el APK al celular, abrilo y aceptá "instalar apps de origen desconocido".
Se lo podés mandar igual a un amigo.

## Carpetas

En la pestaña **Carpetas** podés crear carpetas propias, ponerles nombre y foto,
elegir sus canciones y cambiar el orden con *Editar* (flechas). También podés mantener
apretada cualquier canción para sumarla a una carpeta.

## Permisos

- **Música y audio:** para encontrar tus canciones.
- **Grabar audio:** Android lo exige para leer lo que suena *dentro de la app* y animar.
  No usa el micrófono ni guarda nada. Si lo rechazás, la app funciona igual sin animaciones.
- **Notificaciones:** para los controles en la barra y la pantalla de bloqueo.

## Notas

- Muestra archivos MP3, FLAC, M4A, OGG, WAV, etc. de más de 30 segundos.
- Las descargas de YouTube Premium están cifradas y solo las reproduce la app de YouTube.
- La fuente es Liberation Serif (licencia SIL OFL), con las mismas medidas que Times New Roman.
