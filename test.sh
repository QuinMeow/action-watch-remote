#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/jvm
SRC=app/src/main/java/dev/actionwatch/remote
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d build/jvm "$SRC/Protocol.java" "$SRC/RemoteSession.java" "$SRC/GpsData.java" "$SRC/GpsSourceRules.java" "$SRC/SimulatedCamera.java" "$SRC/DebugGate.java" tests/ProtocolTest.java tests/GpsTest.java tests/SimulationTest.java tests/DebugGateTest.java
for suite in ProtocolTest GpsTest SimulationTest DebugGateTest; do
 "${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp build/jvm "dev.actionwatch.remote.$suite"
done
