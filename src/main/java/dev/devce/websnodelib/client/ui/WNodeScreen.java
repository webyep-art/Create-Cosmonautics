package dev.devce.websnodelib.client.ui;

import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.WConnection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;
import java.util.Base64;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * The main GUI screen for editing node graphs.
 * Supports zooming, panning, multiple node selection, and real-time data flow visualization.
 */
public class WNodeScreen extends Screen {
    private final WGraph graph;
    private final WNodeScreen parentScreen;
    
    // Viewport panning and zoom
    private double panX = 0;
    private double panY = 0;
    private boolean isPanning = false;
    private float zoom = 1.0f;

    // Interaction state
    private WNode selectedNode = null;
    private WNode draggingNode = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    // Connection state
    private WNode linkingNode = null;
    private int linkingPin = -1;
    private int mouseX, mouseY;
    
    // Selection state
    private boolean isSelecting = false;
    private double selStartX, selStartY, selEndX, selEndY;

    // Animation and Effects
    private float screenAnimation = 0.0f;
    private long lastFrameTime = 0;
    
    // AI FIX/ADD START
    private boolean isExiting = false;
    private Screen nextScreen = null;
    public boolean isReturning = false;
    // AI FIX/ADD STOP
    
    /**
     * Particle system for the background.
     */
    private static class NodeParticle {
        double x, y, vx, vy;
        int color;
        int life, maxLife;
    }
    private final java.util.List<NodeParticle> editorParticles = new java.util.ArrayList<>();
    
    private boolean isSearching = false;
    private String searchQuery = "";
    private int menuX, menuY;
    private List<SearchItem> filteredItemsList = new ArrayList<>();
    private int searchScrollOffset = 0;
    private Consumer<CompoundTag> onSave;

    private record SearchItem(ResourceLocation type, String category, boolean isHeader) {}

    // Item Picker Overlay State
    private dev.devce.websnodelib.api.elements.WItemPicker activeItemPicker = null;
    private String itemSearchQuery = "";
    private List<net.minecraft.world.item.Item> filteredItems = new ArrayList<>();
    private int itemScrollOffset = 0;
    private int itemMenuX, itemMenuY;
    
    // Context Menu State
    private boolean isContextMenuOpen = false;
    private List<ContextAction> currentActions = new ArrayList<>();
    private int ctxMenuX, ctxMenuY;

    private record ContextAction(String label, Runnable action, boolean important) {}
    
    // Renaming State
    private WNode renamingNode = null;
    private dev.devce.websnodelib.api.elements.WTextField renameField = null;
    
    // Resizing State
    private boolean isResizing = false;
    private WNode resizingNode = null;

    // Undo/Redo State
    private final java.util.LinkedList<CompoundTag> undoStack = new java.util.LinkedList<>();
    private final java.util.LinkedList<CompoundTag> redoStack = new java.util.LinkedList<>();

    // AI FIX/ADD START
    private long lastClickTime = 0;
    private WNode lastClickedNode = null;
    private int lastClickButton = -1;
    // AI FIX/ADD STOP
    
    // Favorites
    private static final java.util.Set<ResourceLocation> FAVORITES = new java.util.HashSet<>();

    public WNodeScreen(Component title, WGraph graph, java.util.function.Consumer<net.minecraft.nbt.CompoundTag> onSave, WNodeScreen parentScreen) {
        super(title);
        this.graph = graph;
        this.onSave = onSave;
        this.parentScreen = parentScreen;
    }

    public WNodeScreen(WGraph graph) {
        this(Component.literal("Web's Node Editor"), graph, (tag) -> {}, null);
    }

    @Override
    public void removed() {
        if (onSave != null) onSave.accept(graph.save());
        super.removed();
    }

    public WNodeScreen getParentScreen() { return parentScreen; }
    public Component getTitle() { return title; }

    @Override
    protected void init() {
        super.init();
        // AI FIX/ADD START: Auto-evaluate nodes to trigger initial compilation/parsing
        for (WNode node : graph.getNodes()) {
            node.evaluate();
        }
        // AI FIX/ADD STOP
        screenAnimation = 0;
        // AI FIX/ADD START
        lastFrameTime = 0;
        // AI FIX/ADD STOP
        graph.updateTopology();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graph.tick();

        long now = net.minecraft.Util.getMillis();
        float deltaTime = (lastFrameTime == 0) ? 0.016f : (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        
        // AI FIX/ADD START
        if (isExiting) {
            screenAnimation = Math.max(0.0f, screenAnimation - deltaTime * 4.0f);
            if (screenAnimation <= 0.0f && nextScreen != null) {
                minecraft.setScreen(nextScreen);
                // Do not return here! We need to finish rendering the black screen overlay for this frame
                // to prevent the game world from flashing behind it.
            }
        } else {
            screenAnimation = Math.min(1.0f, screenAnimation + deltaTime * 4.0f);
        }
        // AI FIX/ADD STOP
        /*
        screenAnimation = Math.min(1.0f, screenAnimation + deltaTime * 4.0f);
        */
        
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -2000);
        graphics.fill(0, 0, width, height, 0xFF121212);
        
        for (int i = 0; i < height; i += 2) {
            graphics.fill(0, i, width, i + 1, 0x0A000000);
        }
        graphics.pose().popPose();

        graphics.pose().pushPose();
        // AI FIX/ADD START
        float sOut;
        if (isExiting) {
            sOut = (0.98f + 0.02f * screenAnimation) * zoom;
        } else if (isReturning) {
            sOut = (1.02f - 0.02f * screenAnimation) * zoom;
        } else {
            sOut = (0.98f + 0.02f * screenAnimation) * zoom;
        }
        // AI FIX/ADD STOP
        /*
        float sOut = (0.98f + 0.02f * screenAnimation) * zoom;
        */
        graphics.pose().translate(width / 2f, height / 2f, 0);
        graphics.pose().scale(sOut, sOut, 1.0f);
        graphics.pose().translate(-width / 2f, -height / 2f, 0);
        
        drawGrid(graphics);

        graphics.pose().translate(panX, panY, 0);

        // 1. Render Frames (Background layer)
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, -500); // Push frames to the very back
        for (WNode node : graph.getNodes()) {
            if (node.getTypeId().getPath().equals("frame")) {
                renderFrame(graphics, node, mouseX, mouseY);
            }
        }
        graphics.pose().popPose();

        // 2. Render Connections
        for (WConnection conn : graph.getConnections()) {
            // AI FIX/ADD START
            drawConnection(graphics, conn, partialTick, mouseX, mouseY);
            /*
            drawConnection(graphics, conn, partialTick);
            */
            // AI FIX/ADD STOP
        }
        
        // 3. Render Nodes (Foreground layer)
        int z = 0;
        for (WNode node : graph.getNodes()) {
            if (!node.getTypeId().getPath().equals("frame")) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, z++ * 5); 
                node.render(graphics, (int)((mouseX - width / 2f) / zoom + width / 2f - panX), (int)((mouseY - height / 2f) / zoom + height / 2f - panY), partialTick);
                
                // AI FIX/ADD START
                if (renamingNode == node) {
                    // Fill background to cover the original title
                    int nx = (int)((mouseX - width / 2f) / zoom + width / 2f - panX);
                    int ny = (int)((mouseY - height / 2f) / zoom + height / 2f - panY);
                    boolean h = nx >= node.getX() && nx <= node.getX() + node.getWidth() && ny >= node.getY() && ny <= node.getY() + node.getHeight();
                    graphics.fill(node.getX() + 1, node.getY() + 1, node.getX() + node.getWidth() - 1, node.getY() + 14, h ? 0xFF252525 : 0xFF1A1A1A);
                    
                    // Only render the text field overlay
                    renameField.render(graphics, node.getX() + 3, node.getY() + 1, mouseX, mouseY, partialTick);
                }
                // AI FIX/ADD STOP

