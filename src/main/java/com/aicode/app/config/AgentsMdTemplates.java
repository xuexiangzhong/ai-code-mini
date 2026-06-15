package com.aicode.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentsMdTemplates {
    private static final Map<String, String> CATALOG = catalog();
    private static final List<AgentsMdTemplate> TEMPLATES = loadAll();

    private AgentsMdTemplates() {}

    public static List<AgentsMdTemplate> all() {
        return List.copyOf(TEMPLATES);
    }

    public static Optional<AgentsMdTemplate> find(String id) {
        return TEMPLATES.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public static AgentsMdTemplate defaultTemplate() {
        return find("general").orElseThrow();
    }

    private static Map<String, String> catalog() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("general", "通用项目");
        map.put("java-maven", "Java / Maven");
        map.put("spring-boot", "Spring Boot");
        map.put("frontend-react", "前端 / React");
        map.put("python", "Python");
        map.put("go", "Go");
        map.put("minimal", "空白模板");
        return Map.copyOf(map);
    }

    private static List<AgentsMdTemplate> loadAll() {
        List<AgentsMdTemplate> loaded = new ArrayList<>();
        for (Map.Entry<String, String> entry : CATALOG.entrySet()) {
            String id = entry.getKey();
            String label = entry.getValue();
            String content = readResource("/templates/agents/" + id + ".md");
            loaded.add(new AgentsMdTemplate(id, label, describe(id), content));
        }
        return List.copyOf(loaded);
    }

    private static String describe(String id) {
        return switch (id) {
            case "general" -> "适用于大多数项目的通用协作约定";
            case "java-maven" -> "Java 21 + Maven 项目，含构建与测试命令";
            case "spring-boot" -> "Spring Boot REST 服务分层与 API 约定";
            case "frontend-react" -> "React + TypeScript 前端项目规范";
            case "python" -> "Python 项目与 pytest 约定";
            case "go" -> "Go 模块项目与错误处理约定";
            case "minimal" -> "仅保留标题，自行填写内容";
            default -> "";
        };
    }

    private static String readResource(String path) {
        try (InputStream in = AgentsMdTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing template resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read template: " + path, e);
        }
    }
}
