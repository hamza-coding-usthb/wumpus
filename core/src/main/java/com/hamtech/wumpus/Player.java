package com.hamtech.wumpus;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

// This class holds the player's game state (grid position and graphic)
public class Player {
    private int gridX; // Player's horizontal grid coordinate (0 to 7)
    private int gridY; // Player's vertical grid coordinate (0 to 7)
    private Texture texture;

    // Constructor: Takes the player image path and starting position
    public Player(String texturePath, int startX, int startY) {
        texture = new Texture(texturePath);
        this.gridX = startX;
        this.gridY = startY;
    }

    // Method to handle movement
    public void move(int dx, int dy) {
        int newX = gridX + dx;
        int newY = gridY + dy;
        
        // Basic bounds check (8x8 grid)
        if (newX >= 0 && newX < WumpusGame.GRID_SIZE &&
            newY >= 0 && newY < WumpusGame.GRID_SIZE) {
            
            this.gridX = newX;
            this.gridY = newY;
        }
        // *Add Wumpus/Pit collision checks here later*
    }

    // Method to draw the player at their pixel position
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
    
    // Getters for camera following logic
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
}
