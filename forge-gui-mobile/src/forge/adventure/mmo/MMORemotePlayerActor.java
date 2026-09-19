package forge.adventure.mmo;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;

public class MMORemotePlayerActor extends Actor {

    private final String username;
    private TextureRegion currentFrame;
    private final BitmapFont nameFont;
    private float targetX;
    private float targetY;
    private static final float LERP_SPEED = 5f;

    public MMORemotePlayerActor(String username, TextureAtlas atlas) {
        this.username = username;
        this.nameFont = new BitmapFont(); // Simple built-in libGDX font
        this.nameFont.setColor(Color.CYAN);
        
        // Use default avatar textures from atlas
        if (atlas != null && atlas.getRegions().size > 0) {
            this.currentFrame = atlas.getRegions().first();
            this.setSize(currentFrame.getRegionWidth(), currentFrame.getRegionHeight());
        } else {
            this.setSize(32, 48); // Fallback size
        }
    }

    public void setTargetPosition(float x, float y) {
        this.targetX = x;
        this.targetY = y;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        // Smoothly interpolate position (dead reckoning / linear interpolation)
        float newX = getX() + (targetX - getX()) * LERP_SPEED * delta;
        float newY = getY() + (targetY - getY()) * LERP_SPEED * delta;
        setPosition(newX, newY);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        // Draw the player character frame
        if (currentFrame != null) {
            batch.draw(currentFrame, getX(), getY(), getWidth(), getHeight());
        } else {
            // Placeholder color block if texture unavailable
            batch.setColor(Color.BLUE);
            // standard drawing
            batch.setColor(Color.WHITE);
        }

        // Draw username centered above the player's head
        nameFont.draw(batch, username, getX() + (getWidth() / 2) - 15, getY() + getHeight() + 15);
    }
}
