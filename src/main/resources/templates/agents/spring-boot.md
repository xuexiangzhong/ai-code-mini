# 项目 Agent 指令 — Spring Boot

## 项目概述
<!-- Spring Boot REST / 微服务项目 -->

## 技术栈
- Java 21+
- Spring Boot 3.x
- Maven 或 Gradle（以项目现有为准）

## 架构约定
- Controller 只做参数校验与编排，业务逻辑放在 Service
- 数据访问通过 Repository / Mapper，不在 Controller 直接访问 DB
- 配置使用 `application.yml`，敏感项走环境变量或配置中心
- DTO 与 Entity 分离，API 层不暴露持久化实体

## 编码规范
- 构造器注入优先于字段 `@Autowired`
- 异常使用 `@ControllerAdvice` 统一处理
- REST 路径用复数名词，返回合适的 HTTP 状态码

## 构建与测试
```bash
mvn test
mvn spring-boot:run   # 本地启动（若项目支持）
```

## Agent 行为偏好
- 用中文回复
- 新增接口时同步考虑校验、异常处理与测试
- 不硬编码密钥；不修改生产 profile 中的敏感配置
