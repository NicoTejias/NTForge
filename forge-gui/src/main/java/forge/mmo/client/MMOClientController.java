package forge.mmo.client;

import forge.mmo.net.event.MMOEvent;
import forge.mmo.net.protocol.MMOProtocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class MMOClientController {

    private static MMOClientController instance;
    private Channel channel;
    private EventLoopGroup group;
    private UUID myPlayerId;
    private MMOProtocol.PlayerState myState;
    
    private final Map<UUID, MMOProtocol.PlayerState> remotePlayersInZone = new ConcurrentHashMap<>();
    
    // Callbacks for UI updates
    private Consumer<MMOProtocol.PlayerState> onPlayerMoved;
    private Consumer<MMOProtocol.PlayerState> onPlayerJoinedZone;
    private Consumer<UUID> onPlayerLeftZone;
    private Consumer<MMOProtocol.ChatMessage> onChatMessageReceived;
    private Consumer<MMOProtocol.Team> onTeamUpdated;
    private Consumer<MMOProtocol.TradeSession> onTradeUpdated;
    private Consumer<MMOProtocol.Leaderboard> onLeaderboardUpdated;

    private MMOClientController() {}

    public static synchronized MMOClientController getInstance() {
        if (instance == null) {
            instance = new MMOClientController();
        }
        return instance;
    }

    public void connect(String host, int port, String username) {
        group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                         new ObjectEncoder(),
                         new MMOClientHandler()
                     );
                 }
             });

            ChannelFuture f = b.connect(host, port).sync();
            channel = f.channel();

            // Request Login
            MMOEvent loginReq = new MMOEvent(MMOEvent.Type.LOGIN_REQ);
            loginReq.payload = username;
            channel.writeAndFlush(loginReq);

        } catch (Exception e) {
            System.err.println("Failed to connect to MMO Server: " + e.getMessage());
        }
    }

    public void sendMovement(float x, float y) {
        if (channel != null && myState != null) {
            myState.x = x;
            myState.y = y;

            MMOEvent moveEvent = new MMOEvent(MMOEvent.Type.MOVE);
            moveEvent.senderId = myPlayerId;
            moveEvent.playerState = myState;
            channel.writeAndFlush(moveEvent);
        }
    }

    public void requestZoneChange(MMOProtocol.ZoneType zone) {
        if (channel != null && myState != null) {
            MMOProtocol.PlayerState requestedState = new MMOProtocol.PlayerState(myPlayerId, myState.username, 100, 100, zone);
            MMOEvent zoneEvent = new MMOEvent(MMOEvent.Type.ZONE_CHANGE_REQ);
            zoneEvent.senderId = myPlayerId;
            zoneEvent.playerState = requestedState;
            channel.writeAndFlush(zoneEvent);
        }
    }

    public void sendChat(MMOProtocol.ChatMessage.ChatChannel channelType, String message, String receiver) {
        if (channel != null && myState != null) {
            MMOProtocol.ChatMessage chat = new MMOProtocol.ChatMessage(channelType, myState.username, receiver, message);
            MMOEvent chatEvent = new MMOEvent(MMOEvent.Type.CHAT);
            chatEvent.senderId = myPlayerId;
            chatEvent.chatMessage = chat;
            channel.writeAndFlush(chatEvent);
        }
    }

    public void disconnect() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    // Setters for UI Callbacks
    public void setOnPlayerMoved(Consumer<MMOProtocol.PlayerState> cb) { this.onPlayerMoved = cb; }
    public void setOnPlayerJoinedZone(Consumer<MMOProtocol.PlayerState> cb) { this.onPlayerJoinedZone = cb; }
    public void setOnPlayerLeftZone(Consumer<UUID> cb) { this.onPlayerLeftZone = cb; }
    public void setOnChatMessageReceived(Consumer<MMOProtocol.ChatMessage> cb) { this.onChatMessageReceived = cb; }
    public void setOnTeamUpdated(Consumer<MMOProtocol.Team> cb) { this.onTeamUpdated = cb; }
    public void setOnTradeUpdated(Consumer<MMOProtocol.TradeSession> cb) { this.onTradeUpdated = cb; }
    public void setOnLeaderboardUpdated(Consumer<MMOProtocol.Leaderboard> cb) { this.onLeaderboardUpdated = cb; }

    public Map<UUID, MMOProtocol.PlayerState> getRemotePlayersInZone() { return remotePlayersInZone; }
    public MMOProtocol.PlayerState getMyState() { return myState; }

    private class MMOClientHandler extends SimpleChannelInboundHandler<MMOEvent> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MMOEvent msg) {
            switch (msg.type) {
                case LOGIN_RES:
                    myPlayerId = msg.senderId;
                    myState = msg.playerState;
                    break;
                case MOVE:
                    if (!msg.senderId.equals(myPlayerId)) {
                        remotePlayersInZone.put(msg.senderId, msg.playerState);
                        if (onPlayerMoved != null) onPlayerMoved.accept(msg.playerState);
                    }
                    break;
                case ZONE_CHANGE_RES:
                    if (msg.playerState == null) {
                        remotePlayersInZone.remove(msg.senderId);
                        if (onPlayerLeftZone != null) onPlayerLeftZone.accept(msg.senderId);
                    } else if (msg.senderId.equals(myPlayerId)) {
                        myState = msg.playerState;
                        remotePlayersInZone.clear(); // Cleared when we change zones
                    } else {
                        remotePlayersInZone.put(msg.senderId, msg.playerState);
                        if (onPlayerJoinedZone != null) onPlayerJoinedZone.accept(msg.playerState);
                    }
                    break;
                case CHAT:
                    if (onChatMessageReceived != null) onChatMessageReceived.accept(msg.chatMessage);
                    break;
                case TEAM_UPDATE:
                    if (onTeamUpdated != null) onTeamUpdated.accept(msg.team);
                    break;
                case TRADE_UPDATE:
                    if (onTradeUpdated != null) onTradeUpdated.accept(msg.tradeSession);
                    break;
                case RANKING_RES:
                    if (onLeaderboardUpdated != null) onLeaderboardUpdated.accept(msg.leaderboard);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
