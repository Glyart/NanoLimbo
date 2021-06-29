package ru.nanit.limbo.connection;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import ru.nanit.limbo.LimboConstants;
import ru.nanit.limbo.protocol.ByteMessage;
import ru.nanit.limbo.protocol.PreRenderedPacket;
import ru.nanit.limbo.protocol.packets.login.*;
import ru.nanit.limbo.protocol.packets.play.*;
import ru.nanit.limbo.connection.pipeline.PacketDecoder;
import ru.nanit.limbo.connection.pipeline.PacketEncoder;
import ru.nanit.limbo.protocol.packets.PacketHandshake;
import ru.nanit.limbo.protocol.packets.status.PacketStatusPing;
import ru.nanit.limbo.protocol.packets.status.PacketStatusRequest;
import ru.nanit.limbo.protocol.packets.status.PacketStatusResponse;
import ru.nanit.limbo.protocol.registry.State;
import ru.nanit.limbo.protocol.registry.Version;
import ru.nanit.limbo.server.LimboServer;
import ru.nanit.limbo.util.Logger;
import ru.nanit.limbo.util.UuidUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ClientConnection extends ChannelInboundHandlerAdapter {

    private static PreRenderedPacket PACKET_LOGIN_SUCCESS;
    private static PreRenderedPacket PACKET_JOIN_GAME;
    private static PreRenderedPacket PACKET_PLAYER_ABILITIES;
    private static PreRenderedPacket PACKET_PLAYER_INFO;
    private static PreRenderedPacket PACKET_PLAYER_POS;
    private static PreRenderedPacket PACKET_JOIN_MESSAGE;
    private static PreRenderedPacket PACKET_BOSS_BAR;
    private static PreRenderedPacket PACKET_SEND_RESOURCE_PACK;

    private final LimboServer server;
    private final Channel channel;
    private final GameProfile gameProfile;

    private State state;
    private Version clientVersion;
    private SocketAddress address;

    private int velocityLoginMessageId = -1;

    public ClientConnection(Channel channel, LimboServer server){
        this.server = server;
        this.channel = channel;
        this.address = channel.remoteAddress();
        this.gameProfile = new GameProfile();
    }

    public UUID getUuid() {
        return gameProfile.getUuid();
    }

    public String getUsername(){
        return gameProfile.getUsername();
    }

    public SocketAddress getAddress() {
        return address;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (state.equals(State.PLAY)){
            server.getConnections().removeConnection(this);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (channel.isActive()){
            Logger.error("Unhandled exception: ", cause);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        handlePacket(msg);
    }

    public void handlePacket(Object packet){
        if (packet instanceof PacketHandshake){
            PacketHandshake handshake = (PacketHandshake) packet;
            clientVersion = handshake.getVersion();
            updateState(State.getById(handshake.getNextState()));
            Logger.debug("Pinged from " + address);

            if (server.getConfig().getInfoForwarding().isLegacy()){
                String[] split = handshake.getHost().split("\00");

                if (split.length == 3 || split.length == 4){
                    setAddress(split[1]);
                    gameProfile.setUuid(UuidUtil.fromString(split[2]));
                } else {
                    disconnectLogin("You've enabled player info forwarding. You need to connect with proxy");
                }
            }
            return;
        }

        if (packet instanceof PacketStatusRequest){
            sendPacket(new PacketStatusResponse(server));
            return;
        }

        if (packet instanceof PacketStatusPing){
            sendPacketAndClose(packet);
            return;
        }

        if (packet instanceof PacketLoginStart){
            if (server.getConnections().getCount() >= server.getConfig().getMaxPlayers()){
                disconnectLogin("Too many players connected");
                return;
            }

            if (!clientVersion.equals(Version.getCurrentSupported())){
                disconnectLogin("Incompatible client version");
                return;
            }

            if (server.getConfig().getInfoForwarding().isModern()){
                velocityLoginMessageId = ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
                PacketLoginPluginRequest request = new PacketLoginPluginRequest();
                request.setMessageId(velocityLoginMessageId);
                request.setChannel(LimboConstants.VELOCITY_INFO_CHANNEL);
                request.setData(Unpooled.EMPTY_BUFFER);
                sendPacket(request);
                return;
            }

            if (!server.getConfig().getInfoForwarding().isModern()){
                gameProfile.setUsername(((PacketLoginStart)packet).getUsername());
                gameProfile.setUuid(UuidUtil.getOfflineModeUuid(getUsername()));
            }

            fireLoginSuccess();
            return;
        }

        if (packet instanceof PacketLoginPluginResponse){
            PacketLoginPluginResponse response = (PacketLoginPluginResponse) packet;

            if (server.getConfig().getInfoForwarding().isModern()
                    && response.getMessageId() == velocityLoginMessageId){

                if (!response.isSuccessful() || response.getData() == null){
                    disconnectLogin("You need to connect with Velocity");
                    return;
                }

                if (!checkVelocityKeyIntegrity(response.getData())) {
                    disconnectLogin("Can't verify forwarded player info");
                    return;
                }

                // Order is important
                setAddress(response.getData().readString());
                gameProfile.setUuid(response.getData().readUuid());
                gameProfile.setUsername(response.getData().readString());

                fireLoginSuccess();
            }
            return;
        }
        
        if (packet instanceof PacketResourcePackStatus) {
            int status = ((PacketResourcePackStatus) packet).getStatus();
            if (status != 0 && status != 3)
                disconnectPlay(server.getConfig().getDeclinedKickMessage());
        }
    }

    private void fireLoginSuccess(){
        if (server.getConfig().getInfoForwarding().isModern() && velocityLoginMessageId == -1){
            disconnectLogin("You need to connect with Velocity");
            return;
        }

        writePacket(PACKET_LOGIN_SUCCESS);
        updateState(State.PLAY);

        server.getConnections().addConnection(this);

        writePacket(PACKET_JOIN_GAME);
        writePacket(PACKET_PLAYER_ABILITIES);
        writePacket(PACKET_PLAYER_POS);
        writePacket(PACKET_PLAYER_INFO);

        if (PACKET_BOSS_BAR != null)
            writePacket(PACKET_BOSS_BAR);

        if (PACKET_JOIN_MESSAGE != null)
            writePacket(PACKET_JOIN_MESSAGE);
        
        if (PACKET_SEND_RESOURCE_PACK != null)
            writePacket(PACKET_SEND_RESOURCE_PACK);

        sendKeepAlive();
    }

    public void disconnectLogin(String reason){
        if (isConnected() && state == State.LOGIN){
            PacketDisconnect disconnect = new PacketDisconnect();
            disconnect.setReason(reason);
            sendPacketAndClose(disconnect);
        }
    }

    public void disconnectPlay(String reason){
        if (isConnected() && state == State.PLAY){
            PacketPlayDisconnect disconnect = new PacketPlayDisconnect();
            disconnect.setReason(reason);
            sendPacket(disconnect);
        }
    }

    public void sendKeepAlive(){
        if (state.equals(State.PLAY)){
            PacketKeepAlive keepAlive = new PacketKeepAlive();
            keepAlive.setId(ThreadLocalRandom.current().nextLong());
            sendPacket(keepAlive);
        }
    }

    public void sendPacket(Object packet){
        if (isConnected())
            channel.writeAndFlush(packet, channel.voidPromise());
    }

    public void sendPacketAndClose(Object packet){
        if (isConnected())
            channel.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
    }

    public void writePacket(Object packet){
        if (isConnected()) channel.write(packet, channel.voidPromise());
    }

    public void flushPackets(){
        if (isConnected()) channel.flush();
    }

    public boolean isConnected(){
        return channel.isActive();
    }

    private void updateState(State state){
        this.state = state;
        channel.pipeline().get(PacketDecoder.class).updateState(state);
        channel.pipeline().get(PacketEncoder.class).updateState(state);
    }

    private void setAddress(String host){
        this.address = new InetSocketAddress(host, ((InetSocketAddress)this.address).getPort());
    }

    private boolean checkVelocityKeyIntegrity(ByteMessage buf) {
        byte[] signature = new byte[32];
        buf.readBytes(signature);
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(server.getConfig().getInfoForwarding().getSecretKey(), "HmacSHA256"));
            byte[] mySignature = mac.doFinal(data);
            if (!MessageDigest.isEqual(signature, mySignature))
                return false;
        } catch (InvalidKeyException |java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        int version = buf.readVarInt();
        if (version != 1)
            throw new IllegalStateException("Unsupported forwarding version " + version + ", wanted " + '\001');
        return true;
    }

    public static void preInitPackets(LimboServer server){
        final String username = server.getConfig().getPingData().getVersion();
        final UUID uuid = UuidUtil.getOfflineModeUuid(username);

        PacketLoginSuccess loginSuccess = new PacketLoginSuccess();
        loginSuccess.setUsername(username);
        loginSuccess.setUuid(uuid);

        PacketJoinGame joinGame = new PacketJoinGame();
        joinGame.setEntityId(0);
        joinGame.setEnableRespawnScreen(true);
        joinGame.setFlat(false);
        joinGame.setGameMode(server.getConfig().getGameMode());
        joinGame.setHardcore(false);
        joinGame.setMaxPlayers(server.getConfig().getMaxPlayers());
        joinGame.setPreviousGameMode(-1);
        joinGame.setReducedDebugInfo(true);
        joinGame.setDebug(false);
        joinGame.setViewDistance(2);
        joinGame.setWorldName("minecraft:world");
        joinGame.setWorldNames("minecraft:world");
        joinGame.setHashedSeed(0);
        joinGame.setDimensionCodec(server.getDimensionRegistry().getCodec());
        joinGame.setDimension(server.getDimensionRegistry().getDefaultDimension());

        PacketPlayerAbilities playerAbilities = new PacketPlayerAbilities();
        playerAbilities.setFlyingSpeed(0.0F);
        playerAbilities.setFlags(0x02);
        playerAbilities.setFieldOfView(0.1F);

        PacketPlayerPositionAndLook positionAndLook = new PacketPlayerPositionAndLook();
        positionAndLook.setX(server.getConfig().getSpawnPosition().getX());
        positionAndLook.setY(server.getConfig().getSpawnPosition().getY());
        positionAndLook.setZ(server.getConfig().getSpawnPosition().getZ());
        positionAndLook.setYaw(server.getConfig().getSpawnPosition().getYaw());
        positionAndLook.setPitch(server.getConfig().getSpawnPosition().getPitch());
        positionAndLook.setTeleportId(0);

        PacketPlayerInfo info = new PacketPlayerInfo();
        info.setUsername(username);
        info.setGameMode(server.getConfig().getGameMode());
        info.setUuid(uuid);

        PACKET_LOGIN_SUCCESS = PreRenderedPacket.of(loginSuccess);
        PACKET_JOIN_GAME = PreRenderedPacket.of(joinGame);
        PACKET_PLAYER_ABILITIES = PreRenderedPacket.of(playerAbilities);
        PACKET_PLAYER_POS = PreRenderedPacket.of(positionAndLook);
        PACKET_PLAYER_INFO = PreRenderedPacket.of(info);

        if (server.getConfig().isUseJoinMessage()){
            PacketChatMessage joinMessage = new PacketChatMessage();
            joinMessage.setJsonData(server.getConfig().getJoinMessage());
            joinMessage.setPosition(PacketChatMessage.Position.CHAT);
            joinMessage.setSender(UUID.randomUUID());
            PACKET_JOIN_MESSAGE = PreRenderedPacket.of(joinMessage);
        }

        if (server.getConfig().isUseBossBar()){
            PacketBossBar bossBar = new PacketBossBar();
            bossBar.setBossBar(server.getConfig().getBossBar());
            bossBar.setUuid(UUID.randomUUID());
            PACKET_BOSS_BAR = PreRenderedPacket.of(bossBar);
        }
        
        if (server.getConfig().isEnabledResourcePack()) {
            PacketSendResourcePack resourcePack = new PacketSendResourcePack();
            resourcePack.setUrl(server.getConfig().getResourcePackURL());
            resourcePack.setHash(server.getConfig().getResourcePackHash());
            resourcePack.setForced(server.getConfig().isForcedResourcePack());
            resourcePack.setMessage(server.getConfig().getPromptMessage());
            PACKET_SEND_RESOURCE_PACK = PreRenderedPacket.of(resourcePack);
        }
    }
}
