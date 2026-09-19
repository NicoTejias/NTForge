package forge.mmo.net.event;

import forge.mmo.net.protocol.MMOProtocol;
import java.io.Serializable;
import java.util.UUID;

public class MMOEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        LOGIN_REQ,
        LOGIN_RES,
        MOVE,
        ZONE_CHANGE_REQ,
        ZONE_CHANGE_RES,
        CHAT,
        TEAM_INVITE,
        TEAM_JOIN,
        TEAM_LEAVE,
        TEAM_UPDATE,
        TRADE_REQ,
        TRADE_UPDATE,
        TRADE_CONFIRM,
        SHOP_TRANSACTION,
        RANKING_REQ,
        RANKING_RES,
        HEARTBEAT
    }

    public Type type;
    public UUID senderId;
    public String payload;
    
    // Sub-objects for specific packet payloads
    public MMOProtocol.PlayerState playerState;
    public MMOProtocol.ChatMessage chatMessage;
    public MMOProtocol.Team team;
    public MMOProtocol.TradeSession tradeSession;
    public MMOProtocol.Leaderboard leaderboard;

    public MMOEvent(Type type) {
        this.type = type;
    }
}
