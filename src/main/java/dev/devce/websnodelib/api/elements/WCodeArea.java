package dev.devce.websnodelib.api.elements;

import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Multi-line code editor widget with Lua syntax highlighting.
 */
public class WCodeArea extends WElement {

    // ---- Syntax highlighting colors (One Dark-ish palette) ----
    private static final int COL_DEFAULT  = 0xFFABB2BF; // light gray
    private static final int COL_KEYWORD  = 0xFFC678DD; // purple
    private static final int COL_API      = 0xFF61AFEF; // blue
    private static final int COL_STRING   = 0xFF98C379; // green
    private static final int COL_COMMENT  = 0xFF5C6370; // dark gray
    private static final int COL_NUMBER   = 0xFFD19A66; // orange
    private static final int COL_OPERATOR = 0xFF56B6C2; // cyan

    private static final Set<String> KEYWORDS = Set.of(
        "local", "if", "then", "else", "elseif", "end", "function", "return",
        "for", "while", "do", "repeat", "until", "break", "not", "and", "or",
        "true", "false", "nil", "in"
    );

    private static final Set<String> API_FUNCS = Set.of(
        "input", "output", "print", "math", "string", "table", "type",
        "tostring", "tonumber", "pairs", "ipairs", "pcall", "error"
    );

    // ---- State ----
    private List<String> lines = new ArrayList<>();
    private int cursorX = 0;
    private int cursorY = 0;
    private boolean focused = false;
    private int scrollOffset = 0; // vertical scroll in lines
    private int selectionStartX = -1, selectionStartY = -1; // -1 means no selection
    private boolean isSelectingMouse = false;

    public WCodeArea(int width, int height) {
        this.width = width;
        this.height = height;
        this.lines.add("");
    }

    // -----------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int fontH = font.lineHeight + 1;
        int visibleLines = (height - 4) / fontH;

        // Background (Unified)
        graphics.fill(x, y, x + width, y + height, 0xFF0D0D0D);

        // Line numbers gutter (No harsh border)
        int gutterW = 22;
        graphics.fill(x, y, x + gutterW, y + height, 0xFF0A0A0A);
        graphics.fill(x + gutterW, y, x + gutterW + 1, y + height, 0xFF141414);

        int clampedScroll = Math.max(0, Math.min(scrollOffset, Math.max(0, lines.size() - visibleLines)));
        scrollOffset = clampedScroll;

        for (int i = 0; i < visibleLines; i++) {
            int lineIdx = i + scrollOffset;
            int lineY = y + 2 + i * fontH;

            if (lineIdx >= lines.size()) break;

            // Line number (Subtle color)
            String lineNum = String.valueOf(lineIdx + 1);
            int numX = x + gutterW - 4 - font.width(lineNum);
            graphics.drawString(font, lineNum, numX, lineY, 0xFF444444, false);

            // Highlight current line bg
            if (lineIdx == cursorY && focused) {
                graphics.fill(x + gutterW + 1, lineY - 1, x + width, lineY + fontH, 0x18FFFFFF);
            }

            // Selection rendering (behind text)
            if (hasSelection()) {
                renderSelectionForLine(font, graphics, lineIdx, x + gutterW + 4, lineY, fontH);
            }

            // Render highlighted code
            renderHighlightedLine(font, graphics, lines.get(lineIdx), x + gutterW + 4, lineY);

            // Cursor
            if (focused && lineIdx == cursorY && (System.currentTimeMillis() / 500) % 2 == 0) {
                String before = lines.get(lineIdx).substring(0, Math.min(cursorX, lines.get(lineIdx).length()));
                int cursorPx = font.width(before);
                graphics.fill(x + gutterW + 4 + cursorPx, lineY, x + gutterW + 5 + cursorPx, lineY + fontH, 0xFF00FF88);
            }
        }

