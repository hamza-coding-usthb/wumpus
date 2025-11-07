package com.hamtech.wumpus;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20; // Used for glClear constant
import com.badlogic.gdx.graphics.OrthographicCamera;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class WumpusGame extends ApplicationAdapter {
    private Player player;
    private SpriteBatch batch;
    private Texture image;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Texture pebbleTexture; // Holds our loaded image
    
    public static final int GRID_SIZE = 8; // Define your grid dimensions
    public static final int TILE_SIZE = 32; // Example pixel size for each tile
    public static final float WORLD_WIDTH = GRID_SIZE * TILE_SIZE; // 8 * 64 = 512
    public static final float WORLD_HEIGHT = GRID_SIZE * TILE_SIZE; // 512
    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        // Use a Viewport to handle resizing and scaling.
        // FitViewport scales the world to fit the screen while maintaining aspect ratio.
        // It's usually based on the *actual* screen size, not the full world size.
        // For simple scrolling, we use the device's screen size for the viewport:
        viewport = new FitViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), camera);

        // Initially place the camera at the center of your visible screen area
        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0);
        camera.update();

        // Load the texture from the assets folder
        pebbleTexture = new Texture("pebble_brown0.png"); 
        player = new Player("donald.png", 0, 0);
    }
    @Override
public void resize(int width, int height) {
    // This tells the viewport to update itself and the camera when the window size changes.
    viewport.update(width, height);
}
    @Override
public void render() {
    // --- Camera Movement Logic ---
    float speed = 5f; // Pixels per frame
    if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
        camera.position.x -= speed;
    }
    if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
        camera.position.x += speed;
    }
    if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
        camera.position.y -= speed;
    }
    if (Gdx.input.isKeyPressed(Input.Keys.UP)) {
        camera.position.y += speed;
    }

    // Optional: Clamp the camera so it doesn't leave the bounds of the 8x8 world
    // This is more advanced, but recommended for production.
    // float cameraHalfWidth = viewport.getWorldWidth() / 2f;
    // float cameraHalfHeight = viewport.getWorldHeight() / 2f;
    // camera.position.x = Math.max(cameraHalfWidth, Math.min(camera.position.x, WORLD_WIDTH - cameraHalfWidth));
    // camera.position.y = Math.max(cameraHalfHeight, Math.min(camera.position.y, WORLD_HEIGHT - cameraHalfHeight));
    camera.update();
    // 2. Clear the screen (same as before)
    Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1.0f);
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

    // 3. Set the SpriteBatch to use the camera's view
    batch.setProjectionMatrix(camera.combined);
    batch.begin();

    // Loop through the 8x8 grid
    for (int x = 0; x < GRID_SIZE; x++) {
        for (int y = 0; y < GRID_SIZE; y++) {

            // Calculate the position: Tile Index * Tile Pixel Size
            float xPos = x * TILE_SIZE;
            float yPos = y * TILE_SIZE;

            // Draw the texture: (Texture, x, y, width, height)
            batch.draw(pebbleTexture, xPos, yPos, TILE_SIZE, TILE_SIZE);
        }
    }
    batch.end();
}

    @Override
    public void dispose() {
        batch.dispose();
        image.dispose();
    }
}
