package com.aicode.agent;

import com.aicode.agent.index.TfIdfIndex;
import com.aicode.agent.index.CodebaseChunkCollector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TfIdfIndexTest {
    @Test
    void ranksRelevantChunkHigher() {
        List<CodebaseChunkCollector.Chunk> chunks = List.of(
                new CodebaseChunkCollector.Chunk("A.java", 1, 3, "class PaymentService { void pay() {} }"),
                new CodebaseChunkCollector.Chunk("B.java", 1, 3, "class HelloWorld { void hi() {} }")
        );
        TfIdfIndex index = new TfIdfIndex(chunks);
        List<TfIdfIndex.ScoredChunk> hits = index.search("pay", 2);
        assertFalse(hits.isEmpty());
        assertTrue(hits.getFirst().chunk().filePath().equals("A.java"));
    }
}
