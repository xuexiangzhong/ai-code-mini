package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified tools module exports (mirrors {@code python/src/tools/__init__.py} and {@code typescript/src/tools/index.ts}).
 */
public final class Tools {
    private Tools() {}

    public static final Tool READ_TOOL = ReadTool.DEFINITION;
    public static final Tool WRITE_TOOL = WriteTool.DEFINITION;
    public static final Tool SEARCH_REPLACE_TOOL = SearchReplaceTool.DEFINITION;
    public static final Tool DELETE_TOOL = DeleteTool.DEFINITION;
    public static final Tool BASH_TOOL = BashTool.DEFINITION;
    public static final Tool GLOB_TOOL = GlobTool.DEFINITION;
    public static final Tool GREP_TOOL = GrepTool.DEFINITION;
    public static final Tool LIST_DIR_TOOL = ListDirTool.DEFINITION;

    public static String executeRead(ReadTool.Input input) {
        return ReadTool.execute(input);
    }

    public static String executeRead(Map<String, Object> input) {
        return ReadTool.execute(ReadTool.Input.fromMap(input));
    }

    public static String executeWrite(WriteTool.Input input) {
        return WriteTool.execute(input);
    }

    public static String executeWrite(Map<String, Object> input) {
        return WriteTool.execute(WriteTool.Input.fromMap(input));
    }

    public static CompletableFuture<String> executeBash(BashTool.Input input) {
        return BashTool.execute(input);
    }

    public static CompletableFuture<String> executeBash(Map<String, Object> input) {
        return BashTool.execute(BashTool.Input.fromMap(input));
    }

    public static String executeGlob(GlobTool.Input input) {
        return GlobTool.execute(input);
    }

    public static String executeGlob(Map<String, Object> input) {
        return GlobTool.execute(GlobTool.Input.fromMap(input));
    }

    public static String executeGrep(GrepTool.Input input) {
        return GrepTool.execute(input);
    }

    public static String executeGrep(Map<String, Object> input) {
        return GrepTool.execute(GrepTool.Input.fromMap(input));
    }
}
