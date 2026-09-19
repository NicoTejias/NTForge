package forge.mmo.net.protocol;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public class MMOProtocol {

    public enum ZoneType {
        TOWN_HUB,
        TRAINING_ARENA, // AI enemies present here
        DUNGEONS,       // AI enemies present here
        TOURNAMENT_GROUNDS,
        TRADE_DISTRICT,
        SHOPS
    }

    public static class PlayerState implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID playerId;
        public String username;
        public float x;
        public float y;
        public ZoneType currentZone;
        public int eloRating;
        public int gamesPlayedThisWeek;
        public int winsThisWeek;

        public PlayerState(UUID playerId, String username, float x, float y, ZoneType zone) {
            this.playerId = playerId;
            this.username = username;
            this.x = x;
            this.y = y;
            this.currentZone = zone;
        }

        public double getWinRate() {
            return gamesPlayedThisWeek == 0 ? 0.0 : (double) winsThisWeek / gamesPlayedThisWeek;
        }
    }

    public static class ChatMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        public enum ChatChannel { GLOBAL, ZONE, TEAM, WHISPER }
        
        public ChatChannel channel;
        public String sender;
        public String receiver; // For Whisper
        public String message;
        public long timestamp;

        public ChatMessage(ChatChannel channel, String sender, String receiver, String message) {
            this.channel = channel;
            this.sender = sender;
            this.receiver = receiver;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class Team implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID teamId;
        public String teamName;
        public UUID leaderId;
        public List<UUID> memberIds;
        public List<String> memberNames;

        public Team(UUID teamId, String teamName, UUID leaderId) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.leaderId = leaderId;
        }
    }

    public static class TradeSession implements Serializable {
        private static final long serialVersionUID = 1L;
        public UUID tradeId;
        public UUID playerA;
        public UUID playerB;
        public List<String> offeredCardsA;
        public List<String> offeredCardsB;
        public int goldA;
        public int goldB;
        public boolean acceptedA;
        public boolean acceptedB;
        public boolean lockedA;
        public boolean lockedB;
    }

    public static class Leaderboard implements Serializable {
        private static final long serialVersionUID = 1L;
        public List<PlayerState> topEloPlayers;
        public PlayerState topActivePlayerOfWeek;
        public PlayerState topWinRatePlayer;
    }
}
