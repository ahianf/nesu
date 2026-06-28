package cl.ahianf.ppu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Disposable;
import java.nio.IntBuffer;

public class VirtualScreen implements Disposable {

    // =============================================================
    // MEMBERS
    // =============================================================
    private Pixmap m_pixmap;
    private Texture m_texture;
    private IntBuffer m_pixelBuffer; // Direct access to pixel data for speed

    private int m_width;
    private int m_height;
    private float m_pixelScale;

    // Optimization: Track the "Dirty Band" (Top and Bottom of changes)
    private int m_minDirtyY;
    private int m_maxDirtyY;

    // =============================================================
    // INITIALIZATION
    // =============================================================
    public void create(int width, int height, float pixelSize, int color) {
        this.m_width = width;
        this.m_height = height;
        this.m_pixelScale = pixelSize;

        if (m_pixmap != null) m_pixmap.dispose();
        if (m_texture != null) m_texture.dispose();
        // RGBA8888 is standard 32-bit color
        m_pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        m_pixmap.setColor(color);
        m_pixmap.fill();

        // Grab the direct buffer for fast writing
        m_pixelBuffer = m_pixmap.getPixels().asIntBuffer();
        m_texture = new Texture(m_pixmap);
        m_texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Initialize dirty region to "Everything" for the first draw
        resetDirtyRegionFull();
    }

    // =============================================================
    // FAST PIXEL ACCESS
    // =============================================================

    /**
     * Optimized setPixel using direct IntBuffer access and Dirty Band tracking.
     * Still available for partial updates if needed, though PPU now uses setFrameBuffer.
     */
    public void setPixel(int x, int y, int color) {
        if (x < 0 || x >= m_width || y < 0 || y >= m_height) return;
        // 1. Direct Memory Write (Much faster than m_pixmap.drawPixel)
        m_pixelBuffer.put(y * m_width + x, color);
        // 2. Track Dirty Region
        if (y < m_minDirtyY) m_minDirtyY = y;
        if (y > m_maxDirtyY) m_maxDirtyY = y;
    }

    /**
     * NEW METHOD: Bulk upload for PPU Optimization.
     * Copies the entire frame array into the buffer in one operation.
     */
    public void setFrameBuffer(int[] pixels) {
        // Fast bulk copy of the entire integer array to the native buffer
        m_pixelBuffer.clear();
        m_pixelBuffer.put(pixels);
        m_pixelBuffer.flip();

        // Mark the entire screen as dirty so it gets uploaded to the GPU
        m_minDirtyY = 0;
        m_maxDirtyY = m_height - 1;
    }

    // =============================================================
    // DRAWING
    // =============================================================

    public void draw(Batch batch, float x, float y) {
        // Only upload to GPU if something actually changed
        if (m_minDirtyY <= m_maxDirtyY) {
            updateTexturePartial();
            // Reset dirty tracker for next frame (Inverted state)
            m_minDirtyY = Integer.MAX_VALUE;
            m_maxDirtyY = Integer.MIN_VALUE;
        }

        // Calculate scaled dimensions
        float drawWidth = m_width * m_pixelScale;
        float drawHeight = m_height * m_pixelScale;

        // Draw the texture to the LibGDX batch
        batch.draw(m_texture, x, y, drawWidth, drawHeight);
    }

    /**
     * Uses OpenGL to upload only the changed rows of pixels.
     */
    private void updateTexturePartial() {
        m_texture.bind();

        int y = m_minDirtyY;
        int height = m_maxDirtyY - m_minDirtyY + 1;

        // Move buffer position to the start of the dirty data
        m_pixmap.getPixels().position(y * m_width * 4); // 4 bytes per pixel

        // Upload only the dirty band
        Gdx.gl.glTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, y, m_width, height,
                m_pixmap.getGLFormat(), m_pixmap.getGLType(),
                m_pixmap.getPixels());
        // Reset buffer position for safety
        m_pixmap.getPixels().rewind();
    }

    private void resetDirtyRegionFull() {
        m_minDirtyY = 0;
        m_maxDirtyY = m_height - 1;
    }

    // =============================================================
    // UTILITY
    // =============================================================

    public void setPixelScale(float scale) {
        this.m_pixelScale = scale;
    }

    @Override
    public void dispose() {
        if (m_pixmap != null) m_pixmap.dispose();
        if (m_texture != null) m_texture.dispose();
    }
}