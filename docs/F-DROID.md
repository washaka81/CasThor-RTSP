# Inclusión en F-Droid — CasThor RTSP

Estado de la solicitud de inclusión en F-Droid a 2026-11-19.

## Enlaces

- Merge Request: https://gitlab.com/fdroid/fdroiddata/-/merge_requests/52694
- Pipeline verde: https://gitlab.com/washaka81/fdroiddata/-/pipelines/3088935224
- RFP: https://gitlab.com/fdroid/rfp/-/work_items/4580
- Fork: https://gitlab.com/washaka81/fdroiddata
- Rama: `com.casthor` → `master` de `fdroid/fdroiddata`

## Estado

- MR **abierto** y técnicamente `mergeable`.
- Título: `New app: CasThor RTSP`.
- Descripción: plantilla oficial de F-Droid completa.
- Pipeline **verde** en el commit `f1c058fa`; pasan todos los jobs,
  incluidos `fdroid build` y `check apk`.
- Pendiente: revisión y merge por un **maintainer de F-Droid**. La rama
  `master` de `fdroid/fdroiddata` está protegida y la cuenta `washaka81`
  no tiene rol en el proyecto; el autor no puede fusionar su propio MR.

## Contenido enviado

- `metadata/com.casthor.yml`
- `srclibs/RootEncoder.yml`
- `srclibs/RTSP-Server.yml`

Metadata de la app:

- Versión: `1.0.2` (`versionCode 6`).
- Commit: `05497acef197fa856fe7d10785d5daac65839dcd`.
- `AutoUpdateMode: Version`, `UpdateCheckMode: Tags`.
- `AutoName: CasThor RTSP`.
- Sin `Summary` ni `Description` duplicados: se usan los de Fastlane en
  `fastlane/metadata/android/en-US/`.

## Correcciones aplicadas durante la revisión

1. **Plantilla del MR**: la descripción original era texto libre. Los
   revisores cerraron el MR por no usar
   `.gitlab/merge_request_templates/Default.md`; no era un problema del
   archivo `metadata/com.casthor.yml`, que ya existía.
2. **Título**: `New App: CasThor RTSP (com.casthor)` →
   `New app: CasThor RTSP`.
3. **Metadata duplicada**: se eliminaron `Summary` y `Description` del
   YAML y se añadió `AutoName`; el texto viene del repositorio upstream.
4. **`AutoUpdateMode`**: `Version v%v` no es válido para el esquema; debe
   ser `Version`.
5. **`fdroid rewritemeta`**: se aplicó el formato exacto que genera la
   herramienta, incluidas las líneas largas de `sed` y los espacios
   finales que conserva el emisor YAML.
6. **JDK de los srclibs**: el buildserver de F-Droid (trixie) solo trae
   JDK 21, pero `RootEncoder` y `RTSP-Server` declaran
   `jvmToolchain(17)`. Se usa el JDK 21 del buildserver y se fuerza
   bytecode 17 con `jvmToolchain(21)` +
   `compilerOptions.jvmTarget = JVM_17`; no se instala ni descarga nada.

## Pendientes que puede plantear el revisor

No bloquean la pipeline actual, pero están explicados en la descripción
del MR:

- **Submódulos**: la plantilla pide repos externos como submódulos en
  lugar de `srclibs`. `RootEncoder` y `RTSP-Server` siguen como `srclibs`
  porque deben publicarse en Maven Local antes de compilar la app.
- **Reproducible builds**: no habilitado. F-Droid firmaría el APK con su
  clave; no hay `Binaries` ni `AllowedAPKSigningKeys`.
- **Capturas**: `fastlane/.../images/phoneScreenshots/` está vacío. La
  descripción enlaza la carpeta de Fastlane e indica que se añadirán.
- **Etiqueta `waiting-on-response`**: puede reaparecer si el bot reacciona
  a una pipeline fallida. Se elimina cuando el MR está al día.

## Cómo actualizar el MR

```bash
git clone --branch com.casthor https://gitlab.com/washaka81/fdroiddata.git
cd fdroiddata
# editar metadata/com.casthor.yml o srclibs/
git add metadata/com.casthor.yml
git commit -m "F-Droid: ..."
git push origin com.casthor
```

El push lanza una pipeline nueva en el fork. Para consultarla:

```bash
glab ci status --wait -R washaka81/fdroiddata --branch com.casthor
```

Para actualizar la descripción:

```bash
glab mr update 52694 -R fdroid/fdroiddata \
  --description-file <archivo.md>
```

## Entorno y credenciales

- `glab` instalado en `~/.local/bin/glab` (v1.131.0).
- Sesión de GitLab iniciada como `washaka81` mediante OAuth device flow;
  el token se guarda en el keyring del sistema, no en el repositorio.
- Git configurado para usar `glab auth git-credential` en `gitlab.com`:
  `git config --global credential.https://gitlab.com.helper '!glab auth git-credential'`.
- No hay tokens ni secretos en el repositorio.

## Historial de commits de la rama

- `073f8ae7` — descripción fiel a la implementación real.
- `a60480ec` — limpieza de metadata (`Summary`/`Description`, `AutoName`).
- `82b9a4dd` — corrección de esquema y primer intento de toolchain
  (superseded).
- `425334d6` — primer ajuste de toolchain (superseded).
- `f1c058fa` — JDK del buildserver con target 17, sin instalar nada;
  pipeline verde definitiva.
