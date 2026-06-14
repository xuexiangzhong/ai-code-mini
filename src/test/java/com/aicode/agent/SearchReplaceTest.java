package com.aicode.agent;

import com.aicode.agent.tools.SearchReplaceTool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SearchReplaceTest {
    @TempDir
    Path testDir;

    @Nested
    class TestDefinition {
        @Test
        void hasCorrectName() {
            assertEquals("search_replace", SearchReplaceTool.DEFINITION.name());
        }
    }

    @Nested
    class TestExecute {
        @Test
        void singleReplacement() throws Exception {
            Path path = testDir.resolve("a.txt");
            Files.writeString(path, "hello world\n");
            String result = SearchReplaceTool.execute(new SearchReplaceTool.Input(
                    path.toString(), "world", "universe", false));
            assertTrue(result.contains("Updated"));
            assertTrue(result.contains("1 replacement"));
            assertEquals("hello universe\n", Files.readString(path));
        }

        @Test
        void replaceAll() throws Exception {
            Path path = testDir.resolve("b.txt");
            Files.writeString(path, "aa bb aa\n");
            String result = SearchReplaceTool.execute(new SearchReplaceTool.Input(
                    path.toString(), "aa", "xx", true));
            assertTrue(result.contains("2 replacement"));
            assertEquals("xx bb xx\n", Files.readString(path));
        }

        @Test
        void rejectsMultipleMatchesWithoutReplaceAll() throws Exception {
            Path path = testDir.resolve("c.txt");
            Files.writeString(path, "dup\ndup\n");
            String result = SearchReplaceTool.execute(new SearchReplaceTool.Input(
                    path.toString(), "dup", "x", false));
            assertTrue(result.contains("appears 2 times"));
            assertEquals("dup\ndup\n", Files.readString(path));
        }

        @Test
        void notFound() throws Exception {
            Path path = testDir.resolve("d.txt");
            Files.writeString(path, "hello\n");
            String result = SearchReplaceTool.execute(new SearchReplaceTool.Input(
                    path.toString(), "missing", "x", false));
            assertTrue(result.contains("not found"));
        }
    }
}
