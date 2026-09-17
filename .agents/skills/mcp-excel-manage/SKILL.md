---
name: mcp-excel-manage
description: >-
  通过统一 MCP 服务安全读取、解析与管理 Excel 数据表与策划配置文档。
  当需要检查数值配表、解析 .xlsx 表单或导出数据时调用。
---

# MCP-Excel-Manage Excel 数据解析与管理技能

## 何时使用
- 读取玩法配置表、数值定义表；
- 获取指定 Sheet 表头与多行数值；
- 将统计结果或诊断清单写入 Excel。

## 常用工具清单
- `excel_describe_sheets`: 获取工作簿所有 Sheet 基础信息；
- `excel_read_sheet`: 指定 Sheet 读取单元格范围数据；
- `excel_write_to_sheet`: 向指定位置写入数据；
- `excel_create_table`: 创建标准数据表格；
- `excel_format_range`: 批量设置格式；
- `excel_screen_capture`: 生成表格预览截图。

## 工作流
1. **发现表格**：
   - 调用 MCP 的 `excel_describe_sheets` 工具获取工作簿包含的所有 Sheet 清单；
2. **读取数据**：
   - 调用 `excel_read_sheet` 工具按范围读取单元格（支持指定起止行列）；
3. **协同处理**：
   - 将读取出的字段映射与业务 Java 类对照，验证数据类型匹配度。
