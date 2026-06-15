# 项目 Agent 指令 — 前端 / React

## 项目概述
<!-- React / Next.js / Vite 前端项目 -->

## 技术栈
- TypeScript（优先 strict 模式）
- React 18+
- 包管理：npm / pnpm / yarn（以 lock 文件为准）

## 编码规范
- 函数组件 + Hooks，避免 class 组件
- 组件文件 PascalCase，hooks/utils  camelCase
- 样式方案以项目现有为准（CSS Modules / Tailwind / styled-components）
- Props 显式类型；避免 `any`
- 复用现有 UI 组件，不重复造轮子

## 目录约定
- 页面 / 路由组件与通用组件分离
- API 调用集中在 `api/` 或 `services/` 层
- 常量与环境变量通过项目既有方式注入

## 开发与测试
```bash
npm install
npm run dev
npm test
npm run lint
```

## Agent 行为偏好
- 用中文回复
- 改 UI 时保持与现有设计系统一致
- 不提交 `.env.local` 或 API Key
- 大改组件结构前先说明影响范围