                graphics.pose().popPose();
            }
        }

        if (linkingNode != null) {
            int sx = linkingNode.getX() + linkingNode.getWidth();
//             int sy = linkingNode.getY() + 18 + linkingPin * 12;
            // AI FIX/ADD START
            int sy = linkingNode.getY() + 22 + linkingPin * 12;
            // AI FIX/ADD STOP
            int tx = (int) getGraphX(mouseX);
            int ty = (int) getGraphY(mouseY);
            drawSmoothCurve(graphics, sx, sy, tx, ty, 0xAAFFFFFF, 1.5f);
        }


        if (isSelecting) {
            float x1 = (float)Math.min(selStartX, selEndX);
            float y1 = (float)Math.min(selStartY, selEndY);
            float x2 = (float)Math.max(selStartX, selEndX);
            float y2 = (float)Math.max(selStartY, selEndY);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 2000);
            graphics.fill((int)x1, (int)y1, (int)x2, (int)y2, 0x3300FF88);
            graphics.renderOutline((int)x1, (int)y1, (int)(x2 - x1), (int)(y2 - y1), 0xFF00FF88);
            graphics.pose().popPose();
        }

        renderParticles(graphics, deltaTime);

        graphics.pose().popPose();

        if (screenAnimation < 1.0f) {
            int overlayAlpha = (int)((1.0f - screenAnimation) * 255);
            graphics.fill(0, 0, width, height, (overlayAlpha << 24) | 0x121212);
        }

        if (isSearching) {
            renderSearchMenu(graphics);
        }
        if (isContextMenuOpen) {
            renderContextMenu(graphics);
        }
        // AI FIX/ADD START
        // Removed renderRenameOverlay(graphics)
        // AI FIX/ADD STOP
        /*
        if (renamingNode != null) {
            renderRenameOverlay(graphics);
        }
        */
        if (activeItemPicker != null) {
            renderItemPickerOverlay(graphics);
        }
        
        // AI FIX/ADD START
        renderBreadcrumbs(graphics);
        // AI FIX/ADD STOP
        
        renderMinimap(graphics);
    }

    // AI FIX/ADD START
    private void renderBreadcrumbs(GuiGraphics graphics) {
        java.util.List<String> path = new java.util.ArrayList<>();
        WNodeScreen current = this;
        while (current != null) {
            if (current.parentScreen == null) {
                path.add(0, "~");
            } else {
                path.add(0, current.getTitle().getString());
            }
            current = current.parentScreen;
        }

        int x = 10;
        int y = 10;
        for (int i = 0; i < path.size(); i++) {
            String text = path.get(i);
            boolean isLast = (i == path.size() - 1);
            int color = isLast ? 0xFF00FF88 : 0xFFAAAAAA;
            graphics.drawString(font, text, x, y, color, true);
            x += font.width(text);
            
            if (!isLast) {
                graphics.drawString(font, " / ", x, y, 0xFFAAAAAA, true);
                x += font.width(" / ");
            }
        }
    }
    // AI FIX/ADD STOP

    private double getGraphX(double mouseX) {
        return (mouseX - width / 2.0) / zoom + width / 2.0 - panX;
    }

    private double getGraphY(double mouseY) {
        return (mouseY - height / 2.0) / zoom + height / 2.0 - panY;
    }

    private void renderMinimap(GuiGraphics graphics) {
        int mw = 100;
        int mh = 70;
        int mx = width - mw - 10;
        int my = height - mh - 10;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 8000);
        graphics.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, 0xFF00FF88);
        graphics.fill(mx, my, mx + mw, my + mh, 0xCC1A1A1A);
        
        float scale = 0.05f; // Minimap zoom
        
        for (WNode node : graph.getNodes()) {
            int nx = mx + mw/2 + (int)((node.getX() + panX - width/2) * scale);
            int ny = my + mh/2 + (int)((node.getY() + panY - height/2) * scale);
            int nw = (int)(node.getWidth() * scale);
            int nh = (int)(node.getHeight() * scale);
            
            if (nx >= mx && nx + nw <= mx + mw && ny >= my && ny + nh <= my + mh) {
                graphics.fill(nx, ny, nx + nw, ny + nh, 0xAA00FF88);
            }
        }
        
        // Connections on minimap
        for (dev.devce.websnodelib.api.WConnection conn : graph.getConnections()) {
            WNode src = findNode(conn.sourceNode());
            WNode tgt = findNode(conn.targetNode());
            if (src != null && tgt != null) {
                int sx = mx + mw/2 + (int)((src.getX() + src.getWidth() + panX - width/2) * scale);
//                 int sy = my + mh/2 + (int)((src.getY() + 18 + conn.sourcePin() * 12 + panY - height/2) * scale);
                // AI FIX/ADD START
                int sy = my + mh/2 + (int)((src.getY() + 22 + conn.sourcePin() * 12 + panY - height/2) * scale);
                // AI FIX/ADD STOP
                int tx = mx + mw/2 + (int)((tgt.getX() + panX - width/2) * scale);
//                 int ty = my + mh/2 + (int)((tgt.getY() + 18 + conn.targetPin() * 12 + panY - height/2) * scale);
                // AI FIX/ADD START
                int ty = my + mh/2 + (int)((tgt.getY() + 22 + conn.targetPin() * 12 + panY - height/2) * scale);
                // AI FIX/ADD STOP
                
                if (sx >= mx && sx <= mx + mw && sy >= my && sy <= my + mh && tx >= mx && tx <= mx + mw && ty >= my && ty <= my + mh) {
                    drawMinimapLine(graphics, sx, sy, tx, ty, 0xAA00FF88);
                }
            }
        }
        
        // Render viewport box
        int vpx = mx + mw/2 + (int)((-width/2) * scale);
        int vpy = my + mh/2 + (int)((-height/2) * scale);
        int vpw = (int)(width * scale);
        int vph = (int)(height * scale);
        graphics.renderOutline(vpx, vpy, vpw, vph, 0xFFFFFFFF);
        
        graphics.pose().popPose();
    }

    private void drawMinimapLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        if (dx > dy) {
            graphics.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
            graphics.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, color);
        } else {
            graphics.fill(x1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
            graphics.fill(Math.min(x1, x2), y2, Math.max(x1, x2) + 1, y2 + 1, color);
        }
    }


    private void renderContextMenu(GuiGraphics graphics) {
        int mw = 120;
        int itemH = 14;
        int mh = currentActions.size() * itemH + 4;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 9000);
        graphics.fill(ctxMenuX + 2, ctxMenuY + 2, ctxMenuX + mw + 2, ctxMenuY + mh + 2, 0x55000000);
        graphics.fill(ctxMenuX, ctxMenuY, ctxMenuX + mw, ctxMenuY + mh, 0xEE1A1A1A);
        graphics.renderOutline(ctxMenuX, ctxMenuY, mw, mh, 0xFF00FF88);

        for (int i = 0; i < currentActions.size(); i++) {
            ContextAction act = currentActions.get(i);
            int iy = ctxMenuY + 2 + i * itemH;
            boolean hovered = mouseX >= ctxMenuX && mouseX <= ctxMenuX + mw && mouseY >= iy && mouseY < iy + itemH;
            
            if (hovered) graphics.fill(ctxMenuX + 1, iy, ctxMenuX + mw - 1, iy + itemH, 0x3300FF88);
            graphics.drawString(font, act.label, ctxMenuX + 6, iy + 3, act.important ? 0xFFFF5555 : (hovered ? 0xFFFFFFFF : 0xFFAAAAAA), false);
        }
        graphics.pose().popPose();
    }

    private void renderFrame(GuiGraphics graphics, WNode node, int mouseX, int mouseY) {
        int x = node.getX();
        int y = node.getY();
        int w = node.getWidth();
        int h = node.getHeight();
        
        int color = node.isSelected() ? 0x6600FF88 : 0x3300FF88;
        graphics.fill(x, y, x + w, y + h, color);
        graphics.renderOutline(x, y, w, h, 0xFF00FF88);
        graphics.drawString(font, "§l" + node.getTitle(), x + 5, y + 5, 0xFFFFFFFF, false);
        
        // Resize handle
        graphics.fill(x + w - 8, y + h - 8, x + w, y + h, 0xFF00FF88);
    }

    private void renderRenameOverlay(GuiGraphics graphics) {
        int rx = (int)((renamingNode.getX() + panX - width / 2f) * zoom + width / 2f);
        int ry = (int)((renamingNode.getY() + panY - height / 2f) * zoom + height / 2f) - 20;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 9500);
        graphics.fill(rx - 2, ry - 2, rx + 102, ry + 16, 0xEE1A1A1A);
        graphics.renderOutline(rx - 2, ry - 2, 104, 18, 0xFF00FF88);
        renameField.render(graphics, rx, ry, mouseX, mouseY, 0);
        graphics.pose().popPose();
    }

    private void renderSearchMenu(GuiGraphics graphics) {
        int mw = 140;
        int itemH = 12;
        int maxVisible = 12;
        int visibleCount = Math.min(maxVisible, filteredItemsList.size());
        int mh = 15 + visibleCount * itemH;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 5000);
        graphics.fill(menuX + 2, menuY + 2, menuX + mw + 2, menuY + mh + 2, 0x55000000);
        graphics.fill(menuX, menuY, menuX + mw, menuY + mh, 0xEE1A1A1A);
        graphics.renderOutline(menuX, menuY, mw, mh, 0xFF00FF88);
        graphics.drawString(font, "> " + searchQuery + "_", menuX + 4, menuY + 4, 0xFF00FF88, false);
        
        for (int i = 0; i < visibleCount; i++) {
            int idx = i + searchScrollOffset;
            if (idx >= filteredItemsList.size()) break;
            
            SearchItem item = filteredItemsList.get(idx);
            
            if (item.isHeader) {
                graphics.fill(menuX + 1, menuY + 15 + i * itemH, menuX + mw - 1, menuY + 15 + (i + 1) * itemH, 0x22FFFFFF);
                graphics.drawString(font, "§7[" + item.category + "]", menuX + 4, menuY + 16 + i * itemH, 0xFFAAAAAA, false);
                continue;
            }

            ResourceLocation type = item.type;
            String displayName = type.getPath().replace("_", " ");
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
            
            boolean isFav = FAVORITES.contains(type);
            boolean hovered = mouseY >= menuY + 15 + i * itemH && mouseY < menuY + 15 + (i + 1) * itemH && mouseX >= menuX && mouseX <= menuX + mw;
            
            if (hovered) {
                graphics.fill(menuX + 1, menuY + 15 + i * itemH, menuX + mw - 1, menuY + 15 + (i + 1) * itemH, 0x4400FF88);
            }
            graphics.drawString(font, (isFav ? "§e★ " : "  ") + displayName, menuX + 6, menuY + 16 + i * itemH, hovered ? 0xFFFFFFFF : 0xFF888888, false);
        }
        
        // Scroll indicator
        if (filteredItemsList.size() > maxVisible) {
            float progress = (float) searchScrollOffset / (filteredItemsList.size() - maxVisible);
            int barY = menuY + 15 + (int)(progress * (mh - 25));
            graphics.fill(menuX + mw - 3, menuY + 15, menuX + mw - 1, menuY + mh - 1, 0x22FFFFFF);
            graphics.fill(menuX + mw - 3, barY, menuX + mw - 1, barY + 10, 0xFF00FF88);
        }
        
        graphics.pose().popPose();
    }

    private void drawGrid(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(panX, panY, -1000);
        int gridSize = 20;
        int startX = (int)(-panX - (width / 2f) / zoom - 20);
        int startY = (int)(-panY - (height / 2f) / zoom - 20);
        int endX = (int)(-panX + (width / 2f) / zoom + width + 20);
        int endY = (int)(-panY + (height / 2f) / zoom + height + 20);
        startX = (startX / gridSize) * gridSize;
        startY = (startY / gridSize) * gridSize;
        for (int i = startX; i < endX; i += gridSize) graphics.fill(i, startY, i + 1, endY, 0x12FFFFFF);
        for (int i = startY; i < endY; i += gridSize) graphics.fill(startX, i, endX, i + 1, 0x12FFFFFF);
        graphics.pose().popPose();
    }

    // AI FIX/ADD START
    private WConnection getHoveredConnection(int mx, int my) {
        int nx = (int) getGraphX(mx);
        int ny = (int) getGraphY(my);
        for (WConnection conn : graph.getConnections()) {
            WNode src = findNode(conn.sourceNode());
            WNode tgt = findNode(conn.targetNode());
            if (src == null || tgt == null) continue;
            int x1 = src.getX() + src.getWidth();
//             int y1 = src.getY() + 18 + conn.sourcePin() * 12;
        // AI FIX/ADD START
        int y1 = src.getY() + 22 + conn.sourcePin() * 12;
        // AI FIX/ADD STOP
            int x2 = tgt.getX();
//             int y2 = tgt.getY() + 18 + conn.targetPin() * 12;
        // AI FIX/ADD START
        int y2 = tgt.getY() + 22 + conn.targetPin() * 12;
        // AI FIX/ADD STOP
            
            int steps = 24;
            for (int i = 0; i <= steps; i++) {
                float t = (float) i / steps;
                float cx1 = x1 + (x2 - x1) * 0.5f; float cy1 = y1;
                float cx2 = x1 + (x2 - x1) * 0.5f; float cy2 = y2;
                float mt = 1.0f - t;
                float x = mt * mt * mt * x1 + 3 * mt * mt * t * cx1 + 3 * mt * t * t * cx2 + t * t * t * x2;
                float y = mt * mt * mt * y1 + 3 * mt * mt * t * cy1 + 3 * mt * t * t * cy2 + t * t * t * y2;
                
                if (Math.abs(x - nx) < 6 && Math.abs(y - ny) < 6) {
                    return conn;
                }
            }
        }
        return null;
    }

    private void drawConnection(GuiGraphics graphics, WConnection conn, float partialTick, int mouseX, int mouseY) {
    // AI FIX/ADD STOP
    /*
    private void drawConnection(GuiGraphics graphics, WConnection conn, float partialTick) {
    */
        WNode src = findNode(conn.sourceNode());
        WNode tgt = findNode(conn.targetNode());
        if (src == null || tgt == null) return;
        int x1 = src.getX() + src.getWidth();
//         int y1 = src.getY() + 18 + conn.sourcePin() * 12;
        // AI FIX/ADD START
        int y1 = src.getY() + 22 + conn.sourcePin() * 12;
        // AI FIX/ADD STOP
        int x2 = tgt.getX();
//         int y2 = tgt.getY() + 18 + conn.targetPin() * 12;
        // AI FIX/ADD START
        int y2 = tgt.getY() + 22 + conn.targetPin() * 12;
        // AI FIX/ADD STOP
        
        // AI FIX/ADD START
        boolean isHovered = conn.equals(getHoveredConnection(mouseX, mouseY));
        float thickness = isHovered ? 2.5f : 1.5f;
        int color = 0xAA00FF88;
        drawSmoothCurve(graphics, x1, y1, x2, y2, color, thickness);
        // AI FIX/ADD STOP
        /*
        drawSmoothCurve(graphics, x1, y1, x2, y2, 0xAA00FF88, 1.5f);
        */
        
        float speed = 0.003f;
        float time = (System.currentTimeMillis() % 1000000) * speed; 
        float cycle = 4.0f; 
        float t = (time - src.getTopoDepth()) % cycle;
        if (t < 0) t += cycle;
        if (t >= 0 && t <= 1.0f) {
            float pulsePos = t * t * (3.0f - 2.0f * t);
            float dist = (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            graphics.pose().pushPose();
            drawPulsePoint(graphics, x1, y1, x2, y2, pulsePos, dist);
            graphics.pose().translate(0, 0, 500);
            graphics.pose().popPose();
        }
    }

    private void drawPulsePoint(GuiGraphics graphics, int x1, int y1, int x2, int y2, float t, float dist) {
        float cx1 = x1 + (x2 - x1) * 0.5f; float cy1 = y1;
        float cx2 = x1 + (x2 - x1) * 0.5f; float cy2 = y2;
        int segments = (int) Math.max(12, dist / 6);
        float step = 0.25f / segments; 
        for (int i = 0; i < segments; i++) {
            float segmentT = t - (i * step); if (segmentT < 0) continue;
            float mt = 1.0f - segmentT;
            float x = mt * mt * mt * x1 + 3 * mt * mt * segmentT * cx1 + 3 * mt * segmentT * segmentT * cx2 + segmentT * segmentT * segmentT * x2;
            float y = mt * mt * mt * y1 + 3 * mt * mt * segmentT * cy1 + 3 * mt * segmentT * segmentT * cy2 + segmentT * segmentT * segmentT * y2 + 1;
            int alpha = (int)(255 * (1.0f - (float)i / segments));
            int color = (alpha << 24) | 0x00FF88;
            graphics.fill((int)x - 3, (int)y - 3, (int)x + 3, (int)y + 3, (color & 0x00FFFFFF) | ((alpha / 5) << 24));
            graphics.fill((int)x - 1, (int)y - 1, (int)x + 1, (int)y + 1, (color & 0x00FFFFFF) | (alpha << 24));
        }
    }

    private void drawSmoothCurve(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, float thickness) {
        // AI FIX/ADD START
        int steps = 24; 
        double lastX = x1; 
        double lastY = y1;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double cx1 = x1 + (x2 - x1) * 0.5; double cy1 = y1;
            double cx2 = x1 + (x2 - x1) * 0.5; double cy2 = y2;
            double mt = 1.0 - t;
            double x = mt * mt * mt * x1 + 3 * mt * mt * t * cx1 + 3 * mt * t * t * cx2 + t * t * t * x2;
            double y = mt * mt * mt * y1 + 3 * mt * mt * t * cy1 + 3 * mt * t * t * cy2 + t * t * t * y2;
            drawLine(graphics, (int)Math.round(lastX), (int)Math.round(lastY), (int)Math.round(x), (int)Math.round(y), color, thickness);
            lastX = x; lastY = y;
        }
        // AI FIX/ADD STOP
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color, float thickness) {
        // AI FIX/ADD START
        int dx = x2 - x1; int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy)); 
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + (int)Math.max(1, thickness), y1 + (int)Math.max(1, thickness), color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int px = (int)Math.round(x1 + dx * t); 
            int py = (int)Math.round(y1 + dy * t);
            graphics.fill(px, py, px + (int)Math.max(1, thickness), py + (int)Math.max(1, thickness), color);
        }
        // AI FIX/ADD STOP
    }

    private void renderParticles(GuiGraphics graphics, float deltaTime) {
        for (int i = editorParticles.size() - 1; i >= 0; i--) {
            NodeParticle p = editorParticles.get(i);
            p.x += p.vx * deltaTime * 60.0; p.y += p.vy * deltaTime * 60.0; p.life -= deltaTime * 60.0;
            if (p.life <= 0) { editorParticles.remove(i); continue; }
            float alpha = (float) p.life / p.maxLife;
            int rColor = (p.color & 0xFFFFFF) | ((int)(alpha * 255) << 24);
            graphics.fill((int)p.x, (int)p.y, (int)p.x + 2, (int)p.y + 2, rColor);
        }
    }

    private WNode findNode(UUID id) {
        return graph.getNodes().stream().filter(n -> n.getId().equals(id)).findFirst().orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // AI FIX/ADD START
        if (renamingNode != null) {
            renamingNode.setTitle(renameField.getValue());
            renamingNode = null;
        }
        // AI FIX/ADD STOP

        if (activeItemPicker != null) {
            if (handleItemPickerClick(mouseX, mouseY, button)) return true;
            activeItemPicker = null;
            return true;
        }

        // AI FIX/ADD START
        if (button == 0) {
            java.util.List<WNodeScreen> screens = new java.util.ArrayList<>();
            WNodeScreen currentScreen = this;
            while (currentScreen != null) {
                screens.add(0, currentScreen);
                currentScreen = currentScreen.parentScreen;
            }
            int bcX = 10;
            int bcY = 10;
            for (int i = 0; i < screens.size(); i++) {
                WNodeScreen s = screens.get(i);
                String text = s.parentScreen == null ? "~" : s.getTitle().getString();
                int w = font.width(text);
                if (mouseX >= bcX && mouseX <= bcX + w && mouseY >= bcY && mouseY <= bcY + 9) {
                    if (s != this) {
                        if (this.onSave != null) this.onSave.accept(this.graph.save());
                        // AI FIX/ADD START
                        isExiting = true;
                        nextScreen = s;
                        s.isReturning = true;
                        // AI FIX/ADD STOP
                        /*
                        minecraft.setScreen(s);
                        */
                    }
                    return true;
                }
                bcX += w;
                if (i < screens.size() - 1) {
                    bcX += font.width(" / ");
                }
            }
        }
        // AI FIX/ADD STOP

        if (isContextMenuOpen) {
            int mw = 120;
            int itemH = 14;
            int mh = currentActions.size() * itemH + 4;
            if (mouseX >= ctxMenuX && mouseX <= ctxMenuX + mw && mouseY >= ctxMenuY && mouseY <= ctxMenuY + mh) {
                int index = (int)((mouseY - (ctxMenuY + 2)) / itemH);
                if (index >= 0 && index < currentActions.size()) {
                    currentActions.get(index).action().run();
                    isContextMenuOpen = false;
                    return true;
                }
            }
            isContextMenuOpen = false;
            if (button == 0) return true; // Consume click to just close menu
        }

        int nx = (int) getGraphX(mouseX);
        int ny = (int) getGraphY(mouseY);

        if (button == 0) {
            for (WNode node : graph.getNodes()) {
                if (node.getTypeId().getPath().equals("frame")) {
                    int handleX = node.getX() + node.getWidth() - 8;
                    int handleY = node.getY() + node.getHeight() - 8;
                    if (nx >= handleX && nx <= handleX + 8 && ny >= handleY && ny <= handleY + 8) {
                        pushUndo();
                        isResizing = true;
                        resizingNode = node;
                        return true;
                    }
                }
            }
        }

        if (isSearching) {
            int itemH = 12;
            int mw = 140;
            int maxVisible = 12;
            int visibleCount = Math.min(maxVisible, filteredItemsList.size());
            if (mouseX >= menuX && mouseX <= menuX + mw && mouseY >= menuY + 15 && mouseY <= menuY + 15 + visibleCount * itemH) {
                int index = (int)((mouseY - (menuY + 15)) / itemH) + searchScrollOffset;
                if (index >= 0 && index < filteredItemsList.size()) {
                    SearchItem item = filteredItemsList.get(index);
                    if (!item.isHeader) {
                        if (button == 1) { // Right click to toggle favorite
                            if (FAVORITES.contains(item.type)) FAVORITES.remove(item.type);
                            else FAVORITES.add(item.type);
                            updateSearch();
                            return true;
                        }
                        pushUndo();
                        addNodeAt(item.type, nx, ny); isSearching = false; return true;
                    }
                }
            }
            isSearching = false; return true;
        }
        if (button == 1) {
            boolean overNode = false;
            for (WNode node : graph.getNodes()) {
                if (nx >= node.getX() && nx <= node.getX() + node.getWidth() && ny >= node.getY() && ny <= node.getY() + node.getHeight()) {
                    overNode = true;
                    if (!node.isSelected()) {
                        graph.getNodes().forEach(n -> n.setSelected(false));
                        node.setSelected(true);
                    }
                    break;
                }
            }

            if (overNode) {
                openContextMenu((int)mouseX, (int)mouseY);
                return true;
            } else {
                // AI FIX/ADD START
                WConnection hoveredConn = getHoveredConnection((int)mouseX, (int)mouseY);
                if (hoveredConn != null) {
                    pushUndo();
                    graph.getConnections().remove(hoveredConn);
                    if (onSave != null) onSave.accept(graph.save());
                    return true;
                }
                // AI FIX/ADD STOP
                // AI FIX/ADD START
                isSearching = true; 
                searchQuery = ""; 
                menuX = (int)mouseX; 
                menuY = (int)mouseY; 
                updateSearch(); 
                
                int mw = 140;
                int itemH = 12;
                int maxVisible = 12;
                int visibleCount = Math.min(maxVisible, filteredItemsList.size());
                int mh = 15 + visibleCount * itemH;
                if (menuY + mh > this.height) {
                    menuY = Math.max(0, this.height - mh - 5);
                }
                if (menuX + mw > this.width) {
                    menuX = Math.max(0, this.width - mw - 5);
                }
                
                return true;
                // AI FIX/ADD STOP
                /*
                isSearching = true; searchQuery = ""; menuX = (int)mouseX; menuY = (int)mouseY; updateSearch(); return true;
                */
            }
        }
        if (button == 2 || (button == 1 && Screen.hasShiftDown())) { isPanning = true; return true; }
        for (int i = graph.getNodes().size() - 1; i >= 0; i--) {
            WNode node = graph.getNodes().get(i);
            int outPin = node.getPinAt(nx - node.getX(), ny - node.getY(), false);
            if (outPin != -1) { linkingNode = node; linkingPin = outPin; return true; }
            if (nx >= node.getX() && nx <= node.getX() + node.getWidth() && ny >= node.getY() && ny <= node.getY() + node.getHeight()) {
                if (!Screen.hasShiftDown() && !node.isSelected()) graph.getNodes().forEach(n -> n.setSelected(false));
                node.setSelected(true);
                selectedNode = node; // Set before element interaction!

                // AI FIX/ADD START
                long currentTime = net.minecraft.Util.getMillis();
                boolean isDoubleClick = (button == 0 && lastClickedNode == node && lastClickButton == 0 && (currentTime - lastClickTime) < 300);
                lastClickedNode = node;
                lastClickTime = currentTime;
                lastClickButton = button;

                int localY = ny - node.getY();
                if (isDoubleClick) {
                    if (localY <= 15) {
                        pushUndo();
                        renamingNode = node;
                        // AI FIX/ADD START
                        renameField = new dev.devce.websnodelib.api.elements.WTextField(node.getWidth() - 10)
                            .setRenderBackground(false)
                            .setTextColor(0xFF00FF88)
                            .setCursorColor(0xFF00FF88);
                        renameField.setValue(node.getTitle());
                        renameField.setCursorToEnd();
                        renameField.setFocused(true); // Force focus without resetting cursor
                        // AI FIX/ADD STOP
                        return true;
                    } else if (node.getTypeId().getPath().equals("function")) {
                        minecraft.setScreen(new WNodeScreen(Component.literal(node.getTitle()), node.getInternalGraph(), (tag) -> {
                            node.getInternalGraph().load(tag);
                            if (this.onSave != null) this.onSave.accept(this.graph.save());
                        }, this));
                        return true;
                    } else if (node.getTypeId().getPath().equals("lua_script")) {
                        minecraft.setScreen(new WLuaEditorScreen(node, this));
                        return true;
                    }
                }
                // AI FIX/ADD STOP

                if (node.mouseClicked(nx - node.getX(), ny - node.getY(), button)) return true;
                
                draggingNode = node; dragOffsetX = nx - node.getX(); dragOffsetY = ny - node.getY();
                graph.getNodes().remove(i); graph.getNodes().add(node); return true;
            }
        }
        if (button == 0) {
            isSelecting = true; selStartX = nx; selStartY = ny; selEndX = nx; selEndY = ny;
            if (!Screen.hasShiftDown()) {
                graph.getNodes().forEach(n -> n.setSelected(false));
                // AI FIX/ADD START
                if (selectedNode != null) selectedNode.mouseClicked(-1000, -1000, button); // Force unfocus elements
                // AI FIX/ADD STOP
            }
            return true;
        }
        // AI FIX/ADD START
        if (selectedNode != null) selectedNode.mouseClicked(-1000, -1000, button);
        // AI FIX/ADD STOP
        selectedNode = null; if (!Screen.hasShiftDown()) graph.getNodes().forEach(n -> n.setSelected(false));
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isResizing) {
            pushUndo();
            isResizing = false; resizingNode = null;
        }
        int nx = (int) getGraphX(mouseX);
        int ny = (int) getGraphY(mouseY);
        if (isSelecting) {
            float x1 = (float)Math.min(selStartX, selEndX); float y1 = (float)Math.min(selStartY, selEndY);
            float x2 = (float)Math.max(selStartX, selEndX); float y2 = (float)Math.max(selStartY, selEndY);
            for (WNode node : graph.getNodes()) {
                if (node.getX() + node.getWidth() >= x1 && node.getX() <= x2 && node.getY() + node.getHeight() >= y1 && node.getY() <= y2) node.setSelected(true);
            }
            isSelecting = false; return true;
        }
        if (linkingNode != null) {
            for (WNode node : graph.getNodes()) {
                int inPin = node.getPinAt(nx - node.getX(), ny - node.getY(), true);
                if (inPin != -1) { 
                    pushUndo(); 
                    graph.connect(linkingNode.getId(), linkingPin, node.getId(), inPin); 
                    if (onSave != null) onSave.accept(graph.save());
                    break; 
                }
            }
        }
        if (selectedNode != null) selectedNode.mouseReleased(nx, ny, button);
        isPanning = false; draggingNode = null; linkingNode = null; linkingPin = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int nx = (int) getGraphX(mouseX);
        int ny = (int) getGraphY(mouseY);

        if (isResizing && resizingNode != null) {
            resizingNode.setWidth(Math.max(40, nx - resizingNode.getX()));
            resizingNode.setHeight(Math.max(40, ny - resizingNode.getY()));
            return true;
        }
        if (isSelecting) {
            selEndX = nx; selEndY = ny; return true;
        }
        if (isPanning) { panX += dragX / zoom; panY += dragY / zoom; return true; }
        if (draggingNode != null) {
            int dx = (int)(nx - dragOffsetX) - draggingNode.getX();
            int dy = (int)(ny - dragOffsetY) - draggingNode.getY();
            
            for (WNode n : graph.getNodes()) {
                if (n.isSelected()) {
                    n.setPos(n.getX() + dx, n.getY() + dy);
                }
            }
            return true;
        }

        if (selectedNode != null && selectedNode.mouseDragged(nx, ny, button, dragX, dragY)) return true;

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeItemPicker != null) {
            if (scrollY > 0 && itemScrollOffset > 0) itemScrollOffset--;
            if (scrollY < 0 && (itemScrollOffset + 1) * 8 < filteredItems.size()) itemScrollOffset++;
            return true;
        }
        if (isSearching) {
            if (scrollY > 0 && searchScrollOffset > 0) searchScrollOffset--;
            if (scrollY < 0 && searchScrollOffset + 12 < filteredItemsList.size()) searchScrollOffset++;
            return true;
        }
        zoom = (float) Math.max(0.1, Math.min(3.0, zoom + scrollY * 0.1)); return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // AI FIX/ADD START
        if (selectedNode != null && selectedNode.keyPressed(keyCode, scanCode, modifiers)) return true;
        // AI FIX/ADD STOP
        
        if (keyCode == 256) { // ESC
            if (renamingNode != null) { renamingNode = null; return true; }
            if (activeItemPicker != null) { activeItemPicker = null; return true; }
            if (isSearching) { isSearching = false; return true; }
            if (isContextMenuOpen) { isContextMenuOpen = false; return true; }
            
            if (parentScreen != null) {
                if (onSave != null) onSave.accept(graph.save());
                // AI FIX/ADD START
                isExiting = true;
                nextScreen = parentScreen;
                parentScreen.isReturning = true;
                // AI FIX/ADD STOP
                /*
                minecraft.setScreen(parentScreen);
                */
                return true;
            }
        }
        
        if (renamingNode != null) {
            if (keyCode == 257 || keyCode == 335) { // Enter
                renamingNode.setTitle(renameField.getValue());
                renamingNode = null;
                return true;
            }
            return renameField.handleKeyPress(keyCode, scanCode, modifiers);
        }
        
        if (keyCode == 291 && renamingNode == null && !isSearching) { // F2
            WNode node = graph.getNodes().stream().filter(WNode::isSelected).findFirst().orElse(null);
            if (node != null) {
                pushUndo();
                renamingNode = node;
                renameField = new dev.devce.websnodelib.api.elements.WTextField(100);
                renameField.setValue(node.getTitle());
                renameField.handleMouseClick(0, 0, 0); // Force focus
                return true;
            }
        }

        if (Screen.hasControlDown()) {
            if (keyCode == 90) { undo(); return true; } // Ctrl+Z
            if (keyCode == 89) { redo(); return true; } // Ctrl+Y
        }
        
        if (keyCode == 67 && renamingNode == null && !isSearching) { // 'C' key for Comment Frame
            List<WNode> selected = graph.getNodes().stream().filter(WNode::isSelected).toList();
            if (!selected.isEmpty()) {
                pushUndo();
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                for (WNode n : selected) {
                    minX = Math.min(minX, n.getX());
                    minY = Math.min(minY, n.getY());
                    maxX = Math.max(maxX, n.getX() + n.getWidth());
                    maxY = Math.max(maxY, n.getY() + n.getHeight());
                }
                int padding = 20;
                WNode frame = dev.devce.websnodelib.api.NodeRegistry.createNode(ResourceLocation.fromNamespaceAndPath("rocketnautics", "frame"), minX - padding, minY - padding - 15);
                if (frame != null) {
                    frame.setWidth(maxX - minX + padding * 2);
                    frame.setHeight(maxY - minY + padding * 2 + 15);
                    graph.addNode(frame);
                    return true;
                }
            }
        }

        if (activeItemPicker != null) {
            if (keyCode == 256) { activeItemPicker = null; return true; } // ESC
            if (keyCode == 259) { // BACKSPACE
                if (!itemSearchQuery.isEmpty()) {
                    itemSearchQuery = itemSearchQuery.substring(0, itemSearchQuery.length() - 1);
                    updateItemSearch();
                }
                return true;
            }
            return true;
        }
        if (isSearching) {
            if (keyCode == 256) { isSearching = false; return true; }
            if (keyCode == 257 || keyCode == 335) { 
                for (SearchItem item : filteredItemsList) {
                    if (!item.isHeader) {
                        addNodeAt(item.type, (int)(menuX - panX), (int)(menuY - panY)); 
                        isSearching = false; 
                        return true; 
                    }
                }
            }
            if (keyCode == 259) { if (!searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); updateSearch(); } return true; }
            return true;
        }

        // AI FIX/ADD START
        // AI FIX/ADD STOP

        // AI FIX/ADD START
        if (keyCode == 261 || keyCode == 259 || keyCode == 88) { 
            List<WNode> selectedNodes = graph.getNodes().stream().filter(WNode::isSelected).toList();
            if (!selectedNodes.isEmpty() || selectedNode != null) {
                pushUndo();
                if (!selectedNodes.isEmpty()) {
                    graph.getNodes().removeIf(WNode::isSelected);
                } else if (selectedNode != null) {
                    graph.removeNode(selectedNode);
                }
                selectedNode = null;
                graph.updateTopology();
                if (onSave != null) onSave.accept(graph.save());
                return true; 
            }
        }
        /*
        if (selectedNode != null && (keyCode == 261 || keyCode == 88)) { 
            graph.removeNode(selectedNode); 
            selectedNode = null; 
            if (onSave != null) onSave.accept(graph.save());
            return true; 
        }
        */
        // AI FIX/ADD STOP
        if (keyCode == 73 && hasControlDown() && hasAltDown()) { spawnSecretNode(); return true; }
        if (keyCode == 65 && hasControlDown()) { graph.getNodes().forEach(n -> n.setSelected(true)); return true; }
        if (keyCode == 67 && hasControlDown()) { copySelected(); return true; }
        if (keyCode == 86 && hasControlDown()) { pasteFromClipboard(); return true; }
        if (keyCode == 65 && Screen.hasShiftDown()) {
            // AI FIX/ADD START
            isSearching = true;
            searchQuery = "";
            menuX = this.mouseX;
            menuY = this.mouseY;
            updateSearch(); 
            
            int mw = 140;
            int itemH = 12;
            int maxVisible = 12;
            int visibleCount = Math.min(maxVisible, filteredItemsList.size());
            int mh = 15 + visibleCount * itemH;
            if (menuY + mh > this.height) {
                menuY = Math.max(0, this.height - mh - 5);
            }
            if (menuX + mw > this.width) {
                menuX = Math.max(0, this.width - mw - 5);
            }
            return true;
            // AI FIX/ADD STOP
            /*
            isSearching = true;
            searchQuery = "";
            menuX = this.mouseX;
            menuY = this.mouseY;
            updateSearch(); return true;
            */
        }
        
        // AI FIX/ADD START
        // Removed to move priority up
        // AI FIX/ADD STOP
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renamingNode != null) return renameField.handleCharTyped(codePoint, modifiers);
        if (activeItemPicker != null) {
            itemSearchQuery += codePoint;
            updateItemSearch();
            return true;
        }
        if (isSearching) {
            // AI FIX/ADD START
            if (searchQuery.isEmpty() && (codePoint == 'A' || codePoint == 'Ф')) return true;
            /*
            if (searchQuery.isEmpty() && (codePoint == 'a' || codePoint == 'A' || codePoint == 'ф' || codePoint == 'Ф')) return true;
            */
            // AI FIX/ADD STOP
            searchQuery += codePoint; updateSearch(); return true;
        }
        if (selectedNode != null && selectedNode.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    public void openItemPicker(dev.devce.websnodelib.api.elements.WItemPicker picker, int mx, int my) {
        this.activeItemPicker = picker;
        this.itemMenuX = mx;
        this.itemMenuY = my;
        this.itemSearchQuery = "";
        this.itemScrollOffset = 0;
        updateItemSearch();
    }

    private void updateItemSearch() {
        filteredItems = net.minecraft.core.registries.BuiltInRegistries.ITEM.stream()
            .filter(item -> item.getDescriptionId().toLowerCase().contains(itemSearchQuery.toLowerCase()) || 
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString().contains(itemSearchQuery.toLowerCase()))
            .collect(java.util.stream.Collectors.toList());
    }

    private void renderItemPickerOverlay(GuiGraphics graphics) {
        int w = 200;
        int h = 160;
        int x = itemMenuX;
        int y = itemMenuY;
        if (x + w > width) x = width - w - 10;
        if (y + h > height) y = height - h - 10;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 8000);
        graphics.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x55000000);
        graphics.fill(x, y, x + w, y + h, 0xEE1A1A1A);
        graphics.renderOutline(x, y, w, h, 0xFF00FF88);
        
        graphics.fill(x + 5, y + 5, x + w - 5, y + 20, 0xFF000000);
        graphics.drawString(font, "> " + itemSearchQuery + "_", x + 10, y + 8, 0xFF00FF88, false);

        int cols = 8;
        int rows = 6;
        int startIdx = itemScrollOffset * cols;
        for (int i = 0; i < cols * rows; i++) {
            int idx = startIdx + i;
            if (idx >= filteredItems.size()) break;
            int ix = x + 10 + (i % cols) * 22;
            int iy = y + 25 + (i / cols) * 22;
            boolean hovered = mouseX >= ix && mouseX <= ix + 20 && mouseY >= iy && mouseY <= iy + 20;
            graphics.fill(ix, iy, ix + 20, iy + 20, hovered ? 0x44FFFFFF : 0x22000000);
            graphics.renderFakeItem(new net.minecraft.world.item.ItemStack(filteredItems.get(idx)), ix + 2, iy + 2);
        }
        graphics.pose().popPose();
    }

    private boolean handleItemPickerClick(double mx, double my, int button) {
        int w = 200;
        int h = 160;
        int x = itemMenuX;
        int y = itemMenuY;
        if (x + w > width) x = width - w - 10;
        if (y + h > height) y = height - h - 10;

        int cols = 8;
        int rows = 6;
        int startIdx = itemScrollOffset * cols;
        for (int i = 0; i < cols * rows; i++) {
            int idx = startIdx + i;
            if (idx >= filteredItems.size()) break;
            int ix = x + 10 + (i % cols) * 22;
            int iy = y + 25 + (i / cols) * 22;
            if (mx >= ix && mx <= ix + 20 && my >= iy && my <= iy + 20) {
                activeItemPicker.setStack(new net.minecraft.world.item.ItemStack(filteredItems.get(idx)));
                activeItemPicker = null;
                return true;
            }
        }
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void addNodeAt(net.minecraft.resources.ResourceLocation type, int x, int y) {
        // AI FIX/ADD START
        pushUndo();
        // AI FIX/ADD STOP
        WNode node = dev.devce.websnodelib.api.NodeRegistry.createNode(type, x, y);
        if (node != null) {
            graph.addNode(node);
            if (onSave != null) onSave.accept(graph.save());
        }
    }

    private void spawnSecretNode() {
        int nx = (int)(-panX + width / 2f); int ny = (int)(-panY + height / 2f);
        WNode node = new WNode(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("websnodelib", "secret"), "Webyep's Gift", nx, ny);
        node.setWidth(160);
        node.addElement(new dev.devce.websnodelib.api.elements.WGif(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("websnodelib", "textures/gui/secret.gif"), 150, 150));
        node.addElement(new dev.devce.websnodelib.api.elements.WLabel("   Made with love from Webyep", 0xFF00FF88));
        graph.addNode(node);
    }

    private void updateSearch() {
        filteredItemsList.clear();
        String query = searchQuery.toLowerCase();
        List<ResourceLocation> all = new ArrayList<>(dev.devce.websnodelib.api.NodeRegistry.getRegisteredTypes());
        
        java.util.Map<String, List<ResourceLocation>> grouped = new java.util.TreeMap<>();
        
        java.util.Set<String> foundTypes = new java.util.HashSet<>();
        if (graph.getContext() instanceof dev.devce.rocketnautics.content.blocks.SputnikBlockEntity sputnik) {
            for (dev.devce.rocketnautics.api.peripherals.IPeripheral p : sputnik.getPeripherals()) {
                if (p != null && p.getPeripheralType() != null) {
                    foundTypes.add(p.getPeripheralType().toLowerCase());
                }
            }
        }
        
        for (net.minecraft.resources.ResourceLocation type : all) {
            String path = type.getPath().toLowerCase();
            String category = dev.devce.websnodelib.api.NodeRegistry.getCategory(type);
            String cleanCategory = category.toLowerCase().replaceAll("[^a-z0-9]", "");
            
            // Context-sensitive filtering
            if (path.equals("vector_control") && !foundTypes.contains("vector_engine")) continue;
            if (path.equals("rcs_control") && !foundTypes.contains("rcs")) continue;
            if (path.equals("booster_control") && !foundTypes.contains("booster")) continue;
            if (path.equals("thruster_control") && !foundTypes.contains("thruster")) continue;
            
            if (query.isEmpty() || path.contains(query) || category.toLowerCase().contains(query) || cleanCategory.contains(query)) {
                grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(type);
            }
        }
        
        // Favorites group
        if (!FAVORITES.isEmpty()) {
            List<ResourceLocation> favs = FAVORITES.stream()
                .filter(all::contains)
                .sorted((a, b) -> a.getPath().compareTo(b.getPath()))
                .toList();
            if (!favs.isEmpty()) {
                filteredItemsList.add(0, new SearchItem(null, "Favorites", true));
                int i = 1;
                for (ResourceLocation type : favs) {
                    filteredItemsList.add(i++, new SearchItem(type, "Favorites", false));
                }
            }
        }
        
        for (java.util.Map.Entry<String, List<ResourceLocation>> entry : grouped.entrySet()) {
            filteredItemsList.add(new SearchItem(null, entry.getKey(), true));
            for (ResourceLocation type : entry.getValue()) {
                filteredItemsList.add(new SearchItem(type, entry.getKey(), false));
            }
        }
        searchScrollOffset = 0;
    }

    private void copySelected() {
        ListTag nodesTag = new ListTag();
        for (WNode node : graph.getNodes()) if (node.isSelected()) nodesTag.add(node.save());
        if (nodesTag.isEmpty()) return;
        CompoundTag root = new CompoundTag(); root.put("nodes", nodesTag);
        ListTag connTag = new ListTag();
        for (WConnection conn : graph.getConnections()) {
            WNode src = findNode(conn.sourceNode()); WNode tgt = findNode(conn.targetNode());
            if (src != null && tgt != null && src.isSelected() && tgt.isSelected()) {
                CompoundTag c = new CompoundTag();
                c.putString("src", conn.sourceNode().toString()); c.putInt("srcP", conn.sourcePin());
                c.putString("tgt", conn.targetNode().toString()); c.putInt("tgtP", conn.targetPin());
                connTag.add(c);
            }
        }
        root.put("conns", connTag);
        minecraft.keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(root.toString().getBytes()));
    }

    private void pasteFromClipboard() {
        String data = minecraft.keyboardHandler.getClipboard(); if (data == null || data.isEmpty()) return;
        try {
            String decoded = new String(Base64.getDecoder().decode(data));
            CompoundTag root = TagParser.parseTag(decoded);
            ListTag nodesTag = root.getList("nodes", 10);
            Map<UUID, UUID> oldToNew = new HashMap<>();
            graph.getNodes().forEach(n -> n.setSelected(false));
            for (int i = 0; i < nodesTag.size(); i++) {
                CompoundTag nTag = nodesTag.getCompound(i);
                net.minecraft.resources.ResourceLocation type = net.minecraft.resources.ResourceLocation.parse(nTag.getString("typeId"));
                WNode newNode = dev.devce.websnodelib.api.NodeRegistry.createNode(type, nTag.getInt("x") + 10, nTag.getInt("y") + 10);
                if (newNode != null) {
                    newNode.load(nTag); 
                    UUID oldId = nTag.hasUUID("id") ? nTag.getUUID("id") : UUID.fromString(nTag.getString("id"));
                    oldToNew.put(oldId, newNode.getId()); 
                    graph.addNode(newNode); 
                    newNode.setSelected(true);
                }
            }
            ListTag connTag = root.getList("conns", 10);
            for (int i = 0; i < connTag.size(); i++) {
                CompoundTag c = connTag.getCompound(i);
                UUID oldSrc = c.hasUUID("src") ? c.getUUID("src") : (c.contains("src") ? UUID.fromString(c.getString("src")) : null);
                UUID oldTgt = c.hasUUID("tgt") ? c.getUUID("tgt") : (c.contains("tgt") ? UUID.fromString(c.getString("tgt")) : null);
                
                UUID newSrc = oldToNew.get(oldSrc);
                UUID newTgt = oldToNew.get(oldTgt);
                if (newSrc != null && newTgt != null) graph.connect(newSrc, c.getInt("srcP"), newTgt, c.getInt("tgtP"));
            }
        } catch (Exception e) {}
    }

    private void openContextMenu(int mx, int my) {
        isContextMenuOpen = true;
        ctxMenuX = mx;
        ctxMenuY = my;
        currentActions.clear();
        
        long selectedCount = graph.getNodes().stream().filter(WNode::isSelected).count();
        
        if (selectedCount > 0) {
            if (selectedCount == 1) {
                WNode node = graph.getNodes().stream().filter(WNode::isSelected).findFirst().orElse(null);
                if (node != null && node.getTypeId().getPath().equals("function")) {
                    currentActions.add(new ContextAction("§eOpen Graph", () -> {
                        minecraft.setScreen(new WNodeScreen(Component.literal(node.getTitle()), node.getInternalGraph(), (tag) -> {
                            node.getInternalGraph().load(tag);
                            // Important: Propagate save to parent screen/server
                            if (this.onSave != null) this.onSave.accept(this.graph.save());
                        }, this));
                    }, false));
                }
            }
            
            currentActions.add(new ContextAction("Copy", this::copySelected, false));
            currentActions.add(new ContextAction("Rename", () -> {
                WNode node = graph.getNodes().stream().filter(WNode::isSelected).findFirst().orElse(null);
                if (node != null) {
                    pushUndo();
                    renamingNode = node;
                    // AI FIX/ADD START
                    renameField = new dev.devce.websnodelib.api.elements.WTextField(node.getWidth() - 10)
                        .setRenderBackground(false)
                        .setTextColor(0xFF00FF88)
                        .setCursorColor(0xFF00FF88);
                    renameField.setValue(node.getTitle());
                    renameField.setCursorToEnd();
                    renameField.setFocused(true); // Force focus without resetting cursor
                    // AI FIX/ADD STOP
                }
            }, false));
            currentActions.add(new ContextAction("Delete", () -> {
                pushUndo();
                graph.getNodes().removeIf(WNode::isSelected);
                graph.updateTopology();
            }, true));
            
            if (selectedCount > 1) {
                currentActions.add(new ContextAction("§bZip to Function", this::zipToFunction, false));
            }
        }
        
        // AI FIX/ADD START
        int itemH = 14;
        int mh = currentActions.size() * itemH + 4;
        int mw = 120;
        if (ctxMenuY + mh > this.height) {
            ctxMenuY = Math.max(0, this.height - mh - 5);
        }
        if (ctxMenuX + mw > this.width) {
            ctxMenuX = Math.max(0, this.width - mw - 5);
        }
        // AI FIX/ADD STOP
    }

    private void pushUndo() {
        undoStack.addFirst(graph.save());
        if (undoStack.size() > 50) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.addFirst(graph.save());
        graph.load(undoStack.removeFirst());
        graph.updateTopology();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.addFirst(graph.save());
        graph.load(redoStack.removeFirst());
        graph.updateTopology();
    }

    private void zipToFunction() {
        pushUndo();
        List<WNode> selected = graph.getNodes().stream().filter(WNode::isSelected).toList();
        if (selected.isEmpty()) return;

        // Find average position
        int avgX = (int) selected.stream().mapToInt(WNode::getX).average().orElse(0);
        int avgY = (int) selected.stream().mapToInt(WNode::getY).average().orElse(0);

        // Create the encapsulation node
        WNode funcNode = dev.devce.websnodelib.api.NodeRegistry.createNode(ResourceLocation.fromNamespaceAndPath("rocketnautics", "function"), avgX, avgY);
        if (funcNode == null) return;
        
        WGraph subGraph = funcNode.getInternalGraph();
        
        // Move nodes and their internal connections
        for (WNode n : selected) {
            subGraph.addNode(n);
            graph.getNodes().remove(n);
        }
        
        // Move connections that are entirely within the selection
        List<WConnection> internalConns = graph.getConnections().stream()
            .filter(c -> selected.stream().anyMatch(n -> n.getId().equals(c.sourceNode())) && 
                         selected.stream().anyMatch(n -> n.getId().equals(c.targetNode())))
            .toList();
            
        for (WConnection c : internalConns) {
            subGraph.connect(c.sourceNode(), c.sourcePin(), c.targetNode(), c.targetPin());
            graph.getConnections().remove(c);
        }
        
        graph.addNode(funcNode);
        graph.updateTopology();
        if (onSave != null) onSave.accept(graph.save());
        isContextMenuOpen = false;
        
        // Feedback message
        minecraft.gui.getChat().addMessage(Component.literal("§aSuccessfully zipped " + selected.size() + " nodes into a Function."));
    }
}
