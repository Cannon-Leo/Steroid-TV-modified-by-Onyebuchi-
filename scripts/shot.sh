#!/bin/bash
# One screenshot of whatever the TV shows right now (changes nothing), for Claude to look at.
. "$(dirname "$0")/env.sh"
mkdir -p "$ROOT/diag"
tv exec-out screencap -p > "$ROOT/diag/now.png" && echo "saved diag/now.png"