        // Scrollbar
        if (lines.size() > visibleLines) {
            float ratio = (float) scrollOffset / (lines.size() - visibleLines);
            int barH = Math.max(8, height * visibleLines / lines.size());
            int barY = (int) (ratio * (height - barH));
            graphics.fill(x + width - 3, y + barY, x + width - 1, y + barY + barH, 0xFF555555);
        }
    }

    private void renderSelectionForLine(net.minecraft.client.gui.Font font, GuiGraphics graphics, int lineIdx, int startX, int startY, int fontH) {
        int sY = selectionStartY, sX = selectionStartX, eY = cursorY, eX = cursorX;
        if (sY > eY || (sY == eY && sX > eX)) {
            int ty = sY; sY = eY; eY = ty;
            int tx = sX; sX = eX; eX = tx;
        }

        if (lineIdx < sY || lineIdx > eY) return;

        String line = lines.get(lineIdx);
        int selStart = (lineIdx == sY) ? sX : 0;
        int selEnd = (lineIdx == eY) ? eX : line.length();

        selStart = Math.max(0, Math.min(selStart, line.length()));
        selEnd = Math.max(0, Math.min(selEnd, line.length()));

        if (selStart == selEnd && sY == eY) return;

        int x1 = font.width(line.substring(0, selStart));
        int x2 = font.width(line.substring(0, selEnd));
        graphics.fill(startX + x1, startY, startX + x2 + (lineIdx < eY ? 4 : 0), startY + fontH, 0x604444FF);
    }

    private boolean hasSelection() {
        return selectionStartX != -1 && (selectionStartX != cursorX || selectionStartY != cursorY);
    }

    private void clearSelection() {
        selectionStartX = -1;
        selectionStartY = -1;
    }

    private void startSelection() {
        if (!hasSelection()) {
            selectionStartX = cursorX;
            selectionStartY = cursorY;
        }
    }

    /** Render one line with Lua syntax highlighting token-by-token. */
    private void renderHighlightedLine(net.minecraft.client.gui.Font font, GuiGraphics graphics, String line, int x, int y) {
        int i = 0;
        int curX = x;

        while (i < line.length()) {
            char c = line.charAt(i);

            // Comment: -- ...
            if (c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-') {
                String rest = line.substring(i);
                graphics.drawString(font, rest, curX, y, COL_COMMENT, false);
                return;
            }

            // String literal: "..." or '...'
            if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < line.length() && line.charAt(end) != c) {
                    if (line.charAt(end) == '\\') end++; // skip escape
                    end++;
                }
                end = Math.min(end + 1, line.length());
                String str = line.substring(i, end);
                graphics.drawString(font, str, curX, y, COL_STRING, false);
                curX += font.width(str);
                i = end;
                continue;
            }

            // Number
            if (Character.isDigit(c) || (c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {
                int end = i;
                while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.' || line.charAt(end) == 'e' || line.charAt(end) == 'E')) end++;
                String num = line.substring(i, end);
                graphics.drawString(font, num, curX, y, COL_NUMBER, false);
                curX += font.width(num);
                i = end;
                continue;
            }

            // Identifier / keyword / api function
            if (Character.isLetter(c) || c == '_') {
                int end = i;
                while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) end++;
                String word = line.substring(i, end);
                int color;
                if (KEYWORDS.contains(word))   color = COL_KEYWORD;
                else if (API_FUNCS.contains(word)) color = COL_API;
                else color = COL_DEFAULT;
                graphics.drawString(font, word, curX, y, color, false);
                curX += font.width(word);
                i = end;
                continue;
            }

            // Operators
            if ("+-*/^%<>=~#&|".indexOf(c) >= 0) {
                String ch = String.valueOf(c);
                graphics.drawString(font, ch, curX, y, COL_OPERATOR, false);
                curX += font.width(ch);
                i++;
                continue;
            }

            // Default
            String ch = String.valueOf(c);
            graphics.drawString(font, ch, curX, y, COL_DEFAULT, false);
            curX += font.width(ch);
            i++;
        }
    }

    // -----------------------------------------------------------
    //  Input handling
    // -----------------------------------------------------------

    @Override
    public boolean handleMouseClick(double localX, double localY, int button) {
        boolean wasInside = localX >= 0 && localX <= width && localY >= 0 && localY <= height;
        focused = wasInside;
        if (!focused) return false;

        var font = Minecraft.getInstance().font;
        int fontH = font.lineHeight + 1;
        int gutterW = 22;

        int clicked = (int) (localY - 2) / fontH + scrollOffset;
        cursorY = Math.max(0, Math.min(clicked, lines.size() - 1));

        // Find closest char in line
        String line = lines.get(cursorY);
        int cx = 0, bestDist = Integer.MAX_VALUE;
        for (int k = 0; k <= line.length(); k++) {
            int w = font.width(line.substring(0, k));
            int dist = Math.abs(w - ((int) localX - gutterW - 4));
            if (dist < bestDist) { bestDist = dist; cx = k; }
        }
        cursorX = cx;

        if (Screen.hasShiftDown()) {
            startSelection();
        } else {
            clearSelection();
        }
        isSelectingMouse = true;
        return true;
    }

    @Override
    public boolean handleMouseDrag(double localX, double localY, int button, double dragX, double dragY) {
        if (!isSelectingMouse) return false;
        var font = Minecraft.getInstance().font;
        int fontH = font.lineHeight + 1;
        int gutterW = 22;

        startSelection();

        int clicked = (int) (localY - 2) / fontH + scrollOffset;
        cursorY = Math.max(0, Math.min(clicked, lines.size() - 1));

        String line = lines.get(cursorY);
        int cx = 0, bestDist = Integer.MAX_VALUE;
        for (int k = 0; k <= line.length(); k++) {
            int w = font.width(line.substring(0, k));
            int dist = Math.abs(w - ((int) localX - gutterW - 4));
            if (dist < bestDist) { bestDist = dist; cx = k; }
        }
        cursorX = cx;
        return true;
    }

    @Override
    public boolean handleMouseRelease(double localX, double localY, int button) {
        isSelectingMouse = false;
        return true;
    }

    @Override
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;

        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl) {
            if (keyCode == GLFW.GLFW_KEY_C) { copySelection(); return true; }
            if (keyCode == GLFW.GLFW_KEY_V) { pasteFromClipboard(); return true; }
            if (keyCode == GLFW.GLFW_KEY_X) { cutSelection(); return true; }
            if (keyCode == GLFW.GLFW_KEY_A) { selectAll(); return true; }
        }

        if (shift && isNavKey(keyCode)) {
            startSelection();
        } else if (!shift && isNavKey(keyCode)) {
            clearSelection();
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER -> {
                if (hasSelection()) deleteSelection();
                String cur = lines.get(cursorY);
                int indent = 0;
                while (indent < cur.length() && cur.charAt(indent) == ' ') indent++;
                String after = cur.substring(cursorX);
                lines.set(cursorY, cur.substring(0, cursorX));
                lines.add(cursorY + 1, " ".repeat(indent) + after);
                cursorY++;
                cursorX = indent;
                ensureCursorVisible();
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (hasSelection()) deleteSelection();
                String cur = lines.get(cursorY);
                lines.set(cursorY, cur.substring(0, cursorX) + "  " + cur.substring(cursorX));
                cursorX += 2;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) { deleteSelection(); return true; }
                if (cursorX > 0) {
                    String cur = lines.get(cursorY);
                    lines.set(cursorY, cur.substring(0, cursorX - 1) + cur.substring(cursorX));
                    cursorX--;
                } else if (cursorY > 0) {
                    int prevLen = lines.get(cursorY - 1).length();
                    lines.set(cursorY - 1, lines.get(cursorY - 1) + lines.get(cursorY));
                    lines.remove(cursorY);
                    cursorY--;
                    cursorX = prevLen;
                }
                ensureCursorVisible();
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) { deleteSelection(); return true; }
                String cur = lines.get(cursorY);
                if (cursorX < cur.length()) {
                    lines.set(cursorY, cur.substring(0, cursorX) + cur.substring(cursorX + 1));
                } else if (cursorY < lines.size() - 1) {
                    lines.set(cursorY, cur + lines.get(cursorY + 1));
                    lines.remove(cursorY + 1);
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursorX > 0) cursorX--;
                else if (cursorY > 0) { cursorY--; cursorX = lines.get(cursorY).length(); }
                ensureCursorVisible();
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursorX < lines.get(cursorY).length()) cursorX++;
                else if (cursorY < lines.size() - 1) { cursorY++; cursorX = 0; }
                ensureCursorVisible();
            }
            case GLFW.GLFW_KEY_UP -> {
                if (cursorY > 0) { cursorY--; cursorX = Math.min(cursorX, lines.get(cursorY).length()); }
                ensureCursorVisible();
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (cursorY < lines.size() - 1) { cursorY++; cursorX = Math.min(cursorX, lines.get(cursorY).length()); }
                ensureCursorVisible();
            }
            case GLFW.GLFW_KEY_HOME -> cursorX = 0;
            case GLFW.GLFW_KEY_END  -> cursorX = lines.get(cursorY).length();
            case GLFW.GLFW_KEY_ESCAPE -> focused = false;
            default -> { /* consumed */ }
        }
        return true;
    }

    private boolean isNavKey(int key) {
        return key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END || key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN;
    }

    private void selectAll() {
        selectionStartX = 0;
        selectionStartY = 0;
        cursorY = lines.size() - 1;
        cursorX = lines.get(cursorY).length();
    }

    private void copySelection() {
        if (!hasSelection()) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
    }

    private void cutSelection() {
        if (!hasSelection()) return;
        copySelection();
        deleteSelection();
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int sY = selectionStartY, sX = selectionStartX, eY = cursorY, eX = cursorX;
        if (sY > eY || (sY == eY && sX > eX)) {
            int ty = sY; sY = eY; eY = ty;
            int tx = sX; sX = eX; eX = tx;
        }

        String startLine = lines.get(sY);
        String endLine = lines.get(eY);

        String newContent = startLine.substring(0, sX) + endLine.substring(eX);
        
        for (int i = 0; i < (eY - sY); i++) {
            lines.remove(sY + 1);
        }
        lines.set(sY, newContent);
        
        cursorY = sY;
        cursorX = sX;
        clearSelection();
    }

    private void pasteFromClipboard() {
        if (hasSelection()) deleteSelection();
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isEmpty()) return;

        String[] parts = text.replace("\r", "").split("\n", -1);
        String current = lines.get(cursorY);
        String before = current.substring(0, cursorX);
        String after = current.substring(cursorX);

        if (parts.length == 1) {
            lines.set(cursorY, before + parts[0] + after);
            cursorX += parts[0].length();
        } else {
            lines.set(cursorY, before + parts[0]);
            for (int i = 1; i < parts.length - 1; i++) {
                lines.add(cursorY + i, parts[i]);
            }
            lines.add(cursorY + parts.length - 1, parts[parts.length - 1] + after);
            cursorY += parts.length - 1;
            cursorX = parts[parts.length - 1].length();
        }
        ensureCursorVisible();
    }

    private String getSelectedText() {
        if (!hasSelection()) return "";
        int sY = selectionStartY, sX = selectionStartX, eY = cursorY, eX = cursorX;
        if (sY > eY || (sY == eY && sX > eX)) {
            int ty = sY; sY = eY; eY = ty;
            int tx = sX; sX = eX; eX = tx;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = sY; i <= eY; i++) {
            String line = lines.get(i);
            int start = (i == sY) ? sX : 0;
            int end = (i == eY) ? eX : line.length();
            sb.append(line.substring(start, end));
            if (i < eY) sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean handleCharTyped(char codePoint, int modifiers) {
        if (!focused) return false;
        if (hasSelection()) deleteSelection();
        String cur = lines.get(cursorY);
        lines.set(cursorY, cur.substring(0, cursorX) + codePoint + cur.substring(cursorX));
        cursorX++;
        return true;
    }

    // Mouse scroll for code area
    public boolean handleMouseScrolled(double localX, double localY, double delta) {
        if (localX < 0 || localX > width || localY < 0 || localY > height) return false;
        int fontH = Minecraft.getInstance().font.lineHeight + 1;
        int visibleLines = (height - 4) / fontH;
        scrollOffset = Math.max(0, Math.min(scrollOffset - (int) delta, Math.max(0, lines.size() - visibleLines)));
        return true;
    }

    private void ensureCursorVisible() {
        int fontH = Minecraft.getInstance().font.lineHeight + 1;
        int visibleLines = (height - 4) / fontH;
        if (cursorY < scrollOffset) scrollOffset = cursorY;
        if (cursorY >= scrollOffset + visibleLines) scrollOffset = cursorY - visibleLines + 1;
    }

    // -----------------------------------------------------------
    //  Data access
    // -----------------------------------------------------------

    public String getValue() { return String.join("\n", lines); }

    public void setValue(String value) {
        this.lines = new ArrayList<>(List.of(value.split("\n", -1)));
        if (lines.isEmpty()) lines.add("");
        cursorX = 0;
        cursorY = 0;
        scrollOffset = 0;
    }

    // -----------------------------------------------------------
    //  NBT
    // -----------------------------------------------------------

    @Override
    public net.minecraft.nbt.CompoundTag save() {
        net.minecraft.nbt.CompoundTag tag = super.save();
        tag.putString("code", getValue());
        return tag;
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        if (tag.contains("code")) setValue(tag.getString("code"));
    }
}
