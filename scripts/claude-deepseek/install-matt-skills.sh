#!/usr/bin/env bash
set -euo pipefail

# Install four Matt Pocock skills and expose one
# canonical copy to generic agents, Codex, and Claude Code.

repo="mattpocock/skills"
caveman_ref="0a4b76776dfd9979bfe013d99b5562a03b743839"
installer="${CODEX_HOME:-$HOME/.codex}/skills/.system/skill-installer/scripts/install-skill-from-github.py"
canonical_root="${AGENT_SKILLS_HOME:-$HOME/.agents/skills}"
codex_root="${CODEX_HOME:-$HOME/.codex}/skills"
claude_root="${CLAUDE_HOME:-$HOME/.claude}/skills"
script_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
instruction_template="$script_root/matt-skills-required.md"
stage="$(mktemp -d "${TMPDIR:-/tmp}/matt-skills-install.XXXXXX")"

cleanup() {
  rm -rf -- "$stage"
}
trap cleanup EXIT

install_with_codex_helper() {
  python3 "$installer" \
    --repo "$repo" \
    --ref main \
    --dest "$stage" \
    --path \
      skills/engineering/grill-with-docs \
      skills/engineering/diagnosing-bugs \
      skills/engineering/tdd

  # caveman was removed upstream on 2026-06-12. Pin final upstream version.
  python3 "$installer" \
    --repo "$repo" \
    --ref "$caveman_ref" \
    --dest "$stage" \
    --path skills/productivity/caveman
}

install_from_archives() {
  local main_archive="$stage/main.tar.gz"
  local old_archive="$stage/caveman.tar.gz"
  local unpack="$stage/unpack"
  command -v curl >/dev/null 2>&1 || {
    echo "curl is required when the Codex skill-installer is unavailable" >&2
    exit 1
  }
  mkdir -p "$unpack/main" "$unpack/caveman"
  curl -fsSL "https://github.com/$repo/archive/refs/heads/main.tar.gz" -o "$main_archive"
  curl -fsSL "https://github.com/$repo/archive/$caveman_ref.tar.gz" -o "$old_archive"
  tar -xzf "$main_archive" -C "$unpack/main" --strip-components=1
  tar -xzf "$old_archive" -C "$unpack/caveman" --strip-components=1
  cp -a "$unpack/main/skills/engineering/grill-with-docs" "$stage/grill-with-docs"
  cp -a "$unpack/main/skills/engineering/diagnosing-bugs" "$stage/diagnosing-bugs"
  cp -a "$unpack/main/skills/engineering/tdd" "$stage/tdd"
  cp -a "$unpack/caveman/skills/productivity/caveman" "$stage/caveman"
}

if [[ -f "$installer" ]]; then
  install_with_codex_helper
else
  install_from_archives
fi

# Preserve the command name requested by this machine: /diagnose.
mv "$stage/diagnosing-bugs" "$stage/diagnose"
sed -i 's/^name: diagnosing-bugs$/name: diagnose/' "$stage/diagnose/SKILL.md"

mkdir -p "$canonical_root" "$codex_root" "$claude_root" "$canonical_root/.backups"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"

install_canonical() {
  local skill="$1"
  local src="$stage/$skill"
  local dst="$canonical_root/$skill"

  if [[ -e "$dst" || -L "$dst" ]]; then
    if diff -qr "$src" "$dst" >/dev/null 2>&1; then
      return
    fi
    mv "$dst" "$canonical_root/.backups/${skill}.${stamp}"
  fi
  mv "$src" "$dst"
}

link_for_client() {
  local root="$1"
  local skill="$2"
  local dst="$root/$skill"
  local target="$canonical_root/$skill"

  if [[ -L "$dst" && "$(readlink -f "$dst")" == "$(readlink -f "$target")" ]]; then
    return
  fi
  if [[ -e "$dst" || -L "$dst" ]]; then
    mv "$dst" "$canonical_root/.backups/${skill}.$(basename "$root").${stamp}"
  fi
  ln -s "$target" "$dst"
}

skills=(grill-with-docs caveman diagnose tdd)
for skill in "${skills[@]}"; do
  install_canonical "$skill"
  link_for_client "$codex_root" "$skill"
  link_for_client "$claude_root" "$skill"
done

ensure_required_rules() {
  local file="$1"
  if [[ ! -f "$instruction_template" ]]; then
    echo "Instruction template not found: $instruction_template" >&2
    exit 1
  fi
  touch "$file"
  if ! grep -q '<!-- matt-skills-required:start -->' "$file"; then
    printf '\n' >>"$file"
    sed -n '/<!-- matt-skills-required:start -->/,/<!-- matt-skills-required:end -->/p' \
      "$instruction_template" >>"$file"
  fi
}

ensure_required_rules "$HOME/AGENTS.md"
ensure_required_rules "$HOME/CLAUDE.md"

echo "Installed: ${skills[*]}"
echo "Canonical: $canonical_root"
echo "Codex links: $codex_root"
echo "Claude links: $claude_root"
echo "Required-use rules: $HOME/AGENTS.md and $HOME/CLAUDE.md"
echo "Restart active agent sessions to reload skills."
