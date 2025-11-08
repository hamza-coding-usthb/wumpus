package com.hamtech.wumpus;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.InputProcessor;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class WumpusGame extends ApplicationAdapter implements InputProcessor {
    private Player player;
    private Wumpus wumpus;
    
    // New entity lists
    private List<StaticEntity> traps;
    private List<StaticEntity> obstacles;
    private List<StaticEntity> treasure;
    
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Texture pebbleTexture;
    private Texture fogOfWarTexture; // NEW FIELD for Fog of War
    
    // GAME STATE VARIABLES
    private BitmapFont font;
    private boolean isGameOver = false;
    private String gameOverMessage = "";
    private List<String> proximityMessages;
    private boolean[][] isVisible; // NEW FIELD: Tracks explored tiles
    
    // MENU STATE VARIABLES
    private boolean isPaused = false;
    private Rectangle menuButtonBounds;
    
    // UI Constants
    private final float MENU_BUTTON_WIDTH = 70;
    private final float MENU_BUTTON_HEIGHT = 20;
    private final float MENU_START_Y_OFFSET = 100;

    
    public static final int GRID_SIZE = 8;
    public static final int TILE_SIZE = 32;
    public static final float WORLD_WIDTH = GRID_SIZE * TILE_SIZE;
    public static final float WORLD_HEIGHT = GRID_SIZE * TILE_SIZE;
    
    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        viewport = new FitViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), camera);
        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0);
        camera.update();

        pebbleTexture = new Texture("pebble_brown0.png"); 
        fogOfWarTexture = new Texture("FogofWarSingle.png"); // INITIALIZE FOG TEXTURE
        player = new Player("donald.png", 0, 0);
        
        // Initialize Font and Messages
        font = new BitmapFont(); 
        font.getData().setScale(1.0f); 
        proximityMessages = new ArrayList<>();
        
        // Initialize FoW and set starting tile visible
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];
        isVisible[player.getGridX()][player.getGridY()] = true;


        // Initialize Menu Button Bounds (based on viewport size)
        float menuX = viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
        float menuY = viewport.getWorldHeight() - MENU_BUTTON_HEIGHT - 10;
        menuButtonBounds = new Rectangle(menuX, menuY, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);


        // Lists initialization
        traps = new ArrayList<>();
        obstacles = new ArrayList<>();
        treasure = new ArrayList<>();
        
        Random random = new Random();
        
        // List to track all occupied coordinates on the grid
        List<GridPoint2> occupiedPositions = new ArrayList<>();
        
        // 1. Mark the illegal 2x2 area around the player (0,0) as occupied
        occupiedPositions.add(new GridPoint2(0, 0)); // Player's position
        occupiedPositions.add(new GridPoint2(1, 0));
        occupiedPositions.add(new GridPoint2(0, 1));
        occupiedPositions.add(new GridPoint2(1, 1));
        
        // Helper method to find a valid, unoccupied position
        GridPoint2 pos;
        
        // 2. Place Wumpus (sphinx.png)
        pos = findValidPosition(random, occupiedPositions);
        wumpus = new Wumpus("sphinx.png", pos.x, pos.y);
        occupiedPositions.add(pos);
        
        // 3. Place 2 Traps (dngn_trap_arrow.png)
        for (int i = 0; i < 2; i++) {
            pos = findValidPosition(random, occupiedPositions);
            traps.add(new StaticEntity("dngn_trap_arrow.png", pos.x, pos.y));
            occupiedPositions.add(pos);
        }
        
        // 4. Place 2 Obstacles (crumbled_column.png)
        for (int i = 0; i < 2; i++) {
            pos = findValidPosition(random, occupiedPositions);
            obstacles.add(new StaticEntity("crumbled_column.png", pos.x, pos.y));
            occupiedPositions.add(pos);
        }
        
        // 5. Place 1 Treasure (gold_pile.png)
        pos = findValidPosition(random, occupiedPositions);
        treasure.add(new StaticEntity("gold_pile.png", pos.x, pos.y));
        occupiedPositions.add(pos);
        
        Gdx.input.setInputProcessor(this);
        
        // Initial proximity check (for starting tile 0,0)
        checkGameInteraction();
    }
    
    // Method to reset the game state for "New Game"
    private void newGame() {
        isPaused = false;
        isGameOver = false;
        gameOverMessage = "";
        
        // Dispose of existing entities
        wumpus.dispose();
        for (StaticEntity entity : traps) entity.dispose();
        for (StaticEntity entity : obstacles) entity.dispose();
        for (StaticEntity entity : treasure) entity.dispose();

        // Re-run setup logic from create
        traps.clear();
        obstacles.clear();
        treasure.clear();
        
        player = new Player("donald.png", 0, 0); // Recreate player at (0,0)
        
        // Re-initialize FoW
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];
        isVisible[player.getGridX()][player.getGridY()] = true;
        
        // Entity placement logic (copied from create for re-initialization)
        Random random = new Random();
        List<GridPoint2> occupiedPositions = new ArrayList<>();
        
        occupiedPositions.add(new GridPoint2(0, 0));
        occupiedPositions.add(new GridPoint2(1, 0));
        occupiedPositions.add(new GridPoint2(0, 1));
        occupiedPositions.add(new GridPoint2(1, 1));
        
        GridPoint2 pos;
        pos = findValidPosition(random, occupiedPositions);
        wumpus = new Wumpus("sphinx.png", pos.x, pos.y);
        occupiedPositions.add(pos);
        
        for (int i = 0; i < 2; i++) {
            pos = findValidPosition(random, occupiedPositions);
            traps.add(new StaticEntity("dngn_trap_arrow.png", pos.x, pos.y));
            occupiedPositions.add(pos);
        }
        
        for (int i = 0; i < 2; i++) {
            pos = findValidPosition(random, occupiedPositions);
            obstacles.add(new StaticEntity("crumbled_column.png", pos.x, pos.y));
            occupiedPositions.add(pos);
        }
        
        pos = findValidPosition(random, occupiedPositions);
        treasure.add(new StaticEntity("gold_pile.png", pos.x, pos.y));
        occupiedPositions.add(pos);
        
        centerCameraOnPlayer();
        checkGameInteraction(); // Check initial proximity
        Gdx.app.log("WUMPUS_GAME", "New Game Started.");
    }
    
    /** Finds a random, valid position that is not in the occupiedPositions list. */
    private GridPoint2 findValidPosition(Random random, List<GridPoint2> occupiedPositions) {
        int x, y;
        GridPoint2 newPos;
        do {
            x = random.nextInt(GRID_SIZE);
            y = random.nextInt(GRID_SIZE);
            newPos = new GridPoint2(x, y);
        } while (isOccupied(newPos, occupiedPositions));
        return newPos;
    }

    /** Checks if a given position is already in the occupied list. */
    private boolean isOccupied(GridPoint2 pos, List<GridPoint2> occupiedPositions) {
        for (GridPoint2 occupied : occupiedPositions) {
            if (occupied.x == pos.x && occupied.y == pos.y) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }
    
    // HELPER METHOD FOR OBSTACLE COLLISION
    private boolean isTileOccupiedByObstacle(int x, int y) {
        for (StaticEntity obstacle : obstacles) {
            if (obstacle.getGridX() == x && obstacle.getGridY() == y) {
                return true;
            }
        }
        return false;
    }
    
    // HELPER METHOD FOR ADJACENCY CHECK
    private boolean isAdjacent(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x1 - x2);
        int dy = Math.abs(y1 - y2);
        boolean withinOne = dx <= 1 && dy <= 1;
        boolean notSameTile = dx != 0 || dy != 0;
        return withinOne && notSameTile;
    }

    @Override
    public boolean keyDown(int keycode) {
        // Halt input if the game is over OR paused
        if (isGameOver || isPaused) {
            return false;
        }
        
        int dx = 0, dy = 0;

        // 1. Determine intended movement vector (dx, dy)
        if (keycode == Keys.LEFT) {
            dx = -1;
        } else if (keycode == Keys.RIGHT) {
            dx = 1;
        } else if (keycode == Keys.UP) {
            dy = 1;
        } else if (keycode == Keys.DOWN) {
            dy = -1;
        }
        
        // Only proceed if a directional key was pressed
        if (dx != 0 || dy != 0) {
            
            // Calculate potential new position
            int newX = player.getGridX() + dx;
            int newY = player.getGridY() + dy;

            // 2. CHECK: If the target tile has an obstacle, DO NOT MOVE.
            if (isTileOccupiedByObstacle(newX, newY)) {
                return true; 
            }
            
            // 3. If no obstacle, move the player (player.move handles boundary checks)
            player.move(dx, dy);

            // FOG OF WAR: Mark new tile as visible
            isVisible[player.getGridX()][player.getGridY()] = true;

            centerCameraOnPlayer();
            
            // CHECK FOR INTERACTIONS (Collision and Proximity) AFTER MOVING
            checkGameInteraction();
        }

        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        // Unproject screen coordinates to world coordinates (essential for UI overlay)
        float worldX = camera.position.x - viewport.getWorldWidth() / 2f + screenX;
        float worldY = camera.position.y + viewport.getWorldHeight() / 2f - screenY;

        // 1. Menu Button Logic (Always active unless game is over)
        if (!isGameOver) {
            // Recalculate button bounds relative to current camera position
            float viewX = camera.position.x - viewport.getWorldWidth() / 2f;
            float viewY = camera.position.y + viewport.getWorldHeight() / 2f;
            
            float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
            float btnY = viewY - MENU_BUTTON_HEIGHT - 10;
            Rectangle currentMenuButtonBounds = new Rectangle(btnX, btnY, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);
            
            if (currentMenuButtonBounds.contains(worldX, worldY)) {
                isPaused = !isPaused;
                Gdx.app.log("WUMPUS_GAME", "Menu Toggled: " + (isPaused ? "Paused" : "Playing"));
                return true;
            }
        }
        
        // 2. Menu Option Clicks (Only active when paused)
        if (isPaused) {
            float viewX = camera.position.x - viewport.getWorldWidth() / 2f;
            float viewY = camera.position.y + viewport.getWorldHeight() / 2f;
            
            float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
            float menuCenterY = viewY - viewport.getWorldHeight() / 2f;

            float optionX = menuCenterX - 50; // Text is centered roughly here
            float optionWidth = 200; // Large enough click area

            // New Game
            float newGameY = menuCenterY + MENU_START_Y_OFFSET;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > newGameY - 20 && worldY < newGameY + 5) {
                newGame();
                return true;
            }

            // Save Game
            float saveGameY = menuCenterY + MENU_START_Y_OFFSET - 30;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > saveGameY - 20 && worldY < saveGameY + 5) {
                isPaused = false;
                Gdx.app.log("WUMPUS_GAME", "Save Game (Not fully implemented)");
                return true;
            }
            
            // Load Game
            float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 60;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > loadGameY - 20 && worldY < loadGameY + 5) {
                isPaused = false;
                Gdx.app.log("WUMPUS_GAME", "Load Game (Not fully implemented)");
                return true;
            }
            
            // Quit to Menu (Exit Application for now)
            float quitY = menuCenterY + MENU_START_Y_OFFSET - 90;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > quitY - 20 && worldY < quitY + 5) {
                Gdx.app.log("WUMPUS_GAME", "Quitting Game...");
                Gdx.app.exit();
                return true;
            }
        }
        return false;
    }
    
    private void checkGameInteraction() {
        // Clear previous proximity messages at the start of the turn
        proximityMessages.clear();
        
        if (isGameOver) return;
        
        int playerX = player.getGridX();
        int playerY = player.getGridY();

        // --- CHECK FOR IMMEDIATE COLLISIONS (Game Over/Win) ---
        
        // 1. Check for Treasure (Win Condition)
        StaticEntity gold = treasure.get(0);
        if (playerX == gold.getGridX() && playerY == gold.getGridY()) {
            isGameOver = true;
            gameOverMessage = "YOU FOUND THE TREASURE! YOU WIN! 🎉";
            Gdx.app.log("WUMPUS_GAME", "--- VICTORY ---");
            return;
        }
        
        // 2. Check for Traps (Lose Condition)
        for (StaticEntity trap : traps) {
            if (playerX == trap.getGridX() && playerY == trap.getGridY()) {
                isGameOver = true;
                gameOverMessage = "BOOM! You stepped on a trap. Game Over! 💀";
                Gdx.app.log("WUMPUS_GAME", "--- DEFEAT (TRAP) ---");
                return;
            }
        }

        // 3. Check for Wumpus (Lose Condition)
        if (playerX == wumpus.getGridX() && playerY == wumpus.getGridY()) {
            isGameOver = true;
            gameOverMessage = "A ROAR! The Wumpus devoured you! Game Over! 😵";
            Gdx.app.log("WUMPUS_GAME", "--- DEFEAT (WUMPUS) ---");
            return;
        }

        // --- CHECK FOR PROXIMITY EFFECTS (Only runs if game is NOT over) ---
        
        // Treasure Proximity: "A glow is visible"
        if (isAdjacent(playerX, playerY, gold.getGridX(), gold.getGridY())) {
            proximityMessages.add("A glow is visible");
        }

        // Wumpus Proximity: "You can smell a stench"
        if (isAdjacent(playerX, playerY, wumpus.getGridX(), wumpus.getGridY())) {
            proximityMessages.add("You can smell a stench");
        }
        
        // Traps Proximity: "You feel a breeze"
        for (StaticEntity trap : traps) {
            if (isAdjacent(playerX, playerY, trap.getGridX(), trap.getGridY())) {
                proximityMessages.add("You feel a breeze");
            }
        }
    }

    private void centerCameraOnPlayer() {
        float targetX = player.getGridX() * TILE_SIZE + (TILE_SIZE / 2f);
        float targetY = player.getGridY() * TILE_SIZE + (TILE_SIZE / 2f);
        float cameraHalfWidth = viewport.getWorldWidth() / 2f;
        float cameraHalfHeight = viewport.getWorldHeight() / 2f;

        camera.position.x = Math.max(cameraHalfWidth, Math.min(targetX, WORLD_WIDTH - cameraHalfWidth));
        camera.position.y = Math.max(cameraHalfHeight, Math.min(targetY, WORLD_HEIGHT - cameraHalfHeight));
        
        camera.update();
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return false; }
    @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
    @Override public boolean scrolled(float amountX, float amountY) { return false; }
    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }
    
    @Override
    public void render() {
        camera.update();
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // 1. Draw Grid and Entities
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                float xPos = x * TILE_SIZE;
                float yPos = y * TILE_SIZE;
                
                // Draw only visible parts
                if (isGameOver || isVisible[x][y]) {
                    // Draw Tile
                    batch.draw(pebbleTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                    
                    // Draw Entities on visible tile
                    if (x == wumpus.getGridX() && y == wumpus.getGridY()) wumpus.draw(batch, TILE_SIZE);
                    for (StaticEntity entity : treasure) {
                        if (x == entity.getGridX() && y == entity.getGridY()) entity.draw(batch, TILE_SIZE);
                    }
                    for (StaticEntity entity : traps) {
                        if (x == entity.getGridX() && y == entity.getGridY()) entity.draw(batch, TILE_SIZE);
                    }
                    for (StaticEntity entity : obstacles) {
                        if (x == entity.getGridX() && y == entity.getGridY()) entity.draw(batch, TILE_SIZE);
                    }
                } else {
                    // If not visible, draw only the basic tile (optional, but ensures background color is not showing)
                    batch.draw(pebbleTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                }
            }
        }
        
        // 4. Draw Player (on top of everything else)
        player.draw(batch, TILE_SIZE);

        // --- FOG OF WAR DRAWING ---
        if (!isGameOver) {
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int y = 0; y < GRID_SIZE; y++) {
                    // Draw FoW if the tile is NOT visible
                    if (!isVisible[x][y]) {
                        float xPos = x * TILE_SIZE;
                        float yPos = y * TILE_SIZE;
                        batch.draw(fogOfWarTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                    }
                }
            }
        }

        // --- UI DRAWING ---

        // Calculate coordinates relative to the screen view
        float viewX = camera.position.x - viewport.getWorldWidth() / 2f;
        float viewY = camera.position.y + viewport.getWorldHeight() / 2f;

        // Draw Menu Button (Upper Right)
        if (!isGameOver && !isPaused) {
            font.setColor(Color.WHITE);
            float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
            float btnY = viewY - 10;
            font.draw(batch, "[MENU]", btnX, btnY);
        }
        
        // Draw Proximity Messages (Top Left, only if game is NOT over or paused)
        if (!isGameOver && !isPaused && !proximityMessages.isEmpty()) {
            
            float messageY = viewY - 10; 
            
            font.setColor(0.5f, 1.0f, 0.5f, 1.0f); // Light green for effects
            
            for (String message : proximityMessages) {
                font.draw(batch, message, viewX + 10, messageY); 
                messageY -= 20;
            }
        }

        // Draw Game Over Message
        if (isGameOver) {
            if (gameOverMessage.contains("WIN")) {
                font.setColor(1.0f, 1.0f, 0.0f, 1.0f); // Gold/Yellow for win
            } else {
                font.setColor(1.0f, 0.0f, 0.0f, 1.0f); // Red for lose
            }

            font.draw(batch, gameOverMessage, viewX + 50, viewY - 50); 
        } 
        
        // Draw PAUSE MENU Overlay
        if (isPaused) {
            // Draw a semi-transparent black overlay (simulated with text for simplicity)
            font.setColor(0f, 0f, 0f, 0.7f);
            font.getData().setScale(2f);
            font.draw(batch, "                                                                                                                                                                                                                                               ", viewX, viewY);
            font.getData().setScale(1f);

            font.setColor(Color.WHITE);

            float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
            float menuCenterY = viewY - viewport.getWorldHeight() / 2f;

            font.draw(batch, "PAUSED", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET + 30);
            
            // Menu Options (clickable text)
            font.draw(batch, "New Game", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET);
            font.draw(batch, "Save Game", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET - 30);
            font.draw(batch, "Load Game", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET - 60);
            font.draw(batch, "Quit to Menu", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET - 90);
        }

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        fogOfWarTexture.dispose(); // DISPOSE FOG TEXTURE
        
        // Dispose textures for all static entities
        for (StaticEntity entity : traps) {
            entity.dispose();
        }
        for (StaticEntity entity : obstacles) {
            entity.dispose();
        }
        for (StaticEntity entity : treasure) {
            entity.dispose();
        }
        
        player.dispose();
        wumpus.dispose();
        pebbleTexture.dispose();
    }
}