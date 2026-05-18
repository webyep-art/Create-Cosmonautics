package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class WTextField extends WElement {
    private String value = "";
    private boolean focused = false;
    // AI FIX/ADD START
    private String originalValue = "";
    private int cursorPos = 0;
    private int selectionPos = 0;
    
    // Style settings
    private boolean renderBackground = true;
    private int textColor = 0xFFFFFFFF;
    private int cursorColor = 0xFFFFFFFF;
    private int focusedOutlineColor = 0xFFFFFFFF;
    private int unfocusedOutlineColor = 0xFF888888;

    public WTextField setRenderBackground(boolean render) { this.renderBackground = render; return this; }
    public WTextField setTextColor(int color) { this.textColor = color; return this; }
    public WTextField setCursorColor(int color) { this.cursorColor = color; return this; }
    public WTextField setFocusedOutlineColor(int color) { this.focusedOutlineColor = color; return this; }
    // AI FIX/ADD STOP

    public WTextField(int width) {
        this.width = width;
        this.height = 12;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        // AI FIX/ADD START
        if (renderBackground) {
            graphics.fill(x, y, x + width, y + height, 0xFF000000);
            graphics.renderOutline(x, y, width, height, focused ? focusedOutlineColor : unfocusedOutlineColor);
        }
        
        var font = Minecraft.getInstance().font;
        
        // Render selection
        if (focused && selectionPos != cursorPos) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            int selX1 = x + 2 + font.width(value.substring(0, start));
            int selX2 = x + 2 + font.width(value.substring(0, end));
            selX2 = Math.min(selX2, x + width - 2);
            if (selX1 < x + width - 2) {
                graphics.fill(selX1, y + 2, selX2, y + height - 2, 0x6600FF88);
            }
        }

        // Render text
        String visibleText = value;
        if (font.width(value) > width - 4) {
            visibleText = font.plainSubstrByWidth(value, width - 4);
        }
        graphics.drawString(font, visibleText, x + 2, y + 2, textColor, false);

        // Render cursor
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + 2 + font.width(value.substring(0, Math.min(cursorPos, value.length())));
            if (cursorX < x + width - 2) {
                graphics.drawString(font, "_", cursorX, y + 2, cursorColor, false);
            }
        }
        // AI FIX/ADD STOP
    }

    // AI FIX/ADD START
    public WTextField setFocused(boolean focused) {
        if (focused && !this.focused) {
            this.originalValue = value;
        }
        this.focused = focused;
        return this;
    }
    // AI FIX/ADD STOP

    @Override
    public boolean handleMouseClick(double localX, double localY, int button) {
        // AI FIX/ADD START
        boolean previouslyFocused = focused;
        focused = localX >= 0 && localX <= width && localY >= 0 && localY <= height;
        
        if (focused) {
            if (!previouslyFocused) {
                originalValue = value;
                cursorPos = value.length();
                selectionPos = cursorPos;
            }
            var font = Minecraft.getInstance().font;
            cursorPos = 0;
            int currentX = 0;
            for (int i = 0; i < value.length(); i++) {
                int charWidth = font.width(String.valueOf(value.charAt(i)));
                if (localX < currentX + charWidth / 2.0 + 2) break;
                currentX += charWidth;
                cursorPos = i + 1;
            }
            long handle = Minecraft.getInstance().getWindow().getWindow();
            boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                           GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            
            if (!shift) {
                selectionPos = cursorPos;
            }
        }
        
        return focused;
        // AI FIX/ADD STOP
    }

    // AI FIX/ADD START
    public void setCursorToEnd() {
        this.cursorPos = value.length();
        this.selectionPos = cursorPos;
    }
    // AI FIX/ADD STOP

    @Override
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        
        // AI FIX/ADD START
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & (Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL)) != 0;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            focused = false; 
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            value = originalValue; 
            focused = false;
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_A && ctrl) {
            selectionPos = 0;
            cursorPos = value.length();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (ctrl) {
                cursorPos = findNextWord(false);
            } else {
                cursorPos = Math.max(0, cursorPos - 1);
            }
            if (!shift) selectionPos = cursorPos;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (ctrl) {
                cursorPos = findNextWord(true);
            } else {
                cursorPos = Math.min(value.length(), cursorPos + 1);
            }
            if (!shift) selectionPos = cursorPos;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursorPos = 0;
            if (!shift) selectionPos = cursorPos;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            cursorPos = value.length();
            if (!shift) selectionPos = cursorPos;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectionPos != cursorPos) {
                deleteSelection();
            } else if (cursorPos > 0) {
                value = value.substring(0, cursorPos - 1) + value.substring(cursorPos);
                cursorPos--;
                selectionPos = cursorPos;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (selectionPos != cursorPos) {
                deleteSelection();
            } else if (cursorPos < value.length()) {
                value = value.substring(0, cursorPos) + value.substring(cursorPos + 1);
            }
            return true;
        }

        return true; 
        // AI FIX/ADD STOP
    }

    private int findNextWord(boolean forward) {
        if (value.isEmpty()) return 0;
        int pos = cursorPos;
        if (forward) {
            if (pos >= value.length()) return value.length();
            while (pos < value.length() && value.charAt(pos) != ' ') pos++;
            while (pos < value.length() && value.charAt(pos) == ' ') pos++;
            return pos;
        } else {
            if (pos <= 0) return 0;
            pos--;
            while (pos > 0 && value.charAt(pos) == ' ') pos--;
            while (pos > 0 && value.charAt(pos - 1) != ' ') pos--;
            return pos;
        }
    }

    private void deleteSelection() {
        int start = Math.min(cursorPos, selectionPos);
        int end = Math.max(cursorPos, selectionPos);
        value = value.substring(0, start) + value.substring(end);
        cursorPos = start;
        selectionPos = cursorPos;
    }

    @Override
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!focused) return false;
        if (selectionPos != cursorPos) {
            deleteSelection();
        }
        value = value.substring(0, cursorPos) + codePoint + value.substring(cursorPos);
        cursorPos++;
        selectionPos = cursorPos;
        return true;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.cursorPos = Mth.clamp(this.cursorPos, 0, value.length());
        this.selectionPos = Mth.clamp(this.selectionPos, 0, value.length());
    }
    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("value", value);
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        this.value = tag.getString("value");
        this.cursorPos = value.length();
        this.selectionPos = cursorPos;
    }
}
