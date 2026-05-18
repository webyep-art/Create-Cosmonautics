package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.SkyDataHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class RocketSettingsScreen extends Screen {
    private final Screen lastScreen;
    public enum ScreenTab { CLIENT, SERVER }
    private ScreenTab activeTab = ScreenTab.CLIENT;
    
    private float screenAnimation = 0.0f;
    private long lastFrameTime = 0;

    public RocketSettingsScreen(Screen lastScreen) {
        super(Component.literal("RocketNautics Systems Configuration"));
        this.lastScreen = lastScreen;
    }

    private class NodeButton extends Button {
        private final int accentColor;

        public NodeButton(int x, int y, int width, int height, Component message, OnPress onPress, int accentColor) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.accentColor = accentColor;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = hovered ? 0xFF2A2A2A : 0xFF1A1A1A;
            int borderColor = hovered ? accentColor : 0xFF444444;
            int textColor = hovered ? 0xFFFFFFFF : 0xFFAAAAAA;

            // Background
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            // Border
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);
            
            // Text
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 9) / 2, textColor);
            
            if (hovered && active) {
                // Glow effect on top
                graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, accentColor);
            }
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int x = this.width / 2;
        int y = 60;

        if (activeTab == ScreenTab.CLIENT) {
            initClientSettings(x, y);
        } else {
            initServerSettings(x, y);
        }

        // Back Button
        this.addRenderableWidget(new NodeButton(this.width / 2 - 100, this.height - 35, 200, 20, 
            Component.translatable("gui.done"), b -> this.minecraft.setScreen(this.lastScreen), 0xFF00FF88));
    }

    private void addNodeConfig(int x, int y, String title, int color, java.util.function.Consumer<NodeConfigBuilder> setup) {
        int width = 200;
        NodeConfigBuilder builder = new NodeConfigBuilder(x - width / 2, y + 20, color);
        setup.accept(builder);
    }

    private void initClientSettings(int x, int y) {
        addNodeConfig(x, y, "Visuals", 0xFF00FF88, builder -> {
            builder.addToggle("Debug Overlay", RocketConfig.CLIENT.showDebugOverlay.get(), val -> {
                RocketConfig.CLIENT.showDebugOverlay.set(val);
                RocketConfig.CLIENT.showDebugOverlay.save();
            });
            builder.addToggle("Dynamic Render Distance", RocketConfig.CLIENT.enableDynamicRenderDistance.get(), val -> {
                RocketConfig.CLIENT.enableDynamicRenderDistance.set(val);
                RocketConfig.CLIENT.enableDynamicRenderDistance.save();
            });
            builder.addSlider("Camera Shake Intensity", RocketConfig.CLIENT.shakeIntensity.get(), 0.0, 2.0, val -> {
                RocketConfig.CLIENT.shakeIntensity.set(val);
                RocketConfig.CLIENT.shakeIntensity.save();
            });
            builder.addSlider("Shake Radius (m)", RocketConfig.CLIENT.shakeRadius.get(), 4.0, 32.0, val -> {
                RocketConfig.CLIENT.shakeRadius.set(val);
                RocketConfig.CLIENT.shakeRadius.save();
            });
            builder.addSlider("Maximum Planet Render Scale", RocketConfig.CLIENT.planetRenderMaximumScale.get(), SkyDataHandler.MIN_POWER_SIZE, 100, val -> {
                RocketConfig.CLIENT.planetRenderMaximumScale.set(val.intValue());
                RocketConfig.CLIENT.planetRenderMaximumScale.save();
            });
        });
    }

    private void initServerSettings(int x, int y) {
        boolean isLocal = this.minecraft.getSingleplayerServer() != null;
        if (!isLocal) return;

        addNodeConfig(x, y, "Physics & Logic", 0xFFFFAA00, builder -> {
            builder.addSlider("Max Fuel (mB/t)", RocketConfig.SERVER.maxFuelConsumption.get().doubleValue(), 10, 200, val -> {
                RocketConfig.SERVER.maxFuelConsumption.set(val.intValue());
                RocketConfig.SERVER.maxFuelConsumption.save();
            });
            builder.addSlider("Jetpack Power", RocketConfig.SERVER.jetpackThrust.get(), 0.05, 0.5, val -> {
                RocketConfig.SERVER.jetpackThrust.set(val);
                RocketConfig.SERVER.jetpackThrust.save();
            });
            builder.addSlider("Sprint Power", RocketConfig.SERVER.jetpackSprintThrust.get(), 0.1, 1.0, val -> {
                RocketConfig.SERVER.jetpackSprintThrust.set(val);
                RocketConfig.SERVER.jetpackSprintThrust.save();
            });
            builder.addSlider("Ignition Flow", RocketConfig.SERVER.ignitionFlow.get().doubleValue(), 1, 20, val -> {
                RocketConfig.SERVER.ignitionFlow.set(val.intValue());
                RocketConfig.SERVER.ignitionFlow.save();
            });
            builder.addToggle("Engine Debug Logs", RocketConfig.SERVER.enableEngineDebugLogging.get(), val -> {
                RocketConfig.SERVER.enableEngineDebugLogging.set(val);
                RocketConfig.SERVER.enableEngineDebugLogging.save();
            });
        });
    }

    private class NodeSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
        private final int accentColor;
        private final String prefix;
        private final double min, max;
        private final java.util.function.Consumer<Double> onValueChange;

        public NodeSlider(int x, int y, int width, int height, String prefix, double current, double min, double max, int accentColor, java.util.function.Consumer<Double> onValueChange) {
            super(x, y, width, height, Component.literal(prefix), (current - min) / (max - min));
            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.accentColor = accentColor;
            this.onValueChange = onValueChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double val = min + value * (max - min);
            setMessage(Component.literal(prefix + ": " + String.format("%.2f", val)));
        }

        @Override
        protected void applyValue() {
            onValueChange.accept(min + value * (max - min));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            
            boolean hovered = this.isHoveredOrFocused();
            int bgColor = 0xFF1A1A1A;
            int borderColor = hovered ? accentColor : 0xFF444444;

            // Background
            graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
            
            // Slider fill
            int fillW = (int) (this.value * (this.width - 2));
            graphics.fill(this.getX() + 1, this.getY() + 1, this.getX() + 1 + fillW, this.getY() + this.height - 1, (accentColor & 0x44FFFFFF));
            graphics.fill(this.getX() + fillW, this.getY() + 2, this.getX() + fillW + 2, this.getY() + this.height - 2, accentColor);
            
            // Border
            graphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);
            
            // Text
            int textColor = hovered ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 9) / 2, textColor);
        }
    }

    private class NodeConfigBuilder {
        private final int startX;
        private int currentY;
        private final int accent;

        public NodeConfigBuilder(int x, int y, int accent) {
            this.startX = x;
            this.currentY = y;
            this.accent = accent;
        }

        public void addToggle(String name, boolean initial, java.util.function.Consumer<Boolean> callback) {
            addRenderableWidget(new NodeButton(startX + 10, currentY, 180, 20, 
                Component.literal(name + ": " + (initial ? "ON" : "OFF")), 
                btn -> {
                    boolean isCurrentlyOn = btn.getMessage().getString().contains("ON");
                    boolean next = !isCurrentlyOn;
                    callback.accept(next);
                    btn.setMessage(Component.literal(name + ": " + (next ? "ON" : "OFF")));
                }, accent));
            currentY += 25;
        }

        public void addSlider(String name, double initial, double min, double max, java.util.function.Consumer<Double> callback) {
            addRenderableWidget(new NodeSlider(startX + 10, currentY, 180, 20, name, initial, min, max, accent, callback));
            currentY += 25;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = net.minecraft.Util.getMillis();
        float deltaTime = (lastFrameTime == 0) ? 0.016f : (now - lastFrameTime) / 1000f;
        lastFrameTime = now;
        screenAnimation = Math.min(1.0f, screenAnimation + deltaTime * 4.0f);

        graphics.fill(0, 0, width, height, 0xFF121212);
        
        // Grid
        int gridSize = 20;
        for (int i = 0; i < width; i += gridSize) graphics.fill(i, 0, i + 1, height, 0x1AFFFFFF);
        for (int i = 0; i < height; i += gridSize) graphics.fill(0, i, width, i + 1, 0x1AFFFFFF);
        
        // Scanlines
        for (int i = 0; i < height; i += 2) graphics.fill(0, i, width, i + 1, 0x0A000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        graphics.pose().pushPose();
        // Remove scaling to fix mouse interaction mismatch with widgets
        
        // Header
        graphics.fill(0, 0, width, 24, 0xFF121212);
        graphics.fill(0, 23, width, 24, 0xFF2A2A2A);

        renderTabs(graphics, mouseX, mouseY);

        // Node Panel Background
        int pw = 220;
        int ph = 200;
        int px = width / 2 - pw / 2;
        int py = 50;
        
        graphics.fill(px + 2, py + 2, px + pw + 2, py + ph + 2, 0xAA000000); // Shadow
        graphics.fill(px, py, px + pw, py + ph, 0xDD1A1A1A);
        
        int accentColor = activeTab == ScreenTab.CLIENT ? 0xFF00FF88 : 0xFFFFAA00;
        graphics.fill(px, py, px + pw, py + 1, accentColor);
        graphics.renderOutline(px, py, pw, ph, 0xFF444444);
        
        String title = activeTab == ScreenTab.CLIENT ? "§lCLIENT MODULE" : "§lSERVER KERNEL";
        graphics.drawCenteredString(font, title, px + pw / 2, py + 5, 0xFFE0E0E0);

        if (activeTab == ScreenTab.SERVER && this.minecraft.getSingleplayerServer() == null) {
            graphics.drawCenteredString(font, "§cRemote Server Detected", width / 2, py + 60, 0xFFFFFF);
            graphics.drawCenteredString(font, "§7Physics settings are managed", width / 2, py + 75, 0x888888);
            graphics.drawCenteredString(font, "§7by the server host.", width / 2, py + 85, 0x888888);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int tx = 15;
        drawTab(graphics, "CLIENT", ScreenTab.CLIENT, tx, mouseX, mouseY);
        tx += font.width("CLIENT") + 25;
        drawTab(graphics, "SERVER", ScreenTab.SERVER, tx, mouseX, mouseY);
    }

    private void drawTab(GuiGraphics graphics, String label, ScreenTab tab, int x, int mouseX, int mouseY) {
        int tw = font.width(label);
        boolean hover = mouseX >= x && mouseX <= x + tw && mouseY >= 4 && mouseY <= 20;
        boolean active = activeTab == tab;
        int color = active ? 0xFF00FF88 : (hover ? 0xFFFFFFFF : 0xFF888888);
        graphics.drawString(font, "§l" + label, x, 8, color);
        if (active) graphics.fill(x - 2, 22, x + tw + 2, 24, 0xFF00FF88);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int tx = 15;
        if (checkTabClick("CLIENT", ScreenTab.CLIENT, tx, mouseX, mouseY)) return true;
        tx += font.width("CLIENT") + 25;
        if (checkTabClick("SERVER", ScreenTab.SERVER, tx, mouseX, mouseY)) return true;
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean checkTabClick(String label, ScreenTab tab, int x, double mouseX, double mouseY) {
        int tw = font.width(label);
        if (mouseX >= x && mouseX <= x + tw && mouseY >= 4 && mouseY <= 20) {
            if (activeTab != tab) {
                activeTab = tab;
                this.init(this.minecraft, this.width, this.height);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }
}
