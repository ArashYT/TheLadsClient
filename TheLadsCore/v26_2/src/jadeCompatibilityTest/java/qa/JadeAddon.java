package qa;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
/** Independent test mod: depends only on Jade's public API, not on Lads classes. */
public final class JadeAddon implements IWailaPlugin {
    public static int common,client,tooltips,serverData;
    private static final Provider PROVIDER=new Provider();
    public void register(IWailaCommonRegistration registration){registration.registerBlockDataProvider(new ServerProvider(),Block.class);common++;}
    public void registerClient(IWailaClientRegistration registration){registration.registerBlockComponent(PROVIDER,Block.class);client++;}
    public static final class Provider implements IComponentProvider<BlockAccessor> {
        public Identifier getUid(){return Identifier.parse("lads_jade_qa:provider");}
        public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config){tooltips++;tooltip.add(Component.literal("Jade addon compatibility verified"));}
    }
    public static final class ServerProvider implements IServerDataProvider<BlockAccessor> {
        public Identifier getUid(){return Identifier.parse("lads_jade_qa:provider");}
        public void appendServerData(CompoundTag data,BlockAccessor accessor){serverData++;data.putBoolean("lads_jade_qa",true);}
    }
}
