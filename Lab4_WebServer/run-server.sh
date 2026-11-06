#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
exec java -Dfile.encoding=UTF-8 -Djava.awt.headless=true -cp 'target/lab-web-1.0.jar:target/dependency/*' server.WebServer "${1:-8080}" public
