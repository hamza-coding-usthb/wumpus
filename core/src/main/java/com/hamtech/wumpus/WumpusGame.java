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
    private List<String> proximityMessages;
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
    private final int STATE_NEW_GAME_MENU = 5; 
    private int currentState = STATE_MAIN_MENU; 
    
    // GAME MODE CONSTANTS
    private final int MODE_SINGLE_PLAYER = 0;
    private final int MODE_BOT_PLAYER = 1;
    
    // BOT PLAYER FIELDS
    private BotPlayer botPlayer; // Holds the Q-Table and logic
    private int currentMode = MODE_SINGLE_PLAYER; // Current game mode
    private float botMoveTimer = 0.0f; // Timer to pace bot moves
    private final float BOT_MOVE_DELAY = 0.15f; // Bot moves every 0.15 seconds (adjust for speed)
    private int botEpisodeCounter = 0; // Number of bot restarts
    
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

    /** Simple class to encapsulate the result of a move/interaction for the Q-Learning algorithm. */
    private static class InteractionResult {
        double reward;
        boolean isTerminal; // True if the state is Game Over (Win/Loss)

        public InteractionResult(double reward, boolean isTerminal) {
            this.reward = reward;
            this.isTerminal = isTerminal;
        }
    }

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
        saveList = new ArrayList<>(); 
        proximityMessages = new ArrayList<>(); 

        
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
        proximityMessages.clear(); 
        gameOverMessage = ""; 
        isShootingMode = false;
        isLanceAnimating = false;
        
        // Disable bot mode on load
        currentMode = MODE_SINGLE_PLAYER;
        botPlayer = null;
        
        // Initialize FoW structure
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];

        try {
            // ... (file reading and parsing logic remains the same) ...
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
    
    /** Generates a completely new set of Wumpus, Traps, Obstacles, and Treasure. */
    private void generateGridEntities() {
        // 1. Clear existing entities
        if (wumpus != null) wumpus.dispose();
        
        traps = new ArrayList<>();
        obstacles = new ArrayList<>();
        treasure = new ArrayList<>();
        
        // 2. Entity placement logic
        Random random = new Random();
        List<GridPoint2> occupiedPositions = new ArrayList<>();
        
        // Initial safe zone (0, 0) and neighbors
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

        Gdx.app.log("WUMPUS_GAME", "New Grid Generated.");
    }

    /** Resets the player state for a new bot training episode on the SAME grid. 
     * Called when the bot loses an episode.
     */
    private void resetBotEpisode() {
        // 1. Reset Bot Player position to (0, 0)
        // Move by the negative of its current position to ensure it lands at (0, 0)
        player.move(-player.getGridX(), -player.getGridY()); 

        // 2. Reset Game State
        proximityMessages.clear(); 
        gameOverMessage = ""; 
        currentState = STATE_GAMEPLAY;
        gameTime = 0.0f; // Reset timer for the episode
        
        // 3. Reset Items/Wumpus state (Crucial for same grid)
        hasLance = true; 
        isShootingMode = false; 
        isWumpusAlive = true; // Wumpus is revived for the next episode on the same grid
        isLanceAnimating = false; 
        showImpact = false;       
        
        // 4. Reset FoW
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];
        isVisible[player.getGridX()][player.getGridY()] = true;

        // 5. Update Bot Episode Counter and Exploration Rate
        botEpisodeCounter++;
        botPlayer.decayExplorationRate();
        
        centerCameraOnPlayer();
        checkGameInteraction(); // Check initial proximity
        
        proximityMessages.add(String.format("Bot Episode %d started. Eps: %.4f (Grid Kept)", botEpisodeCounter, botPlayer.getExplorationRate()));
        Gdx.app.log("WUMPUS_BOT", "Bot Episode Reset on SAME Grid.");
    }
    
    /** Initializes all game entities and resets state for a new game. 
     * The logic is split to allow for bot episode restarts without new grid generation.
     */
    private void startGame(int mode) {
        // Dispose of single-player entities if they exist and we are switching away
        if (player != null && currentMode == MODE_SINGLE_PLAYER && mode != MODE_SINGLE_PLAYER) {
             player.dispose();
        }

        // Determine if we need a NEW grid (i.e., not a bot episode restart/loss)
        // A new grid is needed if:
        // 1. We are changing modes (e.g., Bot to Single Player).
        // 2. We are starting Single Player mode.
        // 3. The entity lists haven't been initialized yet.
        boolean needNewGrid = (currentMode != mode) || (mode == MODE_SINGLE_PLAYER) || (traps == null || traps.isEmpty());

        // Reset state
        proximityMessages.clear(); 
        gameOverMessage = ""; 
        currentState = STATE_GAMEPLAY;
        gameTime = 0.0f; 
        currentMode = mode; 
        
        // RESET LANCE AND WUMPUS STATE
        hasLance = true; 
        isShootingMode = false; 
        isWumpusAlive = true; 
        isLanceAnimating = false; 
        showImpact = false;       
        
        // --- Player/Bot Instantiation ---
        if (currentMode == MODE_SINGLE_PLAYER) {
            player = new Player("donald.png", 0, 0); 
            botPlayer = null;
        } else { // MODE_BOT_PLAYER
            // Create BotPlayer once, reuse it to preserve Q-table
            if (botPlayer == null) {
                botPlayer = new BotPlayer("donald.png", 0, 0);
            }
            player = botPlayer; 
        }
        
        // --- Entity Placement/Grid Generation ---
        if (needNewGrid) {
            generateGridEntities();
        } 
        
        // Reset player position to (0, 0)
        player.move(-player.getGridX(), -player.getGridY());
        
        // Reset episode counter and display message if a NEW grid was generated for the bot
        if (currentMode == MODE_BOT_PLAYER) {
            if (needNewGrid) {
                botEpisodeCounter = 1;
                // The bot's exploration rate might be high if Q-table was newly initialized (i.e. botPlayer was null)
                proximityMessages.add(String.format("Bot Episode %d started. Eps: %.4f (NEW Grid)", botEpisodeCounter, botPlayer.getExplorationRate()));
            } else {
                // User-initiated restart on the current grid (not a loss restart)
                botEpisodeCounter = 1;
                proximityMessages.add(String.format("Bot Episode %d started. Eps: %.4f (Current Grid)", botEpisodeCounter, botPlayer.getExplorationRate()));
            }
        }

        // Initialize FoW and set starting tile visible
        isVisible = new boolean[GRID_SIZE][GRID_SIZE];
        isVisible[player.getGridX()][player.getGridY()] = true;

        centerCameraOnPlayer();
        checkGameInteraction(); // Check initial proximity
        Gdx.app.log("WUMPUS_GAME", "Game Started in Mode: " + mode);
    }
    
    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }
    
    // HELPER METHOD FOR OBSTACLE COLLISION
    private boolean isTileOccupiedByObstacle(int x, int y) {
        // Bounds check is handled in botPlayerTurn/keyDown
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

    /** Resolves the outcome of the lance shot after the animation finishes. (Only for human mode for now) */
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
    
    /** * Determines the outcome of the player/bot's move for Q-Learning reward.
     * This version runs AFTER the move has been executed.
     */
    private InteractionResult getInteractionResult(int playerX, int playerY) {
        
        // 1. Check for WIN Condition (Treasure)
        StaticEntity gold = treasure.get(0);
        if (playerX == gold.getGridX() && playerY == gold.getGridY()) {
            // Set the terminal game state message
            currentState = STATE_GAME_OVER;
            gameOverMessage = "BOT FOUND THE TREASURE! WIN! 🎉";
            return new InteractionResult(100.0, true); // HIGH POSITIVE REWARD
        }
        
        // 2. Check for LOSS Conditions (Traps/Wumpus)
        for (StaticEntity trap : traps) {
            if (playerX == trap.getGridX() && playerY == trap.getGridY()) {
                currentState = STATE_GAME_OVER;
                gameOverMessage = "BOT STEPPED ON A TRAP. Game Over! 💀";
                return new InteractionResult(-100.0, true); // HIGH NEGATIVE REWARD
            }
        }
        
        if (isWumpusAlive && wumpus != null && playerX == wumpus.getGridX() && playerY == wumpus.getGridY()) {
            currentState = STATE_GAME_OVER;
            gameOverMessage = "WUMPUS DEVOURED BOT! Game Over! 😵";
            return new InteractionResult(-100.0, true); // HIGH NEGATIVE REWARD
        }
        
        // 3. Default movement penalty
        return new InteractionResult(-1.0, false); // SMALL NEGATIVE REWARD (Movement cost)
    }

    /** Executes one turn for the bot player using Q-Learning. */
    private void botPlayerTurn() {
        if (botPlayer == null) return;
        
        // 1. Observe current state
        int oldX = player.getGridX();
        int oldY = player.getGridY();
        
        // 2. Choose action (epsilon-greedy)
        int action = botPlayer.chooseAction();
        int[] move = BotPlayer.actionToMovement(action);
        int dx = move[0];
        int dy = move[1];

        // 3. Execute action
        int targetX = oldX + dx;
        int targetY = oldY + dy;
        
        InteractionResult interaction;
        
        // Check for boundary collision first
        if (targetX < 0 || targetX >= GRID_SIZE || targetY < 0 || targetY >= GRID_SIZE) {
            // Wall collision gives a medium negative reward
            interaction = new InteractionResult(-10.0, false);
            targetX = oldX; // The state hasn't changed
            targetY = oldY;
        } 
        // Check for obstacle collision
        else if (isTileOccupiedByObstacle(targetX, targetY)) {
            // Obstacle collision gives a medium negative reward
            interaction = new InteractionResult(-5.0, false); 
            targetX = oldX; // The state hasn't changed
            targetY = oldY;
        } 
        // Valid move
        else {
            player.move(dx, dy); // Update position
            isVisible[player.getGridX()][player.getGridY()] = true;
            
            // Get reward and check for terminal state (Win/Loss)
            interaction = getInteractionResult(player.getGridX(), player.getGridY());
            centerCameraOnPlayer(); 
            
            // Overwrite proximity messages with learning status
            proximityMessages.clear();
            if (!interaction.isTerminal) {
                proximityMessages.add(String.format("Ep: %d | Eps: %.4f | Move: %s", botEpisodeCounter, botPlayer.getExplorationRate(), getActionName(action)));
            }
        }
        
        // 4. Update Q-Table
        botPlayer.updateQTable(oldX, oldY, action, interaction.reward, targetX, targetY);
        
        // 5. Check for terminal state and restart if necessary
        if (interaction.isTerminal) {
            if (gameOverMessage.contains("WIN")) {
                // If the bot wins, training stops and the win message is displayed.
                Gdx.app.log("BOT_TRAINING", "Bot WON in " + botEpisodeCounter + " episodes!");
                proximityMessages.add("BOT WON! Total Episodes: " + botEpisodeCounter);
                // currentState remains STATE_GAME_OVER
            } else {
                // If bot loses (Wumpus/Trap), restart immediately on the SAME grid
                Gdx.app.log("BOT_TRAINING", "Bot LOST. Restarting episode " + botEpisodeCounter);
                resetBotEpisode(); 
            }
        }
    }

    private String getActionName(int action) {
        switch(action) {
            case BotPlayer.ACTION_UP: return "UP";
            case BotPlayer.ACTION_DOWN: return "DOWN";
            case BotPlayer.ACTION_LEFT: return "LEFT";
            case BotPlayer.ACTION_RIGHT: return "RIGHT";
            default: return "WAIT";
        }
    }


    /** Updates game logic, including the lance animation timer and game timer. */
    private void update(float delta) {
        // Update game timer only if in active gameplay state
        if (currentState == STATE_GAMEPLAY) {
            gameTime += delta;
            
            // --- BOT PLAYER LOGIC ---
            if (currentMode == MODE_BOT_PLAYER && !isLanceAnimating) {
                // Throttle bot movement
                botMoveTimer += delta;
                if (botMoveTimer >= BOT_MOVE_DELAY) {
                    botMoveTimer = 0.0f;
                    botPlayerTurn();
                }
            }
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
        // ... (existing navigation logic for LOAD_MENU, NEW_GAME_MENU, MAIN_MENU) ...
        
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
        
        // --- NEW GAME MENU NAVIGATION ---
        if (currentState == STATE_NEW_GAME_MENU) {
            if (keycode == Keys.UP) {
                menuSelection = (menuSelection - 1 + 3) % 3; // Cycle 0-2 (Single, Bot, Return)
                return true;
            } else if (keycode == Keys.DOWN) {
                menuSelection = (menuSelection + 1) % 3; // Cycle 0-2
                return true;
            } else if (keycode == Keys.ENTER) {
                handleNewGameSelection(menuSelection);
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
        
        // --- GAMEPLAY/SHOOTING LOGIC (Only for Single Player) ---
        if (currentState == STATE_GAMEPLAY && currentMode == MODE_SINGLE_PLAYER) {
            
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
        
        // Block movement if paused, game over, OR while animation is playing, OR in BOT mode
        if (currentState != STATE_GAMEPLAY || isLanceAnimating || currentMode == MODE_BOT_PLAYER) {
            return false;
        }
        
        // In-game movement logic (Single Player)
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

            // Check boundaries
            if (newX < 0 || newX >= GRID_SIZE || newY < 0 || newY >= GRID_SIZE) {
                return true; // Wall collision: block move
            }
            // Check obstacle collision
            if (isTileOccupiedByObstacle(newX, newY)) {
                return true; // Obstacle collision: block move
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
            currentState = STATE_NEW_GAME_MENU;
            menuSelection = 0; 
        } else if (menuSelection == 1) { // Load Game
            refreshSaveList(); 
            currentState = STATE_LOAD_MENU; 
            menuSelection = 0; 
        } else if (menuSelection == 2) { // Quit
            Gdx.app.exit();
        }
    }
    
    private void handleNewGameSelection(int selection) {
        if (selection == 0) { // Single Player
            startGame(MODE_SINGLE_PLAYER);
        } else if (selection == 1) { // Bot Player
            // When selecting Bot Player from the menu, we want a NEW grid
            startGame(MODE_BOT_PLAYER);
        } else if (selection == 2) { // Return to Main Menu
            currentState = STATE_MAIN_MENU;
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
        
        // 2. STATE: NEW GAME MENU Clicks
        if (currentState == STATE_NEW_GAME_MENU) {
            float startY = menuCenterY + MENU_START_Y_OFFSET;
            float optionHeight = 30; // Spacing is 30 in render
            
            // Single Player (i=0)
            float singlePlayerY = startY;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > singlePlayerY - 20 && worldY < singlePlayerY + 5) {
                handleNewGameSelection(0);
                return true;
            }

            // Bot Player (i=1)
            float botPlayerY = startY - optionHeight;
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > botPlayerY - 20 && worldY < botPlayerY + 5) {
                handleNewGameSelection(1);
                return true;
            }
            
            // Return to Main Menu (i=2)
            float returnY = startY - (2 * optionHeight);
            if (worldX > optionX && worldX < optionX + optionWidth && worldY > returnY - 20 && worldY < returnY + 5) {
                handleNewGameSelection(2);
                return true;
            }
            return false;
        }


        // 3. STATE: MAIN MENU Clicks
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


        // 4. STATE: PAUSE/MENU Button Logic (Active during gameplay or game over)
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
        
        // 5. STATE: PAUSE MENU Option Clicks (Only active when paused)
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
                // Transition to mode selection menu
                currentState = STATE_NEW_GAME_MENU;
                menuSelection = 0;
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


    /** * Checks for proximity messages and sets the game over state/message 
     * for the human player/initial start. Bot mode handles terminal checks in botPlayerTurn().
     */
    private void checkGameInteraction() {
        
        proximityMessages.clear();
        
        if (player == null || currentState == STATE_GAME_OVER) return;
        
        int playerX = player.getGridX();
        int playerY = player.getGridY();
        
        // --- PROXIMITY EFFECTS (For visual feedback) ---
        
        StaticEntity gold = treasure.get(0);
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

        // If in SINGLE PLAYER mode, check for immediate death/win
        if (currentMode == MODE_SINGLE_PLAYER) {
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
            if (isWumpusAlive && wumpus != null && playerX == wumpus.getGridX() && playerY == wumpus.getGridY()) {
                currentState = STATE_GAME_OVER;
                gameOverMessage = "A ROAR! The Wumpus devoured you! Game Over! 😵";
                return;
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
        
        // --- STATE: NEW GAME MENU (Mode Selection) ---
        else if (currentState == STATE_NEW_GAME_MENU) {
            font.setColor(Color.WHITE);
            font.getData().setScale(2f);
            font.draw(batch, "SELECT MODE", menuCenterX - 80, menuCenterY + 180);
            font.getData().setScale(1.5f);
            
            // Menu Options
            String[] options = {"Single Player", "Bot Player (Training)", "Return to Main Menu"};
            for(int i = 0; i < options.length; i++) {
                if (menuSelection == i) {
                    font.setColor(Color.YELLOW); // Highlight effect
                } else {
                    font.setColor(Color.WHITE);
                }
                
                float optionY = menuCenterY + MENU_START_Y_OFFSET - (i * 30);
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
                    
                    if (isGameOver || currentMode == MODE_BOT_PLAYER || isVisible[x][y]) {
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
            // Show all if game over OR in Bot mode (since bot has full knowledge)
            if (!isGameOver && !isLanceAnimating && currentMode == MODE_SINGLE_PLAYER) {
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
            if (!proximityMessages.isEmpty()) {
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
                // Draw a large background rectangle for the pause screen
                batch.draw(pebbleTexture, viewX, viewY - viewport.getWorldHeight(), viewport.getWorldWidth(), viewport.getWorldHeight());
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
        
        if (player != null && currentMode == MODE_SINGLE_PLAYER) player.dispose();
        if (botPlayer != null) botPlayer.dispose(); // Dispose of botPlayer only once at the very end
        if (wumpus != null) wumpus.dispose();
        if(pebbleTexture != null) pebbleTexture.dispose();
    }
}