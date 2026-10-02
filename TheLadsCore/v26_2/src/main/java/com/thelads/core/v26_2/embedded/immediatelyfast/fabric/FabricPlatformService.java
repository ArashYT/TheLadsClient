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
// Modified by The Lads: repackaged into Lads Core (com.thelads.core.v26_2.embedded.immediatelyfast).
package com.thelads.core.v26_2.embedded.immediatelyfast.fabric;

import net.fabricmc.loader.api.FabricLoader;
import com.thelads.core.v26_2.embedded.immediatelyfast.service.PlatformService;

import java.nio.file.Path;
import java.util.Optional;

public class FabricPlatformService implements PlatformService {

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Optional<String> getModVersion(final String id) {
        return FabricLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString());
    }

}
