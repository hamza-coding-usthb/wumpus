package com.hamtech.wumpus;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Represents a static game entity (Trap, Obstacle, Treasure) holding its grid position and texture.
 */
public class StaticEntity {
    private int gridX;
    private int gridY;
    private Texture texture;
    
    public StaticEntity(String texturePath, int startX, int startY) {
        texture = new Texture(texturePath);
        this.gridX = startX;
        this.gridY = startY;
    }

    public void draw(SpriteBatch batch, int tileSize) {
        float pixelX = gridX * tileSize;
        float pixelY = gridY * tileSize;
        batch.draw(texture, pixelX, pixelY, tileSize, tileSize);
    }
    
    public void dispose() {
        texture.dispose();
    }
    
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
}