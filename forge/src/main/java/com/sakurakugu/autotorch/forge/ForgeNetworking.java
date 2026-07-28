package com.sakurakugu.autotorch.forge;

import io.netty.buffer.ByteBuf;
import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.network.*;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Forge 1.8.9 的 SimpleNetworkWrapper 适配。 */
final class ForgeNetworking {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(AutoTorch.MOD_ID + ":main");
    private ForgeNetworking() {}
    static void initialize() {
        CHANNEL.registerMessage(StartHandler.class, StartMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(CancelHandler.class, CancelMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(SelectionHandler.class, SelectionMessage.class, 2, Side.SERVER);
        CHANNEL.registerMessage(ConfigHandler.class, ConfigMessage.class, 3, Side.CLIENT);
    }
    static void sendToServer(AutoTorchPayload payload) {
        if (payload instanceof StartLightingPayload) CHANNEL.sendToServer(new StartMessage((StartLightingPayload) payload));
        else if (payload instanceof CancelLightingPayload) CHANNEL.sendToServer(new CancelMessage((CancelLightingPayload) payload));
        else if (payload instanceof SetSelectionToolPayload) CHANNEL.sendToServer(new SelectionMessage((SetSelectionToolPayload) payload));
    }
    static void sendToPlayer(EntityPlayerMP player, AutoTorchPayload payload) {
        if (payload instanceof ServerConfigPayload) CHANNEL.sendTo(new ConfigMessage((ServerConfigPayload) payload), player);
    }

    public abstract static class Message<T> implements IMessage {
        T payload;
        Message() {}
        Message(T payload) { this.payload = payload; }
        @Override public void fromBytes(ByteBuf buf) { payload = decode(new PacketBuffer(buf)); }
        @Override public void toBytes(ByteBuf buf) { encode(payload, new PacketBuffer(buf)); }
        abstract T decode(PacketBuffer buf);
        abstract void encode(T payload, PacketBuffer buf);
    }
    public static final class StartMessage extends Message<StartLightingPayload> { public StartMessage() {} StartMessage(StartLightingPayload p){super(p);} StartLightingPayload decode(PacketBuffer b){return StartLightingPayload.decode(b);} void encode(StartLightingPayload p,PacketBuffer b){p.write(b);} }
    public static final class CancelMessage extends Message<CancelLightingPayload> { public CancelMessage() {} CancelMessage(CancelLightingPayload p){super(p);} CancelLightingPayload decode(PacketBuffer b){return CancelLightingPayload.decode(b);} void encode(CancelLightingPayload p,PacketBuffer b){p.write(b);} }
    public static final class SelectionMessage extends Message<SetSelectionToolPayload> { public SelectionMessage() {} SelectionMessage(SetSelectionToolPayload p){super(p);} SetSelectionToolPayload decode(PacketBuffer b){return SetSelectionToolPayload.decode(b);} void encode(SetSelectionToolPayload p,PacketBuffer b){p.write(b);} }
    public static final class ConfigMessage extends Message<ServerConfigPayload> { public ConfigMessage() {} ConfigMessage(ServerConfigPayload p){super(p);} ServerConfigPayload decode(PacketBuffer b){return ServerConfigPayload.decode(b);} void encode(ServerConfigPayload p,PacketBuffer b){p.write(b);} }
    public static final class StartHandler implements IMessageHandler<StartMessage, IMessage> { public IMessage onMessage(StartMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.mcServer.addScheduledTask(() -> LightingTaskManager.start(p,m.payload)); return null; } }
    public static final class CancelHandler implements IMessageHandler<CancelMessage, IMessage> { public IMessage onMessage(CancelMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.mcServer.addScheduledTask(() -> LightingTaskManager.cancel(p)); return null; } }
    public static final class SelectionHandler implements IMessageHandler<SelectionMessage, IMessage> { public IMessage onMessage(SelectionMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.mcServer.addScheduledTask(() -> SelectionToolEvents.setEnabled(p,m.payload.enabled())); return null; } }
    public static final class ConfigHandler implements IMessageHandler<ConfigMessage, IMessage> { public IMessage onMessage(ConfigMessage m, MessageContext c){ net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> ServerConfigState.update(m.payload)); return null; } }
}
