package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;

public class WItemPicker extends WElement {
    private ItemStack stack = ItemStack.EMPTY;
    private int borderColor = 0xFF00FF88;

    public WItemPicker() {
        this.width = 20;
        this.height = 20;
    }

    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack.copy();
    }

    public int getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    @Override
    public void render(GuiGraphics g, int x, int y, int mx, int my, float pt) {
        boolean hovered = mx >= x && mx <= x + width && my >= y && my <= y + height;
        
        // Background
        g.fill(x, y, x + width, y + height, hovered ? 0x66000000 : 0x44000000);
        
        // Border
        g.renderOutline(x, y, width, height, borderColor);
        if (hovered) g.renderOutline(x - 1, y - 1, width + 2, height + 2, 0xFFFFFFFF);
        
        if (!stack.isEmpty()) {
            g.renderFakeItem(stack, x + 2, y + 2);
            if (hovered) {
                g.renderTooltip(Minecraft.getInstance().font, stack, mx, my);
            }
        } else if (hovered) {
            g.drawString(Minecraft.getInstance().font, "?", x + 8, y + 6, 0x88AAAAAA, false);
        }
    }

    @Override
    public boolean handleMouseClick(double mx, double my, int button) {
        if (mx >= 0 && mx <= width && my >= 0 && my <= height) {
            Minecraft mc = Minecraft.getInstance();
            if (button == 1) { // Right click to clear
                this.stack = ItemStack.EMPTY;
                return true;
            }
            
            if (mc.screen instanceof dev.devce.websnodelib.client.ui.WNodeScreen screen) {
                // We need the screen coordinates of the element
                // Since handleMouseClick is called with element-local coordinates,
                // we'll use the last mouse position as the anchor point for the menu.
                screen.openItemPicker(this, (int)mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth(), 
                                            (int)mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight());
            }
            return true;
        }
        return false;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        if (!stack.isEmpty()) {
            tag.put("item", stack.saveOptional(Minecraft.getInstance().level.registryAccess()));
        }
        tag.putInt("borderColor", borderColor);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("item")) {
            this.stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), tag.getCompound("item"));
        }
        if (tag.contains("borderColor")) {
            this.borderColor = tag.getInt("borderColor");
        }
    }
}
