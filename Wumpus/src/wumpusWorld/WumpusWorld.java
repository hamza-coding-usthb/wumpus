package wumpusWorld;

import java.util.Random;
import java.util.Scanner;

public class WumpusWorld {
    private static final int WORLD_SIZE = 4;
    private static final int MAX_PITS = 3;
    private static final int[] AGENT_POSITION = {0, 0};
    private static final int[] WUMPUS_POSITION = new int[2];
    private static final int[] GOLD_POSITION = new int[2];
    private static final int[][] PITS_POSITIONS = new int[MAX_PITS][2];
    private static boolean hasArrow = true;
    private static boolean hasGold = false;
    private static int score = 0;

    public static void main(String[] args) {
        initializeWorld();
        playGame();
    }

    private static void initializeWorld() {
        Random rand = new Random();
        int pitCount = 0;

        while (pitCount < MAX_PITS) {
            int x = rand.nextInt(WORLD_SIZE);
            int y = rand.nextInt(WORLD_SIZE);
            if (x != AGENT_POSITION[0] || y != AGENT_POSITION[1]) {
                PITS_POSITIONS[pitCount][0] = x;
                PITS_POSITIONS[pitCount][1] = y;
                pitCount++;
            }
        }

        int wumpusX, wumpusY;
        do {
            wumpusX = rand.nextInt(WORLD_SIZE);
            wumpusY = rand.nextInt(WORLD_SIZE);
        } while ((wumpusX == AGENT_POSITION[0] && wumpusY == AGENT_POSITION[1]) ||
                 isPitAt(wumpusX, wumpusY));
        WUMPUS_POSITION[0] = wumpusX;
        WUMPUS_POSITION[1] = wumpusY;

        int goldX, goldY;
        do {
            goldX = rand.nextInt(WORLD_SIZE);
            goldY = rand.nextInt(WORLD_SIZE);
        } while ((goldX == AGENT_POSITION[0] && goldY == AGENT_POSITION[1]) ||
                 (goldX == WUMPUS_POSITION[0] && goldY == WUMPUS_POSITION[1]) ||
                 isPitAt(goldX, goldY));
        GOLD_POSITION[0] = goldX;
        GOLD_POSITION[1] = goldY;
    }

    private static void playGame() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            printPerceptions();
            System.out.print("Enter your action (move, turn left, turn right, shoot, grab): ");
            String action = scanner.nextLine();

            if (action.equalsIgnoreCase("move")) {
                moveAgent();
            } else if (action.equalsIgnoreCase("turn left")) {
                turnAgentLeft();
            } else if (action.equalsIgnoreCase("turn right")) {
                turnAgentRight();
            } else if (action.equalsIgnoreCase("shoot")) {
                shootArrow();
            } else if (action.equalsIgnoreCase("grab")) {
                grabGold();
            } else {
                System.out.println("Invalid action!");
            }

            if (hasGold) {
                System.out.println("You found the gold and escaped! Score: " + (score + 1000));
                break;
            } else if (isAgentAtPosition(WUMPUS_POSITION[0], WUMPUS_POSITION[1])) {
                System.out.println("You were eaten by the Wumpus! Game over.");
                break;
            } else if (isPitAt(AGENT_POSITION[0], AGENT_POSITION[1])) {
                System.out.println("You fell into a pit! Game over.");
                break;
            }
        }
    }

    private static void printPerceptions() {
        boolean stench = isAdjacentToWumpus();
        boolean breeze = isAdjacentToPit();
        boolean glitter = isAtGoldPosition();
        boolean bump = false;
        boolean scream = false;

        System.out.print("Percepts: [");
        if (stench) {
            System.out.print("Stench, ");
        } else {
            System.out.print("None, ");
        }
        if (breeze) {
            System.out.print("Breeze, ");
        } else {
            System.out.print("None, ");
        }
        if (glitter) {
            System.out.print("Glitter, ");
        } else {
            System.out.print("None, ");
        }
        if (bump) {
            System.out.print("Bump, ");
        } else {
            System.out.print("None, ");
        }
        if (scream) {
            System.out.println("Scream]");
        } else {
            System.out.println("None]");
        }
    }

    private static void moveAgent() {
        int[] newPosition = getPositionInFront();
        if (isValidPosition(newPosition[0], newPosition[1])) {
            AGENT_POSITION[0] = newPosition[0];
            AGENT_POSITION[1] = newPosition[1];
            score--;
        } else {
            System.out.println("Cannot move into a wall!");
        }
    }

    private static void turnAgentLeft() {
        // Implement turning left logic
    }

    private static void turnAgentRight() {
        // Implement turning right logic
    }

    private static void shootArrow() {
        if (hasArrow) {
            hasArrow = false;
            score -= 10;
            if (isAgentFacingWumpus()) {
                System.out.println("You killed the Wumpus!");
                // Add scream perception
            } else {
                System.out.println("You missed the Wumpus!");
            }
        } else {
            System.out.println("You don't have an arrow!");
        }
    }

    private static void grabGold() {
        if (isAtGoldPosition()) {
            hasGold = true;
            System.out.println("You grabbed the gold!");
        } else {
            System.out.println("There is no gold here!");
        }
    }

    private static boolean isAdjacentToWumpus() {
        int[] wumpusPosition = WUMPUS_POSITION;
        return isAdjacentPosition(wumpusPosition[0], wumpusPosition[1]);
    }

    private static boolean isAdjacentToPit() {
        for (int[] pitPosition : PITS_POSITIONS) {
            if (isAdjacentPosition(pitPosition[0], pitPosition[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAtGoldPosition() {
        return isAgentAtPosition(GOLD_POSITION[0], GOLD_POSITION[1]);
    }

    private static boolean isAgentFacingWumpus() {
        // Implement facing logic
        return false;
    }

    private static boolean isAdjacentPosition(int x, int y) {
        int agentX = AGENT_POSITION[0];
        int agentY = AGENT_POSITION[1];
        return (Math.abs(agentX - x) == 1 && agentY == y) ||
               (Math.abs(agentY - y) == 1 && agentX == x);
    }

    private static boolean isPitAt(int x, int y) {
        for (int[] pitPosition : PITS_POSITIONS) {
            if (pitPosition[0] == x && pitPosition[1] == y) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAgentAtPosition(int x, int y) {
        return AGENT_POSITION[0] == x && AGENT_POSITION[1] == y;
    }

    private static boolean isValidPosition(int x, int y) {
        return x >= 0 && x < WORLD_SIZE && y >= 0 && y < WORLD_SIZE;
    }

    private static int[] getPositionInFront() {
        // Implement getting position in front logic
        return new int[]{AGENT_POSITION[0], AGENT_POSITION[1]};
    }
}