#!/bin/bash
# DeepSeek Harness 一键安装脚本 (Linux)
# 支持 Ubuntu/Debian/CentOS

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "  DeepSeek Harness 一键安装脚本"
echo "  适用于 Ubuntu/Debian/CentOS"
echo "========================================"
echo ""

# ============================================
# 步骤1: 检测并安装基础依赖
# ============================================
echo -e "[1/6] ${YELLOW}检测基础依赖...${NC}"

if ! command -v curl &> /dev/null; then
    echo -e "[!] ${YELLOW}未检测到 curl，正在安装...${NC}"
    if command -v apt &> /dev/null; then
        sudo apt update && sudo apt install -y curl
    elif command -v yum &> /dev/null; then
        sudo yum install -y curl
    else
        echo -e "[X] ${RED}无法自动安装 curl，请手动安装后重试${NC}"
        exit 1
    fi
fi

if ! command -v git &> /dev/null; then
    echo -e "[!] ${YELLOW}未检测到 git，正在安装...${NC}"
    if command -v apt &> /dev/null; then
        sudo apt install -y git
    elif command -v yum &> /dev/null; then
        sudo yum install -y git
    else
        echo -e "[X] ${RED}无法自动安装 git，请手动安装后重试${NC}"
        exit 1
    fi
fi
echo -e "[√] ${GREEN}基础依赖检测通过${NC}"

echo ""

# ============================================
# 步骤2: 检测并安装 Node.js
# ============================================
echo -e "[2/6] ${YELLOW}检测 Node.js 环境...${NC}"

if ! command -v node &> /dev/null; then
    echo -e "[!] ${YELLOW}未检测到 Node.js，正在自动安装...${NC}"
    
    if command -v apt &> /dev/null; then
        curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
        sudo apt install -y nodejs
    elif command -v yum &> /dev/null; then
        curl -fsSL https://rpm.nodesource.com/setup_22.x | sudo bash -
        sudo yum install -y nodejs
    else
        echo -e "[X] ${RED}无法自动安装 Node.js，请手动安装后重试${NC}"
        echo "    访问 https://nodejs.org/ 下载安装"
        exit 1
    fi
    
    if ! command -v node &> /dev/null; then
        echo -e "[X] ${RED}Node.js 安装失败，请手动安装后重试${NC}"
        exit 1
    fi
fi

echo -e "[√] ${GREEN}Node.js 已安装: $(node --version)${NC}"
echo ""

# ============================================
# 步骤3: 配置镜像并安装 Harness
# ============================================
echo -e "[3/6] ${YELLOW}配置 npm 镜像并安装 Harness...${NC}"

npm config set registry https://registry.npmmirror.com

npm install -g @deepseek-ai/dsh --registry https://registry.npmmirror.com

if [ $? -ne 0 ]; then
    echo -e "[X] ${RED}Harness 安装失败，请检查网络后重试${NC}"
    exit 1
fi

echo -e "[√] ${GREEN}DeepSeek Harness 安装成功${NC}"
echo ""

# ============================================
# 步骤4: 创建技能配置文件
# ============================================
echo -e "[4/6] ${YELLOW}创建技能配置文件...${NC}"

CONFIG_FILE="$HOME/enable-skills.yml"
cat > "$CONFIG_FILE" << 'EOF'
- update:
    - id: skill-filesystem
      disabled: false
    - id: tool-skill
      disabled: false
    - id: skill-badge
      disabled: false
EOF

if [ -f "$CONFIG_FILE" ]; then
    echo -e "[√] ${GREEN}配置文件已创建: $CONFIG_FILE${NC}"
else
    echo -e "[X] ${RED}配置文件创建失败${NC}"
    exit 1
fi
echo ""

# ============================================
# 步骤5: 安装插件（容错处理）
# ============================================
echo -e "[5/6] ${YELLOW}安装插件（网络问题将自动跳过）...${NC}"

install_plugin() {
    local plugin=$1
    echo -e "[*] 安装: $plugin"
    if dsh plugin --profile web add "$plugin" 2>/dev/null; then
        echo -e "[√] ${GREEN}$plugin 安装成功${NC}"
    else
        echo -e "[!] ${YELLOW}$plugin 安装失败，继续下一个...${NC}"
    fi
}

install_plugin "@liustack/modsearch"
install_plugin "@anionex/dsh-vision-toolkit"
install_plugin "github:zhu1090093659/dsh-web-ui#main"
install_plugin "github:omdsh-dev/dsh-at-file#main"
install_plugin "git+https://github.com/omdsh-dev/dsh-annotation.git"
install_plugin "github:Zhenyu98/dsh-context-doctor#main"

echo -e "[√] ${GREEN}插件安装流程结束${NC}"
echo ""

# ============================================
# 步骤6: 启动 Harness
# ============================================
echo -e "[6/6] ${YELLOW}启动 DeepSeek Harness...${NC}"
echo "========================================"
echo -e "  ${GREEN}安装完成！正在启动 Web 服务...${NC}"
echo "  浏览器将自动打开 http://127.0.0.1:3080"
echo "  首次使用请在设置中配置 API Key"
echo "  按 Ctrl+C 可停止服务"
echo "========================================"
echo ""

if command -v xdg-open &> /dev/null; then
    xdg-open http://127.0.0.1:3080 2>/dev/null &
elif command -v gnome-open &> /dev/null; then
    gnome-open http://127.0.0.1:3080 2>/dev/null &
fi

npx @deepseek-ai/dsh web --patch "$CONFIG_FILE"