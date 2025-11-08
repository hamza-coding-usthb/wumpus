package com.hamtech.wumpus;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * Represents the Wumpus game entity, holding its grid position and texture.
 */
public class Wumpus {
    private int gridX;
    private int gridY;
    private Texture texture;

    public Wumpus(String texturePath, int startX, int startY) {
        texture = new Texture(texturePath);
        this.gridX = startX;
        this.gridY = startY;
    }

    // Method to draw the Wumpus at its pixel position
    public void draw(SpriteBatch batch, int tileSize) {
        // Calculate the pixel position based on the grid position
        float pixelX = gridX * tileSize;
        float pixelY = gridY * tileSize;
        
        batch.draw(texture, pixelX, pixelY, tileSize, tileSize);
    }
    
    // Remember to dispose of the texture when the game closes
    public void dispose() {
        texture.dispose();
    }
    
    // Getters for placement logic
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
}