package com.sakurakugu.autotorch.forge;

import io.netty.buffer.ByteBuf;
import com.sakurakugu.autotorch.AutoTorch;
import com.sakurakugu.autotorch.client.AutoTorchClient;
import com.sakurakugu.autotorch.client.AutoTorchClientCommands;
import com.sakurakugu.autotorch.client.LightOverlayState;
import com.sakurakugu.autotorch.client.ServerConfigScreen;
import com.sakurakugu.autotorch.client.ServerConfigState;
import com.sakurakugu.autotorch.network.*;
import com.sakurakugu.autotorch.server.LightingTaskManager;
import com.sakurakugu.autotorch.server.SelectionToolEvents;
import com.sakurakugu.autotorch.server.ServerConfigNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Forge 1.11.2 的 SimpleNetworkWrapper 适配。
 * 1.11.2 没有 1.13 的 SimpleChannel，载荷需要包装成 IMessage 并显式注册收发方向。
 */
final class ForgeNetworking {
    private static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(AutoTorch.MOD_ID + ":main");

    private ForgeNetworking() {
    }

    static void initialize() {
        // 0~5 号消息与旧版保持一致，新增消息追加在后面。
        CHANNEL.registerMessage(StartHandler.class, StartMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(CancelHandler.class, CancelMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(SelectionHandler.class, SelectionMessage.class, 2, Side.SERVER);
        CHANNEL.registerMessage(ConfigHandler.class, ConfigMessage.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(StatusRequestHandler.class, StatusRequestMessage.class, 4, Side.SERVER);
        CHANNEL.registerMessage(StatusHandler.class, StatusMessage.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(OpenScreenHandler.class, OpenScreenMessage.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(SetConfigHandler.class, SetConfigMessage.class, 7, Side.SERVER);
        CHANNEL.registerMessage(ResetConfigHandler.class, ResetConfigMessage.class, 8, Side.SERVER);
        CHANNEL.registerMessage(ConfigResultHandler.class, ConfigResultMessage.class, 9, Side.CLIENT);
        // 10 号消息同步世界种子，供客户端复现史莱姆区块判定。
        CHANNEL.registerMessage(WorldSeedHandler.class, WorldSeedMessage.class, 10, Side.CLIENT);
        // 服务端广播服务端配置时复用本类的发送出口。
        ServerConfigNetwork.installSender(ForgeNetworking::sendToPlayer);
    }

    static void sendToServer(AutoTorchPayload payload) {
        if (payload instanceof StartLightingPayload) CHANNEL.sendToServer(new StartMessage((StartLightingPayload) payload));
        else if (payload instanceof CancelLightingPayload) CHANNEL.sendToServer(new CancelMessage((CancelLightingPayload) payload));
        else if (payload instanceof SetSelectionToolPayload) CHANNEL.sendToServer(new SelectionMessage((SetSelectionToolPayload) payload));
        else if (payload instanceof TaskStatusRequestPayload) CHANNEL.sendToServer(new StatusRequestMessage((TaskStatusRequestPayload) payload));
        else if (payload instanceof ServerConfigSetPayload) CHANNEL.sendToServer(new SetConfigMessage((ServerConfigSetPayload) payload));
        else if (payload instanceof ServerConfigResetPayload) CHANNEL.sendToServer(new ResetConfigMessage((ServerConfigResetPayload) payload));
    }

    static void sendToPlayer(EntityPlayerMP player, AutoTorchPayload payload) {
        if (payload instanceof ServerConfigPayload) CHANNEL.sendTo(new ConfigMessage((ServerConfigPayload) payload), player);
        else if (payload instanceof TaskStatusPayload) CHANNEL.sendTo(new StatusMessage((TaskStatusPayload) payload), player);
        else if (payload instanceof OpenServerConfigScreenPayload) CHANNEL.sendTo(new OpenScreenMessage((OpenServerConfigScreenPayload) payload), player);
        else if (payload instanceof ServerConfigResultPayload) CHANNEL.sendTo(new ConfigResultMessage((ServerConfigResultPayload) payload), player);
        else if (payload instanceof WorldSeedPayload) CHANNEL.sendTo(new WorldSeedMessage((WorldSeedPayload) payload), player);
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
    public static final class StatusRequestMessage extends Message<TaskStatusRequestPayload> { public StatusRequestMessage() {} StatusRequestMessage(TaskStatusRequestPayload p){super(p);} TaskStatusRequestPayload decode(PacketBuffer b){return TaskStatusRequestPayload.decode(b);} void encode(TaskStatusRequestPayload p,PacketBuffer b){p.write(b);} }
    public static final class StatusMessage extends Message<TaskStatusPayload> { public StatusMessage() {} StatusMessage(TaskStatusPayload p){super(p);} TaskStatusPayload decode(PacketBuffer b){return TaskStatusPayload.decode(b);} void encode(TaskStatusPayload p,PacketBuffer b){p.write(b);} }
    public static final class OpenScreenMessage extends Message<OpenServerConfigScreenPayload> { public OpenScreenMessage() {} OpenScreenMessage(OpenServerConfigScreenPayload p){super(p);} OpenServerConfigScreenPayload decode(PacketBuffer b){return OpenServerConfigScreenPayload.decode(b);} void encode(OpenServerConfigScreenPayload p,PacketBuffer b){p.write(b);} }
    public static final class SetConfigMessage extends Message<ServerConfigSetPayload> { public SetConfigMessage() {} SetConfigMessage(ServerConfigSetPayload p){super(p);} ServerConfigSetPayload decode(PacketBuffer b){return ServerConfigSetPayload.decode(b);} void encode(ServerConfigSetPayload p,PacketBuffer b){p.write(b);} }
    public static final class ResetConfigMessage extends Message<ServerConfigResetPayload> { public ResetConfigMessage() {} ResetConfigMessage(ServerConfigResetPayload p){super(p);} ServerConfigResetPayload decode(PacketBuffer b){return ServerConfigResetPayload.decode(b);} void encode(ServerConfigResetPayload p,PacketBuffer b){p.write(b);} }
    public static final class ConfigResultMessage extends Message<ServerConfigResultPayload> { public ConfigResultMessage() {} ConfigResultMessage(ServerConfigResultPayload p){super(p);} ServerConfigResultPayload decode(PacketBuffer b){return ServerConfigResultPayload.decode(b);} void encode(ServerConfigResultPayload p,PacketBuffer b){p.write(b);} }
    public static final class WorldSeedMessage extends Message<WorldSeedPayload> { public WorldSeedMessage() {} WorldSeedMessage(WorldSeedPayload p){super(p);} WorldSeedPayload decode(PacketBuffer b){return WorldSeedPayload.decode(b);} void encode(WorldSeedPayload p,PacketBuffer b){p.write(b);} }

    /** 服务端消息在服务器线程执行，避免直接触碰世界状态。 */
    public static final class StartHandler implements IMessageHandler<StartMessage, IMessage> { public IMessage onMessage(StartMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.getServer().addScheduledTask(() -> LightingTaskManager.start(p,m.payload)); return null; } }
    public static final class CancelHandler implements IMessageHandler<CancelMessage, IMessage> { public IMessage onMessage(CancelMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.getServer().addScheduledTask(() -> LightingTaskManager.cancel(p)); return null; } }
    public static final class SelectionHandler implements IMessageHandler<SelectionMessage, IMessage> { public IMessage onMessage(SelectionMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.getServer().addScheduledTask(() -> SelectionToolEvents.setEnabled(p,m.payload.enabled(),m.payload.itemId())); return null; } }
    public static final class StatusRequestHandler implements IMessageHandler<StatusRequestMessage, IMessage> { public IMessage onMessage(StatusRequestMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.getServer().addScheduledTask(() -> sendToPlayer(p, LightingTaskManager.status(p))); return null; } }
    public static final class SetConfigHandler implements IMessageHandler<SetConfigMessage, IMessage> { public IMessage onMessage(SetConfigMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.getServer().addScheduledTask(() -> ServerConfigNetwork.handleSet(p, m.payload)); return null; } }
    public static final class ResetConfigHandler implements IMessageHandler<ResetConfigMessage, IMessage> { public IMessage onMessage(ResetConfigMessage m, MessageContext c){ EntityPlayerMP p=c.getServerHandler().playerEntity; p.getServer().addScheduledTask(() -> ServerConfigNetwork.handleReset(p, m.payload)); return null; } }

    /** 客户端消息在客户端线程执行；打开界面要等 tick 阶段，避免在收包线程直接切屏。 */
    public static final class ConfigHandler implements IMessageHandler<ConfigMessage, IMessage> { public IMessage onMessage(ConfigMessage m, MessageContext c){ Minecraft.getMinecraft().addScheduledTask(() -> ServerConfigState.update(m.payload)); return null; } }
    public static final class StatusHandler implements IMessageHandler<StatusMessage, IMessage> { public IMessage onMessage(StatusMessage m, MessageContext c){ Minecraft.getMinecraft().addScheduledTask(() -> AutoTorchClientCommands.receiveTaskStatus(m.payload)); return null; } }
    public static final class OpenScreenHandler implements IMessageHandler<OpenScreenMessage, IMessage> { public IMessage onMessage(OpenScreenMessage m, MessageContext c){ Minecraft.getMinecraft().addScheduledTask(AutoTorchClient::requestOpenServerConfigScreen); return null; } }
    public static final class ConfigResultHandler implements IMessageHandler<ConfigResultMessage, IMessage> { public IMessage onMessage(ConfigResultMessage m, MessageContext c){ Minecraft.getMinecraft().addScheduledTask(() -> ServerConfigScreen.receiveResult(m.payload)); return null; } }
    public static final class WorldSeedHandler implements IMessageHandler<WorldSeedMessage, IMessage> { public IMessage onMessage(WorldSeedMessage m, MessageContext c){ Minecraft.getMinecraft().addScheduledTask(() -> LightOverlayState.setWorldSeed(m.payload.seed())); return null; } }
}
