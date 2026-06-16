/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.level.Ticket
 *  org.spongepowered.asm.mixin.Mixin
 */
package com.thelads.core.mixin.alwayson.vmp.access;

import net.minecraft.server.level.Ticket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={Ticket.class})
public interface IChunkTicket {
}

