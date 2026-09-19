package forge.mmo;

import forge.mmo.client.MMOClientController;
import forge.mmo.net.protocol.MMOProtocol;

public class TestClient {
    public static void main(String[] args) {
        MMOClientController client = MMOClientController.getInstance();
        
        // Configure callbacks for testing
        client.setOnChatMessageReceived(msg -> {
            System.out.println("[CHAT] " + msg.channel + " - " + msg.sender + ": " + msg.message);
        });
        
        client.setOnLeaderboardUpdated(lb -> {
            System.out.println("[LEADERBOARD] Top Active Player: " + 
                (lb.topActivePlayerOfWeek != null ? lb.topActivePlayerOfWeek.username : "N/A"));
            System.out.println("[LEADERBOARD] Top Win Rate: " + 
                (lb.topWinRatePlayer != null ? lb.topWinRatePlayer.username + " - " + lb.topWinRatePlayer.getWinRate() : "N/A"));
        });
        
        client.setOnTeamUpdated(team -> {
            if (team != null) {
                System.out.println("[TEAM] Updated: " + team.teamName + " (" + team.memberNames.size() + " members)");
            }
        });

        // Connect to local server
        System.out.println("Connecting to localhost:8089...");
        client.connect("localhost", 8089, "TestPlayer");
        
        // Simulate some actions after 2 seconds
        new Thread(() -> {
            try {
                Thread.sleep(2000);
                
                // Send a global chat message
                System.out.println("Sending chat message...");
                client.sendChat(MMOProtocol.ChatMessage.ChatChannel.GLOBAL, "Hello Forge MMO!", null);
                
                // Send movement
                System.out.println("Sending movement...");
                client.sendMovement(150, 200);
                
                // Request ranking
                System.out.println("Requesting rankings...");
                MMOEvent rankingReq = new MMOEvent(MMOEvent.Type.RANKING_REQ);
                // The client controller handles this automatically via heartbeat
                
                // Wait and disconnect
                Thread.sleep(5000);
                System.out.println("Test complete, disconnecting...");
                client.disconnect();
                System.exit(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}