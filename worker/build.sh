#!/usr/bin/env bash
# Builds worker/build/{common,own,microemu}. Needs a JDK 21 (javac). No Maven.
# Two cores share the package javax.microedition.*, so each has its own output dir and classpath:
#   own     -> build/common + build/own
#   microemu-> build/common + build/microemu + vendor/microemu/lib/asm-3.2.jar
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/common build/own build/microemu

javac -nowarn -d build/common src/common/*.java 2>&1 | grep -v "Picked up" || true

javac -nowarn -Xlint:-options --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED \
  -cp build/common -d build/own $(find src/own -name '*.java') 2>&1 | grep -v "Picked up\|^Note:" || true

V=vendor/microemu
MODULES="microemu-cldc microemu-midp microemu-javase microemu-javase-swing microemu-jsr-120 microemu-jsr-135 microemu-nokiaui"
SRC=$(for m in $MODULES; do find $V/$m/src/main/java -name '*.java'; done)
javac -nowarn -Xlint:none --release 8 -encoding UTF-8 -cp $V/lib/asm-3.2.jar -d build/microemu $SRC \
  2>&1 | grep -v "Picked up\|^Note:\|warning:" || true
for m in $MODULES; do
  [ -d $V/$m/src/main/resources ] && cp -r $V/$m/src/main/resources/. build/microemu/
done
javac -nowarn -Xlint:-options -cp build/common:build/microemu:$V/lib/asm-3.2.jar -d build/microemu src/microemu/MicroEmuEmulator.java \
  2>&1 | grep -v "Picked up\|^Note:" || true

echo "common: $(find build/common -name '*.class' | wc -l) classes, own: $(find build/own -name '*.class' | wc -l), microemu: $(find build/microemu -name '*.class' | wc -l)"
