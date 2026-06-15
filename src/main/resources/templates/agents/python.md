# 项目 Agent 指令 — Python

## 项目概述
<!-- Python 应用 / 库 / FastAPI 项目 -->

## 技术栈
- Python 3.10+
- 依赖管理：requirements.txt / pyproject.toml / poetry（以项目为准）
- 测试：pytest

## 编码规范
- 遵循 PEP 8；类型注解用于 public API
- 模块与包名小写 snake_case
- 虚拟环境隔离依赖，不全局 pip install
- 匹配项目现有的 async/sync 风格

## 测试与运行
```bash
pytest
python -m app          # 按项目实际入口调整
```

## 常见约定
- 配置通过环境变量或 `.env.example` 文档化，不提交真实 `.env`
- 日志用 `logging` 模块
- 新增依赖需更新对应的 lock / requirements 文件

## Agent 行为偏好
- 用中文回复
- 改依赖或 Python 版本前说明兼容性
- 保持改动最小，遵循 surrounding code
