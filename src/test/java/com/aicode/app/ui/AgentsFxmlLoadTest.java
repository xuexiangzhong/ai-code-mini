package com.aicode.app.ui;

import com.aicode.app.config.ModelRegistry;
import com.aicode.app.window.WindowManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AgentsFxmlLoadTest {
    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(latch::countDown);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("JavaFX platform failed to start");
        }
    }

    @Test
    void agentsFxmlLoadsWithControllerFactory() {
        assertDoesNotThrow(() -> {
            FXMLLoader loader = new FXMLLoader(AgentsFxmlLoadTest.class.getResource("/ui/agents.fxml"));
            WindowManager wm = new WindowManager(ModelRegistry.load());
            loader.setControllerFactory(type -> {
                if (type == AgentsWindowController.class) {
                    return new AgentsWindowController(wm, Path.of("."), "");
                }
                try {
                    return type.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            loader.load();
        });
    }
}
