package com.aicode.agent;

import com.aicode.agent.tools.CodebaseSearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodebaseSearchTest {
    @TempDir
    Path workspace;

    @Test
    void findsRelevantChunk() throws Exception {
        Path pkg = workspace.resolve("src/main/java/com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("PaymentService.java"), """
                package com.example;
                public class PaymentService {
                    public void handleFailedPayment(String orderId) {
                        notifyCustomer(orderId);
                    }
                }
                """);

        CodebaseSearch.Result result = CodebaseSearch.search(workspace, "handleFailedPayment", 5);
        assertFalse(result.chunks().isEmpty());
        assertTrue(result.chunks().getFirst().filePath().contains("PaymentService.java"));
        String formatted = CodebaseSearch.formatResults(result);
        assertTrue(formatted.contains("PaymentService"));
    }

    @Test
    void emptyQueryReturnsNoHits() {
        CodebaseSearch.Result result = CodebaseSearch.search(workspace, "  ", 5);
        assertTrue(result.chunks().isEmpty());
    }
}
