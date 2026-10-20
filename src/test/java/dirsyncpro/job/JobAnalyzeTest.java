package dirsyncpro.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dirsyncpro.Const;
import dirsyncpro.DirSyncPro;
import dirsyncpro.sync.Sync;
import dirsyncpro.sync.SyncPair;

class JobAnalyzeTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Path homeDir = tempDir.resolve("home");
        Files.createDirectories(homeDir);
        System.setProperty("dirsyncpro.home", homeDir.toString());
        DirSyncPro.loadProperties();
        DirSyncPro.setUpLogFile(false);

        Sync sync = new Sync();
        Field syncDateField = Sync.class.getDeclaredField("syncDate");
        syncDateField.setAccessible(true);
        syncDateField.set(sync, new Date());
        DirSyncPro.setSync(sync);
    }

    @Test
    void mirrorModeDetectsNestedNewFilesDuringRecursiveScan() throws Exception {
        Path sourceRoot = tempDir.resolve("source");
        Path targetRoot = tempDir.resolve("target");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);

        Path nestedSourceDir = Files.createDirectories(sourceRoot.resolve("nested"));
        Path sourceFile = Files.createFile(nestedSourceDir.resolve("alpha.txt"));
        Files.writeString(sourceFile, "new content");

        Job job = new Job(true);
        job.setName("nested-new-file");
        job.setSrc(sourceRoot.toString());
        job.setDst(targetRoot.toString());
        job.setSyncMode(Const.SyncMode.ABMirror);

        job.analyze();

        assertEquals(2, DirSyncPro.getSync().getSyncQ().size());
        boolean sawDirNew = false;
        boolean sawFileNew = false;
        for (int i = 0; i < DirSyncPro.getSync().getSyncQ().size(); i++) {
            SyncPair pair = DirSyncPro.getSync().getSyncQ().get(i);
            if (pair.getSyncPairStatus() == Const.SyncPairStatus.DirAIsNew) {
                sawDirNew = true;
            }
            if (pair.getSyncPairStatus() == Const.SyncPairStatus.FileAIsNew
                    && pair.getFileA().equals(sourceFile.toFile())) {
                sawFileNew = true;
            }
        }
        assertEquals(true, sawDirNew);
        assertEquals(true, sawFileNew);
    }

    @Test
    void mirrorModeDetectsModifiedFilesByTimestamp() throws Exception {
        Path sourceRoot = tempDir.resolve("source");
        Path targetRoot = tempDir.resolve("target");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);

        Path sourceFile = Files.createFile(sourceRoot.resolve("alpha.txt"));
        Path targetFile = Files.createFile(targetRoot.resolve("alpha.txt"));
        Files.writeString(sourceFile, "newer content");
        Files.writeString(targetFile, "older content");

        long sourceTime = System.currentTimeMillis();
        long targetTime = sourceTime - 10_000L;
        Files.setLastModifiedTime(sourceFile, FileTime.fromMillis(sourceTime));
        Files.setLastModifiedTime(targetFile, FileTime.fromMillis(targetTime));

        Job job = new Job(true);
        job.setName("modified-file");
        job.setSrc(sourceRoot.toString());
        job.setDst(targetRoot.toString());
        job.setSyncMode(Const.SyncMode.ABMirror);

        job.analyze();

        assertEquals(1, DirSyncPro.getSync().getSyncQ().size());
        SyncPair pair = DirSyncPro.getSync().getSyncQ().get(0);
        assertEquals(Const.SyncPairStatus.FileAIsModified, pair.getSyncPairStatus());
        assertEquals(sourceFile.toFile(), pair.getFileA());
        assertEquals(targetFile.toFile(), pair.getFileB());
    }

    @Test
    void mirrorModeDetectsRedundantFilesPresentOnlyInDestination() throws Exception {
        Path sourceRoot = tempDir.resolve("source");
        Path targetRoot = tempDir.resolve("target");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);

        Path orphanFile = Files.createFile(targetRoot.resolve("orphan.txt"));
        Files.writeString(orphanFile, "obsolete content");

        Job job = new Job(true);
        job.setName("redundant-file");
        job.setSrc(sourceRoot.toString());
        job.setDst(targetRoot.toString());
        job.setSyncMode(Const.SyncMode.ABMirror);

        job.analyze();

        assertEquals(1, DirSyncPro.getSync().getSyncQ().size());
        SyncPair pair = DirSyncPro.getSync().getSyncQ().get(0);
        assertEquals(Const.SyncPairStatus.FileBIsRedundant, pair.getSyncPairStatus());
        assertEquals(orphanFile.toFile(), pair.getFileB());
    }

    @Test
    void recursiveJobAnalysisVisitsNestedDirectoriesAndSkipsTheRootFolders() throws Exception {
        Path sourceRoot = tempDir.resolve("source");
        Path targetRoot = tempDir.resolve("target");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);

        Path childDir = Files.createDirectories(sourceRoot.resolve("nested").resolve("child"));
        Files.createFile(childDir.resolve("leaf.txt"));

        Job job = new Job(true);
        job.setName("recursive-visit");
        job.setSrc(sourceRoot.toString());
        job.setDst(targetRoot.toString());
        job.setSyncMode(Const.SyncMode.ABMirror);

        job.analyze();

        assertEquals(3, DirSyncPro.getSync().getSyncQ().size());
        boolean sawNestedDir = false;
        boolean sawChildDir = false;
        boolean sawLeafFile = false;
        for (int i = 0; i < DirSyncPro.getSync().getSyncQ().size(); i++) {
            SyncPair pair = DirSyncPro.getSync().getSyncQ().get(i);
            if (pair.getSyncPairStatus() == Const.SyncPairStatus.DirAIsNew
                    && pair.getFileA().equals(sourceRoot.resolve("nested").toFile())) {
                sawNestedDir = true;
            }
            if (pair.getSyncPairStatus() == Const.SyncPairStatus.DirAIsNew
                    && pair.getFileA().equals(sourceRoot.resolve("nested").resolve("child").toFile())) {
                sawChildDir = true;
            }
            if (pair.getSyncPairStatus() == Const.SyncPairStatus.FileAIsNew
                    && pair.getFileA().equals(sourceRoot.resolve("nested").resolve("child").resolve("leaf.txt").toFile())) {
                sawLeafFile = true;
            }
        }
        assertEquals(true, sawNestedDir);
        assertEquals(true, sawChildDir);
        assertEquals(true, sawLeafFile);
    }
}
