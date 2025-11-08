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
import java.util.Collections;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.MathUtils; 
import com.badlogic.gdx.files.FileHandle;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator; 

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
    private GlyphLayout layout;
    private String gameOverMessage = "";
    private List<String> proximityMessages; // ERROR FIX: Will now be initialized in create()
    private boolean[][] isVisible;
    
    private float gameTime = 0.0f; 

    // SAVE FILE CONSTANTS
    private final String SAVE_FOLDER = "wumpus_saves/";
    private final String SAVE_BASE_NAME = "save";
    private List<SaveMetadata> saveList; 
    private int loadSelection = 0; 
    
    // APPLICATION FLOW STATES
    private final int STATE_MAIN_MENU = 0;
    private final int STATE_GAMEPLAY = 1;
    private final int STATE_PAUSED = 2;
    private final int STATE_GAME_OVER = 3;
    private final int STATE_LOAD_MENU = 4;
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

    /** Inner class to hold the necessary data for displaying a save file in the load menu. */
    private static class SaveMetadata {
        String displayName;
        String dateAndTime;
        FileHandle file;

        public SaveMetadata(String name, String dateTime, FileHandle file) {
            this.displayName = name;
            this.dateAndTime = dateTime;
            this.file = file;
        }
    }
    
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
        layout = new GlyphLayout();
        saveList = new ArrayList<>(); // Initialize save list
        proximityMessages = new ArrayList<>(); // FIX: Initialize proximityMessages here

        
        // Initialize menu button bounds
        float menuX = viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
        float menuY = viewport.getWorldHeight() - MENU_BUTTON_HEIGHT - 10;
        menuButtonBounds = new Rectangle(menuX, menuY, MENU_BUTTON_WIDTH, MENU_BUTTON_HEIGHT);

        Gdx.input.setInputProcessor(this);
    }

    /** Finds all existing save files, extracts metadata, and populates the saveList. */
    private void refreshSaveList() {
        FileHandle dir = Gdx.files.local(SAVE_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
            saveList.clear();
            return;
        }

        FileHandle[] files = dir.list((file, name) -> name.endsWith(".txt"));
        saveList.clear();

        for (FileHandle file : files) {
            try {
                // Read the first few lines to get metadata
                String content = file.readString();
                String[] lines = content.split("\n");
                
                String saveName = "Unknown Save";
                String saveDate = "Unknown Date";

                for (String line : lines) {
                    if (line.startsWith("SAVE_NAME=")) {
                        saveName = line.substring("SAVE_NAME=".length());
                    } else if (line.startsWith("SAVE_DATE=")) {
                        saveDate = line.substring("SAVE_DATE=".length());
                    }
                }
                
                if (!saveName.equals("Unknown Save")) {
                    saveList.add(new SaveMetadata(saveName, saveDate, file));
                }
                
            } catch (Exception e) {
                Gdx.app.error("SAVE_SCAN", "Error reading metadata from file: " + file.name() + " - " + e.getMessage());
            }
        }
        
        // Sort the list by filename (e.g., save1, save2, save3) to keep a consistent order
        Collections.sort(saveList, Comparator.comparing(meta -> meta.file.name()));
        loadSelection = 0; // Reset selection to the first item
    }


    /** Parses the content of a save file and loads the game state. */
    private void loadGame(FileHandle file) {
        Gdx.app.log("WUMPUS_LOAD", "Loading game from: " + file.path());
        
        // 1. Reset everything before loading
        if (player != null) player.dispose();
        if (wumpus != null) wumpus.dispose();
        traps = new ArrayList<>();
        obstacles = new ArrayList<>();
        treasure = new ArrayList<>();
        proximityMessages.clear(); // This line now works because it's initialized in create()
        gameOverMessage = ""; 
        isShootingMode = false;
        isLanceAnimating = false;
        
        // Initialize FoW structure
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];

        try {
            String content = file.readString();
            String[] lines = content.split("\n");
            
            // Temporary variables for construction
            int pX = 0, pY = 0;
            int wX = -1, wY = -1; // Default to non-existent
            
            for (String line : lines) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("=", 2);
                if (parts.length < 2) continue;
                String key = parts[0].trim();
                String value = parts[1].trim();
                
                switch (key) {
                    case "GAME_TIME":
                        gameTime = Float.parseFloat(value);
                        break;
                    case "PLAYER_POS":
                        String[] pos = value.split(",");
                        pX = Integer.parseInt(pos[0]);
                        pY = Integer.parseInt(pos[1]);
                        break;
                    case "LANCE_STATUS":
                        hasLance = Boolean.parseBoolean(value);
                        break;
                    case "WUMPUS_ALIVE":
                        isWumpusAlive = Boolean.parseBoolean(value);
                        break;
                    case "WUMPUS_POS":
                        String[] wPos = value.split(",");
                        wX = Integer.parseInt(wPos[0]);
                        wY = Integer.parseInt(wPos[1]);
                        break;
                    case "TRAPS":
                        if (!value.isEmpty()) {
                            for (String trapPos : value.split("\\|")) {
                                String[] coords = trapPos.split(",");
                                traps.add(new StaticEntity("dngn_trap_arrow.png", Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
                            }
                        }
                        break;
                    case "OBSTACLES":
                        if (!value.isEmpty()) {
                            for (String obsPos : value.split("\\|")) {
                                String[] coords = obsPos.split(",");
                                obstacles.add(new StaticEntity("crumbled_column.png", Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
                            }
                        }
                        break;
                    case "TREASURE_POS":
                        String[] tPos = value.split(",");
                        treasure.add(new StaticEntity("gold_pile.png", Integer.parseInt(tPos[0]), Integer.parseInt(tPos[1])));
                        break;
                    case "FOG_OF_WAR":
                        // FOG_OF_WAR=100100... (row by row, bottom-up)
                        for (int y = 0; y < GRID_SIZE; y++) {
                            for (int x = 0; x < GRID_SIZE; x++) {
                                int index = y * GRID_SIZE + x;
                                if (index < value.length()) {
                                    isVisible[x][y] = (value.charAt(index) == '1');
                                }
                            }
                        }
                        break;
                }
            }
            
            // 2. Initialize the main entities (must be done after reading positions)
            player = new Player("donald.png", pX, pY);
            if (wX != -1 && wY != -1) {
                wumpus = new Wumpus("sphinx.png", wX, wY);
            }

            // 3. Finalize and switch state
            centerCameraOnPlayer();
            checkGameInteraction(); // Check proximity for messages
            currentState = STATE_GAMEPLAY;
            proximityMessages.add("Game loaded successfully!");

        } catch (Exception e) {
            Gdx.app.error("WUMPUS_LOAD", "Failed to load game: " + e.getMessage());
            currentState = STATE_MAIN_MENU; // Go back to main menu on critical error
            proximityMessages.clear();
            proximityMessages.add("Error loading save file!");
        }
    }


    /** Finds all existing save files and returns the next incremental save name (e.g., "save3.txt"). */
    private String getNextSaveName() {
        FileHandle dir = Gdx.files.local(SAVE_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs(); // Create the directory if it doesn't exist
        }

        // List files that start with the base name and end with ".txt"
        FileHandle[] files = dir.list((file, name) -> name.startsWith(SAVE_BASE_NAME) && name.endsWith(".txt"));

        int maxNum = 0;
        for (FileHandle file : files) {
            String name = file.nameWithoutExtension();
            try {
                // Extract the number part: "save1" -> "1"
                String numStr = name.substring(SAVE_BASE_NAME.length());
                int num = Integer.parseInt(numStr);
                if (num > maxNum) {
                    maxNum = num;
                }
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                // Ignore files that don't match the saveN format
                Gdx.app.log("SAVE_FILE_SCAN", "Ignoring malformed save file: " + file.name());
            }
        }
        
        return SAVE_BASE_NAME + (maxNum + 1) + ".txt";
    }

    /** Saves the current game state to a file. */
    private void saveGame(String requestedName) {
        String fileName = requestedName.isEmpty() ? getNextSaveName() : requestedName.replaceAll("[^a-zA-Z0-9_-]", "") + ".txt";
        FileHandle file = Gdx.files.local(SAVE_FOLDER + fileName);
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        
        StringBuilder saveContent = new StringBuilder();
        
        // METADATA
        String saveDisplayName = requestedName.isEmpty() ? fileName.substring(0, fileName.length() - 4) : requestedName;
        saveContent.append("SAVE_NAME=").append(saveDisplayName).append("\n");
        saveContent.append("SAVE_DATE=").append(dtf.format(now)).append("\n");
        saveContent.append("GAME_TIME=").append(gameTime).append("\n");
        
        // PLAYER STATE
        saveContent.append("PLAYER_POS=").append(player.getGridX()).append(",").append(player.getGridY()).append("\n");
        saveContent.append("LANCE_STATUS=").append(hasLance).append("\n");
        
        // WUMPUS STATE
        saveContent.append("WUMPUS_ALIVE=").append(isWumpusAlive).append("\n");
        if (wumpus != null) {
            saveContent.append("WUMPUS_POS=").append(wumpus.getGridX()).append(",").append(wumpus.getGridY()).append("\n");
        } else {
             saveContent.append("WUMPUS_POS=-1,-1\n");
        }
        
        // ENTITIES (Traps, Obstacles, Treasure)
        
        // Traps
        saveContent.append("TRAPS=");
        for (StaticEntity trap : traps) {
            saveContent.append(trap.getGridX()).append(",").append(trap.getGridY()).append("|");
        }
        if (traps.size() > 0) saveContent.setLength(saveContent.length() - 1); // Remove trailing '|'
        saveContent.append("\n");

        // Obstacles
        saveContent.append("OBSTACLES=");
        for (StaticEntity obstacle : obstacles) {
            saveContent.append(obstacle.getGridX()).append(",").append(obstacle.getGridY()).append("|");
        }
        if (obstacles.size() > 0) saveContent.setLength(saveContent.length() - 1); // Remove trailing '|'
        saveContent.append("\n");
        
        // Treasure (Gold) - assuming there's only one
        if (!treasure.isEmpty()) {
            StaticEntity gold = treasure.get(0);
            saveContent.append("TREASURE_POS=").append(gold.getGridX()).append(",").append(gold.getGridY()).append("\n");
        }
        
        // FOG OF WAR (isVisible) - Format: FoW=100100... (row by row, bottom-up)
        saveContent.append("FOG_OF_WAR=");
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                saveContent.append(isVisible[x][y] ? '1' : '0');
            }
        }
        saveContent.append("\n");

        // Write to file
        try (Writer writer = file.writer(false)) { // false means not append
            writer.write(saveContent.toString());
            Gdx.app.log("WUMPUS_SAVE", "Game successfully saved to: " + file.path());
            proximityMessages.clear();
            proximityMessages.add("Game saved as: " + saveDisplayName);
        } catch (IOException e) {
            Gdx.app.error("WUMPUS_SAVE", "Error writing save file: " + e.getMessage());
            proximityMessages.clear();
            proximityMessages.add("Save failed! Check logs.");
        }
        
        // Reset shooting mode and unpause
        isShootingMode = false;
        currentState = STATE_GAMEPLAY; 
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
        // proximityMessages is already initialized in create(), just clear it
        proximityMessages.clear(); 
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
            if (isWumpusAlive && wumpus != null && targetX == wumpus.getGridX() && targetY == wumpus.getGridY()) {
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
        // --- LOAD MENU NAVIGATION ---
        if (currentState == STATE_LOAD_MENU) {
            if (keycode == Keys.UP) {
                loadSelection = Math.max(0, loadSelection - 1);
                return true;
            } else if (keycode == Keys.DOWN) {
                loadSelection = Math.min(saveList.size() - 1, loadSelection + 1);
                return true;
            } else if (keycode == Keys.ENTER && !saveList.isEmpty()) {
                loadGame(saveList.get(loadSelection).file);
                return true;
            } else if (keycode == Keys.ESCAPE) {
                // Go back to the main menu
                currentState = STATE_MAIN_MENU;
                return true;
            }
        }
        
        // --- MAIN MENU NAVIGATION ---
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
        
        // --- GAMEPLAY/SHOOTING LOGIC ---
        if (currentState == STATE_GAMEPLAY) {
            
            // 1. SPACE BAR: TOGGLE SHOOTING MODE
            if (keycode == Keys.SPACE) {
                if (isLanceAnimating) return true;
                
                proximityMessages.clear();
                if (hasLance) {
                    isShootingMode = !isShootingMode;
                    if (isShootingMode) {
                        proximityMessages.add("Lance equipped!");
                    }
                } else {
                    proximityMessages.add("Lance already used.");
                    isShootingMode = false;
                }
                return true;
            }

            // 2. SHOOTING ACTION
            if (isShootingMode) {
                int dx = 0, dy = 0;
                if (keycode == Keys.LEFT) dx = -1;
                else if (keycode == Keys.RIGHT) dx = 1;
                else if (keycode == Keys.UP) dy = 1;
                else if (keycode == Keys.DOWN) dy = -1;

                if (dx != 0 || dy != 0) {
                    if (isLanceAnimating) return true;

                    lanceStartX = player.getGridX();
                    lanceStartY = player.getGridY();
                    lanceEndX = lanceStartX + dx;
                    lanceEndY = lanceStartY + dy;

                    if (dx == 1) lanceRotation = 0f;
                    else if (dx == -1) lanceRotation = 180f;
                    else if (dy == 1) lanceRotation = 90f;
                    else if (dy == -1) lanceRotation = 270f;
                    
                    isLanceAnimating = true;
                    lanceAnimTimer = 0.0f;
                    showImpact = false;
                    proximityMessages.clear();
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
            refreshSaveList(); // Load the list of available saves
            currentState = STATE_LOAD_MENU; // Switch to the load menu state
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

        // 1. STATE: LOAD MENU Clicks
        if (currentState == STATE_LOAD_MENU) {
            float startY = menuCenterY + MENU_START_Y_OFFSET - 80;
            float optionHeight = 20;
            for(int i = 0; i < saveList.size(); i++) {
                float optionY = startY - (i * optionHeight * 1.5f); // 1.5f for spacing
                if (worldX > optionX && worldX < optionX + 300 && worldY > optionY - 15 && worldY < optionY + 5) {
                    loadGame(saveList.get(i).file);
                    return true;
                }
            }
            // Add a check to go back (Clicking near the [BACK] label)
            if (worldX > optionX && worldX < optionX + 100 && worldY < menuCenterY + 180 && worldY > menuCenterY + 150) {
                currentState = STATE_MAIN_MENU;
                return true;
            }
            return false;
        }


        // 2. STATE: MAIN MENU Clicks
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
                handleMainMenuSelection(); // Now switches to STATE_LOAD_MENU
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


        // 3. STATE: PAUSE/MENU Button Logic (Active during gameplay or game over)
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
        
        // 4. STATE: PAUSE MENU Option Clicks (Only active when paused)
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
                    saveGame(""); 
                }
                return true;
            }
            
            // Load Game (Shifted)
            float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 90;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > loadGameY - 20 && worldY < loadGameY + 5) {
                refreshSaveList(); // Load the list of available saves
                currentState = STATE_LOAD_MENU; // Switch to the load menu state
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
        
        if (treasure.isEmpty()) { 
             // Should not happen in a new game, but possible if loaded from a corrupt file
             proximityMessages.add("Error: Treasure not found!");
             return;
        }
        
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
        if (isWumpusAlive && wumpus != null && playerX == wumpus.getGridX() && playerY == wumpus.getGridY()) {
            currentState = STATE_GAME_OVER;
            gameOverMessage = "A ROAR! The Wumpus devoured you! Game Over! 😵";
            return;
        }

        // --- CHECK FOR PROXIMITY EFFECTS ---
        
        if (isAdjacent(playerX, playerY, gold.getGridX(), gold.getGridY())) {
            proximityMessages.add("A glow is visible");
        }
        
        // Wumpus Proximity - ONLY if Wumpus is alive
        if (isWumpusAlive && wumpus != null && isAdjacent(playerX, playerY, wumpus.getGridX(), wumpus.getGridY())) { 
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
        float menuCenterX = viewX + viewport.getWorldWidth() / 2f;
        float menuCenterY = viewY - viewport.getWorldHeight() / 2f;


        // --- STATE: MAIN MENU ---
        if (currentState == STATE_MAIN_MENU) {
            font.setColor(Color.WHITE);
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
                
                float optionY = menuCenterY + MENU_START_Y_OFFSET - 80 - (i * 30);
                font.draw(batch, options[i], menuCenterX - 50, optionY);
            }
            
            font.getData().setScale(1f); // Reset font scale
        } 
        
        // --- STATE: LOAD MENU ---
        else if (currentState == STATE_LOAD_MENU) {
            font.setColor(Color.WHITE);

            // Title
            font.getData().setScale(2f);
            font.draw(batch, "LOAD GAME", menuCenterX - 80, menuCenterY + 180);
            font.getData().setScale(1f);
            
            // Back Option
            font.draw(batch, "[BACK]", menuCenterX - 50, menuCenterY + 150);
            
            if (saveList.isEmpty()) {
                font.draw(batch, "No saved games found.", menuCenterX - 100, menuCenterY);
            } else {
                float startY = menuCenterY + MENU_START_Y_OFFSET - 80;
                float optionHeight = 20;
                
                // Draw the list of saves
                for (int i = 0; i < saveList.size(); i++) {
                    SaveMetadata metadata = saveList.get(i);
                    float optionY = startY - (i * optionHeight * 1.5f);
                    
                    if (loadSelection == i) {
                        font.setColor(Color.YELLOW); // Highlight
                    } else {
                        font.setColor(Color.WHITE);
                    }
                    
                    String displayString = String.format("%-15s - %s", metadata.displayName, metadata.dateAndTime);
                    font.draw(batch, displayString, menuCenterX - 150, optionY);
                }
            }
        }
        
        // --- STATE: GAMEPLAY, PAUSED, GAME OVER ---
        else {
            boolean isGameOver = (currentState == STATE_GAME_OVER);
            
            // 1. Draw Grid and Entities
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int y = 0; y < GRID_SIZE; y++) {
                    float xPos = x * TILE_SIZE;
                    float yPos = y * TILE_SIZE;
                    
                    if (isGameOver || isVisible[x][y]) {
                        batch.draw(pebbleTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
                        
                        if (wumpus != null && x == wumpus.getGridX() && y == wumpus.getGridY()) {
                            if (isWumpusAlive) {
                                wumpus.draw(batch, TILE_SIZE);
                            } else {
                                batch.draw(wumpusDeadTexture, xPos, yPos, TILE_SIZE, TILE_SIZE); 
                            }
                        }
                        
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
            if (player != null) {
                player.draw(batch, TILE_SIZE);
            }
            
            // 3. Draw Lance Animation
            if (isLanceAnimating) {
                float t = lanceAnimTimer / LANCE_ANIM_DURATION;
                
                float startPixelX = lanceStartX * TILE_SIZE + (TILE_SIZE / 2f);
                float startPixelY = lanceStartY * TILE_SIZE + (TILE_SIZE / 2f);
                float endPixelX = lanceEndX * TILE_SIZE + (TILE_SIZE / 2f);
                float endPixelY = lanceEndY * TILE_SIZE + (TILE_SIZE / 2f);
                
                float currentPixelX = MathUtils.lerp(startPixelX, endPixelX, t);
                float currentPixelY = MathUtils.lerp(startPixelY, endPixelY, t);
                
                batch.draw(
                    lanceTexture, 
                    currentPixelX - (TILE_SIZE / 2f), 
                    currentPixelY - (TILE_SIZE / 2f), 
                    TILE_SIZE / 2f, 
                    TILE_SIZE / 2f, 
                    TILE_SIZE, 
                    TILE_SIZE, 
                    1f, 1f, 
                    lanceRotation, 
                    0, 0, 
                    lanceTexture.getWidth(), 
                    lanceTexture.getHeight(), 
                    false, false
                );
            }
            
            // 4. Draw Impact Graphic (briefly)
            if (showImpact && impactTimer > 0) {
                 float impactX = lanceEndX * TILE_SIZE;
                 float impactY = lanceEndY * TILE_SIZE;
                 batch.draw(impactTexture, impactX, impactY, TILE_SIZE, TILE_SIZE);
            }

            // 5. FOG OF WAR DRAWING
            if (!isGameOver && !isLanceAnimating) {
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

            // 6. UI DRAWING
            font.setColor(Color.WHITE);
            String timerDisplay = "Time: " + formatTime(gameTime);
            layout.setText(font, timerDisplay);
            float textWidth = layout.width;
            float timerX = viewX + (viewport.getWorldWidth() / 2f) - (textWidth / 2f);
            font.draw(batch, timerDisplay, timerX, viewY - 10);

            // Draw Menu Button (Upper Right)
            if (currentState == STATE_GAMEPLAY || currentState == STATE_GAME_OVER) { 
                font.setColor(Color.WHITE);
                float btnX = viewX + viewport.getWorldWidth() - MENU_BUTTON_WIDTH - 10;
                float btnY = viewY - 30; 
                font.draw(batch, "[MENU]", btnX, btnY);
            }
            
            // Draw Proximity Messages (Top Left)
            if (currentState == STATE_GAMEPLAY && !proximityMessages.isEmpty()) {
                float messageY = viewY - 30; 
                font.setColor(0.5f, 1.0f, 0.5f, 1.0f);
                for (String message : proximityMessages) {
                    font.draw(batch, message, viewX + 10, messageY); 
                    messageY -= 20;
                }
            }

            // Draw Game Over Message
            if (isGameOver) {
                if (gameOverMessage.contains("WIN")) {
                    font.setColor(1.0f, 1.0f, 0.0f, 1.0f);
                } else {
                    font.setColor(1.0f, 0.0f, 0.0f, 1.0f);
                }
                font.draw(batch, gameOverMessage, viewX + 50, viewY - 50); 
            } 
            
            // Draw PAUSE MENU Overlay
            if (currentState == STATE_PAUSED) {
                font.setColor(0f, 0f, 0f, 0.7f);
                font.getData().setScale(2f);
                font.draw(batch, "                                                                                                                                                                                                                                               ", viewX, viewY);
                font.getData().setScale(1f);

                boolean isGameOverFinal = !gameOverMessage.isEmpty();

                font.setColor(Color.WHITE);
                font.draw(batch, "PAUSED", menuCenterX - 50, menuCenterY + MENU_START_Y_OFFSET + 30);
                
                float resumeGameY = menuCenterY + MENU_START_Y_OFFSET;
                if (isGameOverFinal) {
                    font.setColor(Color.GRAY);
                } else {
                    font.setColor(Color.WHITE);
                }
                font.draw(batch, "Resume", menuCenterX - 50, resumeGameY);
                
                float newGameY = menuCenterY + MENU_START_Y_OFFSET - 30;
                font.setColor(Color.WHITE);
                font.draw(batch, "New Game", menuCenterX - 50, newGameY);
                
                float saveGameY = menuCenterY + MENU_START_Y_OFFSET - 60;
                if (isGameOverFinal) {
                    font.setColor(Color.GRAY);
                } else {
                    font.setColor(Color.WHITE);
                }
                font.draw(batch, "Save Game", menuCenterX - 50, saveGameY);
                
                float loadGameY = menuCenterY + MENU_START_Y_OFFSET - 90;
                font.setColor(Color.WHITE); 
                font.draw(batch, "Load Game", menuCenterX - 50, loadGameY);
                
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
        if(fogOfWarTexture != null) fogOfWarTexture.dispose();
        if(wumpusDeadTexture != null) wumpusDeadTexture.dispose(); 
        if(lanceTexture != null) lanceTexture.dispose(); 
        if(impactTexture != null) impactTexture.dispose(); 
        
        if (traps != null) for (StaticEntity entity : traps) entity.dispose();
        if (obstacles != null) for (StaticEntity entity : obstacles) entity.dispose();
        if (treasure != null) for (StaticEntity entity : treasure) entity.dispose();
        
        if (player != null) player.dispose();
        if (wumpus != null) wumpus.dispose();
        if(pebbleTexture != null) pebbleTexture.dispose();
    }
}