#!/bin/bash
# Installs git hooks in this folder that refuse to commit or push anything personal:
#   anything under personal/, and any image outside the app's own icons (launcher/res) and the
#   original screenshots (docs/images). Run once; scripts/buchi.sh all runs it for you.
set -e
cd "$(dirname "$0")/.."
H=.git/hooks; mkdir -p "$H"
cat > "$H/privacy-check" <<'C'
#!/bin/bash
# reads file names on stdin; prints the personal ones and fails if there are any
bad=$(grep -iE '^personal/|\.(jpe?g|png|webp|heic|gif|bmp|tiff?)$' | grep -vE '^(launcher/res/|docs/images/)' || true)
[ -z "$bad" ] && exit 0
echo "Blocked: these look personal and must never go to GitHub:" >&2
echo "$bad" | sed 's/^/   /' >&2
exit 1
C
cat > "$H/pre-commit" <<'C'
#!/bin/bash
git diff --cached --name-only --diff-filter=ACMR | "$(dirname "$0")/privacy-check" || {
  echo "Unstage them: git restore --staged <file>" >&2; exit 1; }
C
cat > "$H/pre-push" <<'C'
#!/bin/bash
# check every commit about to be pushed, not only the last one
while read -r lref lsha rref rsha; do
  [ "$lsha" = 0000000000000000000000000000000000000000 ] && continue
  if [ "$rsha" = 0000000000000000000000000000000000000000 ]; then range="$lsha"; else range="$rsha..$lsha"; fi
  git log --name-only --pretty=format: "$range" | sort -u | grep -v '^$' | "$(dirname "$0")/privacy-check" || {
    echo "Push stopped. Remove those files from the commits first." >&2; exit 1; }
done
C
chmod +x "$H/privacy-check" "$H/pre-commit" "$H/pre-push"
echo "Privacy guard on: personal photos and files can't be committed or pushed."
