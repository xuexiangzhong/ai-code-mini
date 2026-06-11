package com.aicode.agent;

import com.aicode.agent.tools.GlobTool;
import com.aicode.agent.tools.GrepTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SearchTest {
    @TempDir
    Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        Path src = testDir.resolve("src");
        Path utils = src.resolve("utils");
        Path tests = testDir.resolve("tests");
        Files.createDirectories(utils);
        Files.createDirectories(tests);

        Files.writeString(src.resolve("main.ts"), "const x = \"hello\";\nconsole.log(x);\n");
        Files.writeString(utils.resolve("helper.ts"),
                "export function add(a: number, b: number) {\n  return a + b;\n}\n");
        Files.writeString(utils.resolve("format.py"),
                "def format_name(name: str) -> str:\n    return name.strip()\n");
        Files.writeString(tests.resolve("main.test.ts"),
                "import { test } from \"vitest\";\ntest(\"works\", () => {});\n");
        Files.writeString(testDir.resolve("README.md"), "# Project\nThis is a test project.\n");
    }

    @Nested
    class TestGlobToolDefinition {
        @Test
        void hasCorrectName() {
            assertEquals("glob", GlobTool.DEFINITION.name());
        }
    }

    @Nested
    class TestExecuteGlobTool {
        @Test
        void findTsFiles() {
            String result = GlobTool.execute(
                    new GlobTool.Input("*.ts", testDir.resolve("src").toString()));
            assertTrue(result.contains("main.ts"));
        }

        @Test
        void findFilesRecursively() {
            String result = GlobTool.execute(new GlobTool.Input("**/*.ts", testDir.toString()));
            assertTrue(result.contains("main.ts"));
            assertTrue(result.contains("helper.ts"));
            assertTrue(result.contains("main.test.ts"));
        }

        @Test
        void findPyFiles() {
            String result = GlobTool.execute(new GlobTool.Input("**/*.py", testDir.toString()));
            assertTrue(result.contains("format.py"));
            assertFalse(result.contains(".ts"));
        }

        @Test
        void noMatches() {
            String result = GlobTool.execute(new GlobTool.Input("*.xyz", testDir.toString()));
            assertTrue(result.contains("No files matching"));
        }

        @Test
        void directoryNotFound() {
            String result = GlobTool.execute(new GlobTool.Input("*.ts", "/no/such/dir"));
            assertTrue(result.contains("Error: directory not found"));
        }
    }

    @Nested
    class TestGrepToolDefinition {
        @Test
        void hasCorrectName() {
            assertEquals("grep", GrepTool.DEFINITION.name());
        }
    }

    @Nested
    class TestExecuteGrepTool {
        @Test
        void findPattern() {
            String result = GrepTool.execute(new GrepTool.Input("hello", testDir.toString(), null));
            assertTrue(result.contains("main.ts"));
            assertTrue(result.contains("hello"));
        }

        @Test
        void regexPattern() {
            String result = GrepTool.execute(
                    new GrepTool.Input("function\\s+\\w+", testDir.toString(), null));
            assertTrue(result.contains("helper.ts"));
            assertTrue(result.contains("add"));
        }

        @Test
        void includeFilter() {
            String result = GrepTool.execute(
                    new GrepTool.Input("return", testDir.toString(), "*.py"));
            assertTrue(result.contains("format.py"));
            assertFalse(result.contains(".ts"));
        }

        @Test
        void searchSingleFile() {
            Path path = testDir.resolve("src/main.ts");
            String result = GrepTool.execute(new GrepTool.Input("console", path.toString(), null));
            assertTrue(result.contains("console.log"));
        }

        @Test
        void showsLineNumbers() {
            String result = GrepTool.execute(new GrepTool.Input("console", testDir.toString(), null));
            assertTrue(result.contains(":2:") || result.contains(":1:"));
        }

        @Test
        void noMatches() {
            String result = GrepTool.execute(
                    new GrepTool.Input("nonexistent_xyz", testDir.toString(), null));
            assertTrue(result.contains("No matches"));
        }

        @Test
        void invalidRegex() {
            String result = GrepTool.execute(new GrepTool.Input("[invalid", testDir.toString(), null));
            assertTrue(result.contains("Error: invalid regex"));
        }
    }
}
