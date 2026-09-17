---
name: tool-utils
description: >-
  查询和复用项目 utils/ 与 hub/common/ 模块已有的通用工具类。
  在编写业务逻辑需要日期、随机数、加解密、字符串处理、反射扫描、网络包编解码时调用。严禁重复造轮子。
---

# Tool-Utils 项目工具类复用技能

## 何时使用
- 业务编写中需要用到工具函数（如生成随机序列、计算牌型、格式化时间、MD5/SHA256、JSON 转换）；
- 避免在不同模块中各自手写相同的工具方法。

## 工具类分布与选用指南

### 1. `utils` 模块常用工具
- **反射与扫描**：`utils.registry.ClassScanner`（支持包扫描与注解类查找）、`HandlerRegistry`（处理器自动装配）
- **网络与消息路由**：`net.msg.MsgRouter`（消息分发路由）、`net.client.Sender`（统一长连接发送接口）
- **加密与摘要**：优先使用 Spring Security Crypto 或项目内提供的哈希工具，避免自定义简陋加盐
- **JSON 序列化**：优先使用 Jackson（Spring 默认）或统一的 FastJson，保持全局行为一致

### 2. `hub/common` 模块工具
- `hub/common/core/`：核心上下文与公共常量定义
- `hub/storage/DataPathResolver`：自适应数据存储路径解析器（负责统一定位 `data/` 目录）

## 研发准则
1. **使用顺序**：优先使用 `utils` 现有类 ➔ 其次使用 JDK 8 标准库 ➔ 严禁随手在业务包新建散装 `*Util`。
2. **新增工具规范**：如果确实是跨模块通用的基础工具，统一提炼在 `utils` 模块中，并补齐三层注释与单元测试。
