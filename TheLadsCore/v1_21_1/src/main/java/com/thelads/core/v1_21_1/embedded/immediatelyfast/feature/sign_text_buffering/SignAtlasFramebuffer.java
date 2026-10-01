/*
 * This file is part of ImmediatelyFast - https://github.com/RaphiMC/ImmediatelyFast
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v1_21_1.embedded.immediatelyfast).
package com.thelads.core.v1_21_1.embedded.immediatelyfast.feature.sign_text_buffering;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11C;

public class SignAtlasFramebuffer extends RenderTarget implements AutoCloseable {

    public static final int ATLAS_SIZE = 4096;

    private final ResourceLocation textureId;
    private final Slot rootSlot;

    public SignAtlasFramebuffer() {
        super(false);
        this.resize(ATLAS_SIZE, ATLAS_SIZE, Minecraft.ON_OSX);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        this.textureId = ResourceLocation.fromNamespaceAndPath("immediatelyfast", "sign_atlas/" + this.colorTextureId);
        Minecraft.getInstance().getTextureManager().register(this.textureId, new FboTexture());

        this.rootSlot = new Slot(null, 0, 0, ATLAS_SIZE, ATLAS_SIZE);
    }

    @Override
    public void close() {
        this.destroyBuffers();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    public Slot findSlot(final int width, final int height) {
        return this.rootSlot.findSlot(width, height);
    }

    public void clear() {
        this.clear(Minecraft.ON_OSX);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);

        this.rootSlot.subSlot1 = null;
        this.rootSlot.subSlot2 = null;
    }

    public ResourceLocation getTextureId() {
        return this.textureId;
    }

    public class Slot {

        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final Slot parentSlot;
        public Slot subSlot1;
        public Slot subSlot2;
        public boolean occupied;

        public Slot(final Slot parentSlot, final int x, final int y, final int width, final int height) {
            this.parentSlot = parentSlot;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public void markFree() {
            if (this.subSlot1 != null || this.subSlot2 != null) {
                throw new UnsupportedOperationException("Cannot mark slot as free if it has sub slots");
            }
            if (!this.occupied) {
                throw new UnsupportedOperationException("Cannot mark slot as free if it is not occupied");
            }
            this.occupied = false;
            removeUnoccupiedSubSlots(this);

            GL11C.glScissor(this.x, ATLAS_SIZE - this.y - this.height, this.width, this.height);
            GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            SignAtlasFramebuffer.this.clear(Minecraft.ON_OSX);
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }

        public Slot findSlot(final int width, final int height) {
            if (this.subSlot1 != null && this.subSlot2 != null) {
                Slot slot = this.subSlot1.findSlot(width, height);
                if (slot == null) {
                    slot = this.subSlot2.findSlot(width, height);
                }

                return slot;
            } else if (this.occupied) {
                return null;
            } else {
                if (width > this.width || height > this.height) {
                    return null;
                } else if (width == this.width && height == this.height) {
                    this.occupied = true;
                    return this;
                } else {
                    final int k = this.width - width;
                    final int l = this.height - height;
                    if (k > l) {
                        this.subSlot1 = new Slot(this, this.x, this.y, width, this.height);
                        this.subSlot2 = new Slot(this, this.x + width, this.y, this.width - width, this.height);
                    } else {
                        this.subSlot1 = new Slot(this, this.x, this.y, this.width, height);
                        this.subSlot2 = new Slot(this, this.x, this.y + height, this.width, this.height - height);
                    }

                    return this.subSlot1.findSlot(width, height);
                }
            }
        }

        private static void removeUnoccupiedSubSlots(final Slot slot) {
            if (slot == null) {
                return;
            }
            removeUnoccupiedSubSlots(slot.parentSlot);
            final boolean subSlot1Unoccupied = slot.subSlot1 != null && !hasOccupiedSlot(slot.subSlot1);
            final boolean subSlot2Unoccupied = slot.subSlot2 != null && !hasOccupiedSlot(slot.subSlot2);

            if (subSlot1Unoccupied && subSlot2Unoccupied) {
                slot.subSlot1 = null;
                slot.subSlot2 = null;
            }
        }

        private static boolean hasOccupiedSlot(final Slot slot) {
            if (slot == null) {
                return false;
            }
            if (slot.occupied) {
                return true;
            }
            return hasOccupiedSlot(slot.subSlot1) || hasOccupiedSlot(slot.subSlot2);
        }

    }

    private class FboTexture extends AbstractTexture {

        @Override
        public void load(ResourceManager manager) {
        }

        @Override
        public void releaseId() {
        }

        @Override
        public int getId() {
            return SignAtlasFramebuffer.this.colorTextureId;
        }

    }

}
