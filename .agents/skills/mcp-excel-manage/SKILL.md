---
name: mcp-excel-manage
description: >-
  通过统一 MCP 服务安全读取、解析与管理 Excel 数据表与策划配置文档。
  当需要检查数值配表、解析 .xlsx 表单、修改配置或导出数据时调用。
---

# MCP-Excel-Manage Excel 配置与数据管理技能

## 1. 何时使用
- **策划配置查阅**：查阅棋牌玩法规则、题目知识点、数值表、掉落概率等配置；
- **配置与代码核对**：检查 Excel 列头定义与 Java 中的 `Config`/`ConfigManager` 类字段是否一致；
- **数据回写与截图**：将运维诊断结果导出至 Excel，或生成表格预览截图。

## 2. 常用工具清单

| MCP 工具 | 核心参数 | 用途说明 |
|----------|----------|----------|
| `excel_describe_sheets` | `fileAbsolutePath` | 获取工作簿中所有 Sheet 的名称与元数据 |
| `excel_read_sheet` | `fileAbsolutePath`, `sheetName`<br>可选: `range`, `showFormula` | 按单元格范围读取数据（大表必须传 range 分页） |
| `excel_write_to_sheet` | `fileAbsolutePath`, `sheetName`<br>`range`, `values` | 向指定 Sheet 写入单元格矩阵数据 |
| `excel_create_table` | `fileAbsolutePath`, `sheetName`, `tableName` | 将指定区域创建为标准结构化表格 |
| `excel_format_range` | `fileAbsolutePath`, `sheetName`, `range`, `styles` | 批量设置单元格样式与对齐 |
| `excel_screen_capture` | `fileAbsolutePath`, `sheetName`, `range`? | （仅 Windows）生成指定表格区域的预览截图 |

## 3. 核心规约与防坑指南
1. **正式配置 Sheet 判定原则**：
   - **仅英文命名的工作表视为正式游戏导出配置**；
   - 中文命名的工作表多为策划设计说明、草稿或临时演算，不得误作为程序运行时配置。
2. **大表范围分页读取**：
   - 严禁对未知行数的大表全量不限范围读取，必须使用 `range` 参数（如 `A1:P60`）分片拉取，防止撑爆上下文。
3. **工作区绝对路径**：
   - `fileAbsolutePath` 必须传入当前工作区计算出的标准绝对路径，不依赖相对路径或盘符盲猜。

## 4. 标准执行流程
1. **元数据探测**：先调用 `excel_describe_sheets` 确定目标英文 Sheet 是否存在；
2. **表头与样例采样**：调用 `excel_read_sheet` 并指定 `range="A1:Z5"`，确认第 1~4 行的字段名、字段类型及注释；
3. **业务映射对照**：核对 Java 实体中字段命名与映射逻辑（如 `@ConfigField` 或反射映射），确保类型与空值处理稳健。
