#!/bin/zsh
# Builds dist/weasis-espinopelvico-<version>.jar (an OSGi bundle for Weasis 4.7) after running
# the geometry tests. Uses the JDK in .tools/ and compiles against the bundles of the installed Weasis.
set -euo pipefail
cd "${0:A:h}"

JDK=$(ls -d .tools/jdk-25*/Contents/Home 2>/dev/null | head -1)
[[ -n "$JDK" ]] || { echo "Falta el JDK 25 en .tools/ (ver README, sección Compilar)"; exit 1; }
LIBS=.tools/weasis-libs
if [[ ! -f $LIBS/weasis-core-4.7.3.jar ]]; then
  mkdir -p $LIBS
  python3 - <<'PY'
import lzma, glob, os
for f in glob.glob('/Applications/Weasis.app/Contents/app/bundle/*.jar.xz'):
    open('.tools/weasis-libs/' + os.path.basename(f)[:-3], 'wb').write(lzma.open(f).read())
PY
fi
CP=$(ls $LIBS/*.jar /Applications/Weasis.app/Contents/app/felix.jar /Applications/Weasis.app/Contents/app/weasis-launcher.jar | tr '\n' ':')
VERSION=$(awk '/^Bundle-Version:/{print $2}' MANIFEST.MF)

rm -rf build && mkdir -p build/classes build/test dist
"$JDK/bin/javac" -d build/test src/com/columnaspine/weasis/spinopelvic/Geometry.java test/GeometryTest.java
"$JDK/bin/java" -cp build/test GeometryTest >/dev/null || { "$JDK/bin/java" -cp build/test GeometryTest; exit 1; }

"$JDK/bin/javac" --release 25 -Xlint:-options -encoding UTF-8 -cp "$CP" -d build/classes $(find src -name '*.java')
cp -R resources/* build/classes/

# Import-Package: every non-java.* package the classes reference, found with jdeps.
IMPORTS=$("$JDK/bin/jdeps" -verbose:package --multi-release 25 -cp "$CP" build/classes 2>/dev/null \
  | awk '/->/{print $3}' | grep -E '^[a-z]+(\.[A-Za-z0-9_]+)+$' | grep -vE '^(java\.|com\.columnaspine\.)' | sort -u | paste -sd, -)
{ cat MANIFEST.MF; echo "Import-Package: $IMPORTS" | fold -w 70 | sed '2,$s/^/ /'; } > build/MANIFEST.MF

OUT=dist/weasis-espinopelvico-$VERSION.jar
"$JDK/bin/jar" --create --file "$OUT" --manifest build/MANIFEST.MF -C build/classes .
echo "Built $OUT"

# ---- Mac installer (.pkg): payload in /Library/Application Support, per-user setup in postinstall.
PKGROOT=build/pkgroot/Library/Application\ Support/WeasisEspinopelvico
mkdir -p "$PKGROOT"
cp "$OUT" "$PKGROOT/"
cp "installer/mac/Desinstalar Espinopelvico Weasis.command" "$PKGROOT/"
pkgbuild --root build/pkgroot --scripts installer/mac/scripts --install-location / \
  --identifier com.columnaspine.weasis.spinopelvic --version "$VERSION" build/plugin.pkg >/dev/null
sed "s/VERSION/$VERSION/" installer/mac/distribution.xml > build/distribution.xml
productbuild --distribution build/distribution.xml --resources installer/mac/resources --package-path build \
  "dist/Instalar-SagitalApp-Weasis-Mac.pkg" >/dev/null

# ---- Windows (.zip): bundle + instalar.bat / desinstalar.bat.
WIN=build/SagitalApp-Weasis-Windows
mkdir -p "$WIN"
cp "$OUT" installer/windows/instalar.bat installer/windows/desinstalar.bat "$WIN/"
(cd build && rm -f ../dist/SagitalApp-Weasis-Windows.zip && zip -qr ../dist/SagitalApp-Weasis-Windows.zip SagitalApp-Weasis-Windows)
echo "Built installers in dist/"
