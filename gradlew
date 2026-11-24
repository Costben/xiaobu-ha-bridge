#!/bin/sh
# S1 scaffold gradlew shim: Gradle 9.x (Kotlin 2.3.20) supports JDK 26; 8.14 embedded Kotlin 2.0.21 fails JavaVersion.parse(26.0.1).
# 版本号取自 gradle/wrapper/gradle-wrapper.properties，改版本时只改那一处。
export JAVA_HOME=/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home
APP_BASE=$(dirname "$0")
PROPS="$APP_BASE/gradle/wrapper/gradle-wrapper.properties"
GRADLE_VERSION=$(grep '^distributionUrl=' "$PROPS" | sed -e 's/.*gradle-//' -e 's/-bin\.zip$//' -e 's/-all\.zip$//')
CACHED_BIN=$(ls -d "$HOME/.gradle/wrapper/dists/gradle-$GRADLE_VERSION-bin/"*/gradle-$GRADLE_VERSION/bin/gradle 2>/dev/null | head -n 1)
if [ -x "$CACHED_BIN" ]; then
  exec "$CACHED_BIN" "$@"
fi
# fallback: standard wrapper
WRAPPER_JAR="$APP_BASE/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  exec "$JAVA_HOME/bin/java" -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
echo "Gradle binary not found: $CACHED_BIN and no gradle-wrapper.jar" >&2
exit 1
