package dev.devce.websnodelib.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an individual node within the graph.
 * A node can have inputs, outputs, UI elements, and a custom evaluation logic.
 */
public class WNode {
    private UUID id;
    private ResourceLocation typeId;
    private String title;
    private int x, y;
    private int width = 120;
    private int height = 40;
    private WGraph parentGraph;
    private net.minecraft.nbt.CompoundTag customData = new net.minecraft.nbt.CompoundTag();

    public net.minecraft.nbt.CompoundTag getCustomData() { return customData; }

    public void setParentGraph(WGraph graph) {
        this.parentGraph = graph;
    }

    public WGraph getParentGraph() {
        return parentGraph;
    }

    private final List<WElement> elements = new ArrayList<>();
    private final List<WPin> inputs = new ArrayList<>();
    private final List<WPin> outputs = new ArrayList<>();
    private Evaluator evaluator = (node) -> {};
    private WGraph internalGraph;
    private int topoDepth = 0;
    private boolean selected = false;

    /**
     * Interface for custom node behavior.
     * The evaluate method is called every graph tick.
     */
    public interface Evaluator {
        void evaluate(WNode node);
    }

    /**
     * Sets the custom logic for this node.
     * @param evaluator A lambda or class implementing the logic.
     */
    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Executes the node's custom logic.
     */
    public void evaluate() {
        this.evaluator.evaluate(this);
    }

    /**
     * Creates a new node instance.
     * @param typeId Unique identifier for the node type.
     * @param title Display title of the node.
     * @param x Initial X coordinate in logical space.
     * @param y Initial Y coordinate in logical space.
     */
    public WNode(ResourceLocation typeId, String title, int x, int y) {
        this.id = UUID.randomUUID();
        this.typeId = typeId;
        this.title = title;
        this.x = x;
        this.y = y;
    }

    /**
     * Adds a UI element (slider, button, text field, etc.) to the node body.
     * @param element The element to add.
     */
    public void addElement(WElement element) {
        this.elements.add(element);
        updateLayout();
    }

    /**
     * Adds an input pin to the left side of the node.
     * @param name Name of the input.
     * @param color Display color of the pin.
     */
    public void addInput(String name, int color) {
        WPin pin = new WPin(name, WPin.Type.INPUT, color);
        this.inputs.add(pin);
        updateLayout();
    }

    /**
     * Adds an output pin to the right side of the node.
     * @param name Name of the output.
     * @param color Display color of the pin.
     */
    public void addOutput(String name, int color) {
        WPin pin = new WPin(name, WPin.Type.OUTPUT, color);
        this.outputs.add(pin);
        updateLayout();
    }

    /**
     * Recalculates the node's dimensions based on its pins, elements, and title.
     * Automatically adjusts the width and height for a clean look.
     */
    public void updateLayout() {
        int headerHeight = 16;
        int maxInputLabelWidth = 0;
        for (WPin pin : inputs) {
            maxInputLabelWidth = Math.max(maxInputLabelWidth, net.minecraft.client.Minecraft.getInstance().font.width(pin.getName()));
        }
        
        int maxOutputLabelWidth = 0;
        for (WPin pin : outputs) {
            maxOutputLabelWidth = Math.max(maxOutputLabelWidth, net.minecraft.client.Minecraft.getInstance().font.width(pin.getName()));
        }

        int leftMargin = maxInputLabelWidth > 0 ? maxInputLabelWidth + 15 : 5;
        int rightMargin = maxOutputLabelWidth > 0 ? maxOutputLabelWidth + 15 : 5;

        int bodyWidth = 80;
        int bodyHeight = 0;
        for (WElement element : elements) {
            bodyWidth = Math.max(bodyWidth, element.getWidth());
            bodyHeight += element.getHeight();
        }

        int titleWidth = net.minecraft.client.Minecraft.getInstance().font.width(title) + 20;
        this.width = Math.max(titleWidth, leftMargin + bodyWidth + rightMargin);
        
        int pinAreaHeight = Math.max(inputs.size(), outputs.size()) * 12;
        this.height = headerHeight + Math.max(bodyHeight, pinAreaHeight) + 5;
    }

