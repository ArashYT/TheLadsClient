package com.thelads.core.v26_2.embedded.etf.config.screens.skin;


import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.FileUtil;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;

import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import org.apache.commons.io.FilenameUtils;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.ETFException;
import com.thelads.core.v26_2.embedded.etf.features.ETFManager;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v26_2.embedded.etf.utils.UScreen;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


//inspired by puzzles custom gui code
public class ETFConfigScreenSkinToolOutcome extends ETFScreenOldCompat {
    private final boolean didSucceed;
    private final NativeImage skin;

    protected ETFConfigScreenSkinToolOutcome(Screen parent, boolean success, NativeImage skin) {
        super("config." + ETF.MOD_ID + ".player_skin_editor.print_skin.result", parent, false);
        didSucceed = success;
        this.skin = skin;
        //this.skin = new PlayerSkinTexture(skin);
    }

    public static boolean uploadSkin(boolean skinType) {
        try {
            if ("127.0.0.1".equals(InetAddress.getLocalHost().getHostAddress())) {
                return false;
            }

            String auth = Minecraft.getInstance().getUser().getAccessToken();

            Path skinPath = Path.of(
                    ETF.getConfigDirectory().toFile().getParent(),
                    "ETF_player_skin_printout.png"
            );

            String boundary = UUID.randomUUID().toString();
            byte[] fileBytes = Files.readAllBytes(skinPath);

            String bodyStart =
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"variant\"\r\n\r\n" +
                            (skinType ? "classic" : "slim") + "\r\n" +
                            "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n" +
                            "Content-Type: image/png\r\n\r\n";

            String bodyEnd = "\r\n--" + boundary + "--\r\n";

            byte[] requestBody = concat(
                    bodyStart.getBytes(),
                    fileBytes,
                    bodyEnd.getBytes()
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
                    .header("Authorization", "Bearer " + auth)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();


            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            client.close();
            return response.statusCode() == 200;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;

        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();


        this.addRenderableWidget(getETFButton((int) (this.width * 0.55), (int) (this.height * 0.9), (int) (this.width * 0.2), 20,
                CommonComponents.GUI_DONE,
                (button) -> UScreen.setScreen(parent)));
        if (didSucceed) {
            this.addRenderableWidget(getETFButton((int) (this.width * 0.15), (int) (this.height * 0.6), (int) (this.width * 0.7), 20,
                    ETF.getTextFromTranslation("config." + ETF.MOD_ID + ".player_skin_editor.print_skin.open"),
                    (button) -> {
                        try {
                            assert ETF.getConfigDirectory() != null;
                            Path outputDirectory = Path.of(ETF.getConfigDirectory().toFile().getParent());
                            com.mojang.blaze3d.Blaze3D.openPath(outputDirectory);
                        } catch (Exception ignored) {
                        }
                    }));
            this.addRenderableWidget(getETFButton((int) (this.width * 0.15), (int) (this.height * 0.4), (int) (this.width * 0.7), 20,
                    ETF.getTextFromTranslation("config." + ETF.MOD_ID + ".player_skin_editor.upload_skin"),
                    (button) -> {
                        if (Minecraft.getInstance().player == null) return;
                        boolean skinType = true;//true for steve false for alex
                        if (Minecraft.getInstance().getConnection() != null) {
                            PlayerInfo playerListEntry = Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID());
                            if (playerListEntry != null) {
                                try {
                                    var skin = Minecraft.getInstance().getSkinManager().get(playerListEntry.getProfile()).get();
                                    if (skin.isPresent()) skinType = skin.get().model() == PlayerModelType.WIDE;
                                } catch (InterruptedException | ExecutionException ignored) { }
                            }
                        }
                        boolean changeSuccess = uploadSkin(skinType);
                        button.setMessage(ETF.getTextFromTranslation("config." + ETF.MOD_ID + ".player_skin_editor.upload_skin_v2." +
                                (changeSuccess ? "success" : "fail")));
                        if (changeSuccess) {
                            // change internally cached skin
                            try {
                                GameProfile gameProfile = Minecraft.getInstance().player.getGameProfile();
                                var playerSkinOptional = Minecraft.getInstance().getSkinManager().get(gameProfile).get();
                                if (playerSkinOptional.isEmpty()) throw new ETFException("No profile texture found for player: " + gameProfile.name());

                                var minecraftProfileTexture = playerSkinOptional.get().body();
                                if (!(minecraftProfileTexture instanceof ClientAsset.DownloadedTexture))  throw new ETFException("No profile texture found 2 for player: " + gameProfile.name());

                                var url = new URL(((ClientAsset.DownloadedTexture) minecraftProfileTexture).url()).getPath();
                                String string = Hashing.sha1().hashUnencodedChars(FilenameUtils.getBaseName(url)).toString();

                                Path path = Minecraft.getInstance().getSkinManager().skinTextures.
                                        root.resolve(string.length() > 2 ? string.substring(0, 2) : "xx").resolve(string);
                                if (Files.isRegularFile(path)){
                                    FileUtil.createDirectoriesSafe(path.getParent());
                                    skin.writeToFile(path);
                                }

                            }catch (Exception e){
                                ETFUtils2.logError("Failed to change in-game skin correctly, you might need to restart to see all the uploaded changes in-game", true);
                                ETFUtils2.logError("cause: " + e.getMessage(), false);
                            }

                            // update the registered texture
                            var texture = Minecraft.getInstance().getTextureManager().getTexture(Minecraft.getInstance().player.getSkin()
                                    .body().texturePath());
                            if(texture instanceof DynamicTexture dynamicTexture){
                                dynamicTexture.setPixels(skin);
                            }


                            // clear etf data of skin
                            if (Minecraft.getInstance().player != null) {
                                ETFManager.getInstance().PLAYER_TEXTURE_MAP.removeEntryOnly(Minecraft.getInstance().player.getUUID());
                            }
                        }else {
                            ETFUtils2.logError("Failed to change in-game skin correctly, you might need to restart to see all the uploaded changes in-game", true);
                        }
                        button.active = false;
                    }));
        }
    }

    @Override
    public void
        extractRenderState
    (GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.
                extractRenderState
                        (context, mouseX, mouseY, delta);

        String[] strings =
                ETF.getTextFromTranslation(
                        "config." + ETF.MOD_ID + ".player_skin_editor.print_skin.result." + (didSucceed ? "success" : "fail")
                ).getString().split("\n");
        List<Component> lines = new ArrayList<>();

        for (String str :
                strings) {
            lines.add(Component.nullToEmpty(str.strip()));
        }
        int i = 0;
        for (Component txt :
                lines) {
            context.centeredText(font, txt.getVisualOrderText(), (int) (width * 0.5), (int) (height * 0.3) + i, 0xFFFFFF);
            i += txt.getString().isBlank() ? 5 : 10;
        }


    }

}
