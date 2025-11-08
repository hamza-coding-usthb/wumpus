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
import com.badlogic.gdx.graphics.g2d.GlyphLayout; // NEW: Added for text width measurement
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.MathUtils; 

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
    private Texture wumpusDeadTexture;
    private Texture lanceTexture;   
    private Texture impactTexture;  
    
    // GAME STATE VARIABLES
    private BitmapFont font;
    private GlyphLayout layout; // NEW: Used for calculating text width
    private String gameOverMessage = "";
    private List<String> proximityMessages;
    private boolean[][] isVisible;
    
    private float gameTime = 0.0f; 

    
    // APPLICATION FLOW STATES
    private final int STATE_MAIN_MENU = 0;
    private final int STATE_GAMEPLAY = 1;
    private final int STATE_PAUSED = 2;
    private final int STATE_GAME_OVER = 3;
    private int currentState = STATE_MAIN_MENU; 
    
    // LANCE/SHOOTING/ANIMATION VARIABLES
    private boolean hasLance = true;
    private boolean isShootingMode = false;
    private boolean isWumpusAlive = true;
    
    private boolean isLanceAnimating = false;      
    private float lanceAnimTimer = 0.0f;           
    private final float LANCE_ANIM_DURATION = 0.2f; 
    private int lanceStartX, lanceStartY;          
    private int lanceEndX, lanceEndY;              
    private float lanceRotation = 0f;              
    private boolean showImpact = false;            
    private float impactTimer = 0.0f;              
    private final float IMPACT_DURATION = 0.1f;    

    
    // MENU SPECIFIC VARIABLES
    private int menuSelection = 0; 
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

        // Load non-game-specific textures/fonts
        pebbleTexture = new Texture("pebble_brown0.png"); 
        fogOfWarTexture = new Texture("FogofWarSingle.png"); 
        wumpusDeadTexture = new Texture("brown_ooze.png"); 
        lanceTexture = new Texture("javelin2.png");   
        impactTexture = new Texture("bolt06.png");    
        font = new BitmapFont(); 
        font.getData().setScale(1.0f); 
        layout = new GlyphLayout(); // NEW: Initialize the GlyphLayout

        
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
        gameTime = 0.0f; 
        
        // RESET LANCE AND WUMPUS STATE
        hasLance = true; 
        isShootingMode = false; 
        isWumpusAlive = true; 
        isLanceAnimating = false; 
        showImpact = false;       
        
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

    /** Resolves the outcome of the lance shot after the animation finishes. */
    private void resolveLanceShot(int targetX, int targetY) {
        String shotMessage = "";
        
        if (targetX < 0 || targetX >= GRID_SIZE || targetY < 0 || targetY >= GRID_SIZE) {
            shotMessage = "Shot hit the cave wall.";
            showImpact = true;
            impactTimer = IMPACT_DURATION;
        } else {
            // Check for Wumpus hit
            if (isWumpusAlive && targetX == wumpus.getGridX() && targetY == wumpus.getGridY()) {
                isWumpusAlive = false;
                currentState = STATE_GAME_OVER;
                gameOverMessage = "WUMPUS SLAIN! YOU WIN! 🎉";
                shotMessage = "WUMPUS SLAIN!";
            } else {
                shotMessage = "Missed shot!";
                showImpact = true;
                impactTimer = IMPACT_DURATION;
            }
        }
        
        // Display message (Only if not already displaying WIN message)
        if (currentState != STATE_GAME_OVER) {
            proximityMessages.clear();
            proximityMessages.add(shotMessage);
        }
        
        // Final reset/consumption
        isLanceAnimating = false;
        hasLance = false; // Lance is always consumed upon shooting
        isShootingMode = false;
    }
    
    /** Updates game logic, including the lance animation timer and game timer. */
    private void update(float delta) {
        // Update game timer only if in active gameplay state
        if (currentState == STATE_GAMEPLAY) {
            gameTime += delta;
        }

        if (isLanceAnimating) {
            lanceAnimTimer += delta;
            
            if (lanceAnimTimer >= LANCE_ANIM_DURATION) {
                // Animation finished, resolve the shot
                resolveLanceShot(lanceEndX, lanceEndY);
                lanceAnimTimer = 0.0f; 
            }
        }
        
        if (showImpact) {
            impactTimer -= delta;
            if (impactTimer <= 0) {
                showImpact = false;
            }
        }
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
        
        // LANCE SHOOTING LOGIC (Only in gameplay state)
        if (currentState == STATE_GAMEPLAY) {
            
            // 1. SPACE BAR: TOGGLE SHOOTING MODE
            if (keycode == Keys.SPACE) {
                if (isLanceAnimating) return true; // Cannot toggle while shooting
                
                proximityMessages.clear(); // Clear other messages when toggling
                if (hasLance) {
                    isShootingMode = !isShootingMode; // Toggle mode
                    if (isShootingMode) {
                        proximityMessages.add("Lance equipped!");
                    }
                } else {
                    proximityMessages.add("Lance already used.");
                    isShootingMode = false; // Ensure mode is off if lance is used
                }
                return true;
            }

            // 2. SHOOTING ACTION (Directional key while in shoot mode)
            if (isShootingMode) {
                int dx = 0, dy = 0;
                if (keycode == Keys.LEFT) dx = -1;
                else if (keycode == Keys.RIGHT) dx = 1;
                else if (keycode == Keys.UP) dy = 1;
                else if (keycode == Keys.DOWN) dy = -1;

                if (dx != 0 || dy != 0) {
                    
                    if (isLanceAnimating) return true; // Should be blocked by space check, but safety check

                    // Set up animation coordinates
                    lanceStartX = player.getGridX();
                    lanceStartY = player.getGridY();
                    lanceEndX = lanceStartX + dx;
                    lanceEndY = lanceStartY + dy;

                    // Calculate rotation for the lance sprite
                    if (dx == 1) lanceRotation = 0f;
                    else if (dx == -1) lanceRotation = 180f;
                    else if (dy == 1) lanceRotation = 90f;
                    else if (dy == -1) lanceRotation = 270f;
                    
                    // Start animation
                    isLanceAnimating = true;
                    lanceAnimTimer = 0.0f;
                    showImpact = false;
                    
                    // Clear shooting message
                    proximityMessages.clear();
                    
                    // Exit shooting mode
                    isShootingMode = false; 
                    
                    return true;
                }
            }
        }
        
        // Block movement if paused, game over, OR while animation is playing
        if (currentState != STATE_GAMEPLAY || isLanceAnimating) {
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
        if (currentState == STATE_GAMEPLAY || currentState == STATE_GAME_OVER) { 
            // Check [MENU] button click area
            float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
            float btnY = viewY - MENU_BUTTON_HEIGHT - 30; // Adjusted for timer placement
            Rectangle currentMenuButtonBounds = new Rectangle(btnX, btnY, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);
            
            if (currentMenuButtonBounds.contains(worldX, worldY)) {
                currentState = STATE_PAUSED;
                Gdx.app.log("WUMPUS_GAME", "Menu Toggled: Paused");
                return true;
            }
        }
        
        // 3. PAUSE MENU Option Clicks (Only active when paused)
        if (currentState == STATE_PAUSED) {
            boolean isGameOver = !gameOverMessage.isEmpty();

            // Resume Game (Top item)
            float resumeGameY = menuCenterY + MENU_START_Y_OFFSET;
            if (!isGameOver && worldX > optionX && worldX < optionX + optionWidth && worldY > resumeGameY - 20 && worldY < resumeGameY + 5) {
                currentState = STATE_GAMEPLAY;
                return true;
            }

            // New Game (Shifted)
            float newGameY = menuCenterY + MENU_START_Y_OFFSET - 30;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > newGameY - 20 && worldY < newGameY + 5) {
                startGame();
                return true;
            }

            // Save Game (Shifted)
            float saveGameY = menuCenterY + MENU_START_Y_OFFSET - 60;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > saveGameY - 20 && worldY < saveGameY + 5) {
                // If game is over, prevent saving
                if (!isGameOver) { 
                    currentState = STATE_GAMEPLAY; // Close menu after 'saving'
                    Gdx.app.log("WUMPUS_GAME", "Save Game (Not fully implemented)");
                }
                return true;
            }
            
            // Load Game (Shifted)
            float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 90;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > loadGameY - 20 && worldY < loadGameY + 5) {
                currentState = STATE_GAMEPLAY; // Close menu after 'loading'
                Gdx.app.log("WUMPUS_GAME", "Load Game (Not fully implemented)");
                return true;
            }
            
            // Quit to Menu (Shifted)
            float quitY = menuCenterY + MENU_START_Y_OFFSET - 120;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > quitY - 20 && worldY < quitY + 5) {
                currentState = STATE_MAIN_MENU;
                Gdx.app.log("WUMPUS_GAME", "Quit to Main Menu");
                return true;
            }
        }
        return false;
    }
    
    private void checkGameInteraction() {
        // If we are waiting for a shot to resolve, don't update messages
        if (isLanceAnimating) return; 

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

        // Check for Wumpus (Lose Condition) - ONLY if Wumpus is alive
        if (isWumpusAlive && playerX == wumpus.getGridX() && playerY == wumpus.getGridY()) {
            currentState = STATE_GAME_OVER;
            gameOverMessage = "A ROAR! The Wumpus devoured you! Game Over! 😵";
            return;
        }

        // --- CHECK FOR PROXIMITY EFFECTS ---
        
        if (isAdjacent(playerX, playerY, gold.getGridX(), gold.getGridY())) {
            proximityMessages.add("A glow is visible");
        }
        
        // Wumpus Proximity - ONLY if Wumpus is alive
        if (isWumpusAlive && isAdjacent(playerX, playerY, wumpus.getGridX(), wumpus.getGridY())) { 
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
    
    /** Helper method to format time in seconds to MM:SS string. */
    private String formatTime(float totalTimeSeconds) {
        int totalSeconds = (int) totalTimeSeconds;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
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
        // 1. UPDATE GAME LOGIC
        update(Gdx.graphics.getDeltaTime());
        
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
                        
                        // Draw Wumpus (or blood if dead)
                        if (wumpus != null && x == wumpus.getGridX() && y == wumpus.getGridY()) {
                            if (isWumpusAlive) {
                                wumpus.draw(batch, TILE_SIZE);
                            } else {
                                // Draw blood if Wumpus is dead
                                batch.draw(wumpusDeadTexture, xPos, yPos, TILE_SIZE, TILE_SIZE); 
                            }
                        }
                        
                        // Draw other entities
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
            
            // 3. Draw Lance Animation
            if (isLanceAnimating) {
                float t = lanceAnimTimer / LANCE_ANIM_DURATION;
                
                // Interpolated value (from 0.0 to 1.0)
                float startPixelX = lanceStartX * TILE_SIZE + (TILE_SIZE / 2f);
                float startPixelY = lanceStartY * TILE_SIZE + (TILE_SIZE / 2f);
                float endPixelX = lanceEndX * TILE_SIZE + (TILE_SIZE / 2f);
                float endPixelY = lanceEndY * TILE_SIZE + (TILE_SIZE / 2f);
                
                // Calculate current interpolated position (center of tile to center of tile)
                float currentPixelX = MathUtils.lerp(startPixelX, endPixelX, t);
                float currentPixelY = MathUtils.lerp(startPixelY, endPixelY, t);
                
                // Draw Lance at interpolated position
                batch.draw(
                    lanceTexture, 
                    currentPixelX - (TILE_SIZE / 2f), // x position (adjusted for sprite origin)
                    currentPixelY - (TILE_SIZE / 2f), // y position (adjusted for sprite origin)
                    TILE_SIZE / 2f,                   // origin X (center of sprite for rotation)
                    TILE_SIZE / 2f,                   // origin Y
                    TILE_SIZE,                        // width
                    TILE_SIZE,                        // height
                    1f, 1f,                           // scale X, Y
                    lanceRotation,                    // rotation
                    0, 0,                             // srcX, srcY
                    lanceTexture.getWidth(),          // srcWidth
                    lanceTexture.getHeight(),         // srcHeight
                    false, false                      // flipX, flipY
                );
            }
            
            // 4. Draw Impact Graphic (briefly)
            if (showImpact && impactTimer > 0) {
                 float impactX = lanceEndX * TILE_SIZE;
                 float impactY = lanceEndY * TILE_SIZE;
                 batch.draw(impactTexture, impactX, impactY, TILE_SIZE, TILE_SIZE);
            }

            // 5. FOG OF WAR DRAWING
            if (!isGameOver && !isLanceAnimating) { // Hide FoW during the short animation
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

            // 6. UI DRAWING (HUD, Messages, Pause Menu)

            // Draw Timer (Top Center)
            font.setColor(Color.WHITE);
            String timerDisplay = "Time: " + formatTime(gameTime);
            
            // FIX: Use GlyphLayout to get the width
            layout.setText(font, timerDisplay);
            float textWidth = layout.width;
            
            float timerX = viewX + (viewport.getWorldWidth() / 2f) - (textWidth / 2f);
            font.draw(batch, timerDisplay, timerX, viewY - 10);

            // Draw Menu Button (Upper Right)
            if (currentState == STATE_GAMEPLAY || currentState == STATE_GAME_OVER) { 
                font.setColor(Color.WHITE);
                float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
                float btnY = viewY - 30; // Shifted down to avoid colliding with timer
                font.draw(batch, "[MENU]", btnX, btnY);
            }
            
            // Draw Proximity Messages (Top Left)
            if (currentState == STATE_GAMEPLAY && !proximityMessages.isEmpty()) {
                float messageY = viewY - 30; // Shifted down to avoid colliding with timer
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
                // Simple way to draw a full-screen semi-transparent rectangle using the font
                // This draws a large block of invisible characters
                font.draw(batch, "                                                                                                                                                                                                                                               ", viewX, viewY);
                font.getData().setScale(1f);

                float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
                float menuCenterY = viewY - viewport.getWorldHeight() / 2f;
                boolean isGameOverFinal = !gameOverMessage.isEmpty(); // Check if game is over

                font.setColor(Color.WHITE);
                font.draw(batch, "PAUSED", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET + 30);
                
                // Resume Game (Top item)
                float resumeGameY = menuCenterY + MENU_START_Y_OFFSET;
                if (isGameOverFinal) {
                    font.setColor(Color.GRAY);
                } else {
                    font.setColor(Color.WHITE);
                }
                font.draw(batch, "Resume", menuCenterX - 50, resumeGameY);
                
                // New Game (Shifted)
                float newGameY = menuCenterY + MENU_START_Y_OFFSET - 30;
                font.setColor(Color.WHITE); // Always white
                font.draw(batch, "New Game", menuCenterX - 50, newGameY);
                
                // Save Game (Shifted, Grayed out on Game Over)
                float saveGameY = menuCenterY + MENU_START_Y_OFFSET - 60;
                if (isGameOverFinal) {
                    font.setColor(Color.GRAY);
                } else {
                    font.setColor(Color.WHITE);
                }
                font.draw(batch, "Save Game", menuCenterX - 50, saveGameY);
                
                // Load Game (Shifted)
                float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 90;
                font.setColor(Color.WHITE); 
                font.draw(batch, "Load Game", menuCenterX - 50, loadGameY);
                
                // Quit to Menu (Shifted)
                float quitY = menuCenterY + MENU_START_Y_OFFSET - 120;
                font.draw(batch, "Quit to Menu", menuCenterX - 50, quitY);
            }
        }

        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        fogOfWarTexture.dispose();
        wumpusDeadTexture.dispose(); 
        lanceTexture.dispose(); 
        impactTexture.dispose(); 
        
        // Dispose textures for all static entities (only if they were initialized)
        if (traps != null) for (StaticEntity entity : traps) entity.dispose();
        if (obstacles != null) for (StaticEntity entity : obstacles) entity.dispose();
        if (treasure != null) for (StaticEntity entity : treasure) entity.dispose();
        
        if (player != null) player.dispose();
        if (wumpus != null) wumpus.dispose();
        pebbleTexture.dispose();
    }
}