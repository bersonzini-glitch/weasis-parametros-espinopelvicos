# Parámetros espinopélvicos — Weasis plugin

[Español](#español) · [English](#english)

Plugin for **Weasis 4.7** (Mac and Windows) that measures sagittal spinopelvic alignment on a lateral standing radiograph: **PI, PT, SS, LL, PI−LL, thoracic kyphosis, T1PA and SVA**, with the SRS-Schwab sagittal modifiers. Same measurements as the [Horos plugin](https://github.com/bersonzini-glitch/horos-parametros-espinopelvicos) and [columna-spine.com/medidor](https://www.columna-spine.com/medidor/).

---

## Español

### Qué mide

| Parámetro | Definición |
|---|---|
| **PI** – Incidencia pélvica | Ángulo entre la perpendicular al platillo de S1 en su punto medio y la línea al centro de las cabezas femorales |
| **PT** – Tilt pélvico | Ángulo entre la vertical y la línea cabezas femorales – punto medio de S1 |
| **SS** – Slope sacro | Ángulo entre el platillo de S1 y la horizontal |
| **LL** – Lordosis lumbar | Cobb entre el platillo superior de L1 y el de S1 |
| **PI−LL** | Mismatch pelvis-lordosis |
| **TK** – Cifosis torácica | Cobb entre el platillo superior de T4 (o T5) y el inferior de T12 |
| **T1PA** – T1 pelvic angle | Ángulo entre las líneas cabezas femorales–centro de T1 y cabezas femorales–punto medio de S1 |
| **SVA** | Distancia horizontal entre la plomada de C7 y la esquina posterosuperior de S1 |

También muestra los **modificadores SRS-Schwab** de PI−LL, PT y SVA (0 / + / ++).

Convenciones: LL y TK positivos significan lordosis y cifosis. SVA y PT positivos significan hacia anterior. El paciente puede mirar a la izquierda o a la derecha, y las líneas se pueden dibujar en cualquier sentido.

### Requisitos

- **Weasis 4.7** instalado (Mac o Windows).

### Instalación en Mac

1. Cerrá Weasis.
2. Descargá **`Instalar-Espinopelvico-Weasis-Mac.pkg`** desde [Releases](../../releases/latest) y hacé doble clic.
3. Como el instalador no está notarizado por Apple, la Mac puede decir que no puede verificarlo: tocá **OK**, andá a **Ajustes del Sistema → Privacidad y seguridad**, bajá hasta el final y tocá **"Abrir igualmente"**.
4. Seguí el instalador (pide tu contraseña) y abrí Weasis.

**Desinstalar:** hacé doble clic en `/Library/Application Support/WeasisEspinopelvico/Desinstalar Espinopelvico Weasis.command`.

### Instalación en Windows

1. Cerrá Weasis.
2. Descargá **`Espinopelvico-Weasis-Windows.zip`** desde [Releases](../../releases/latest).
3. Hacé clic derecho en el `.zip` → **Extraer todo**. Tiene que quedar una carpeta con `instalar.bat`, `desinstalar.bat` y el archivo `.jar`.
4. Hacé doble clic en **`instalar.bat`**. Si Windows muestra "Windows protegió su PC", tocá **Más información → Ejecutar de todas formas**. No necesita permisos de administrador.
5. Cuando diga "Listo", abrí Weasis.

**Desinstalar:** doble clic en `desinstalar.bat`, en la misma carpeta.

### Uso

1. Abrí en Weasis una **telerradiografía de columna completa de perfil**, de pie.
2. En la barra lateral derecha del visor, abrí la pestaña **Espinopélvico**. Conviene fijarla con el botón de su esquina superior para que quede abierta. Igual funciona si se pliega al hacer clic en la imagen.
3. El panel te va pidiendo cada paso y **elige solo la herramienta de Weasis** que corresponde:
   1. **Cabeza femoral 1:** *círculo por 3 puntos*. Tres clics sobre el borde de la cabeza femoral.
   2. **Cabeza femoral 2** (opcional; **Saltar** si se superponen): otro círculo.
   3. **Platillo de S1:** *línea* de esquina a esquina del platillo superior.
   4. **Platillo superior de L1:** línea.
   5. **Platillo inferior de T12** (opcional): línea.
   6. **Platillo superior de T4 o T5** (opcional; elegí el nivel en el menú): línea.
   7. **Cuerpo de T1** (opcional): línea diagonal de esquina a esquina del cuerpo. Su punto medio se toma como el centro.
   8. **Cuerpo de C7** (opcional): ídem.
4. Los valores y las líneas auxiliares (perpendicular de S1, línea a las caderas, plomada de C7) se actualizan en vivo. Para corregir, **seleccioná el trazo y arrastrá sus extremos**. Podés usar mover, zoom o cualquier otra herramienta de Weasis: el panel no te la cambia. Para volver a marcar, tocá un paso en la lista.
5. **Borrar trazo** borra el trazo del paso elegido para volver a hacerlo. **Reiniciar** borra todos los trazos de la medición y empieza de nuevo.
6. **Copiar resultados** copia el informe al portapapeles.

Los trazos son mediciones comunes de Weasis: se guardan y se exportan como cualquier otra. Las líneas auxiliares no se guardan.

**SVA en mm.** Si la imagen trae espaciado de píxel DICOM, el SVA sale en mm. Si no, sale en píxeles hasta que calibres con la herramienta de calibración de Weasis, sobre una línea de longitud conocida. La magnificación radiográfica puede alterar el SVA. Los ángulos no dependen de la escala.

### Cómo funciona la instalación

El plugin es un módulo OSGi (`.jar`) que se copia en `~/.weasis/plugins` (en Windows, `%USERPROFILE%\.weasis\plugins`), junto con `espinopelvico.json`, que lo suma a la lista de módulos de Weasis. El instalador define la variable de entorno estándar de Java **`JAVA_TOOL_OPTIONS`** con `-Dfelix.extended.config.properties=` apuntando a ese archivo:
- **Mac:** con un agente de inicio de sesión (`~/Library/LaunchAgents/com.columnaspine.weasis-espinopelvico.plist`).
- **Windows:** como variable de usuario.

No modifica Weasis, así que sigue funcionando cuando Weasis se actualiza. Si en una versión mayor de Weasis cambia algo interno, va a hacer falta una versión nueva del plugin.

Efecto secundario inofensivo: los programas Java que ejecutes desde la terminal van a mostrar el aviso `Picked up JAVA_TOOL_OPTIONS`.

### Compilar desde el código

```bash
./build.sh   # tests de geometría + plugin + instaladores en dist/
```

Necesita un JDK 25 en `.tools/` (Weasis 4.7 está compilado para Java 25) y Weasis instalado en `/Applications`. El script toma de ahí los módulos contra los que compila.

### Aviso

Herramienta de apoyo para medición. Los valores dependen de dónde se marquen los reparos y deben ser revisados por el profesional. Este software **no es un dispositivo médico** y se distribuye "tal cual", sin garantía (ver [LICENSE](LICENSE)).

---

## English

### What it measures

PI, PT, SS, LL (L1–S1), PI−LL, TK (T4 or T5 to T12), T1PA and SVA, with the SRS-Schwab modifiers for PI−LL, PT and SVA. Same definitions and sign conventions as the Horos plugin.

### Install

- **Mac:** quit Weasis, run **`Instalar-Espinopelvico-Weasis-Mac.pkg`** from [Releases](../../releases/latest). If macOS says it can't verify the installer, allow it in **System Settings → Privacy & Security → Open Anyway**. Then open Weasis.
- **Windows:** quit Weasis, extract **`Espinopelvico-Weasis-Windows.zip`**, double-click **`instalar.bat`** (no admin rights needed; if SmartScreen appears, choose **More info → Run anyway**). Then open Weasis.

Uninstall with `Desinstalar Espinopelvico Weasis.command` (Mac, in `/Library/Application Support/WeasisEspinopelvico`) or `desinstalar.bat` (Windows).

### Usage

Open a standing lateral whole-spine radiograph and the **Espinopélvico** tab on the right side of the 2D viewer. The panel picks the right Weasis tool for each step:
- **Femoral heads:** 3-point circle.
- **S1, L1, T12 and T4/T5 endplates:** line.
- **T1 and C7 bodies:** a diagonal line, whose midpoint is taken as the body center.

Each finished graphic is assigned to the waiting step, and values update live as graphics are edited. The landmarks are ordinary Weasis measurements, so they are saved with the image.

### How the install works

The bundle lives in `~/.weasis/plugins` with an extension config (`espinopelvico.json`) that adds it to Weasis' `felix.auto.start` list. The Java-standard **`JAVA_TOOL_OPTIONS`** variable passes `-Dfelix.extended.config.properties` to Weasis' launcher. On Mac it is set by a LaunchAgent at login; on Windows it is a user environment variable. Weasis itself is never modified.

### Building

`./build.sh` runs the geometry tests and builds the bundle and both installers into `dist/`. It needs a JDK 25 in `.tools/` and Weasis in `/Applications`, whose bundles it compiles against. The `Import-Package` header is generated with `jdeps`.

| File | Purpose |
|---|---|
| `src/.../SpinopelvicToolFactory.java` | OSGi component (`OSGI-INF/spinopelvic-tool.xml`) registering the panel in the DICOM 2D viewer |
| `src/.../SpinopelvicTool.java` | Panel, step flow, tool selection, assignment of graphics, helper lines |
| `src/.../Geometry.java` | Geometry (no Weasis dependency) |
| `test/GeometryTest.java` | Synthetic tests: facing both ways, lines drawn in either direction |

### Disclaimer

A measurement aid. Values depend on landmark placement and must be reviewed by the clinician. This software is **not a medical device** and is provided "as is", without warranty (see [LICENSE](LICENSE)).

---

Made by **Bernardo Sonzini Astudillo** — [columna-spine.com](https://columna-spine.com)
