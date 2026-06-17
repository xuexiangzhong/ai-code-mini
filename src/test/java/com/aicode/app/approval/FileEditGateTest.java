package com.aicode.app.approval;

import com.aicode.app.session.FileEditProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileEditGateTest {

    @Test
    void resolveKeepCompletesFuture(@TempDir Path workspace) throws Exception {
        Path file = workspace.resolve("demo.txt");
        Files.writeString(file, "new");
        FileEditProposal proposal = FileEditProposal.create(file, "old", "new", false);
        FileEditGate gate = new FileEditGate();

        var future = gate.awaitReview(proposal);
        assertFalse(future.isDone());

        gate.resolve(proposal.id(), true);

        assertTrue(future.join());
        assertEquals("new", Files.readString(file));
    }

    @Test
    void resolveRevertRollsBackAndCompletesFalse(@TempDir Path workspace) throws Exception {
        Path file = workspace.resolve("demo.txt");
        Files.writeString(file, "new");
        FileEditProposal proposal = FileEditProposal.create(file, "old", "new", false);
        FileEditGate gate = new FileEditGate();

        var future = gate.awaitReview(proposal);
        gate.resolve(proposal.id(), false);

        assertFalse(future.join());
        assertEquals("old", Files.readString(file));
    }

    @Test
    void secondEditToSameFileAfterKeepSkipsReview(@TempDir Path workspace) throws Exception {
        Path file = workspace.resolve("demo.txt");
        FileEditGate gate = new FileEditGate();

        Files.writeString(file, "v1");
        FileEditProposal first = FileEditProposal.create(file, "old", "v1", false);
        var firstFuture = gate.awaitReview(first);
        assertTrue(gate.needsReview(file));
        gate.resolve(first.id(), true);
        assertTrue(firstFuture.join());

        Files.writeString(file, "v2");
        FileEditProposal second = FileEditProposal.create(file, "v1", "v2", false);
        assertFalse(gate.needsReview(file));
        assertTrue(gate.awaitReview(second).join());
    }

    @Test
    void rejectThenRewriteRequiresReviewAgain(@TempDir Path workspace) throws Exception {
        Path file = workspace.resolve("demo.txt");
        FileEditGate gate = new FileEditGate();

        Files.writeString(file, "v1");
        FileEditProposal first = FileEditProposal.create(file, "old", "v1", false);
        var firstFuture = gate.awaitReview(first);
        gate.resolve(first.id(), false);
        assertFalse(firstFuture.join());
        assertTrue(gate.needsReview(file));

        Files.writeString(file, "v2");
        FileEditProposal second = FileEditProposal.create(file, "old", "v2", false);
        var secondFuture = gate.awaitReview(second);
        assertFalse(secondFuture.isDone());
    }
}
