package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.SliderOption;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Opt-in QA: temporary client-only block and synthetic server packets; no server/world-save edits or network sends. */
final class NativeShulkerProbe {
    private NativeShulkerProbe() {}

    static int run() {
        Minecraft minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var module = NativeQualityOfLife.module("ShulkerBoxUtils");
        var size = (SliderOption) module.getOption("Icon Size");
        var height = (SliderOption) module.getOption("Height");
        var distance = (SliderOption) module.getOption("Distance");
        var animate = (BoolOption) module.getOption("Animate");
        var remember = (BoolOption) module.getOption("Remember Observed Contents");
        boolean enabledBefore = module.isEnabled(), animateBefore = animate.get(), rememberBefore = remember.get();
        long modifiedBefore = module.getLastModified();
        double sizeBefore = size.getValue(), heightBefore = height.getValue(), distanceBefore = distance.getValue();
        var menuBefore = minecraft.player.containerMenu;
        var worldIcon = (BoolOption) module.getOption("World Icon");
        var persist = (BoolOption) module.getOption("Persist Observed Contents");
        var mode = (com.thelads.core.config.DropdownOption) module.getOption("Display Mode");
        boolean worldIconBefore = worldIcon.get(), persistBefore = persist.get();
        int modeBefore = mode.getIndex();
        BlockPos position = null;
        for (int y = 1; y <= 3 && position == null; y++) {
            for (int x = -2; x <= 2 && position == null; x++) {
                for (int z = -2; z <= 2 && position == null; z++) {
                    BlockPos candidate = minecraft.player.blockPosition().offset(x, y, z);
                    // Air or water (a QA world may spawn underwater): the state is restored after the fixture.
                    if (level.hasChunkAt(candidate) && level.getBlockState(candidate).canBeReplaced()
                        && level.getBlockEntity(candidate) == null) position = candidate;
                }
            }
        }
        require(position != null, "a nearby loaded replaceable block is available for the reversible client-only fixture");
        var stateBefore = level.getBlockState(position);
        int passed = 0;
        try {
            require(ShulkerContents.first((ItemContainerContents) null).isEmpty(), "unknown container has no invented preview"); passed++;
            var slots = new ArrayList<>(Collections.nCopies(27, ItemStack.EMPTY));
            slots.set(2, new ItemStack(Items.DIAMOND, 7));
            slots.set(7, new ItemStack(Items.GOLD_INGOT, 3));
            var components = ItemContainerContents.fromItems(slots);
            ItemStack first = ShulkerContents.first(components);
            require(first.is(Items.DIAMOND) && first.getCount() == 7, "actual container components use the first occupied slot"); passed++;
            first.setCount(1);
            require(ShulkerContents.first(components).getCount() == 7, "preview stack is an independent component copy"); passed++;
            var playerOnly = new ArrayList<>(Collections.nCopies(28, ItemStack.EMPTY));
            playerOnly.set(27, new ItemStack(Items.APPLE));
            require(ShulkerContents.first(playerOnly, 27).isEmpty(), "player inventory slots cannot become box contents"); passed++;

            module.setEnabled(true); animate.set(false); remember.set(true); distance.setValue(24);
            worldIcon.set(true); persist.set(false); mode.setIndex(0);
            level.setBlock(position, Blocks.SHULKER_BOX.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            require(level.getBlockEntity(position) instanceof ShulkerBoxBlockEntity, "vanilla client block creates a real shulker block entity"); passed++;
            var box = (ShulkerBoxBlockEntity) level.getBlockEntity(position);
            var renderer = (ShulkerBoxRenderer) (Object) minecraft.getBlockEntityRenderDispatcher().getRenderer(box);
            var state = renderer.createRenderState();
            require(state instanceof ShulkerIconState, "actual shulker render state implements icon snapshot"); passed++;
            var icon = (ShulkerIconState) state;
            Vec3 camera = Vec3.atCenterOf(position).add(0, 0, 3);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(icon.lads$shulkerIcon().isEmpty(), "unknown box extracts no item geometry"); passed++;

            ItemStack placed = new ItemStack(Items.SHULKER_BOX);
            placed.set(DataComponents.CONTAINER, components);
            box.applyComponentsFromItemStack(placed);
            require(ShulkerContents.resolve(box).is(Items.DIAMOND), "transformed placement component hook captures actual stack contents"); passed++;
            size.setValue(80); height.setValue(50);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(!icon.lads$shulkerIcon().isEmpty(), "real item model is prepared by transformed shulker extraction"); passed++;
            require(Math.abs(icon.lads$iconScale() - .8) < .00001 && Math.abs(icon.lads$iconHeight() - 1.5) < .00001,
                "size and height settings change the extracted transform"); passed++;
            int[] itemSubmissions = {0};
            SubmitNodeCollector collector = (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[] {SubmitNodeCollector.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("submitItem")) itemSubmissions[0]++;
                    return method.getName().equals("order") ? proxy : null;
                });
            PoseStack pose = new PoseStack();
            var poseBefore = new org.joml.Matrix4f(pose.last().pose());
            renderer.submit(state, pose, collector, new CameraRenderState());
            require(itemSubmissions[0] > 0 && pose.last().pose().equals(poseBefore), "real renderer submits icon geometry and restores pose"); passed++;
            mode.setIndex(1);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(icon.lads$shulkerIcon().isEmpty(), "uniform mode hides mixed placed boxes"); passed++;
            mode.setIndex(0);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(!icon.lads$shulkerIcon().isEmpty(), "first-item mode restores mixed-box icon"); passed++;
            worldIcon.set(false);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(icon.lads$shulkerIcon().isEmpty(), "world-icon control independently disables world geometry"); passed++;
            worldIcon.set(true);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(!icon.lads$shulkerIcon().isEmpty(), "world-icon control restores world geometry"); passed++;
            module.setEnabled(false);
            renderer.extractRenderState(box, state, 0, camera, null);
            require(icon.lads$shulkerIcon().isEmpty(), "disable clears previously prepared geometry"); passed++;
            module.setEnabled(true); distance.setValue(8);
            renderer.extractRenderState(box, state, 0, camera.add(20, 0, 0), null);
            require(icon.lads$shulkerIcon().isEmpty(), "configured distance culls icon extraction"); passed++;
            box.applyComponentsFromItemStack(new ItemStack(Items.SHULKER_BOX));
            require(ShulkerContents.resolve(box).isEmpty(), "empty placed container removes the previous preview"); passed++;

            ShulkerBoxMenu menu = new ShulkerBoxMenu(247, minecraft.player.getInventory());
            minecraft.player.containerMenu = menu;
            ShulkerContents.clicked(position);
            ShulkerContents.opened();
            require(ShulkerContents.resolve(box).isEmpty(), "newly opened menu stays blank until server content arrives"); passed++;
            minecraft.getConnection().handleContainerContent(new ClientboundContainerSetContentPacket(247, 1, slots, ItemStack.EMPTY));
            require(ShulkerContents.resolve(box).is(Items.DIAMOND), "transformed full-content packet captures server slots"); passed++;
            menu.getSlot(0).set(new ItemStack(Items.STONE));
            require(ShulkerContents.resolve(box).is(Items.DIAMOND), "unacknowledged client inventory predictions do not alter preview"); passed++;
            minecraft.getConnection().handleContainerSetSlot(new ClientboundContainerSetSlotPacket(247, 2, 2, ItemStack.EMPTY));
            require(ShulkerContents.resolve(box).is(Items.GOLD_INGOT), "acknowledged slot update finds next authoritative item, ignoring predictions"); passed++;
            minecraft.getConnection().handleContainerSetSlot(new ClientboundContainerSetSlotPacket(246, 3, 0, new ItemStack(Items.APPLE)));
            require(ShulkerContents.resolve(box).is(Items.GOLD_INGOT), "unrelated menu packet is ignored"); passed++;
            ShulkerContents.slot(247, 27, new ItemStack(Items.APPLE));
            require(ShulkerContents.resolve(box).is(Items.GOLD_INGOT), "player inventory updates are excluded"); passed++;
            minecraft.getConnection().handleContainerSetSlot(new ClientboundContainerSetSlotPacket(247, 4, 0, new ItemStack(Items.APPLE)));
            require(ShulkerContents.resolve(box).is(Items.APPLE), "acknowledged earlier slot immediately replaces preview"); passed++;
            remember.set(false);
            minecraft.player.containerMenu = menuBefore;
            ShulkerContents.tick();
            require(ShulkerContents.resolve(box).isEmpty(), "closing menu forgets observed contents when configured"); passed++;
            remember.set(true);
            box.applyComponentsFromItemStack(placed);
            ShulkerContents.otherOpening(position, ShulkerBoxBlockEntity.EVENT_SET_OPEN_COUNT, 1);
            require(ShulkerContents.resolve(box).isEmpty(), "another viewer invalidates closed last-known contents"); passed++;
            box.applyComponentsFromItemStack(placed);
            box.setRemoved();
            require(ShulkerContents.resolve(box).isEmpty(), "removed or unloaded block entity cannot show cached contents"); passed++;
            LoggerFactory.getLogger("TheLadsCore").info("Lads shulker probe END: {} passed, 0 failed (client-only fixture, synthetic inventory packets, real component/render hooks)", passed);
            return passed;
        } finally {
            minecraft.player.containerMenu = menuBefore;
            level.setBlock(position, stateBefore, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            ShulkerContents.tick();
            size.setValue(sizeBefore); height.setValue(heightBefore); distance.setValue(distanceBefore);
            animate.set(animateBefore); remember.set(rememberBefore);
            worldIcon.set(worldIconBefore); persist.set(persistBefore); mode.setIndex(modeBefore);
            module.setEnabled(enabledBefore); module.setLastModified(modifiedBefore);
        }
    }

    private static void require(boolean value, String name) { if (!value) throw new IllegalStateException(name); }
}
