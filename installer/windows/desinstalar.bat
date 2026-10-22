@echo off
rem Desinstala el plugin "Parametros espinopelvicos" de Weasis para este usuario.
setlocal
set "DEST=%USERPROFILE%\.weasis\plugins"
del /q "%DEST%\weasis-espinopelvico-*.jar" 2>nul
del /q "%DEST%\espinopelvico.json" 2>nul
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$cur = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'User');" ^
  "if ($cur) { $rest = ($cur -replace '-Dfelix\.extended\.config\.properties=\S*espinopelvico\.json', '').Trim();" ^
  "  if ($rest) { [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $rest, 'User') } else { [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $null, 'User') } }"
echo.
echo Plugin desinstalado. Cerra y volve a abrir Weasis.
echo.
pause
