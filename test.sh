#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/jvm
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 17 -d build/jvm app/src/main/java/dev/actionwatch/remote/Protocol.java app/src/main/java/dev/actionwatch/remote/RemoteSession.java tests/ProtocolTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp build/jvm dev.actionwatch.remote.ProtocolTest
