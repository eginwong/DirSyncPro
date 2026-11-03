package dirsyncpro.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dirsyncpro.Const;
import dirsyncpro.Const.CopyMode;
import dirsyncpro.job.Job;

class SyncQTest {

    @Test
    void filteredQueueReflectsAddsAndDeletes(@TempDir Path tempDir) throws Exception {
        Path sourceFile = Files.writeString(tempDir.resolve("source.txt"), "source");
        Path missingTarget = tempDir.resolve("target.txt");

        Job job = new Job(true);
        SyncPair pair = new SyncPair(
                sourceFile.toFile(), missingTarget.toFile(), true, false, false, false,
                sourceFile.toFile().lastModified(), 0L,
                Files.size(sourceFile), 0L,
                Const.SyncPairStatus.FileAIsNew, true, job);

        SyncQ queue = new SyncQ();
        queue.setSyncQSyncFilterMode(CopyMode.DeleteFiles, false);
        queue.syncFilter();
        queue.add(pair);
        assertEquals(1, queue.size());

        queue.delete(pair);
        assertEquals(0, queue.size());
    }

    @Test
    void statusChangeMovesPairBetweenFilterCategories(@TempDir Path tempDir) throws Exception {
        Path sourceFile = Files.writeString(tempDir.resolve("source.txt"), "source");
        Path targetFile = Files.writeString(tempDir.resolve("target.txt"), "target");

        Job job = new Job(true);
        SyncPair pair = new SyncPair(
                sourceFile.toFile(), targetFile.toFile(), true, true, false, false,
                sourceFile.toFile().lastModified(), targetFile.toFile().lastModified(),
                Files.size(sourceFile), Files.size(targetFile),
                Const.SyncPairStatus.FileAIsNew, true, job);

        SyncQ queue = new SyncQ();
        queue.add(pair);
        queue.setSyncQSyncFilterMode(CopyMode.New, true);
        queue.setSyncQSyncFilterMode(CopyMode.Modified, false);
        queue.syncFilter();
        assertEquals(1, queue.size());

        queue.change(pair, Const.SyncPairStatus.FileAIsModified);
        queue.syncFilter();
        assertEquals(0, queue.size());

        queue.setSyncQSyncFilterMode(CopyMode.New, false);
        queue.setSyncQSyncFilterMode(CopyMode.Modified, true);
        queue.syncFilter();
        assertEquals(1, queue.size());
    }

    @Test
    void filteredViewReflectsQueueMutation(@TempDir Path tempDir) throws Exception {
        Path sourceFile = Files.writeString(tempDir.resolve("source.txt"), "source");
        Path missingTarget = tempDir.resolve("target.txt");

        Job job = new Job(true);
        SyncPair pair = new SyncPair(
                sourceFile.toFile(), missingTarget.toFile(), true, false, false, false,
                sourceFile.toFile().lastModified(), 0L,
                Files.size(sourceFile), 0L,
                Const.SyncPairStatus.FileAIsNew, true, job);

        SyncQ queue = new SyncQ();
        queue.setSyncQViewFilterMode(CopyMode.DeleteFiles, false);
        queue.viewFilter();
        queue.add(pair);
        assertEquals(1, queue.viewSize());

        queue.delete(pair);
        assertEquals(0, queue.viewSize());
    }
}
