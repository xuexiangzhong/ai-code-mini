# 项目 Agent 指令 — Go

## 项目概述
<!-- Go 服务 / CLI / 库项目 -->

## 技术栈
- Go 1.21+
- 模块：`go.mod` 管理依赖
- 测试：`go test`

## 编码规范
- 遵循 `gofmt` / `goimports`
- 错误必须处理，优先 `fmt.Errorf("...: %w", err)` 包装
- 包名简短小写；导出标识符 PascalCase
- 接口定义在使用方包内（Accept interfaces, return structs）

## 构建与测试
```bash
go test ./...
go build ./...
go vet ./...
```

## 常见约定
- 配置通过 flag / 环境变量 / viper 等项目既有方式
- 不在代码中硬编码密钥
- 新增 public API 考虑 godoc 注释

## Agent 行为偏好
- 用中文回复
- 改 go.mod 前说明依赖原因
- 保持改动聚焦，不做无关 refactor
