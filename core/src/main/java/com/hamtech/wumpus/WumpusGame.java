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
    
    // Entity lists
    private List<StaticEntity> traps;
    private List<StaticEntity> obstacles;
    private List<StaticEntity> treasure;
    
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Texture pebbleTexture;
    private Texture fogOfWarTexture;
    
    // GAME STATE VARIABLES
    private BitmapFont font;
    private String gameOverMessage = "";
    private List<String> proximityMessages;
    private boolean[][] isVisible;
    
    // APPLICATION FLOW STATES
    private final int STATE_MAIN_MENU = 0;
    private final int STATE_GAMEPLAY = 1;
    private final int STATE_PAUSED = 2;
    private final int STATE_GAME_OVER = 3;
    private int currentState = STATE_MAIN_MENU; // Starts on Main Menu
    
    // MENU SPECIFIC VARIABLES
    private int menuSelection = 0; // 0: New Game, 1: Load Game, 2: Quit
    private Rectangle menuButtonBounds; // <-- CORRECTED: FIELD DECLARATION RE-ADDED
    
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

        // Load non-game-specific textures/fonts
        pebbleTexture = new Texture("pebble_brown0.png"); 
        fogOfWarTexture = new Texture("FogofWarSingle.png"); 
        font = new BitmapFont(); 
        font.getData().setScale(1.0f); 
        
        // Initialize menu button bounds
        float menuX = viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
        float menuY = viewport.getWorldHeight() - MENU_BUTTON_HEIGHT - 10;
        menuButtonBounds = new Rectangle(menuX, menuY, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);

        Gdx.input.setInputProcessor(this);
    }
    
    /** Initializes all game entities and resets state for a new game. */
    private void startGame() {
        // Dispose of existing entities if they exist
        if (player != null) player.dispose();
        if (wumpus != null) wumpus.dispose();
        if (traps != null) for (StaticEntity entity : traps) entity.dispose();
        if (obstacles != null) for (StaticEntity entity : obstacles) entity.dispose();
        if (treasure != null) for (StaticEntity entity : treasure) entity.dispose();

        // Reset lists and state
        traps = new ArrayList<>();
        obstacles = new ArrayList<>();
        treasure = new ArrayList<>();
        proximityMessages = new ArrayList<>();
        gameOverMessage = "";
        currentState = STATE_GAMEPLAY;
        
        player = new Player("donald.png", 0, 0); 
        
        // Initialize FoW and set starting tile visible
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];
        isVisible[player.getGridX()][player.getGridY()] = true;
        
        // Entity placement logic
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
        Gdx.app.log("WUMPUS_GAME", "Game Started.");
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
        // Main Menu Navigation
        if (currentState == STATE_MAIN_MENU) {
            if (keycode == Keys.UP) {
                menuSelection = (menuSelection - 1 + 3) % 3; // Cycle 0-2 (New, Load, Quit)
                return true;
            } else if (keycode == Keys.DOWN) {
                menuSelection = (menuSelection + 1) % 3; // Cycle 0-2
                return true;
            } else if (keycode == Keys.ENTER) {
                handleMainMenuSelection();
                return true;
            }
        }
        
        // Block movement if paused or game over
        if (currentState != STATE_GAMEPLAY) {
            return false;
        }
        
        // In-game movement logic
        int dx = 0, dy = 0;
        if (keycode == Keys.LEFT) {
            dx = -1;
        } else if (keycode == Keys.RIGHT) {
            dx = 1;
        } else if (keycode == Keys.UP) {
            dy = 1;
        } else if (keycode == Keys.DOWN) {
            dy = -1;
        }
        
        if (dx != 0 || dy != 0) {
            int newX = player.getGridX() + dx;
            int newY = player.getGridY() + dy;

            if (isTileOccupiedByObstacle(newX, newY)) {
                return true; 
            }
            
            player.move(dx, dy);

            isVisible[player.getGridX()][player.getGridY()] = true;

            centerCameraOnPlayer();
            checkGameInteraction();
        }

        return true;
    }

    private void handleMainMenuSelection() {
        if (menuSelection == 0) { // New Game
            startGame();
        } else if (menuSelection == 1) { // Load Game
            // Placeholder: Switch state to gameplay for testing if load was successful
            Gdx.app.log("WUMPUS_GAME", "Load Game (Not implemented)");
        } else if (menuSelection == 2) { // Quit
            Gdx.app.exit();
        }
    }


    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        // Unproject screen coordinates to world coordinates
        float worldX = camera.position.x - viewport.getWorldWidth() / 2f + screenX;
        float worldY = camera.position.y + viewport.getWorldHeight() / 2f - screenY;

        // Base coordinates for UI elements relative to the viewport
        float viewX = camera.position.x - viewport.getWorldWidth() / 2f;
        float viewY = camera.position.y + viewport.getWorldHeight() / 2f;
        float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
        float menuCenterY = viewY - viewport.getWorldHeight() / 2f;
        float optionX = menuCenterX - 50; 
        float optionWidth = 200; 

        // 1. MAIN MENU Clicks
        if (currentState == STATE_MAIN_MENU) {
            // New Game
            float newGameY = menuCenterY + MENU_START_Y_OFFSET - 80;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > newGameY - 20 && worldY < newGameY + 5) {
                menuSelection = 0;
                handleMainMenuSelection();
                return true;
            }

            // Load Game
            float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 110;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > loadGameY - 20 && worldY < loadGameY + 5) {
                menuSelection = 1;
                handleMainMenuSelection();
                return true;
            }
            
            // Quit
            float quitY = menuCenterY + MENU_START_Y_OFFSET - 140;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > quitY - 20 && worldY < quitY + 5) {
                menuSelection = 2;
                handleMainMenuSelection();
                return true;
            }
            return false;
        }


        // 2. PAUSE/MENU Button Logic (Active during gameplay or game over)
        if (currentState == STATE_GAMEPLAY || currentState == STATE_GAME_OVER) { // <-- MODIFIED CONDITION
            // Check [MENU] button click area
            float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
            float btnY = viewY - MENU_BUTTON_HEIGHT - 10;
            Rectangle currentMenuButtonBounds = new Rectangle(btnX, btnY, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);
            
            if (currentMenuButtonBounds.contains(worldX, worldY)) {
                currentState = STATE_PAUSED;
                Gdx.app.log("WUMPUS_GAME", "Menu Toggled: Paused");
                return true;
            }
        }
        
        // 3. PAUSE MENU Option Clicks (Only active when paused)
        if (currentState == STATE_PAUSED) {
            // New Game
            float newGameY = menuCenterY + MENU_START_Y_OFFSET;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > newGameY - 20 && worldY < newGameY + 5) {
                startGame();
                return true;
            }

            // Save Game
            float saveGameY = menuCenterY + MENU_START_Y_OFFSET - 30;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > saveGameY - 20 && worldY < saveGameY + 5) {
                currentState = STATE_GAMEPLAY; // Close menu after 'saving'
                Gdx.app.log("WUMPUS_GAME", "Save Game (Not fully implemented)");
                return true;
            }
            
            // Load Game
            float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 60;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > loadGameY - 20 && worldY < loadGameY + 5) {
                currentState = STATE_GAMEPLAY; // Close menu after 'loading'
                Gdx.app.log("WUMPUS_GAME", "Load Game (Not fully implemented)");
                return true;
            }
            
            // Quit to Menu (Return to Main Menu state)
            float quitY = menuCenterY + MENU_START_Y_OFFSET - 90;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > quitY - 20 && worldY < quitY + 5) {
                currentState = STATE_MAIN_MENU;
                Gdx.app.log("WUMPUS_GAME", "Quit to Main Menu");
                return true;
            }
        }
        return false;
    }
    
    private void checkGameInteraction() {
        proximityMessages.clear();
        
        if (currentState == STATE_GAME_OVER) return;
        
        int playerX = player.getGridX();
        int playerY = player.getGridY();

        // --- CHECK FOR IMMEDIATE COLLISIONS (Game Over/Win) ---
        
        StaticEntity gold = treasure.get(0);
        if (playerX == gold.getGridX() && playerY == gold.getGridY()) {
            currentState = STATE_GAME_OVER;
            gameOverMessage = "YOU FOUND THE TREASURE! YOU WIN! 🎉";
            return;
        }
        
        for (StaticEntity trap : traps) {
            if (playerX == trap.getGridX() && playerY == trap.getGridY()) {
                currentState = STATE_GAME_OVER;
                gameOverMessage = "BOOM! You stepped on a trap. Game Over! 💀";
                return;
            }
        }

        if (playerX == wumpus.getGridX() && playerY == wumpus.getGridY()) {
            currentState = STATE_GAME_OVER;
            gameOverMessage = "A ROAR! The Wumpus devoured you! Game Over! 😵";
            return;
        }

        // --- CHECK FOR PROXIMITY EFFECTS ---
        
        if (isAdjacent(playerX, playerY, gold.getGridX(), gold.getGridY())) {
            proximityMessages.add("A glow is visible");
        }
        if (isAdjacent(playerX, playerY, wumpus.getGridX(), wumpus.getGridY())) {
            proximityMessages.add("You can smell a stench");
        }
        for (StaticEntity trap : traps) {
            if (isAdjacent(playerX, playerY, trap.getGridX(), trap.getGridY())) {
                proximityMessages.add("You feel a breeze");
            }
        }
    }

    private void centerCameraOnPlayer() {
        if (player == null) return;
        
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

        // Calculate coordinates relative to the screen view
        float viewX = camera.position.x - viewport.getWorldWidth() / 2f;
        float viewY = camera.position.y + viewport.getWorldHeight() / 2f;


        // --- STATE: MAIN MENU ---
        if (currentState == STATE_MAIN_MENU) {
            font.setColor(Color.WHITE);
            float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
            float menuCenterY = viewY - viewport.getWorldHeight() / 2f;

            // WUMPUS Title
            font.getData().setScale(3f);
            font.draw(batch, "WUMPUS", menuCenterX - 120, menuCenterY + 180);
            font.getData().setScale(1.5f);
            
            // Menu Options
            String[] options = {"New Game", "Load Game", "Quit"};
            for(int i = 0; i < options.length; i++) {
                if (menuSelection == i) {
                    font.setColor(Color.YELLOW); // Highlight effect
                } else {
                    font.setColor(Color.WHITE);
                }
                
                // Quit is index 2, Load is 1, New is 0. Draw order: New, Load, Quit (descending Y)
                float optionY = menuCenterY + MENU_START_Y_OFFSET - 80 - (i * 30);
                font.draw(batch, options[i], menuCenterX - 50, optionY);
            }
            
            font.getData().setScale(1f); // Reset font scale
        } 
        
        // --- STATE: GAMEPLAY, PAUSED, GAME OVER ---
        else {
            boolean isGameOver = (currentState == STATE_GAME_OVER);
            
            // 1. Draw Grid and Entities (Only visible tiles or entire map if game over)
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int y = 0; y < GRID_SIZE; y++) {
                    float xPos = x * TILE_SIZE;
                    float yPos = y * TILE_SIZE;
                    
                    if (isGameOver || isVisible[x][y]) {
                        batch.draw(pebbleTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                        
                        if (wumpus != null && x == wumpus.getGridX() && y == wumpus.getGridY()) wumpus.draw(batch, TILE_SIZE);
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
                        batch.draw(pebbleTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                    }
                }
            }
            
            // 2. Draw Player
            player.draw(batch, TILE_SIZE);

            // 3. FOG OF WAR DRAWING
            if (!isGameOver) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    for (int y = 0; y < GRID_SIZE; y++) {
                        if (!isVisible[x][y]) {
                            float xPos = x * TILE_SIZE;
                            float yPos = y * TILE_SIZE;
                            batch.draw(fogOfWarTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                        }
                    }
                }
            }

            // 4. UI DRAWING (HUD, Messages, Pause Menu)

            // Draw Menu Button (Upper Right)
            if (currentState == STATE_GAMEPLAY || currentState == STATE_GAME_OVER) { // <-- MODIFIED CONDITION
                font.setColor(Color.WHITE);
                float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
                float btnY = viewY - 10;
                font.draw(batch, "[MENU]", btnX, btnY);
            }
            
            // Draw Proximity Messages (Top Left)
            if (currentState == STATE_GAMEPLAY && !proximityMessages.isEmpty()) {
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
            if (currentState == STATE_PAUSED) {
                // Draw a semi-transparent black overlay
                font.setColor(0f, 0f, 0f, 0.7f);
                font.getData().setScale(2f);
                font.draw(batch, "                                                                                                                                                                                                                                               ", viewX, viewY);
                font.getData().setScale(1f);

                font.setColor(Color.WHITE);
                float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
                float menuCenterY = viewY - viewport.getWorldHeight() / 2f;

                font.draw(batch, "PAUSED", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET + 30);
                
                font.draw(batch, "New Game", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET);
                font.draw(batch, "Save Game", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET - 30);
                font.draw(batch, "Load Game", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET - 60);
                font.draw(batch, "Quit to Menu", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET - 90);
            }
        }

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        fogOfWarTexture.dispose();
        
        // Dispose textures for all static entities (only if they were initialized)
        if (traps != null) for (StaticEntity entity : traps) entity.dispose();
        if (obstacles != null) for (StaticEntity entity : obstacles) entity.dispose();
        if (treasure != null) for (StaticEntity entity : treasure) entity.dispose();
        
        if (player != null) player.dispose();
        if (wumpus != null) wumpus.dispose();
        pebbleTexture.dispose();
    }
}