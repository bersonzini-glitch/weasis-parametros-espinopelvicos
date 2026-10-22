@echo off
rem Instala el plugin "Parametros espinopelvicos" en Weasis para este usuario de Windows.
rem Copia el plugin a %USERPROFILE%\.weasis\plugins, escribe la configuracion de Weasis y define
rem la variable de usuario JAVA_TOOL_OPTIONS para que Weasis la lea al arrancar.
setlocal
set "SRC=%~dp0"
set "JAR="
for %%F in ("%SRC%weasis-espinopelvico-*.jar") do set "JAR=%%~nxF"
if "%JAR%"=="" (
  echo No encuentro el archivo weasis-espinopelvico-*.jar junto a este instalador.
  echo Descomprimi el .zip completo y volve a ejecutar instalar.bat desde esa carpeta.
  pause
  exit /b 1
)
set "DEST=%USERPROFILE%\.weasis\plugins"
if not exist "%DEST%" mkdir "%DEST%"
del /q "%DEST%\weasis-espinopelvico-*.jar" 2>nul
copy /y "%SRC%%JAR%" "%DEST%\" >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$d = Join-Path $env:USERPROFILE '.weasis\plugins';" ^
  "$jar = ([Uri](Join-Path $d '%JAR%')).AbsoluteUri;" ^
  "$jsonPath = Join-Path $d 'espinopelvico.json';" ^
  "$pref = [ordered]@{ code = 'felix.auto.start.125'; value = $jar; description = 'Plugin Parametros espinopelvicos (columna-spine.com)'; type = 'A'; category = 'FELIX_INSTALL' };" ^
  "$text = ConvertTo-Json -Depth 4 -InputObject ([ordered]@{ weasisPreferences = @($pref) });" ^
  "[IO.File]::WriteAllText($jsonPath, $text, (New-Object Text.UTF8Encoding $false));" ^
  "$opt = '-Dfelix.extended.config.properties=' + ([Uri]$jsonPath).AbsoluteUri;" ^
  "$cur = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'User');" ^
  "if ($cur) { $cur = ($cur -replace '-Dfelix\.extended\.config\.properties=\S*espinopelvico\.json', '').Trim(); if ($cur) { $opt = $cur + ' ' + $opt } };" ^
  "[Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $opt, 'User')"
if errorlevel 1 (
  echo Hubo un error al configurar el plugin.
  pause
  exit /b 1
)
echo.
echo Listo. Cerra Weasis si estaba abierto y volve a abrirlo.
echo Abri una telerradiografia de perfil y busca la pestana "Espinopelvico"
echo en la barra lateral derecha del visor.
echo.
pause
