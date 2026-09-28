#!/usr/bin/env bash
# Regenerate PNG from docs/**/*.puml (requires Java).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${ROOT}/tools/plantuml.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — download from https://github.com/plantuml/plantuml/releases"
  exit 1
fi
cd "$ROOT"
java -jar "$JAR" -tpng \
  docs/bpmn/as_is.puml \
  docs/bpmn/to_be.puml \
  docs/architecture/context_diagram.puml \
  docs/architecture/container_diagram.puml \
  docs/data/er_diagram.puml
echo "Done. PNG next to each .puml in docs/"
