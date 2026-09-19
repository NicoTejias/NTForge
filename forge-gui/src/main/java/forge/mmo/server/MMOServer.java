package forge.mmo.server;

import forge.mmo.net.event.MMOEvent;
import forge.mmo.net.protocol.MMOProtocol;
import forge.mmo.net.protocol.MMOProtocol.ZoneType;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MMOServer {

    private final int port;
    private final Map<UUID, ChannelHandlerContext> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MMOProtocol.PlayerState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, MMOProtocol.Team> activeTeams = new ConcurrentHashMap<>();
    private final Map<UUID, MMOProtocol.TradeSession> activeTrades = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService serverScheduler = Executors.newScheduledThreadPool(4);

    public MMOServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline().addLast(
                         new ObjectDecoder(ClassResolvers.cacheDisabled(null)),
                         new ObjectEncoder(),
                         new MMOServerHandler()
                     );
                 }
             })
             .option(ChannelOption.SO_BACKLOG, 128)
             .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            System.out.println("Dedicated Forge MMO Server started on port " + port);

            // Periodically calculate weekly rankings and active statistics
            serverScheduler.scheduleAtFixedRate(this::updateRankings, 0, 5, TimeUnit.MINUTES);

            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
            serverScheduler.shutdown();
        }
    }

    private void updateRankings() {
        // Compute Leaderboards across all active players
        List<MMOProtocol.PlayerState> sortedByElo = new ArrayList<>(playerStates.values());
        sortedByElo.sort((p1, p2) -> Integer.compare(p2.eloRating, p1.eloRating));

        List<MMOProtocol.PlayerState> sortedByActivity = new ArrayList<>(playerStates.values());
        sortedByActivity.sort((p1, p2) -> Integer.compare(p2.gamesPlayedThisWeek, p1.gamesPlayedThisWeek));

        List<MMOProtocol.PlayerState> sortedByWinRate = new ArrayList<>(playerStates.values());
        sortedByWinRate.sort((p1, p2) -> Double.compare(p2.getWinRate(), p1.getWinRate()));

        MMOProtocol.Leaderboard leaderboard = new MMOProtocol.Leaderboard();
        leaderboard.topEloPlayers = sortedByElo.subList(0, Math.min(sortedByElo.size(), 50));
        leaderboard.topActivePlayerOfWeek = sortedByActivity.isEmpty() ? null : sortedByActivity.get(0);
        leaderboard.topWinRatePlayer = sortedByWinRate.isEmpty() ? null : sortedByWinRate.get(0);

        // Broadcast leaderboard update
        MMOEvent updateEvent = new MMOEvent(MMOEvent.Type.RANKING_RES);
        updateEvent.leaderboard = leaderboard;
        broadcastToAll(updateEvent);
    }

    private void broadcastToAll(MMOEvent event) {
        for (ChannelHandlerContext ctx : activeSessions.values()) {
            ctx.writeAndFlush(event);
        }
    }

    private void broadcastToZone(ZoneType zone, MMOEvent event) {
        for (Map.Entry<UUID, MMOProtocol.PlayerState> entry : playerStates.entrySet()) {
            if (entry.getValue().currentZone == zone) {
                ChannelHandlerContext ctx = activeSessions.get(entry.getKey());
                if (ctx != null) {
                    ctx.writeAndFlush(event);
                }
            }
        }
    }

    private class MMOServerHandler extends SimpleChannelInboundHandler<MMOEvent> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MMOEvent msg) {
            switch (msg.type) {
                case LOGIN_REQ:
                    handleLogin(ctx, msg);
                    break;
                case MOVE:
                    handleMove(msg);
                    break;
                case ZONE_CHANGE_REQ:
                    handleZoneChange(msg);
                    break;
                case CHAT:
                    handleChat(msg);
                    break;
                case TEAM_INVITE:
                case TEAM_JOIN:
                case TEAM_LEAVE:
                    handleTeamOperation(msg);
                    break;
                case TRADE_REQ:
                case TRADE_UPDATE:
                case TRADE_CONFIRM:
                    handleTradeOperation(msg);
                    break;
                case RANKING_REQ:
                    handleRankingRequest(ctx);
                    break;
                default:
                    break;
            }
        }

        private void handleLogin(ChannelHandlerContext ctx, MMOEvent msg) {
            UUID id = UUID.randomUUID();
            MMOProtocol.PlayerState newState = new MMOProtocol.PlayerState(id, msg.payload, 100, 100, ZoneType.TOWN_HUB);
            newState.eloRating = 1200;
            newState.gamesPlayedThisWeek = 0;
            newState.winsThisWeek = 0;

            activeSessions.put(id, ctx);
            playerStates.put(id, newState);

            MMOEvent response = new MMOEvent(MMOEvent.Type.LOGIN_RES);
            response.senderId = id;
            response.playerState = newState;
            ctx.writeAndFlush(response);

            // Announce connection to the hub
            MMOEvent joinAnnounce = new MMOEvent(MMOEvent.Type.ZONE_CHANGE_RES);
            joinAnnounce.senderId = id;
            joinAnnounce.playerState = newState;
            broadcastToZone(ZoneType.TOWN_HUB, joinAnnounce);
        }

        private void handleMove(MMOEvent msg) {
            MMOProtocol.PlayerState state = playerStates.get(msg.senderId);
            if (state != null && msg.playerState != null) {
                state.x = msg.playerState.x;
                state.y = msg.playerState.y;

                // Sync position to everyone in the same zone
                MMOEvent moveSync = new MMOEvent(MMOEvent.Type.MOVE);
                moveSync.senderId = msg.senderId;
                moveSync.playerState = state;
                broadcastToZone(state.currentZone, moveSync);
            }
        }

        private void handleZoneChange(MMOEvent msg) {
            MMOProtocol.PlayerState state = playerStates.get(msg.senderId);
            if (state != null && msg.playerState != null) {
                ZoneType oldZone = state.currentZone;
                ZoneType newZone = msg.playerState.currentZone;

                // Leave old zone
                MMOEvent leaveMsg = new MMOEvent(MMOEvent.Type.ZONE_CHANGE_RES);
                leaveMsg.senderId = msg.senderId;
                leaveMsg.playerState = null; // Represents leave
                broadcastToZone(oldZone, leaveMsg);

                // Update state
                state.currentZone = newZone;
                state.x = 100; // Reset zone coordinates safely
                state.y = 100;

                // Join new zone
                MMOEvent joinMsg = new MMOEvent(MMOEvent.Type.ZONE_CHANGE_RES);
                joinMsg.senderId = msg.senderId;
                joinMsg.playerState = state;
                broadcastToZone(newZone, joinMsg);
            }
        }

        private void handleChat(MMOEvent msg) {
            if (msg.chatMessage == null) return;
            switch (msg.chatMessage.channel) {
                case GLOBAL:
                    broadcastToAll(msg);
                    break;
                case ZONE:
                    MMOProtocol.PlayerState state = playerStates.get(msg.senderId);
                    if (state != null) {
                        broadcastToZone(state.currentZone, msg);
                    }
                    break;
                case TEAM:
                    MMOProtocol.Team team = activeTeams.values().stream()
                        .filter(t -> t.memberIds.contains(msg.senderId))
                        .findFirst().orElse(null);
                    if (team != null) {
                        for (UUID mid : team.memberIds) {
                            ChannelHandlerContext mCtx = activeSessions.get(mid);
                            if (mCtx != null) mCtx.writeAndFlush(msg);
                        }
                    }
                    break;
                case WHISPER:
                    UUID targetId = playerStates.entrySet().stream()
                        .filter(e -> e.getValue().username.equalsIgnoreCase(msg.chatMessage.receiver))
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null);
                    if (targetId != null) {
                        ChannelHandlerContext targetCtx = activeSessions.get(targetId);
                        if (targetCtx != null) targetCtx.writeAndFlush(msg);
                    }
                    break;
            }
        }

        private void handleTeamOperation(MMOEvent msg) {
            if (msg.type == MMOEvent.Type.TEAM_INVITE) {
                // Team creation / inviting player
                UUID teamId = UUID.randomUUID();
                MMOProtocol.Team newTeam = new MMOProtocol.Team(teamId, msg.payload, msg.senderId);
                newTeam.memberIds = new ArrayList<>(List.of(msg.senderId));
                newTeam.memberNames = new ArrayList<>(List.of(playerStates.get(msg.senderId).username));
                activeTeams.put(teamId, newTeam);

                MMOEvent update = new MMOEvent(MMOEvent.Type.TEAM_UPDATE);
                update.team = newTeam;
                activeSessions.get(msg.senderId).writeAndFlush(update);
            }
        }

        private void handleTradeOperation(MMOEvent msg) {
            if (msg.type == MMOEvent.Type.TRADE_REQ) {
                UUID tradeId = UUID.randomUUID();
                MMOProtocol.TradeSession session = new MMOProtocol.TradeSession();
                session.tradeId = tradeId;
                session.playerA = msg.senderId;
                session.offeredCardsA = new ArrayList<>();
                session.offeredCardsB = new ArrayList<>();
                activeTrades.put(tradeId, session);
            }
        }

        private void handleRankingRequest(ChannelHandlerContext ctx) {
            List<MMOProtocol.PlayerState> sortedByElo = new ArrayList<>(playerStates.values());
            sortedByElo.sort((p1, p2) -> Integer.compare(p2.eloRating, p1.eloRating));

            MMOProtocol.Leaderboard leaderboard = new MMOProtocol.Leaderboard();
            leaderboard.topEloPlayers = sortedByElo.subList(0, Math.min(sortedByElo.size(), 10));

            MMOEvent res = new MMOEvent(MMOEvent.Type.RANKING_RES);
            res.leaderboard = leaderboard;
            ctx.writeAndFlush(res);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            // Cleanup on disconnect
            UUID foundId = null;
            for (Map.Entry<UUID, ChannelHandlerContext> entry : activeSessions.entrySet()) {
                if (entry.getValue() == ctx) {
                    foundId = entry.getKey();
                    break;
                }
            }
            if (foundId != null) {
                activeSessions.remove(foundId);
                MMOProtocol.PlayerState state = playerStates.remove(foundId);
                if (state != null) {
                    MMOEvent leaveMsg = new MMOEvent(MMOEvent.Type.ZONE_CHANGE_RES);
                    leaveMsg.senderId = foundId;
                    leaveMsg.playerState = null;
                    broadcastToZone(state.currentZone, leaveMsg);
                }
            }
            ctx.close();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8089;
        new MMOServer(port).start();
    }
}
