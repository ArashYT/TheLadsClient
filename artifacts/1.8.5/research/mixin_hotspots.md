# Mixin hot spots (vanilla classes mixed into by >= 3 mod jars)

Mixin classes per mod in brackets; OW = @Overwrite, RD = @Redirect. Counted statically from the 128 baseline jars (the three jars another lane added later are not included). Targets of Fabric-intermediary mods (Immersive Winds, Lads Core, Continuity) are not resolved.

| # mods | target class | mods (mixin classes) |
|---:|---|---|
| 28 | `net.minecraft.client.Minecraft` | ModernFix(9/OW1/RD2), Embeddium(2), Entity Model Features(2), moreculling(2), Xaero's World Map(2), Distant Horizons(1/RD1), GlitchCore(1/RD1), ImmediatelyFast(1), Quark(1), Alex's Caves(1), Architectury API(1), Balm(1), Carry On(1), Dynamic FPS(1), Entity Texture Features(1), Exposure(1), Immersive Armors(1), Konkrete(1), Moonlight Lib(1), Not Enough Animations(1), Oculus(1), Embeddium (Rubidium) Extra(1), 3D Skin Layers(1), Sound Physics Remastered(1), Supplementaries(1), What Are They Up To (Watut)(1), Xaero's Minimap(1), YetAnotherConfigLib (YACL)(1) |
| 22 | `net.minecraft.world.entity.Entity` | Kiwi(2), Artifacts(2), backpacked(2), Create(2), Exposure(2), ModernFix(2), CorgiLib(1), Data Anchor(1), Distant Horizons(1), Quark(1), Alex's Caves(1), Balm(1), Carry On(1), Curios API(1), Entity Model Features(1), Entity Texture Features(1), Entity Culling(1), Open Parties and Claims(1), Particular  Reforged(1), Sound Physics Remastered(1), Supplementaries(1), Wakes Reforged(1) |
| 21 | `net.minecraft.world.entity.player.Player` | Entity Texture Features(2), Supplementaries(2), Easy Magic(1), Farmer's Delight(1), Kiwi(1), Paragliders(1), Alex's Caves(1), backpacked(1), Carry On(1), Comforts(1), Create(1), Entity Model Features(1), Exposure(1), Immersive Aircraft(1), Immersive Armors(1), Not Enough Animations(1), Open Parties and Claims(1), playerAnimator(1), 3D Skin Layers(1), What Are They Up To (Watut)(1), YDM's Weapon Master(1) |
| 20 | `net.minecraft.client.renderer.GameRenderer` | Oculus(5/RD4), Entity Model Features(2), ImmediatelyFast(1), Kiwi(1), Coastal Waves(1), Zeta(1), Alex's Caves(1), Artifacts(1), Create(1), Dynamic FPS(1), Embeddium(1), Entity Texture Features(1), Exposure(1), Immersive Aircraft(1), Just Zoom(1), ModernFix(1), Moonlight Lib(1), Embeddium (Rubidium) Extra(1), Supplementaries(1), What Are They Up To (Watut)(1) |
| 20 | `net.minecraft.client.renderer.LevelRenderer` | Oculus(11/RD1), Embeddium (Rubidium) Extra(6), Embeddium(5/OW10/RD2), moreculling(3), Particle Effects(2), Create(2), Particle Core(2), Supplementaries(2), Distant Horizons(1), Quark(1), Alex's Caves(1), Citadel(1/RD2), Cool Rain Reforged(1), Entity Texture Features(1), Entity Culling(1), Grassier Grass(1), Not Enough Animations(1), Particle Rain(1), Particular  Reforged(1), playerAnimator(1) |
| 20 | `net.minecraft.world.entity.LivingEntity` | Artifacts(6), Create(3), Supplementaries(3), backpacked(2), Particle Effects(1), Quark(1), Alex's Caves(1), Citadel(1), Curios API(1), Ecologics(1), Enderman Overhaul(1), Exposure(1), Integrated Dungeons and Structures(1), Immersive Armors(1), Immersive Hotbar(1), ModernFix(1/RD1), Not Enough Animations(1), Open Parties and Claims(1), Particle Core(1), Particular  Reforged(1) |
| 15 | `net.minecraft.client.multiplayer.ClientLevel` | Embeddium(4/OW1/RD2), Particle Rain(2), Alex's Caves(1), Architectury API(1), Citadel(1), Entity Culling(1), Grassier Grass(1), Not Enough Animations(1), Oculus(1), Open Parties and Claims(1), Particular  Reforged(1), Sound Physics Remastered(1), Supplementaries(1), Xaero's Minimap(1), Xaero's World Map(1) |
| 15 | `net.minecraft.server.level.ServerLevel` | ModernFix(3/RD2), CorgiLib(1), Data Anchor(1), GlitchCore(1), Snow! Real Magic!(1), You Shall Not Spawn!(1), backpacked(1), Citadel(1), Comforts(1), Create(1), Integrated Dungeons and Structures(1), Moonlight Lib(1), Open Parties and Claims(1), Supplementaries(1), Xaero's World Map(1) |
| 13 | `net.minecraft.client.multiplayer.ClientPacketListener` | ModernFix(2), Xaero's World Map(2), Distant Horizons(1), Kiwi(1), Alex's Caves(1), Chat Heads(1), Embeddium(1), Immersive Aircraft(1), Moonlight Lib(1), Oculus(1), Open Parties and Claims(1), Sound Physics Remastered(1), Xaero's Minimap(1) |
| 13 | `net.minecraft.server.level.ServerPlayer` | Distant Horizons(1), GlitchCore(1), YUNG's API(1), backpacked(1), Exposure(1), Integrated Dungeons and Structures(1), Immersive Aircraft(1), ModernFix(1), Moonlight Lib(1), Open Parties and Claims(1), Supplementaries(1), Xaero's Minimap(1), Xaero's World Map(1) |
| 11 | `net.minecraft.client.gui.Gui` | Immersive Hotbar(5/RD1), Dynamic FPS(2), ImmediatelyFast(1), Quark(1), backpacked(1), Create(1), Embeddium(1/RD1), Immersive Aircraft(1), Immersive Armors(1), Oculus(1), What Are They Up To (Watut)(1) |
| 10 | `net.minecraft.client.renderer.entity.LivingEntityRenderer` | ModernFix(2/RD1), playerAnimator(2/RD2), Alex's Caves(1), Artifacts(1), Citadel(1), Entity Model Features(1), Entity Texture Features(1), Immersive Aircraft(1), Not Enough Animations(1), Embeddium (Rubidium) Extra(1) |
| 10 | `net.minecraft.world.level.block.Block` | Kiwi(3), Snow! Real Magic!(1), Another Furniture(1), Architectury API(1), backpacked(1), Create(1), ModernFix(1), moreculling(1), Open Parties and Claims(1), Wakes Reforged(1) |
| 9 | `net.minecraft.client.MouseHandler` | CorgiLib(1), CreativeCore(1), Balm(1), Create(1), Exposure(1), Just Zoom(1), Konkrete(1), Supplementaries(1), What Are They Up To (Watut)(1) |
| 9 | `net.minecraft.client.gui.components.DebugScreenOverlay` | ImmediatelyFast(2), Embeddium(2/RD1), Exposure(2), Oculus(2), Distant Horizons(1), Necronomicon API(1), Dynamic FPS(1), Entity Culling(1), Wakes Reforged(1) |
| 9 | `net.minecraft.client.gui.screens.Screen` | What Are They Up To (Watut)(2), Controlling(1), Kiwi(1), Quark(1), Balm(1), configured(1), Create(1), Dynamic FPS(1), Konkrete(1) |
| 9 | `net.minecraft.client.model.HumanoidModel` | Quark(1), Artifacts(1), Carry On(1), Citadel(1), Create(1), Exposure(1/RD1), Moonlight Lib(1), playerAnimator(1), YDM's Weapon Master(1) |
| 9 | `net.minecraft.client.player.LocalPlayer` | Xaero's Minimap(2), Farmer's Delight(1), Kiwi(1), Alex's Caves(1), Create(1), Exposure(1), Immersive Aircraft(1), Sound Physics Remastered(1), Supplementaries(1) |
| 9 | `net.minecraft.server.MinecraftServer` | ModernFix(5/RD2), YUNG's API(1), Balm(1), Citadel(1), Exposure(1), Moonlight Lib(1), Open Parties and Claims(1), Xaero's Minimap(1), Xaero's World Map(1) |
| 9 | `net.minecraft.world.item.Item` | Moonlight Lib(2), Farmer's Delight(1), Puzzles Lib(1), Quark(1), Zeta(1), Architectury API(1), ftb(1), ModernFix(1), Oculus(1) |
| 8 | `net.minecraft.client.Camera` | Snow! Real Magic!(1), Alex's Caves(1), Create(1), Exposure(1), Immersive Aircraft(1), playerAnimator(1), Embeddium (Rubidium) Extra(1), Supplementaries(1) |
| 8 | `net.minecraft.client.model.geom.ModelPart` | Embeddium(2/OW2/RD2), Entity Model Features(1), Entity Texture Features(1), Oculus(1/OW1), playerAnimator(1), 3D Skin Layers(1), Supplementaries(1), Xaero's Minimap(1) |
| 8 | `net.minecraft.client.renderer.entity.EntityRenderDispatcher` | Oculus(4), Embeddium(1), Entity Model Features(1), Entity Texture Features(1), Immersive Aircraft(1), Not Enough Animations(1), playerAnimator(1), Embeddium (Rubidium) Extra(1) |
| 8 | `net.minecraft.client.renderer.entity.ItemRenderer` | moreculling(3), ModernFix(2/RD1), Quark(1), Embeddium(1), Exposure(1), Immersive Armors(1), Mahou Tsukai(1), Oculus(1) |
| 8 | `net.minecraft.client.renderer.entity.player.PlayerRenderer` | Create(1), Entity Model Features(1), Entity Texture Features(1), Immersive Aircraft(1), ModernFix(1/RD1), Not Enough Animations(1), playerAnimator(1), 3D Skin Layers(1) |
| 8 | `net.minecraft.server.players.PlayerList` | ModernFix(3/RD1), Data Anchor(1), Balm(1), ftb(1), Open Parties and Claims(1), What Are They Up To (Watut)(1), Xaero's Minimap(1), Xaero's World Map(1) |
| 8 | `net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase` | ModernFix(2/RD4), Kiwi(1), Snow! Real Magic!(1), FerriteCore(1), moreculling(1), Oculus(1/OW1), Supplementaries(1), What Are They Up To (Watut)(1) |
| 8 | `net.minecraft.world.level.chunk.ChunkGenerator` | Distant Horizons(2), YUNG's Better Mineshafts(1), YUNG's Better Nether Fortresses(1), Citadel(1), Integrated Dungeons and Structures(1), Integrated API(1), Integrated Stronghold(1), Integrated Villages(1) |
| 7 | `com.mojang.blaze3d.vertex.BufferBuilder` | Embeddium(4/OW3), Oculus(4), CreativeCore(1), ImmediatelyFast(1), Entity Texture Features(1), ModernFix(1), Particle Core(1) |
| 7 | `net.minecraft.client.renderer.ItemInHandRenderer` | Quark(1), Entity Model Features(1), Exposure(1), Moonlight Lib(1), Not Enough Animations(1), Oculus(1), playerAnimator(1) |
| 7 | `net.minecraft.server.network.ServerGamePacketListenerImpl` | Quark(1), Artifacts(1), Exposure(1), Lootr(1/RD1), ModernFix(1/RD1), Open Parties and Claims(1), Supplementaries(1) |
| 7 | `net.minecraft.world.level.biome.Biome` | Quark(1), Embeddium(1/OW2), ModernFix(1/OW1), Oculus(1), Particular  Reforged(1), Embeddium (Rubidium) Extra(1), Tectonic(1) |
| 7 | `net.minecraft.world.level.block.FireBlock` | Open Parties and Claims(2), Supplementaries(2), Kiwi(1), Puzzles Lib(1), Another Furniture(1), Ecologics(1), Moonlight Lib(1) |
| 7 | `net.minecraft.world.level.chunk.LevelChunk` | CorgiLib(1), Snow! Real Magic!(1), Lootr(1), ModernFix(1), Moonlight Lib(1/RD1), Xaero's Minimap(1), Xaero's World Map(1) |
| 6 | `net.minecraft.client.model.PlayerModel` | Not Enough Animations(2), Paragliders(1), Entity Model Features(1), playerAnimator(1), 3D Skin Layers(1), What Are They Up To (Watut)(1) |
| 6 | `net.minecraft.client.particle.ParticleEngine` | Particle Core(7), Particle Effects(3), Oculus(2/RD2), Particle Rain(2), What Are They Up To (Watut)(2), Embeddium (Rubidium) Extra(1) |
| 6 | `net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer` | Create(2), Quark(1), Alex's Caves(1), Immersive Armors(1), Oculus(1), playerAnimator(1) |
| 6 | `net.minecraft.server.commands.LocateCommand` | YUNG's Better Mineshafts(1), YUNG's Better Nether Fortresses(1), Integrated Dungeons and Structures(1), Integrated API(1), Integrated Stronghold(1), Integrated Villages(1) |
| 6 | `net.minecraft.world.entity.Mob` | Open Parties and Claims(2), Alex's Caves(1), Artifacts(1), backpacked(1), Immersive Armors(1), Moonlight Lib(1/RD1) |
| 6 | `net.minecraft.world.item.BlockItem` | Kiwi(2), Quark(2), backpacked(2), Snow! Real Magic!(1), Create(1), Moonlight Lib(1) |
| 5 | `com.mojang.blaze3d.platform.NativeImage` | Embeddium(1), Particle Rain(1), Particular  Reforged(1), 3D Skin Layers(1), What Are They Up To (Watut)(1) |
| 5 | `com.mojang.blaze3d.platform.Window` | Embeddium (Rubidium) Extra(2), ImmediatelyFast(1), Dynamic FPS(1), Embeddium(1/RD2), Oculus(1/RD1) |
| 5 | `net.minecraft.client.KeyboardHandler` | Exposure(2), GlitchCore(1), Konkrete(1), Supplementaries(1), What Are They Up To (Watut)(1) |
| 5 | `net.minecraft.client.color.block.BlockColors` | Kiwi(1), Puzzles Lib(1), Zeta(1), Embeddium(1), ModernFix(1) |
| 5 | `net.minecraft.client.color.item.ItemColors` | Kiwi(1), Puzzles Lib(1), Zeta(1), Embeddium(1), ModernFix(1) |
| 5 | `net.minecraft.client.multiplayer.MultiPlayerGameMode` | Immersive Hotbar(2), Kiwi(1), Quark(1), Exposure(1), Immersive Aircraft(1) |
| 5 | `net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher` | Entity Texture Features(1), Entity Culling(1), Oculus(1), Embeddium (Rubidium) Extra(1), Supplementaries(1) |
| 5 | `net.minecraft.client.renderer.entity.ItemFrameRenderer` | Alex's Caves(1), Entity Model Features(1), Exposure(1), moreculling(1), Embeddium (Rubidium) Extra(1) |
| 5 | `net.minecraft.world.entity.item.FallingBlockEntity` | Quark(1), Alex's Caves(1), Architectury API(1), Create(1), Supplementaries(1) |
| 5 | `net.minecraft.world.entity.projectile.AbstractArrow` | Quark(1), Alex's Caves(1), backpacked(1), Open Parties and Claims(1), Supplementaries(1) |
| 5 | `net.minecraft.world.item.ItemStack` | GlitchCore(1), Necronomicon API(1), Quark(1), Geckolib(1), Not Enough Animations(1) |
| 5 | `net.minecraft.world.level.Level` | Data Anchor(2), backpacked(1), Citadel(1), ModernFix(1), Open Parties and Claims(1) |
| 5 | `net.minecraft.world.level.chunk.ChunkAccess` | ModernFix(3/OW1), CorgiLib(1), Data Anchor(1), Quark(1), Tectonic(1) |
| 5 | `net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement` | Quark(1), YUNG's API(1), Integrated API(1), Lithostitched(1), Waystones(1) |
| 4 | `com.mojang.blaze3d.vertex.VertexBuffer` | ImmediatelyFast(1/RD2), Embeddium(1), Grassier Grass(1), Oculus(1) |
| 4 | `net.minecraft.client.KeyMapping` | Immersive Aircraft(2), Controlling(1), Balm(1), Exposure(1) |
| 4 | `net.minecraft.client.gui.GuiGraphics` | ImmediatelyFast(2), GlitchCore(1), Embeddium(1), Embeddium (Rubidium) Extra(1) |
| 4 | `net.minecraft.client.gui.screens.inventory.AbstractContainerScreen` | ImmediatelyFast(2), Mouse Tweaks(1), Quark(1), Balm(1) |
| 4 | `net.minecraft.client.model.geom.ModelPart$Cube` | Entity Model Features(2), Embeddium(1/RD1), moreculling(1), Oculus(1/RD1) |
| 4 | `net.minecraft.client.particle.Particle` | Particle Core(4), Particle Effects(1), Particle Rain(1), Particular  Reforged(1) |
| 4 | `net.minecraft.client.particle.SingleQuadParticle` | Particle Core(2), Embeddium(1), Particle Rain(1), Particular  Reforged(1) |
| 4 | `net.minecraft.client.player.AbstractClientPlayer` | Citadel(1), Immersive Aircraft(1), Just Zoom(1), Xaero's Minimap(1) |
| 4 | `net.minecraft.client.renderer.FogRenderer` | Embeddium (Rubidium) Extra(2), Distant Horizons(1), Embeddium(1/RD1), Oculus(1) |
| 4 | `net.minecraft.client.renderer.ItemBlockRenderTypes` | Embeddium(2/RD1), Quark(1), Citadel(1), Oculus(1) |
| 4 | `net.minecraft.client.renderer.LightTexture` | Oculus(2), Distant Horizons(1), Alex's Caves(1), Exposure(1) |
| 4 | `net.minecraft.client.renderer.blockentity.BeaconRenderer` | Embeddium (Rubidium) Extra(2/RD1), Quark(1), moreculling(1), Oculus(1) |
| 4 | `net.minecraft.client.renderer.entity.EntityRenderer` | Embeddium(1), Entity Texture Features(1), Entity Culling(1), Oculus(1) |
| 4 | `net.minecraft.client.renderer.entity.layers.ItemInHandLayer` | Artifacts(1), Entity Model Features(1), Not Enough Animations(1), playerAnimator(1) |
| 4 | `net.minecraft.client.renderer.texture.TextureAtlas` | Oculus(2), Embeddium(1), Embeddium (Rubidium) Extra(1), What Are They Up To (Watut)(1) |
| 4 | `net.minecraft.client.sounds.SoundEngine` | Alex's Caves(1), Citadel(1), Dynamic FPS(1), Sound Physics Remastered(1) |
| 4 | `net.minecraft.resources.ResourceLocation` | Entity Texture Features(1), FerriteCore(1), ftb(1), Oculus(1) |
| 4 | `net.minecraft.server.level.ChunkMap` | ModernFix(3/RD1), Data Anchor(1), Distant Horizons(1), Lithostitched(1/RD1) |
| 4 | `net.minecraft.world.entity.LightningBolt` | ImmersiveThunder-Forged(1/RD1), Quark(1), Architectury API(1), Moonlight Lib(1) |
| 4 | `net.minecraft.world.entity.npc.Villager` | Kiwi(1), Quark(1), Artifacts(1), Moonlight Lib(1) |
| 4 | `net.minecraft.world.entity.player.Inventory` | backpacked(1), Carry On(1), Curios API(1), Moonlight Lib(1) |
| 4 | `net.minecraft.world.item.alchemy.PotionBrewing` | Puzzles Lib(1), YUNG's API(1), Zeta(1), Amendments(1) |
| 4 | `net.minecraft.world.item.crafting.RecipeManager` | Kiwi(3), Farmer's Delight(1), ModernFix(1/RD1), Polymorph(1) |
| 4 | `net.minecraft.world.level.block.DispenserBlock` | Farmer's Delight(1), Create(1), Moonlight Lib(1), Open Parties and Claims(1) |
| 4 | `net.minecraft.world.level.block.LeavesBlock` | Cull Leaves(1), Embeddium(1), moreculling(1), Particular  Reforged(1) |
| 4 | `net.minecraft.world.level.block.entity.BlockEntity` | Data Anchor(1), Entity Model Features(1), Entity Texture Features(1), Entity Culling(1) |
| 4 | `net.minecraft.world.level.chunk.storage.ChunkSerializer` | CorgiLib(1), Data Anchor(1), Architectury API(1), Tectonic(1) |
| 4 | `net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool` | YUNG's API(2), Integrated API(2), Lithostitched(2), Waystones(1) |
| 4 | `net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate` | Integrated API(2), Zeta(1), Lithostitched(1), Lootr(1) |
| 4 | `net.minecraftforge.client.gui.overlay.ForgeGui` | ImmediatelyFast(1), Quark(1), Embeddium(1), Oculus(1) |
| 3 | `me.jellysquid.mods.sodium.client.gui.SodiumGameOptionPages` | Embeddium (Rubidium) Extra(3), Cull Leaves(1), Oculus(1/RD2) |
| 3 | `me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI` | Embeddium (Rubidium) Extra(2), moreculling(1), Oculus(1) |
| 3 | `net.minecraft.Util` | ModernFix(2), Distant Horizons(1), YUNG's Better Mineshafts(1) |
| 3 | `net.minecraft.client.Options` | Oculus(3/RD1), Alex's Caves(1), Dynamic FPS(1) |
| 3 | `net.minecraft.client.gui.Font$StringRenderOutput` | ImmediatelyFast(1/RD2), Jade(1), Chat Heads(1) |
| 3 | `net.minecraft.client.gui.MapRenderer$MapInstance` | Supplementaries(2), ImmediatelyFast(1/RD3), Alex's Caves(1) |
| 3 | `net.minecraft.client.gui.components.AbstractSelectionList` | YetAnotherConfigLib (YACL)(2/RD1), configured(1), Xaero's Minimap(1) |
| 3 | `net.minecraft.client.gui.components.ChatComponent` | Chat Heads(2), ImmediatelyFast(1), Quark(1) |
| 3 | `net.minecraft.client.gui.screens.OptionsScreen` | Distant Horizons(1), Embeddium(1), MidnightLib(1) |
| 3 | `net.minecraft.client.model.AgeableListModel` | Create(1), Supplementaries(1), Xaero's Minimap(1) |
| 3 | `net.minecraft.client.multiplayer.ClientLevel$ClientLevelData` | Alex's Caves(1), Oculus(1), Particle Rain(1) |
| 3 | `net.minecraft.client.multiplayer.chat.ChatListener` | Chat Heads(2), Xaero's Minimap(1), Xaero's World Map(1) |
| 3 | `net.minecraft.client.renderer.MultiBufferSource$BufferSource` | Oculus(3/RD1), Embeddium(1), Entity Texture Features(1) |
| 3 | `net.minecraft.client.renderer.RenderBuffers` | Quark(1), ModernFix(1), Oculus(1) |
| 3 | `net.minecraft.client.renderer.block.ModelBlockRenderer` | Embeddium(2), Cull Leaves(1), moreculling(1) |
| 3 | `net.minecraft.client.renderer.block.model.BakedQuad` | Embeddium(1), FerriteCore(1), moreculling(1) |
| 3 | `net.minecraft.client.renderer.blockentity.SignRenderer` | Amendments(2/OW1), ImmediatelyFast(1/RD1), moreculling(1) |
| 3 | `net.minecraft.client.renderer.blockentity.SkullBlockRenderer` | Amendments(1), Entity Texture Features(1), 3D Skin Layers(1) |
| 3 | `net.minecraft.client.renderer.entity.EnderDragonRenderer` | Entity Model Features(1), Entity Texture Features(1), ModernFix(1) |
| 3 | `net.minecraft.client.renderer.entity.layers.ElytraLayer` | Quark(1), Entity Texture Features(1), Oculus(1) |
| 3 | `net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer` | Entity Model Features(2), Entity Texture Features(1), Supplementaries(1) |
| 3 | `net.minecraft.client.renderer.texture.HttpTexture` | Chat Heads(1), Entity Texture Features(1), 3D Skin Layers(1) |
| 3 | `net.minecraft.client.renderer.texture.SpriteContents` | Oculus(5/RD2), Embeddium(4/RD1), moreculling(1) |
| 3 | `net.minecraft.client.renderer.texture.TextureAtlasSprite` | Embeddium(1), Model Gap Fix(1), moreculling(1) |
| 3 | `net.minecraft.client.renderer.texture.TextureManager` | ImmediatelyFast(1), Geckolib(1), Oculus(1) |
| 3 | `net.minecraft.client.resources.language.ClientLanguage` | ModernFix(1), Moonlight Lib(1), Oculus(1) |
| 3 | `net.minecraft.client.resources.model.ModelBakery` | ModernFix(2/RD8), Balm(1), Embeddium(1) |
| 3 | `net.minecraft.client.resources.model.ModelManager` | Quark(1), ModernFix(1/RD2), moreculling(1) |
| 3 | `net.minecraft.client.resources.model.MultiPartBakedModel` | Embeddium(1/OW2), FerriteCore(1/RD2), moreculling(1) |
| 3 | `net.minecraft.client.resources.model.SimpleBakedModel` | Embeddium(1), FerriteCore(1), moreculling(1) |
| 3 | `net.minecraft.client.server.IntegratedServer` | ModernFix(2), Lithostitched(1), Open Parties and Claims(1) |
| 3 | `net.minecraft.server.level.ServerEntity` | Data Anchor(1), Quark(1), Exposure(1) |
| 3 | `net.minecraft.world.entity.Display` | Kiwi(1), Entity Culling(1), Particle Rain(1) |
| 3 | `net.minecraft.world.entity.ExperienceOrb` | Clumps(2), Quark(1), Open Parties and Claims(1) |
| 3 | `net.minecraft.world.entity.vehicle.Boat` | Quark(1), Exposure(1), Open Parties and Claims(1) |
| 3 | `net.minecraft.world.inventory.AbstractContainerMenu` | Kiwi(1), Geckolib(1), What Are They Up To (Watut)(1) |
| 3 | `net.minecraft.world.inventory.AnvilMenu` | Easy Anvils(1), Quark(1), Exposure(1/RD1) |
| 3 | `net.minecraft.world.inventory.SmithingMenu` | Citadel(1/RD1), Create(1), Polymorph(1) |
| 3 | `net.minecraft.world.item.MapItem` | Integrated API(1), Moonlight Lib(1), Supplementaries(1) |
| 3 | `net.minecraft.world.item.ShearsItem` | Quark(1), Curios API(1), Moonlight Lib(1) |
| 3 | `net.minecraft.world.item.alchemy.PotionUtils` | Quark(1), Alex's Caves(1), Exposure(1/RD1) |
| 3 | `net.minecraft.world.item.enchantment.EnchantmentHelper` | Artifacts(2), Quark(1), Immersive Armors(1) |
| 3 | `net.minecraft.world.level.block.CampfireBlock` | Farmer's Delight(1), moreculling(1), Supplementaries(1) |
| 3 | `net.minecraft.world.level.block.CropBlock` | backpacked(1), Balm(1), Create(1) |
| 3 | `net.minecraft.world.level.block.LanternBlock` | Quark(1), Amendments(1), Supplementaries(1) |
| 3 | `net.minecraft.world.level.block.entity.SkullBlockEntity` | Amendments(1), Entity Texture Features(1), 3D Skin Layers(1) |
| 3 | `net.minecraft.world.level.block.piston.PistonBaseBlock` | Quark(1), Zeta(1), Open Parties and Claims(1) |
| 3 | `net.minecraft.world.level.block.state.BlockBehaviour` | Snow! Real Magic!(3), Kiwi(1), Quark(1) |
| 3 | `net.minecraft.world.level.block.state.StateHolder` | Create(1), FerriteCore(1/OW1/RD1), ModernFix(1) |
| 3 | `net.minecraft.world.level.chunk.PalettedContainer` | ModernFix(2), Embeddium(1), FerriteCore(1/OW2) |
| 3 | `net.minecraft.world.level.levelgen.Beardifier` | YUNG's API(2), Integrated API(2), Lithostitched(1) |
| 3 | `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator` | Noisiumed(2/RD2), ModernFix(1), Tectonic(1) |
| 3 | `net.minecraft.world.level.levelgen.structure.StructurePiece` | Zeta(1), Integrated API(1), Tectonic(1) |
| 3 | `net.minecraft.world.level.material.FlowingFluid` | Create(2), Alex's Caves(1), Open Parties and Claims(1) |
| 3 | `net.minecraft.world.level.saveddata.maps.MapItemSavedData` | Create(1), Moonlight Lib(1), moreculling(1) |
| 3 | `net.minecraft.world.level.storage.loot.LootDataManager` | Puzzles Lib(1), Architectury API(1), ModernFix(1) |
| 3 | `net.minecraft.world.level.storage.loot.LootTable` | Puzzles Lib(1), Zeta(1), Balm(1) |
