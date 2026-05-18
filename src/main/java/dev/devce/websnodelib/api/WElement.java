package dev.devce.websnodelib.api;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The base class for all interactive and visual components inside a node (WNode).
 * Elements are arranged vertically within the node body.
 * Subclasses implement specific UI components like buttons, sliders, or labels.
 */
public abstract class WElement {
    protected int width;
    protected int height;
    protected int padding = 2;
    protected int margin = 2;

    /**
     * Renders the element at the specified logical coordinates.
     * @param graphics The GuiGraphics context.
     * @param x Top-left X coordinate of the element's bounding box.
     * @param y Top-left Y coordinate of the element's bounding box.
     * @param mouseX Current transformed mouse X.
     * @param mouseY Current transformed mouse Y.
     * @param partialTick Animation frame fraction.
     */
    public abstract void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick);

    /**
     * @return Total width including padding.
     */
    public int getWidth() {
        return width + padding * 2;
    }

    /**
     * @return Total height including padding and margin.
     */
    public int getHeight() {
        return height + padding * 2 + margin * 2;
    }

    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }

    /**
     * Serializes element state (e.g., slider value, text field content).
     */
    public net.minecraft.nbt.CompoundTag save() { return new net.minecraft.nbt.CompoundTag(); }

    /**
     * Loads element state from NBT.
     */
    public void load(net.minecraft.nbt.CompoundTag tag) {}

    /**
     * Handles mouse click events local to the element.
     * @return True if the event was consumed.
     */
    public boolean handleMouseClick(double localX, double localY, int button) { return false; }

    /**
     * Handles mouse release events.
     */
    public boolean handleMouseRelease(double mouseX, double mouseY, int button) { return false; }

    /**
     * Handles keyboard key presses.
     */
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) { return false; }

    /**
     * Handles character input.
     */
    public boolean handleCharTyped(char codePoint, int modifiers) { return false; }

    /**
     * Handles mouse drag events local to the element.
     */
    public boolean handleMouseDrag(double localX, double localY, int button, double dragX, double dragY) { return false; }
}
