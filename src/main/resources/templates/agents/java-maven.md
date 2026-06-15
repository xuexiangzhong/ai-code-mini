# 项目 Agent 指令 — Java / Maven

## 项目概述
<!-- Maven 多模块或单模块 Java 项目 -->

## 技术栈
- Java 21+
- Maven 3.8+
- 测试：JUnit 5

## 编码规范
- 包名小写，类名 PascalCase，常量 UPPER_SNAKE_CASE
- 优先使用 `var` 仅当类型明显时；公共 API 显式声明类型
- 遵循现有模块边界，不跨层直接依赖
- 修改范围最小化，匹配 surrounding code 的 import 与格式风格

## 构建与测试
```bash
mvn test          # 运行单元测试
mvn -q -DskipTests package   # 打包（跳过测试时说明原因）
```

## 常见约定
- 配置放在 `src/main/resources`，测试资源在 `src/test/resources`
- 新增 public 类需考虑是否需要对应测试类
- 日志使用 SLF4J，避免 `System.out.println`

## Agent 行为偏好
- 用中文回复
- 改 pom.xml 或依赖前说明影响
- 不提交 `.env`、密钥或 IDE 本地配置
