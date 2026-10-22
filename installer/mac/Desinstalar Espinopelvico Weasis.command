#!/bin/bash
# Removes the Weasis "Parámetros espinopélvicos" plugin for this user.
AGENT="$HOME/Library/LaunchAgents/com.columnaspine.weasis-espinopelvico.plist"
launchctl bootout "gui/$(id -u)" "$AGENT" 2>/dev/null
rm -f "$AGENT"
CURRENT=$(launchctl getenv JAVA_TOOL_OPTIONS)
REST=$(echo "$CURRENT" | sed -E 's#-Dfelix\.extended\.config\.properties=[^ ]*espinopelvico\.json##; s/^ +//; s/ +$//')
if [[ -n "$REST" ]]; then launchctl setenv JAVA_TOOL_OPTIONS "$REST"; else launchctl unsetenv JAVA_TOOL_OPTIONS; fi
rm -f "$HOME/.weasis/plugins/espinopelvico.json" "$HOME"/.weasis/plugins/weasis-espinopelvico-*.jar
echo
echo "Plugin desinstalado. Cerrá y volvé a abrir Weasis."
echo "(Para borrar también la copia en /Library/Application Support/WeasisEspinopelvico hace falta tu contraseña.)"
sudo rm -rf "/Library/Application Support/WeasisEspinopelvico" 2>/dev/null
echo "Listo. Podés cerrar esta ventana."
