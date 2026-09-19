package forge.adventure.mmo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import forge.mmo.client.MMOClientController;
import forge.mmo.net.protocol.MMOProtocol;

import java.util.ArrayList;
import java.util.List;

public class MMOHudOverlay extends Table {

    private final MMOClientController client;
    private final BitmapFont font;
    
    // UI Elements
    private final Table chatTable;
    private final ScrollPane chatScroll;
    private final Label chatDisplay;
    private final TextField chatInput;
    private final SelectBox<String> channelSelector;

    private final Table partyTable;
    private final Label partyLabel;

    private final Table rankTable;
    private final List<String> leaderboardLines = new ArrayList<>();

    public MMOHudOverlay(Skin skin) {
        super(skin);
        this.client = MMOClientController.getInstance();
        this.font = new BitmapFont();
        this.setFillParent(true);

        // Configure layout: Top bar, Center world area, Bottom/Side Panels
        this.top().left();

        // 1. Party Overlay (Top Left)
        partyTable = new Table(skin);
        partyTable.setBackground("default-rect");
        partyLabel = new Label("Team: Solo", skin);
        partyLabel.setColor(Color.GOLD);
        partyTable.add(partyLabel).pad(10).row();
        this.add(partyTable).top().left().pad(10);

        // Space
        this.add().expandX();

        // 2. Rankings / High Scores Overlay (Top Right)
        rankTable = new Table(skin);
        rankTable.setBackground("default-rect");
        Label rankHeader = new Label("--- Rankings & Stars ---", skin);
        rankHeader.setColor(Color.CYAN);
        rankTable.add(rankHeader).pad(5).row();
        this.add(rankTable).top().right().pad(10).row();

        this.add().expandY().row();

        // 3. Multitasking Chat Overlay (Bottom Left)
        chatTable = new Table(skin);
        chatTable.setBackground("default-rect");

        chatDisplay = new Label("Welcome to Forge MMO Chat!\n", skin);
        chatDisplay.setWrap(true);
        chatScroll = new ScrollPane(chatDisplay, skin);
        chatScroll.setFadeScrollBars(false);

        channelSelector = new SelectBox<>(skin);
        channelSelector.setItems("GLOBAL", "ZONE", "TEAM", "WHISPER");

        chatInput = new TextField("", skin);
        chatInput.setMessageText("Press Enter to send message...");

        TextButton sendBtn = new TextButton("Send", skin);

        chatTable.add(chatScroll).colspan(3).width(400).height(150).pad(5).row();
        chatTable.add(channelSelector).width(100).pad(2);
        chatTable.add(chatInput).width(220).pad(2);
        chatTable.add(sendBtn).width(80).pad(2);

        this.add(chatTable).bottom().left().pad(10).colspan(2);

        // Bind chat send actions
        sendBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                sendMessage();
            }
        });

        // Initialize Listeners for Server updates
        setupNetworkListeners();
    }

    private void sendMessage() {
        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            String selectedChan = channelSelector.getSelected();
            MMOProtocol.ChatMessage.ChatChannel chan = MMOProtocol.ChatMessage.ChatChannel.valueOf(selectedChan);
            
            String receiver = "";
            if (chan == MMOProtocol.ChatMessage.ChatChannel.WHISPER && msg.contains(":")) {
                int colonIdx = msg.indexOf(':');
                receiver = msg.substring(0, colonIdx).trim();
                msg = msg.substring(colonIdx + 1).trim();
            }

            client.sendChat(chan, msg, receiver);
            chatInput.setText("");
        }
    }

    private void setupNetworkListeners() {
        client.setOnChatMessageReceived(msg -> {
            String channelColor = "[WHITE]";
            if (msg.channel == MMOProtocol.ChatMessage.ChatChannel.GLOBAL) channelColor = "[LIGHT_GRAY]";
            if (msg.channel == MMOProtocol.ChatMessage.ChatChannel.ZONE) channelColor = "[SKY]";
            if (msg.channel == MMOProtocol.ChatMessage.ChatChannel.TEAM) channelColor = "[GOLD]";
            if (msg.channel == MMOProtocol.ChatMessage.ChatChannel.WHISPER) channelColor = "[PINK]";

            String formatted = channelColor + "[" + msg.channel + "] " + msg.sender + ": " + msg.message + "[]\n";
            chatDisplay.setText(chatDisplay.getText() + formatted);
            chatScroll.setScrollPercentY(1f); // Auto Scroll to bottom
        });

        client.setOnTeamUpdated(team -> {
            if (team == null) {
                partyLabel.setText("Team: Solo");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append("Team: ").append(team.teamName).append("\n");
                for (String name : team.memberNames) {
                    sb.append(" - ").append(name).append("\n");
                }
                partyLabel.setText(sb.toString());
            }
        });

        client.setOnLeaderboardUpdated(lb -> {
            rankTable.clearChildren();
            Label rankHeader = new Label("--- Rankings & Stars ---", getSkin());
            rankHeader.setColor(Color.CYAN);
            rankTable.add(rankHeader).pad(5).row();

            // Weekly MVP
            if (lb.topActivePlayerOfWeek != null) {
                Label mvp = new Label("Weekly MVP (Most Games): " + lb.topActivePlayerOfWeek.username, getSkin());
                mvp.setColor(Color.GOLD);
                rankTable.add(mvp).left().pad(2).row();
            }

            // Highest Win Rate
            if (lb.topWinRatePlayer != null) {
                Label wr = new Label("Top Win-Rate: " + lb.topWinRatePlayer.username + String.format(" (%.1f%%)", lb.topWinRatePlayer.getWinRate() * 100), getSkin());
                wr.setColor(Color.GREEN);
                rankTable.add(wr).left().pad(2).row();
            }

            // Top ELO ratings
            if (lb.topEloPlayers != null) {
                rankTable.add(new Label("-- Top ELO Rankings --", getSkin())).padTop(10).row();
                int idx = 1;
                for (MMOProtocol.PlayerState p : lb.topEloPlayers) {
                    if (idx > 5) break; // Display top 5
                    Label eloLabel = new Label(idx + ". " + p.username + " (ELO: " + p.eloRating + ")", getSkin());
                    rankTable.add(eloLabel).left().pad(1).row();
                    idx++;
                }
            }
        });
    }
}
