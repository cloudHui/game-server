#!/usr/bin/env bash

set -Eeuo pipefail

APP="claude-deepseek"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/claude-deepseek"
CONFIG_FILE="$CONFIG_DIR/config.env"
BACKUP_DIR="$CONFIG_DIR/backups"
BASHRC_FILE="$HOME/.bashrc"
BASE_URL="https://api.deepseek.com/anthropic"
MODELS_URL="https://api.deepseek.com/models"
INSTALL_URL="https://claude.ai/install.sh"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SKILL_INSTALLER="$SCRIPT_DIR/install-matt-skills.sh"
AVAILABLE_MODELS=()

log() { printf '[%s] %s\n' "$APP" "$*"; }
die() { printf '[%s] ERROR: %s\n' "$APP" "$*" >&2; exit 1; }

confirm() {
    local prompt="$1" default_answer="${2:-no}" answer=""
    if [[ ! -t 0 ]]; then
        [[ "$default_answer" == "yes" ]]
        return
    fi
    if [[ "$default_answer" == "yes" ]]; then
        read -r -p "$prompt [Y/n] " answer
        [[ -z "$answer" || "$answer" =~ ^[Yy]([Ee][Ss])?$ ]]
    else
        read -r -p "$prompt [y/N] " answer
        [[ "$answer" =~ ^[Yy]([Ee][Ss])?$ ]]
    fi
}

