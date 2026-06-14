package com.aicode.agent;

import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TurnContextTest {
    @TempDir
    Path workspace;

    @Test
    void dynamicBlockHasDateButNotStaticWorkspace() {
        String block = TurnContext.of(workspace, null).formatDynamicEnvironmentBlock();
        assertTrue(block.contains("<environment>"));
        assertTrue(block.contains("- Date:"));
        assertFalse(block.contains("- Workspace:"));
        assertFalse(block.contains("- Shell:"));
    }

    @Test
    void prependToUserMessageAddsEnvironment() {
        String result = TurnContext.of(workspace, null).prependToUserMessage("hello");
        assertTrue(result.startsWith("<environment>"));
        assertTrue(result.endsWith("hello"));
    }

    @Test
    void injectOnLastUserMessageOnlyEnrichesLatestUser() {
        TurnContext ctx = TurnContext.of(workspace, null);
        List<Message> messages = List.of(
                Message.user("first question"),
                Message.assistant(List.of(new TextBlock("answer"))),
                Message.user("follow up")
        );
        List<Message> injected = TurnContext.injectOnLastUserMessage(messages, ctx);
        assertEquals("first question", injected.get(0).contentText());
        assertTrue(injected.get(2).contentText().startsWith("<environment>"));
        assertTrue(injected.get(2).contentText().endsWith("follow up"));
    }

    @Test
    void limitGitStatusTruncatesLongOutput() {
        String manyLines = String.join("\n", java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> " M file" + i + ".java")
                .toList());
        String limited = TurnContext.limitGitStatus(manyLines, 20);
        assertTrue(limited.contains("…(+10 more files)"));
        assertEquals(20, limited.lines().filter(l -> !l.startsWith("…")).count());
    }
}