    /**
     * Renders the node, its pins, and all internal elements.
     * @param graphics The GuiGraphics context.
     * @param mouseX Transformed mouse X coordinate.
     * @param mouseY Transformed mouse Y coordinate.
     * @param partialTick Animation frame fraction.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;

        // Shadow/Glow
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x55000000);
        
        // Background
        graphics.fill(x, y, x + width, y + height, isHovered ? 0xEE252525 : 0xDD1A1A1A);
        
        // Title Bar
        boolean isFailed = getCustomData().getBoolean("err") || getCustomData().getBoolean("failed");
        graphics.fill(x, y, x + width, y + 15, isFailed ? 0xAAFF0000 : 0x44000000);
        int headerCol = isFailed ? 0xFFFF0000 : 0xFF00FF88;
        graphics.fill(x, y + 14, x + width, y + 15, headerCol);
        
        // Border
        int borderCol = selected ? 0xFFFFFFFF : (isHovered ? 0xFFAAAAAA : 0xFF444444);
        if (isFailed) borderCol = 0xFFFF0000; 
        graphics.renderOutline(x, y, width, height, borderCol);
        if (isFailed) {
            graphics.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFFFF0000);
        }
        
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, title, x + 5, y + 3, isFailed ? 0xFFFF5555 : 0xFF00FF88, false);

        int maxInputLabelWidth = 0;
        for (WPin pin : inputs) {
            maxInputLabelWidth = Math.max(maxInputLabelWidth, net.minecraft.client.Minecraft.getInstance().font.width(pin.getName()));
        }
        int leftMargin = maxInputLabelWidth > 0 ? maxInputLabelWidth + 15 : 5;

        // Render elements
        int currentY = y + 20;
        for (WElement element : elements) {
            element.render(graphics, x + leftMargin, currentY, mouseX, mouseY, partialTick);
            currentY += element.getHeight();
        }

        // Render pins
        for (int i = 0; i < inputs.size(); i++) {
//             renderPin(graphics, x - 4, y + 18 + i * 12, inputs.get(i), true, mouseX, mouseY);
            // AI FIX/ADD START
            renderPin(graphics, x - 4, y + 20 + i * 12, inputs.get(i), true, mouseX, mouseY);
            // AI FIX/ADD STOP
        }
        for (int i = 0; i < outputs.size(); i++) {
//             renderPin(graphics, x + width - 1, y + 18 + i * 12, outputs.get(i), false, mouseX, mouseY);
            // AI FIX/ADD START
            renderPin(graphics, x + width - 1, y + 20 + i * 12, outputs.get(i), false, mouseX, mouseY);
            // AI FIX/ADD STOP
        }
    }

    /**
     * Internal method to render a single pin with hover effects and labels.
     */
    private void renderPin(GuiGraphics graphics, int px, int py, WPin pin, boolean isInput, int mouseX, int mouseY) {
        int color = pin.getColor();
        boolean hover = mouseX >= px && mouseX <= px + 5 && mouseY >= py && mouseY <= py + 5;
        
        if (hover) {
            graphics.fill(px - 1, py - 1, px + 6, py + 6, 0x33FFFFFF);
        }
        
        graphics.fill(px, py, px + 5, py + 5, pin.isConnected() ? color : (color & 0x44FFFFFF));
        graphics.renderOutline(px, py, 5, 5, hover ? 0xFFFFFFFF : (color | 0xFF000000));

        // Pin Label
        String name = pin.getName();
        int tx = isInput ? px + 8 : px - 4 - net.minecraft.client.Minecraft.getInstance().font.width(name);
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "§8" + name, tx, py - 2, 0xFFFFFFFF);
    }

    /**
     * Checks if a specific screen position hits a pin on this node.
     * @param px Local X coordinate.
     * @param py Local Y coordinate.
     * @param isInput True to check inputs, false for outputs.
     * @return The index of the pin hit, or -1 if none.
     */
    public int getPinAt(int px, int py, boolean isInput) {
        int startX = isInput ? -4 : width - 1;
        List<WPin> list = isInput ? inputs : outputs;
        for (int i = 0; i < list.size(); i++) {
            int rx = startX;
//             int ry = 18 + i * 12;
            // AI FIX/ADD START
            int ry = 20 + i * 12;
            // AI FIX/ADD STOP
            if (px >= rx && px <= rx + 5 && py >= ry && py <= ry + 5) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Forwards mouse click events to internal UI elements.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int maxInputLabelWidth = 0;
        for (WPin pin : inputs) {
            maxInputLabelWidth = Math.max(maxInputLabelWidth, net.minecraft.client.Minecraft.getInstance().font.width(pin.getName()));
        }
        int leftMargin = maxInputLabelWidth > 0 ? maxInputLabelWidth + 15 : 5;

        boolean handled = false;
        int currentY = 20;
        for (WElement element : elements) {
            if (element.handleMouseClick(mouseX - leftMargin, mouseY - currentY, button)) {
                handled = true;
            }
            currentY += element.getHeight();
        }
        return handled;
    }

    /**
     * Forwards mouse release events to internal UI elements.
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        int maxInputLabelWidth = 0;
        for (WPin pin : inputs) {
            maxInputLabelWidth = Math.max(maxInputLabelWidth, net.minecraft.client.Minecraft.getInstance().font.width(pin.getName()));
        }
        int leftMargin = maxInputLabelWidth > 0 ? maxInputLabelWidth + 15 : 5;

        int currentY = 20;
        for (WElement element : elements) {
            element.handleMouseRelease(mouseX - leftMargin, mouseY - currentY, button);
            currentY += element.getHeight();
        }
        return false;
    }

    /**
     * Forwards mouse drag events to internal UI elements.
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int maxInputLabelWidth = 0;
        for (WPin pin : inputs) {
            maxInputLabelWidth = Math.max(maxInputLabelWidth, net.minecraft.client.Minecraft.getInstance().font.width(pin.getName()));
        }
        int leftMargin = maxInputLabelWidth > 0 ? maxInputLabelWidth + 15 : 5;

        int currentY = 20;
        for (WElement element : elements) {
            if (element.handleMouseDrag(mouseX - leftMargin, mouseY - currentY, button, dragX, dragY)) return true;
            currentY += element.getHeight();
        }
        return false;
    }

    /**
     * Forwards key press events to internal UI elements.
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (WElement element : elements) {
            if (element.handleKeyPress(keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    /**
     * Forwards character typing events to internal UI elements.
     */
    public boolean charTyped(char codePoint, int modifiers) {
        for (WElement element : elements) {
            if (element.handleCharTyped(codePoint, modifiers)) return true;
        }
        return false;
    }

    // Standard Getters and Setters
    public List<WElement> getElements() { return elements; }
    public List<WPin> getInputs() { return inputs; }
    public List<WPin> getOutputs() { return outputs; }

    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putUUID("id", id);
        tag.putString("typeId", typeId.toString());
        tag.putString("title", title);
        tag.putInt("x", x);
        tag.putInt("y", y);
        
        net.minecraft.nbt.ListTag inputsTag = new net.minecraft.nbt.ListTag();
        for (WPin pin : inputs) inputsTag.add(pin.save());
        tag.put("inputs", inputsTag);
        
        net.minecraft.nbt.ListTag outputsTag = new net.minecraft.nbt.ListTag();
        for (WPin pin : outputs) outputsTag.add(pin.save());
        tag.put("outputs", outputsTag);
        
        net.minecraft.nbt.ListTag elementsTag = new net.minecraft.nbt.ListTag();
        for (WElement el : elements) elementsTag.add(el.save());
        tag.put("elements", elementsTag);
        
        if (internalGraph != null) {
            tag.put("internalGraph", internalGraph.save());
        }
        
        tag.put("customData", customData);
        
        return tag;
    }

    public void load(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("id")) this.id = tag.getUUID("id");
        if (tag.contains("title")) this.title = tag.getString("title");
        this.x = tag.getInt("x");
        this.y = tag.getInt("y");
        
        net.minecraft.nbt.ListTag inputsTag = tag.getList("inputs", 10);
        if (inputsTag.size() > 0 && inputs.isEmpty()) {
            for (int i = 0; i < inputsTag.size(); i++) {
                net.minecraft.nbt.CompoundTag pinTag = inputsTag.getCompound(i);
                addInput(pinTag.getString("name"), pinTag.getInt("color"));
                inputs.get(i).load(pinTag);
            }
        } else {
            for (int i = 0; i < Math.min(inputs.size(), inputsTag.size()); i++) inputs.get(i).load(inputsTag.getCompound(i));
        }
        
        net.minecraft.nbt.ListTag outputsTag = tag.getList("outputs", 10);
        if (outputsTag.size() > 0 && outputs.isEmpty()) {
            for (int i = 0; i < outputsTag.size(); i++) {
                net.minecraft.nbt.CompoundTag pinTag = outputsTag.getCompound(i);
                addOutput(pinTag.getString("name"), pinTag.getInt("color"));
                outputs.get(i).load(pinTag);
            }
        } else {
            for (int i = 0; i < Math.min(outputs.size(), outputsTag.size()); i++) outputs.get(i).load(outputsTag.getCompound(i));
        }
        
        net.minecraft.nbt.ListTag elementsTag = tag.getList("elements", 10);
        for (int i = 0; i < Math.min(elements.size(), elementsTag.size()); i++) elements.get(i).load(elementsTag.getCompound(i));
        
        if (tag.contains("internalGraph")) {
            if (internalGraph == null) internalGraph = new WGraph();
            internalGraph.load(tag.getCompound("internalGraph"));
        }
        
        if (tag.contains("customData")) {
            this.customData = tag.getCompound("customData");
        }
    }

    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }

    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }

    public UUID getId() { return id; }
    public ResourceLocation getTypeId() { return typeId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public void setPos(int x, int y) { this.x = x; this.y = y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTopoDepth() { return topoDepth; }
    public void setTopoDepth(int depth) { this.topoDepth = depth; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public WGraph getInternalGraph() { return internalGraph; }
    public void setInternalGraph(WGraph graph) { this.internalGraph = graph; }

    public void clearInputs() { inputs.clear(); updateLayout(); }
    public void clearOutputs() { outputs.clear(); updateLayout(); }
}
