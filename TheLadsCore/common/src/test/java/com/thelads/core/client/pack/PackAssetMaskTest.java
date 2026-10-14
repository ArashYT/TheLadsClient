package com.thelads.core.client.pack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class PackAssetMaskTest {

    @AfterEach
    public void tearDown() {
        PackAssetMask.endReload();
    }

    @Test
    public void testZipPackInspection(@TempDir Path tempDir) throws IOException {
        Path textureOnlyZip = tempDir.resolve("textures_only.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(textureOnlyZip))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("{}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("assets/minecraft/textures/items/diamond_sword.png"));
            zos.write(new byte[] {1, 2, 3});
            zos.closeEntry();
        }
        assertEquals(0, PackAssetMask.getMask(textureOnlyZip));

        Path soundAndFontZip = tempDir.resolve("sound_font.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(soundAndFontZip))) {
            zos.putNextEntry(new ZipEntry("assets/minecraft/sounds/random/orb.ogg"));
            zos.write(new byte[] {1});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("assets/minecraft/textures/font/ascii.png"));
            zos.write(new byte[] {2});
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("assets/minecraft/shaders/core/rendertype_solid.fsh"));
            zos.write(new byte[] {3});
            zos.closeEntry();
        }
        int mask = PackAssetMask.getMask(soundAndFontZip);
        assertEquals(PackAssetMask.SOUNDS | PackAssetMask.FONTS | PackAssetMask.SHADERS, mask);
    }

    @Test
    public void testDirectoryPackInspection(@TempDir Path tempDir) throws IOException {
        Path dirPack = Files.createDirectories(tempDir.resolve("dirPack"));
        Files.writeString(dirPack.resolve("pack.mcmeta"), "{}");
        Path blockTex = Files.createDirectories(dirPack.resolve("assets/minecraft/textures/block"));
        Files.write(blockTex.resolve("stone.png"), new byte[] {1});
        assertEquals(0, PackAssetMask.getMask(dirPack));

        Path langDir = Files.createDirectories(dirPack.resolve("assets/minecraft/lang"));
        Files.writeString(langDir.resolve("en_us.json"), "{}");
        Path cemDir = Files.createDirectories(dirPack.resolve("assets/minecraft/optifine/cem"));
        Files.writeString(cemDir.resolve("cow.jem"), "{}");
        // Update assets lastModified or use a new directory path for clean uncached check
        Path dirPack2 = Files.createDirectories(tempDir.resolve("dirPack2"));
        Files.createDirectories(dirPack2.resolve("assets/minecraft/lang"));
        Files.writeString(dirPack2.resolve("assets/minecraft/lang/en_us.json"), "{}");
        Files.createDirectories(dirPack2.resolve("assets/minecraft/optifine/cem"));
        Files.writeString(dirPack2.resolve("assets/minecraft/optifine/cem/cow.jem"), "{}");

        int mask = PackAssetMask.getMask(dirPack2);
        assertEquals(PackAssetMask.LANG | PackAssetMask.CEM_RANDOM_ENTITIES, mask);
    }

    @Test
    public void testComputeDiffMaskAndLifecycle() {
        Map<String, Integer> masks = Map.of(
            "vanilla", PackAssetMask.ALL,
            "fabric", PackAssetMask.ALL,
            "file/pvp_textures.zip", 0,
            "file/custom_sounds.zip", PackAssetMask.SOUNDS,
            "file/custom_font.zip", PackAssetMask.FONTS
        );

        // Adding a texture-only pack above vanilla+fabric leaves diffMask == 0
        int diff1 = PackAssetMask.computeDiffMask(
            List.of("vanilla", "fabric"),
            List.of("vanilla", "fabric", "file/pvp_textures.zip"),
            masks::get
        );
        assertEquals(0, diff1);

        PackAssetMask.beginPackSwitchReload(diff1, 0);
        PackAssetMask.onReloadStart();
        assertTrue(PackAssetMask.shouldSkipSounds());
        assertTrue(PackAssetMask.shouldSkipLang());
        assertTrue(PackAssetMask.shouldSkipFonts());
        assertTrue(PackAssetMask.shouldSkipShaders());
        PackAssetMask.endReload();
        assertFalse(PackAssetMask.shouldSkipSounds());

        // Adding a sound pack sets only SOUNDS
        int diff2 = PackAssetMask.computeDiffMask(
            List.of("vanilla", "fabric", "file/pvp_textures.zip"),
            List.of("vanilla", "fabric", "file/pvp_textures.zip", "file/custom_sounds.zip"),
            masks::get
        );
        assertEquals(PackAssetMask.SOUNDS, diff2);

        PackAssetMask.beginPackSwitchReload(diff2, PackAssetMask.SOUNDS);
        PackAssetMask.onReloadStart();
        assertFalse(PackAssetMask.shouldSkipSounds());
        assertTrue(PackAssetMask.shouldSkipLang());
        assertTrue(PackAssetMask.shouldSkipFonts());
        assertTrue(PackAssetMask.shouldSkipShaders());
        PackAssetMask.endReload();

        // Reordering custom_font.zip and pvp_textures.zip sets FONTS
        int diff3 = PackAssetMask.computeDiffMask(
            List.of("vanilla", "fabric", "file/pvp_textures.zip", "file/custom_font.zip"),
            List.of("vanilla", "fabric", "file/custom_font.zip", "file/pvp_textures.zip"),
            masks::get
        );
        assertEquals(PackAssetMask.FONTS, diff3);
    }
}
