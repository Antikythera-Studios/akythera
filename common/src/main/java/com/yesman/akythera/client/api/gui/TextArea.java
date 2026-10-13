package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.DataBoundWidget;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// A multi-line text input, wrapping its lines at the widget width. Enter breaks the line.
///
/// Drawn in the style of [BuiltInEditBoxTheme#DEFAULT_EDIT_BOX]. An inactive area shows its text
/// greyed and takes no input. A non-[#editable] area takes no focus, so it takes no typing either.
@ScriptType
public class TextArea extends AbstractTextAreaWidget implements AnchoredWidget, DataBoundWidget<String> {
    private static final int LINE_H = 9;
    private static final int SCROLLBAR_W = 5;
    private static final int CURSOR_BLINK_INTERVAL_MS = 600;

    private final Font font;

    /// The text, its cursor and its wrapped lines. Rebuilt on every width change, as it wraps at a
    /// fixed width.
    private MultilineTextField textField;

    /// Overrides the text color. Null leaves it to the active state.
    private @Nullable Integer textColor;

    /// Display-only transform of the text, drawn while the area is unfocused. Null draws the text as it is.
    private @Nullable Function<String, String> formatter;

    /// Called on focus loss with the text at focus gain and the text now.
    private @Nullable BiConsumer<String, String> onCommitted;

    /// The text at the last focus gain, see [#onCommitted].
    private String preEditValue = "";

    /// Timestamp of the last focus gain, for the cursor blink.
    private long focusTimestamp;

    /// Whether the area takes focus and typing.
    private boolean editable = true;

    public TextArea(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<String> dataProvider,
        Consumer<String> onValueChanged,
        Component placeholder
    ) {
        super(0, 0, 0, 0, placeholder, AbstractScrollArea.defaultSettings(LINE_H / 2));

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onValueChanged = onValueChanged;
        this.textField = new MultilineTextField(font, 1);
        this.textField.setValueListener(onValueChanged);
        this.textField.setCursorListener(this::scrollToCursor);
    }

    /// Re-creates [#textField] at the current width, keeping its text and cursor.
    private void reflow() {
        String value = textField.value();
        int cursor = textField.cursor();

        textField = new MultilineTextField(font, Math.max(1, getWidth() - totalInnerPadding() - SCROLLBAR_W));
        textField.setValue(value, true);
        textField.seekCursor(Whence.ABSOLUTE, cursor);
        textField.setValueListener(onValueChanged);
        textField.setCursorListener(this::scrollToCursor);
    }

    @Override
    public void setWidth(int width) {
        boolean changed = width != getWidth();
        super.setWidth(width);

        if (changed) reflow();
    }

    /// Replaces the text without calling the change callback, the cursor moving to its end.
    public void setValue(String value) {
        textField.setValueListener(_ -> {});
        textField.setValue(value, true);
        textField.setValueListener(onValueChanged);
        setScrollAmount(0);
    }

    /// Shows `message` in the current language, the cursor moving to its end. The lines and the scroll bar
    /// follow the new text.
    @ScriptFunction
    public void setText(final LocalizedMessage message) {
        setValue(message.toComponent().getString());
    }

    @ScriptFunction(pure = true)
    public String getValue() {
        return textField.value();
    }

    public void setOnCommitted(@Nullable BiConsumer<String, String> onCommitted) {
        this.onCommitted = onCommitted;
    }

    public TextArea setTextColor(@Nullable Integer textColor) {
        this.textColor = textColor;

        return this;
    }

    public void setFormatter(@Nullable Function<String, String> formatter) {
        this.formatter = formatter;
    }

    public TextArea setEditable(boolean editable) {
        this.editable = editable;

        if (!editable && isFocused()) setFocused(false);

        return this;
    }

    public boolean isEditable() {
        return editable;
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && (!active || !editable)) return;

        boolean wasFocused = isFocused();
        super.setFocused(focused);
        Minecraft.getInstance().onTextInputFocusChange(this, focused);

        if (focused && !wasFocused) {
            focusTimestamp = System.currentTimeMillis();
            preEditValue = textField.value();
        }

        if (!focused && wasFocused && onCommitted != null) {
            onCommitted.accept(preEditValue, textField.value());
        }
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        focusTimestamp = System.currentTimeMillis();

