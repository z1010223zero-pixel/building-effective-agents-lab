#!/usr/bin/env bash
# 一次性把 6 层跑一遍。密钥从 unified-proxy 的进程环境里取，不落盘。
set -e
cd "$(dirname "$0")"
KEY=$(sudo sh -c 'tr "\0" "\n" < /proc/2258/environ' | grep '^UNIFIED_API_KEYS=' | cut -d= -f2 | cut -d, -f1)
export LLM_KEY="$KEY" LLM_BASE="http://100.78.2.6:8390/v1" LLM_MODEL="${LLM_MODEL:-DeepSeek-Flash}"
for f in p0_augmented p1_chain p2_routing p3_parallel p4_orchestrator p5_evaluator p6_agent; do
  echo; echo "############ $f ############"
  python3 "$f.py"
done