install_dependencies() {
    local missing=() name manager=""
    local elevate=()
    for name in curl jq; do
        command -v "$name" >/dev/null 2>&1 || missing+=("$name")
    done
    ((${#missing[@]} == 0)) && return

    command -v dnf >/dev/null 2>&1 && manager="dnf"
    [[ -z "$manager" ]] && command -v yum >/dev/null 2>&1 && manager="yum"
    [[ -z "$manager" ]] && command -v apt-get >/dev/null 2>&1 && manager="apt-get"
    [[ -n "$manager" ]] || die "缺少 ${missing[*]}，且未找到 dnf、yum 或 apt-get"
    confirm "安装缺少的依赖 ${missing[*]}？" yes || die "已取消"

    if ((EUID != 0)); then
        command -v sudo >/dev/null 2>&1 || die "安装依赖需要 root 或 sudo"
        elevate=(sudo)
    fi
    [[ "$manager" == "apt-get" ]] && "${elevate[@]}" apt-get update
    "${elevate[@]}" "$manager" install -y "${missing[@]}"
}

install_claude() {
    local current_path temp_dir
    export PATH="$HOME/.local/bin:$HOME/bin:$PATH"
    current_path="$(command -v claude 2>/dev/null || true)"

    if [[ -n "$current_path" ]]; then
        log "检测到 Claude Code：$current_path ($(claude --version 2>/dev/null || echo unknown))"
        if [[ "$current_path" != "$HOME/.local/bin/claude" ]] && \
            confirm "安装用户级原生版本以避免全局 npm 更新权限问题？" yes; then
            claude install stable
            hash -r
        fi
    else
        log "从 Anthropic 官方下载安装 Claude Code"
        temp_dir="$(mktemp -d)"
        curl -fsSL "$INSTALL_URL" -o "$temp_dir/install.sh"
        bash "$temp_dir/install.sh"
        rm -rf -- "$temp_dir"
        hash -r
    fi

    command -v claude >/dev/null 2>&1 || die "安装完成后仍找不到 claude，请确认 ~/.local/bin 在 PATH 中"
    log "Claude Code 已就绪：$(command -v claude) ($(claude --version))"
}

install_shared_skills() {
    [[ -x "$SKILL_INSTALLER" ]] || die "未找到技能安装器：$SKILL_INSTALLER"
    log "安装 Agent/Codex/Claude 共享工程技能"
    "$SKILL_INSTALLER"
}

fetch_models() {
    local api_key="$1" response_file http_status error_message
    response_file="$(mktemp)"
    if ! http_status="$(curl -sS --max-time 30 -o "$response_file" -w '%{http_code}' \
        -H "Authorization: Bearer $api_key" "$MODELS_URL")"; then
        rm -f -- "$response_file"
        die "无法连接 DeepSeek 模型接口"
    fi
    if [[ "$http_status" != "200" ]]; then
        error_message="$(jq -r '.error.message // "unknown error"' "$response_file" 2>/dev/null || true)"
        rm -f -- "$response_file"
        die "DeepSeek 鉴权失败（HTTP $http_status）：${error_message:-unknown error}"
    fi
    mapfile -t AVAILABLE_MODELS < <(jq -r '.data[]?.id // empty' "$response_file")
    rm -f -- "$response_file"
    ((${#AVAILABLE_MODELS[@]} > 0)) || die "DeepSeek 返回的模型列表为空"
}

model_exists() {
    local requested="$1" model
    for model in "${AVAILABLE_MODELS[@]}"; do
        [[ "$model" == "$requested" ]] && return 0
    done
    return 1
}

choose_model() {
    local preferred="${1:-}" selection="" index default_index=1
    if [[ -n "${DEEPSEEK_MODEL:-}" ]]; then
        model_exists "$DEEPSEEK_MODEL" || die "指定模型不可用：$DEEPSEEK_MODEL"
        printf '%s\n' "$DEEPSEEK_MODEL"
        return
    fi
    for index in "${!AVAILABLE_MODELS[@]}"; do
        [[ "${AVAILABLE_MODELS[$index]}" == "deepseek-v4-pro" ]] && default_index=$((index + 1))
        [[ -n "$preferred" && "${AVAILABLE_MODELS[$index]}" == "$preferred" ]] && default_index=$((index + 1))
        printf '  %d) %s\n' "$((index + 1))" "${AVAILABLE_MODELS[$index]}" >&2
    done
    if [[ ! -t 0 ]]; then
        printf '%s\n' "${AVAILABLE_MODELS[$((default_index - 1))]}"
        return
    fi
    read -r -p "请选择模型编号 [默认 $default_index]: " selection
    selection="${selection:-$default_index}"
    [[ "$selection" =~ ^[0-9]+$ ]] || die "模型编号无效"
    ((selection >= 1 && selection <= ${#AVAILABLE_MODELS[@]})) || die "模型编号超出范围"
    printf '%s\n' "${AVAILABLE_MODELS[$((selection - 1))]}"
}

validate_values() {
    local api_key="$1" model="$2"
    [[ -n "$api_key" && "$api_key" != *[[:space:]]* ]] || die "API Key 不能为空或包含空白字符"
    [[ "$api_key" != *"'"* && "$api_key" != *'"'* ]] || die "API Key 包含不支持的引号"
    [[ "$model" =~ ^[A-Za-z0-9._:-]+$ ]] || die "模型名称格式无效"
}

write_config() {
    local api_key="$1" model="$2" temp_file timestamp
    validate_values "$api_key" "$model"
    umask 077
    mkdir -p "$CONFIG_DIR" "$BACKUP_DIR"
    if [[ -f "$CONFIG_FILE" ]]; then
        timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
        cp -p "$CONFIG_FILE" "$BACKUP_DIR/config.env.$timestamp"
    fi
    temp_file="$(mktemp "$CONFIG_DIR/.config.env.XXXXXX")"
    {
        printf '%s\n' '# Managed by claude-deepseek.sh. Do not commit this file.'
        printf '%s\n' 'unset ANTHROPIC_AUTH_TOKEN'
        printf 'export ANTHROPIC_BASE_URL="%s"\n' "$BASE_URL"
        printf 'export ANTHROPIC_API_KEY="%s"\n' "$api_key"
        printf 'export ANTHROPIC_MODEL="%s"\n' "$model"
        printf '%s\n' 'export CLAUDE_CODE_ATTRIBUTION_HEADER="0"'
        printf '%s\n' 'export CLAUDE_CODE_DISABLE_EXPERIMENTAL_BETAS="1"'
        printf '%s\n' 'export ENABLE_TOOL_SEARCH="false"'
        printf '%s\n' 'export DISABLE_AUTOUPDATER="1"'
    } >"$temp_file"
    chmod 600 "$temp_file"
    mv -f "$temp_file" "$CONFIG_FILE"
}

ensure_shell_loader() {
    local marker='# >>> claude-deepseek managed configuration >>>'
    touch "$BASHRC_FILE"
    if ! grep -Fq "$marker" "$BASHRC_FILE"; then
        {
            printf '\n%s\n' "$marker"
            printf '%s\n' 'if [ -f "$HOME/.config/claude-deepseek/config.env" ]; then'
            printf '%s\n' '    . "$HOME/.config/claude-deepseek/config.env"'
            printf '%s\n' 'fi'
            printf '%s\n' '# <<< claude-deepseek managed configuration <<<'
        } >>"$BASHRC_FILE"
    fi
    chmod 600 "$BASHRC_FILE"
}

load_config() {
    [[ -f "$CONFIG_FILE" ]] || return 1
    # shellcheck disable=SC1090
    . "$CONFIG_FILE"
}

configure_key() {
    local required="${1:-no}" api_key="${DEEPSEEK_API_KEY:-}" selected_model
    if [[ -z "$api_key" ]]; then
        if [[ -t 0 ]]; then
            read -r -s -p "请输入 DeepSeek API Key（直接回车稍后配置）: " api_key
            printf '\n'
        elif [[ "$required" == "yes" ]]; then
            die "非交互配置需要设置 DEEPSEEK_API_KEY"
        fi
    fi
    if [[ -z "$api_key" ]]; then
        [[ "$required" == "yes" ]] && die "API Key 不能为空"
        log "已跳过 DeepSeek Key；稍后运行 claude-deepseek setup-key"
        return
    fi
    log "验证 DeepSeek Key 并读取官方模型列表"
    fetch_models "$api_key"
    selected_model="$(choose_model "${ANTHROPIC_MODEL:-}")"
    write_config "$api_key" "$selected_model"
    ensure_shell_loader
    log "配置完成，当前模型：$selected_model"
    log "执行 source ~/.bashrc 后即可运行 claude"
}

setup_command() {
    install_dependencies
    install_claude
    install_shared_skills
    configure_key no
}

setup_key_command() {
    install_dependencies
    configure_key yes
}

model_command() {
    local api_key current_model selected_model temp_file timestamp
    load_config || die "尚未生成 $CONFIG_FILE，请先运行 setup"
    api_key="${ANTHROPIC_API_KEY:-}"
    current_model="${ANTHROPIC_MODEL:-}"
    [[ -n "$api_key" ]] || die "配置中没有 ANTHROPIC_API_KEY"
    fetch_models "$api_key"
    log "当前模型：${current_model:-<未设置>}"
    model_exists "$current_model" && log "当前模型仍然可用" || log "当前模型已不在官方列表中"
    confirm "是否选择并替换模型？" no || { log "保持当前模型不变"; return; }
    selected_model="$(choose_model "$current_model")"
    [[ "$selected_model" != "$current_model" ]] || { log "选择结果未变化"; return; }
    confirm "确认将 $current_model 替换为 $selected_model？" no || { log "已取消"; return; }

    timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
    mkdir -p "$BACKUP_DIR"
    cp -p "$CONFIG_FILE" "$BACKUP_DIR/config.env.$timestamp"
    temp_file="$(mktemp "$CONFIG_DIR/.config.env.XXXXXX")"
    awk -v model="$selected_model" '
        /^export ANTHROPIC_MODEL=/ { print "export ANTHROPIC_MODEL=\"" model "\""; found=1; next }
        { print }
        END { if (!found) print "export ANTHROPIC_MODEL=\"" model "\"" }
    ' "$CONFIG_FILE" >"$temp_file"
    chmod 600 "$temp_file"
    mv -f "$temp_file" "$CONFIG_FILE"
    log "模型已替换为 $selected_model；执行 source ~/.bashrc 后生效"
}

doctor_command() {
    local api_key=""
    export PATH="$HOME/.local/bin:$HOME/bin:$PATH"
    if command -v claude >/dev/null 2>&1; then
        log "Claude Code：$(command -v claude) ($(claude --version))"
    else
        log "Claude Code：未安装"
    fi
    if [[ -x "$SKILL_INSTALLER" ]]; then
        log "共享技能安装器：$SKILL_INSTALLER"
    else
        log "共享技能安装器：缺失（$SKILL_INSTALLER）"
    fi
    if load_config; then
        api_key="${ANTHROPIC_API_KEY:-}"
        log "配置文件：$CONFIG_FILE"
        log "Base URL：${ANTHROPIC_BASE_URL:-<未设置>}"
        log "当前模型：${ANTHROPIC_MODEL:-<未设置>}"
        log "API Key：$([[ -n "$api_key" ]] && echo 已配置 || echo 未配置)"
        if [[ -n "$api_key" ]]; then
            fetch_models "$api_key"
            log "DeepSeek 鉴权成功，可用模型：${AVAILABLE_MODELS[*]}"
        fi
    else
        log "私有配置尚未生成，请运行 setup"
    fi
}

update_cli_command() {
    export PATH="$HOME/.local/bin:$HOME/bin:$PATH"
    command -v claude >/dev/null 2>&1 || die "Claude Code 尚未安装，请先运行 setup"
    log "当前版本：$(claude --version)"
    claude update
    hash -r
    log "更新后版本：$(claude --version)"
}

usage() {
    cat <<'EOF'
Usage: claude-deepseek.sh [command]

  setup          安装 Claude、技能；输入 Key 或回车跳过（默认）
  setup-key      配置或补充 DeepSeek Key
  model          查询模型并询问是否替换
  update-model   model 的别名
  update-cli     手动更新 Claude Code
  doctor         检查 CLI、配置、鉴权和模型
  help           显示帮助

非交互安装：
  DEEPSEEK_API_KEY=... DEEPSEEK_MODEL=deepseek-v4-pro ./claude-deepseek.sh setup
EOF
}

case "${1:-setup}" in
    setup|install) setup_command ;;
    setup-key) setup_key_command ;;
    model|update-model) install_dependencies; model_command ;;
    update-cli) update_cli_command ;;
    doctor) install_dependencies; doctor_command ;;
    help|-h|--help) usage ;;
    *) usage >&2; exit 2 ;;
esac
