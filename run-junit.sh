#!/usr/bin/env bash
# 021: run a single JUnit test class in a module whose surefire is disabled by the root pom.
# The root pom hard-codes <skipTests>true</skipTests> (a literal, not a ${property}), so
# `-DskipTests=false` cannot override it. Compile + invoke JUnitCore directly instead.
#
# Usage: ./run-junit.sh <module> <TestClassFqn> [moreFqns...]
#
# ⚠️ Tests living in Reactor-agent-app (e.g. org.wwz.ai.test.domain.BrowserRelayBoundaryTest)
# need the app's own test classpath plus every module's target/classes. Easiest:
#   export CLASSPATH="C:\Users\WWZ\.m2\repository\junit\junit\4.13.2\junit-4.13.2.jar;\
# C:\Users\WWZ\.m2\repository\org\hamcrest\hamcrest\2.2\hamcrest-2.2.jar;\
# Reactor-agent-{app,trigger,domain,case,infrastructure,api,types}/target/classes;\
# Reactor-agent-app/target/test-classes;$(cat Reactor-agent-app/target/cp.txt)"
#   java org.junit.runner.JUnitCore <fqns...>
# (Argfile `@file` fails on mixed path separators; direct -cp hits "Argument list too long".)
set -uo pipefail

MODULE="$1"; shift
CP_FILE="$MODULE/target/cp.txt"

mvn -q -pl "$MODULE" -am compile -DskipTests 2>&1 | grep -E "^\[ERROR\]" | head -3 || true
mvn -q -pl "$MODULE" -am test-compile -DskipTests 2>&1 | grep -E "^\[ERROR\]" | head -3 || true
mvn -q -pl "$MODULE" dependency:build-classpath -Dmdep.outputFile="$CP_FILE" >/dev/null 2>&1

CP="$MODULE/target/classes;$MODULE/target/test-classes;$(cat "$CP_FILE")"
java -cp "$CP" org.junit.runner.JUnitCore "$@" 2>&1 | grep -vE "注:|警告|^ע"
