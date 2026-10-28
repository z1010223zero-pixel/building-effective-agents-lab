#!/usr/bin/env bash
# Java 版跑法。密钥从环境变量读，不落盘。
#   export LLM_BASE=http://127.0.0.1:8390/v1
#   export LLM_KEY=sk-xxx
#   export LLM_MODEL=DeepSeek-Flash
set -e
cd "$(dirname "$0")"
JAR=target/building-effective-agents-java-1.0.0-jar-with-dependencies.jar
[ -f "$JAR" ] || mvn -q -B package -DskipTests
[ -z "$LLM_KEY" ] && { echo "请先 export LLM_KEY=..."; exit 1; }
java -jar "$JAR" "${1:-all}"
