package com.hamtech.wumpus;

import java.util.Random;

public class BotPlayer extends Player {
    // Q-Learning Hyperparameters
    public static final double LEARNING_RATE = 0.8;   // alpha (how quickly the agent adopts new info)
    public static final double DISCOUNT_FACTOR = 0.9; // gamma (how important future rewards are)
    public static final double EXPLORATION_DECAY = 0.9995; // Decay rate for epsilon

    // Actions (Indices 0 to 3)
    public static final int ACTION_UP = 0;
    public static final int ACTION_DOWN = 1;
    public static final int ACTION_LEFT = 2;
    public static final int ACTION_RIGHT = 3;
    public static final int NUM_ACTIONS = 4;

    // Q-Learning Variables
    private double[][] qTable;
    private double explorationRate = 1.0; // epsilon (start with full exploration)
    private Random random = new Random();

    // State is simply the (x, y) coordinate pair.
    // Q-Table size: (GRID_SIZE * GRID_SIZE) x NUM_ACTIONS
    private static final int NUM_STATES = WumpusGame.GRID_SIZE * WumpusGame.GRID_SIZE;

    public BotPlayer(String texturePath, int startX, int startY) {
        super(texturePath, startX, startY);
        // Initialize Q-Table to zeros
        qTable = new double[NUM_STATES][NUM_ACTIONS];
    }

    // --- State Conversion Helper ---
    // Converts (x, y) grid coordinates into a single state index (0 to 63)
    private int getStateIndex(int x, int y) {
        if (x < 0 || x >= WumpusGame.GRID_SIZE || y < 0 || y >= WumpusGame.GRID_SIZE) {
            return -1; 
        }
        // Row-major indexing: state = y * 8 + x (consistent 0-63 index for the table)
        return y * WumpusGame.GRID_SIZE + x;
    }

    // --- Action Selection (Epsilon-Greedy) ---
    public int chooseAction() {
        if (random.nextDouble() < explorationRate) {
            // Explore: Choose a random action
            return random.nextInt(NUM_ACTIONS); 
        } else {
            // Exploit: Choose the best action based on Q-Table
            return getBestAction(getGridX(), getGridY());
        }
    }
    
    // Finds the action with the maximum Q-value for the current state
    private int getBestAction(int x, int y) {
        int stateIndex = getStateIndex(x, y);
        int bestAction = 0;
        double maxQ = Double.NEGATIVE_INFINITY;

        // Iterate through all actions to find the best one
        for (int a = 0; a < NUM_ACTIONS; a++) {
            if (qTable[stateIndex][a] > maxQ) {
                maxQ = qTable[stateIndex][a];
                bestAction = a;
            } else if (qTable[stateIndex][a] == maxQ) {
                // Introduce tie-breaker randomness to prefer unexplored paths
                if (random.nextBoolean()) {
                    bestAction = a;
                }
            }
        }
        return bestAction;
    }
    
    // Finds the maximum Q-value for a given state (used in Q-Learning formula)
    private double getMaxQ(int x, int y) {
        int stateIndex = getStateIndex(x, y);
        double maxQ = Double.NEGATIVE_INFINITY;

        for (int a = 0; a < NUM_ACTIONS; a++) {
            maxQ = Math.max(maxQ, qTable[stateIndex][a]);
        }
        return maxQ;
    }

    // --- Q-Table Update ---
    public void updateQTable(int oldX, int oldY, int action, double reward, int newX, int newY) {
        int oldState = getStateIndex(oldX, oldY);
        
        // If oldState is -1, it's an error state, so skip update
        if (oldState == -1) return;

        double maxFutureQ = getMaxQ(newX, newY);
        
        // Q-Learning Formula: Q(s, a) <- Q(s, a) + alpha * [reward + gamma * max_a' Q(s', a') - Q(s, a)]
        double oldQ = qTable[oldState][action];
        double newQ = oldQ + LEARNING_RATE * (reward + DISCOUNT_FACTOR * maxFutureQ - oldQ);
        
        qTable[oldState][action] = newQ;
    }
    
    // --- Exploration Rate Update ---
    public void decayExplorationRate() {
        explorationRate *= EXPLORATION_DECAY;
        // Ensure it doesn't drop below a minimum
        explorationRate = Math.max(0.01, explorationRate); 
    }
    
    public double getExplorationRate() {
        return explorationRate;
    }

    // Convert action index to (dx, dy)
    public static int[] actionToMovement(int action) {
        switch (action) {
            case ACTION_UP: return new int[]{0, 1};
            case ACTION_DOWN: return new int[]{0, -1};
            case ACTION_LEFT: return new int[]{-1, 0};
            case ACTION_RIGHT: return new int[]{1, 0};
            default: return new int[]{0, 0}; // Should not happen
        }
    }
}