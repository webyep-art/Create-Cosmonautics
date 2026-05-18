package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

public class WLabel extends WElement {
    private String text;
    private int color;

    public WLabel(String text) {
        this(text, 0xFFFFFFFF);
    }

    public WLabel(String text, int color) {
        this.text = text;
        this.color = color;
        this.width = Minecraft.getInstance().font.width(text);
        this.height = 10;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(Minecraft.getInstance().font, text, x, y, color);
    }

    public void setText(String text) {
        this.text = text;
        this.width = Minecraft.getInstance().font.width(text);
    }
}
