package com.thelads.core.client.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PackFolderHelperTest {

    @Test
    public void testIsPackAndIsFolder(@TempDir Path tempDir) throws IOException {
        // Zip file -> pack, not folder
        Path zipPack = Files.createFile(tempDir.resolve("pack1.zip"));
        assertTrue(PackFolderHelper.isPack(zipPack));
        assertFalse(PackFolderHelper.isFolder(zipPack));

        // Directory with pack.mcmeta -> pack, not folder
        Path dirPack = Files.createDirectory(tempDir.resolve("folderPack"));
        Files.createFile(dirPack.resolve("pack.mcmeta"));
        assertTrue(PackFolderHelper.isPack(dirPack));
        assertFalse(PackFolderHelper.isFolder(dirPack));

        // Directory without pack.mcmeta -> folder, not pack
        Path subfolder = Files.createDirectory(tempDir.resolve("PvP"));
        assertFalse(PackFolderHelper.isPack(subfolder));
        assertTrue(PackFolderHelper.isFolder(subfolder));

        // Non-existent path -> neither
        Path nonExistent = tempDir.resolve("missing");
        assertFalse(PackFolderHelper.isPack(nonExistent));
        assertFalse(PackFolderHelper.isFolder(nonExistent));
    }

    @Test
    public void testRelativePathAndParent() {
        Path root = Path.of("C:/games/minecraft/resourcepacks");
        Path child = Path.of("C:/games/minecraft/resourcepacks/PvP/pack1.zip");
        Path subChild = Path.of("C:/games/minecraft/resourcepacks/PvP/Bedwars/pack2.zip");

        assertEquals("PvP/pack1.zip", PackFolderHelper.getRelativePath(root, child));
        assertEquals("PvP/Bedwars/pack2.zip", PackFolderHelper.getRelativePath(root, subChild));
        assertEquals("", PackFolderHelper.getRelativePath(root, root));

        assertEquals("PvP", PackFolderHelper.getParentRelativePath("PvP/Bedwars"));
        assertEquals("", PackFolderHelper.getParentRelativePath("PvP"));
        assertNull(PackFolderHelper.getParentRelativePath(""));
        assertNull(PackFolderHelper.getParentRelativePath(null));
    }

    @Test
    public void testRelativePackPath() {
        // Direct child of resourcepacks
        Path rootPack = Path.of("C:/Users/Arash/AppData/Roaming/.minecraft/resourcepacks/AlphaPack.zip");
        assertEquals("AlphaPack.zip", PackFolderHelper.getRelativePackPath(rootPack));

        // Subfolder child of resourcepacks
        Path subPack = Path.of("C:/Users/Arash/AppData/Roaming/.minecraft/resourcepacks/PvP/PvPPackOne.zip");
        assertEquals("PvP/PvPPackOne.zip", PackFolderHelper.getRelativePackPath(subPack));

        // Deep subfolder child of resourcepacks
        Path deepPack = Path.of("C:/minecraft/resourcepacks/PvP/Bedwars/Pack.zip");
        assertEquals("PvP/Bedwars/Pack.zip", PackFolderHelper.getRelativePackPath(deepPack));

        // Folder pack (directory) inside subfolder
        Path folderPack = Path.of("/home/user/.minecraft/resourcepacks/Folders/CustomPack");
        assertEquals("Folders/CustomPack", PackFolderHelper.getRelativePackPath(folderPack));

        // Case-insensitivity on RESOURCEPACKS directory name
        Path upperPack = Path.of("D:/games/RESOURCEPACKS/PvP/pack.zip");
        assertEquals("PvP/pack.zip", PackFolderHelper.getRelativePackPath(upperPack));

        // Path not inside a resourcepacks directory falls back to filename
        Path otherPack = Path.of("C:/custom/path/pack.zip");
        assertEquals("pack.zip", PackFolderHelper.getRelativePackPath(otherPack));

        // Null and empty checks
        assertEquals("", PackFolderHelper.getRelativePackPath((Path) null));
    }

    @Test
    public void testIsInsideFolder() {
        // Non-recursive
        assertTrue(PackFolderHelper.isInsideFolder("pack1.zip", "", false));
        assertFalse(PackFolderHelper.isInsideFolder("PvP/pack1.zip", "", false));
        assertTrue(PackFolderHelper.isInsideFolder("PvP/pack1.zip", "PvP", false));
        assertFalse(PackFolderHelper.isInsideFolder("PvP/sub/pack1.zip", "PvP", false));
        assertFalse(PackFolderHelper.isInsideFolder("Other/pack1.zip", "PvP", false));

        // Recursive (search within folder and below)
        assertTrue(PackFolderHelper.isInsideFolder("pack1.zip", "", true));
        assertTrue(PackFolderHelper.isInsideFolder("PvP/pack1.zip", "", true));
        assertTrue(PackFolderHelper.isInsideFolder("PvP/pack1.zip", "PvP", true));
        assertTrue(PackFolderHelper.isInsideFolder("PvP/sub/pack1.zip", "PvP", true));
        assertFalse(PackFolderHelper.isInsideFolder("Other/pack1.zip", "PvP", true));
    }

    @Test
    public void testFindSubfoldersAndScanAllPacks(@TempDir Path tempDir) throws IOException {
        // Root: packA.zip, packFolder (with pack.mcmeta), folderPvP (no mcmeta)
        Path packA = Files.createFile(tempDir.resolve("packA.zip"));
        Path packFolder = Files.createDirectory(tempDir.resolve("packFolder"));
        Files.createFile(packFolder.resolve("pack.mcmeta"));

        Path folderPvP = Files.createDirectory(tempDir.resolve("PvP"));
        // Inside PvP: packB.zip, packC.zip, subFolder (no mcmeta)
        Path packB = Files.createFile(folderPvP.resolve("packB.zip"));
        Path packC = Files.createFile(folderPvP.resolve("packC.zip"));
        Path subFolder = Files.createDirectory(folderPvP.resolve("Bedwars"));
        Path packD = Files.createFile(subFolder.resolve("packD.zip"));

        // Direct subfolders of root -> only PvP (packFolder is a pack!)
        List<Path> subfoldersRoot = PackFolderHelper.findSubfolders(tempDir);
        assertEquals(1, subfoldersRoot.size());
        assertEquals("PvP", subfoldersRoot.get(0).getFileName().toString());

        // Direct subfolders of PvP -> only Bedwars
        List<Path> subfoldersPvP = PackFolderHelper.findSubfolders(folderPvP);
        assertEquals(1, subfoldersPvP.size());
        assertEquals("Bedwars", subfoldersPvP.get(0).getFileName().toString());

        // Recursive pack scan -> packA.zip, packFolder, packB.zip, packC.zip, packD.zip (5 packs total)
        List<Path> allPacks = PackFolderHelper.scanAllPacks(tempDir);
        assertEquals(5, allPacks.size());
    }
}