        if (doubleClick) {
            textField.selectWordAtCursor();
        } else {
            textField.setSelecting(event.hasShiftDown());
            seekCursorScreen(event.x(), event.y());
        }
    }

    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) {
        textField.setSelecting(true);
        seekCursorScreen(event.x(), event.y());
        textField.setSelecting(event.hasShiftDown());
    }

    private void seekCursorScreen(double x, double y) {
        textField.seekCursorToPoint(x - getInnerLeft(), y - getInnerTop() + scrollAmount());
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!active || !isFocused()) return false;

        if (event.isEscape()) {
            setFocused(false);
            return true;
        }

        focusTimestamp = System.currentTimeMillis();

        return textField.keyPressed(event);
    }

    /// Selects the whole text with the cursor at its end.
    protected void selectAll() {
        textField.setSelecting(false);
        textField.seekCursor(Whence.ABSOLUTE, 0);
        textField.setSelecting(true);
        textField.seekCursor(Whence.END, 0);
        textField.setSelecting(false);
    }

    /// Puts the selected text on the OS clipboard.
    protected void copySelection() {
        Minecraft.getInstance().keyboardHandler.setClipboard(textField.getSelectedText());
    }

    /// Copies the selected text, then removes it.
    protected void cutSelection() {
        copySelection();
        textField.insertText("");
    }

    /// Replaces the selected text with the OS clipboard.
    protected void paste() {
        textField.insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (!active || !visible || !isFocused() || !event.isAllowedChatCharacter()) return false;

        textField.insertText(event.codepointAsString());

        return true;
    }

    /// Scrolls just far enough to show the cursor line.
    private void scrollToCursor() {
        int cursorTop = textField.getLineAtCursor() * LINE_H;
        int viewHeight = getHeight() - totalInnerPadding();

        if (cursorTop < scrollAmount()) {
            setScrollAmount(cursorTop);
        } else if (cursorTop + LINE_H > scrollAmount() + viewHeight) {
            setScrollAmount(cursorTop + LINE_H - viewHeight);
        }
    }

    private boolean cursorBlinkOn() {
        return (System.currentTimeMillis() - focusTimestamp) % (CURSOR_BLINK_INTERVAL_MS * 2) < CURSOR_BLINK_INTERVAL_MS;
    }

    @Override
    protected void extractBackground(@NonNull GuiGraphicsExtractor graphics) {
        int borderColor = isFocused() ? 0xFFFFFFFF : active ? 0xFFA0A0A0 : 0xFF6F6F6F;

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF000000);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), borderColor);
    }

    @Override
    protected void extractContents(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        String value = textField.value();
        int left = getInnerLeft();
        int lineTop = getInnerTop();

        if (value.isEmpty() && !isFocused()) {
            graphics.text(font, getMessage(), left, lineTop, 0xFF555555, false);
            return;
        }

        int color = textColor != null ? textColor : active ? 0xFFCCCCCC : 0xFF7A7A7A;

        if (formatter != null && !isFocused()) {
            graphics.textWithWordWrap(font, Component.literal(formatter.apply(value)), left, lineTop, getWidth() - totalInnerPadding() - SCROLLBAR_W, color);
            return;
        }

        MultilineTextField.StringView selection = textField.getSelected();
        int cursor = textField.cursor();
        boolean cursorPending = isFocused() && cursorBlinkOn();

        for (MultilineTextField.StringView line : textField.iterateLines()) {
            int begin = line.beginIndex();
            int end = line.endIndex();

            if (withinContentAreaTopBottom(lineTop, lineTop + LINE_H)) {
                int selectionBegin = Math.max(selection.beginIndex(), begin);
                int selectionEnd = Math.min(selection.endIndex(), end);

                if (selectionBegin < selectionEnd || (selectionBegin <= end && selection.endIndex() > end)) {
                    int highlightLeft = left + font.width(value.substring(begin, selectionBegin));
                    int highlightRight = selection.endIndex() > end
                        ? getRight() - innerPadding() - SCROLLBAR_W
                        : left + font.width(value.substring(begin, selectionEnd));

                    graphics.fill(highlightLeft, lineTop - 1, highlightRight, lineTop + LINE_H, 0xFF2F5C8F);
                }

                graphics.text(font, value.substring(begin, end), left, lineTop, color, false);

                if (cursorPending && begin <= cursor && cursor <= end) {
                    int cursorX = left + font.width(value.substring(begin, cursor));
                    graphics.fill(cursorX, lineTop - 1, cursorX + 1, lineTop + LINE_H, 0xFFCCCCCC);
                    cursorPending = false;
                }
            }

            lineTop += LINE_H;
        }
    }

    @Override
    public int getInnerHeight() {
        return LINE_H * textField.getLineCount();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return active && visible && mouseX >= getX() && mouseY >= getY() && mouseX < getRight() && mouseY < getBottom();
    }

    @Override
    public int scrollbarWidth() {
        return SCROLLBAR_W;
    }

    /// Inside the outline at the right edge, in the column the wrapped text leaves free, so [#isMouseOver] covers the
    /// thumb.
    @Override
    protected int scrollBarX() {
        return getRight() - 1 - SCROLLBAR_W;
    }

    /// The thumb's height in the track, which runs inside the outline.
    @Override
    protected int scrollerHeight() {
        int track = height - 2;

        return Mth.clamp((int) ((float) (track * track) / (float) contentHeight()), 8, Math.max(8, track - 8));
    }

    /// The thumb's top, moving within the track inside the outline.
    @Override
    public int scrollBarY() {
        int top = getY() + 1;

        return maxScrollAmount() == 0 ? top : top + (int) (scrollAmount() * (height - 2 - scrollerHeight()) / maxScrollAmount());
    }

    @Override
    protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!scrollable()) return;

        int sbX = scrollBarX();
        int scrollerH = scrollerHeight();
        int scrollerY = scrollBarY();
        boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

        graphics.fill(sbX, getY() + 1, sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
        graphics.fill(sbX + 1, scrollerY + 1, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH - 1, hovered ? 0xFF6A6A6A : 0xFF505050);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    // ***************************************************************
    // [AnchoredWidget] implementations
    // ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private HorizontalAnchorType horizontalAnchorType;
    private VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
    @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
    @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }

    // *****************************************************************
    // [DataBoundWidget] implementations
    // *****************************************************************
    private final Callable<String> dataProvider;
    private final Consumer<String> onValueChanged;

    @Override
    public Callable<String> getDataProvider() {
        return dataProvider;
    }

    @Override
    public Consumer<String> valueChangeCallback() {
        return onValueChanged;
    }

    @Override
    public Consumer<String> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        setValue("");
    }
}
