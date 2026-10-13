package com.yesman.akythera.client.api.gui;

import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.common.PlaceholderExpressionWidget;
import com.yesman.akythera.client.api.gui.common.PressableWidget;
import com.yesman.akythera.client.api.gui.common.ThemeApplicableWidget;
import com.yesman.akythera.client.api.gui.common.WarningMarkableWidget;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.client.api.gui.modal.DropdownSizeMemory;
import com.yesman.akythera.client.api.gui.modal.FrameResizer;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.modal.ResizableFrame;
import com.yesman.akythera.core.reflection.ScriptFunction;
import com.yesman.akythera.core.reflection.ScriptType;
import com.yesman.akythera.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/// A combo box whose dropdown opens through [ModalManager]: the item rows, or the modal a
/// [#setDropdownFactory] factory builds.
@ScriptType
public class ComboBox<T> extends AbstractWidget
    implements AnchoredWidget, WarningMarkableWidget<T>, PlaceholderExpressionWidget<T>, PressableWidget, ThemeApplicableWidget<ComboBoxTheme>,
    DropdownSizeMemory {

    private static final int ENTRY_H = 14;
    private static final int SEARCH_H = 16;

    /// Pixels a row listed under a category heading is inset by.
    private static final int CATEGORY_INDENT = 8;

    private static final int HEADING_COLOR = 0xFF1F2A33; // category heading row background

    protected final Font font;
    private final int maxRows;
    private final List<T> items;
    private final Function<T, String> displayStringMapper;

    /// The icon a [ComboBoxTheme] may draw in front of an item's label. Null — the default — means no icon.
    @Nullable
    private Function<T, @Nullable ComboIcon> iconMapper;

    /// The heading an item is listed under in the dropdown. Null — the default — lists the items without
    /// headings. Items sharing a heading must be adjacent in [#items].
    @Nullable
    private Function<T, Component> categoryMapper;

    /// Shown greyed in place of the dropdown — and of an unselected label — while [#items] is empty:
    /// *why* there is nothing to pick, which a bare "(none)" row cannot say. Null (the default) leaves
    /// the empty dropdown as it was.
    @Nullable
    private Component emptyMessage;

    /// Whether the dropdown lists a "(none)" row, which clears the value. True by default.
    private boolean clearable = true;

    /// Whether the dropdown shows a search box over its rows. False by default.
    private boolean searchable;

    /// Builds the modal opened in place of the item rows. Null — the default — opens the rows.
    @Nullable
    private Function<ComboBox<T>, AbstractWidget> dropdownFactory;

    /// The dropdown this combo opened last, so a second press closes it.
    @Nullable
    private AbstractWidget dropdown;

    private ComboBoxTheme theme = BuiltInComboBoxTheme.DEFAULT_COMBO_BOX;

    @Nullable
    private T selected;

    /// Drawn greyed in the label slot while nothing is selected. Null = no placeholder.
    @Nullable
    private T placeholderValue;

    private boolean marked;

    /// Size this combo's dropdown was last dragged to, or 0 while it has never been resized, see
    /// [DropdownSizeMemory].
    private int dropdownWidth;
    private int dropdownHeight;

    public ComboBox(
        Font font,
        Unit x1,
        Unit x2,
        Unit y1,
        Unit y2,
        HorizontalAnchorType horizontalAnchor,
        VerticalAnchorType verticalAnchor,
        Callable<T> dataProvider,
        Consumer<T> onWidgetChanged,
        int maxRows,
        Component title,
        Collection<T> items,
        Function<T, String> displayStringMapper
    ) {
        super(0, 0, 0, 0, title);

        this.font = font;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchor;
        this.verticalAnchorType = verticalAnchor;
        this.dataProvider = dataProvider;
        this.onWidgetChanged = onWidgetChanged;
        this.maxRows = Math.min(maxRows, items.size());
        this.items = List.copyOf(items);
        this.displayStringMapper = displayStringMapper;
    }

    @Override
    public void mark() {
        this.marked = true;
    }

    @Override
    public void unmark() {
        this.marked = false;
    }

    @Override
    public void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        theme.renderBackground(this, g, mouseX, mouseY, a);

        T placeholder = hasPlaceholderValue() ? placeholderValue : null;
        String display = placeholder != null ? displayStringMapper.apply(placeholder) : displayString();
        Component emptyMessage = display.isEmpty() ? emptyMessageIfNothingToPick() : null;
        int fontColor = !isActive() ? 0xFF404040 : placeholder != null || emptyMessage != null ? 0xFF888888 : 0xFFFFFFFF;

        theme.renderText(
            g, font, getX(), getY(), getWidth(), getHeight(),
            emptyMessage != null ? emptyMessage.getString() : display,
            iconFor(placeholder != null ? placeholder : selected), fontColor, true
        );
    }

    /// The label text for the current selection (empty when nothing is selected).
    public String displayString() {
        return selected == null ? "" : displayStringMapper.apply(selected);
    }

    /// Sets the message shown while there is nothing to pick, see [#emptyMessage]. Returns `this` for
    /// chaining.
    public ComboBox<T> setEmptyMessage(@Nullable Component message) {
        this.emptyMessage = message;
        return this;
    }

    /// [#emptyMessage] while there is genuinely nothing to pick; null when the list has items (an
    /// unselected combo with candidates is blank, not "nothing to pick").
    @Nullable
    Component emptyMessageIfNothingToPick() {
        return items.isEmpty() ? emptyMessage : null;
    }

    /// The icon for an arbitrary `value` (used by the dropdown rows), or null when no mapper is set or
    /// `value` is null.
    @Nullable
    ComboIcon iconFor(@Nullable T value) {
        return value == null || iconMapper == null ? null : iconMapper.apply(value);
    }

    /// Sets the per-item icon source; a [ComboBoxTheme] that draws icons reads it. Returns `this` for chaining.
    public ComboBox<T> setIconMapper(@Nullable Function<T, @Nullable ComboIcon> mapper) {
        this.iconMapper = mapper;
        return this;
    }

    /// Sets the heading each item is listed under, see [#categoryMapper]. Returns `this` for chaining.
    public ComboBox<T> setCategoryMapper(@Nullable Function<T, Component> mapper) {
        this.categoryMapper = mapper;
        return this;
    }

    /// Sets whether the dropdown lists the "(none)" row, see [#clearable]. Returns `this` for chaining.
    public ComboBox<T> setClearable(boolean clearable) {
        this.clearable = clearable;
        return this;
    }

    /// Sets whether the dropdown shows a search box, see [#searchable]. Returns `this` for chaining.
    public ComboBox<T> setSearchable(boolean searchable) {
        this.searchable = searchable;
        return this;
    }

    /// Sets the factory building the dropdown in place of the item rows, see [#dropdownFactory]. Returns
    /// `this` for chaining.
    public ComboBox<T> setDropdownFactory(@Nullable Function<ComboBox<T>, AbstractWidget> dropdownFactory) {
        this.dropdownFactory = dropdownFactory;
        return this;
    }

    public boolean isMarked() {
        return marked;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible) return false;
        if (!isOverBody(event.x(), event.y())) return false;
        if (!isValidClickButton(event.buttonInfo())) return false;

        onPressed();
        playDownSound(Minecraft.getInstance().getSoundManager());

        return true;
    }

    /// Builds the box filtering the dropdown rows of a [#searchable] combo, calling `onQueryChanged` with
    /// its text. The dropdown lays it out.
    protected AkytheraEditBox<String> createSearchBox(Consumer<String> onQueryChanged) {
        return AkytheraEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            HorizontalAnchorType.LEFT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            onQueryChanged,
            Component.translatable(LangKeys.GUI_COMBO_BOX_SEARCH)
        );
    }

    /// Opens the dropdown under the combo, see [ModalManager#openModalWidgetUnder]. Pressed
    /// again while open, closes it.
    @Override
    public void onPressed() {
        ModalManager modals = ModalManager.getInstance();

        if (dropdown != null && modals.getModal() == dropdown) {
            modals.closeModal();
            return;
        }

        dropdown = dropdownFactory != null ? dropdownFactory.apply(this) : new DropdownModal<>(this);
        modals.openModalWidgetUnder(this, dropdown);
    }

    private boolean isOverBody(double x, double y) {
        return active && visible
            && x >= getX() && y >= getY()
            && x < getX() + getWidth() && y < getY() + getHeight();
    }

    public void setValue(@Nullable T value) {
        this.selected = value;
        this.onWidgetChanged.accept(value);
    }

    /// Updates the displayed selection without firing the change callback.
    @ScriptFunction
    public void setValueQuiet(@Nullable T value) {
        this.selected = value;
    }

    /// The selection, or null when nothing is selected.
    @ScriptFunction(pure = true)
    public @Nullable T getValue() {
        return selected;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    public int dropdownWidth() {
        return dropdownWidth;
    }

    @Override
    public int dropdownHeight() {
        return dropdownHeight;
    }

    @Override
    public void rememberDropdownSize(int width, int height) {
        this.dropdownWidth = width;
        this.dropdownHeight = height;
    }

    // *****************************************************************
    // [PlaceholderExpressionWidget] implementations
    // *****************************************************************
    @Override
    public @Nullable T getPlaceholderValue() {
        return placeholderValue;
    }

    @Override
    public void setPlaceholderValue(@Nullable T value) {
        this.placeholderValue = value;
    }

    @Override
    public boolean hasPlaceholderValue() {
        return selected == null && placeholderValue != null;
    }


    // *****************************************************************
    // [DataBoundWidget] implementations
    // *****************************************************************
    private final Callable<T> dataProvider;
    private final Consumer<T> onWidgetChanged;

    @Override
    public Callable<T> getDataProvider() {
        return this.dataProvider;
    }

    @Override
    public Consumer<T> valueChangeCallback() {
        return this.onWidgetChanged;
    }

    @Override
    public Consumer<T> valueSetter() {
        return this::setValue;
    }

    @Override
    public void reset() {
        this.selected = null;
    }

    // ***************************************************************
    // [ThemeApplicableWidget] implementations
    // ***************************************************************
    @Override
    public boolean isSupportedTheme(WidgetTheme theme) {
        return ComboBoxTheme.class.isAssignableFrom(theme.getClass());
    }

    @Override
    public @NonNull ComboBoxTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(ComboBoxTheme theme) {
        this.theme = theme;
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

    /// The dropdown: the rows, under a search box that lists only the rows whose label contains the typed
    /// text when the combo is [#searchable].
    private static final class DropdownModal<T> extends AbstractContainerWidget
        implements Modal, AnchoredWidget, ResizableFrame
    {
        final ComboBox<T> owner;
        @Nullable
        private final AkytheraEditBox<String> searchBox;
        private final RowList list;
        private final FrameResizer resizer;

        /// Pixels above the list the search box takes, its gap included. 0 without one.
        private final int searchHeight;

        DropdownModal(ComboBox<T> owner) {
            super(
                0, 0, owner.getWidth(),
                (owner.searchable ? SEARCH_H + 1 : 0) + 2 + ENTRY_H * Math.max(1, owner.maxRows + (owner.clearable ? 1 : 0)),
                Component.empty(),
                AbstractScrollArea.defaultSettings(0)
            );

            this.owner = owner;
            this.searchHeight = owner.searchable ? SEARCH_H + 1 : 0;
            this.resizer = new FrameResizer(this, owner.getWidth(), searchHeight + 2 + ENTRY_H);
            this.x2 = AnchoredWidget.pixel(getWidth());
            this.y2 = AnchoredWidget.pixel(getHeight());
            this.list = new RowList();
            this.searchBox = owner.searchable ? owner.createSearchBox(list::rebuild) : null;

            if (searchBox != null) {
                searchBox.setX1(AnchoredWidget.pixel(1));
                searchBox.setX2(AnchoredWidget.pixel(1));
                searchBox.setY1(AnchoredWidget.pixel(1));
                searchBox.setY2(AnchoredWidget.pixel(SEARCH_H));
                searchBox.setHorizontalAnchorType(HorizontalAnchorType.LEFT_RIGHT);
                searchBox.setVerticalAnchorType(VerticalAnchorType.TOP_HEIGHT);
            }

            list.rebuild("");

            if (searchBox != null) setFocused(searchBox);
        }

        /// Cover the whole screen so this widget intercepts every click —
        /// outside-clicks reach `mouseClicked` below and close the modal.
        @Override
        public boolean isMouseOver(double mx, double my) {
            return isActive();
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            // The frame grip lies just outside the frame, where a press would otherwise dismiss the modal.
            if (event.button() == 0 && resizer.mousePressed(event.x(), event.y())) {
                return true;
            }

            boolean inside = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();

            if (!inside) {
                ModalManager.getInstance().closeModal();
                return true;
            }

            super.mouseClicked(event, doubleClick);

            return true;
        }

        @Override
        public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
            return resizer.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
        }

        @Override
        public boolean mouseReleased(@NonNull MouseButtonEvent event) {
            return resizer.mouseReleased() || super.mouseReleased(event);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public FrameResizer frameResizer() {
            return resizer;
        }

        @Override
        public boolean keyPressed(@NonNull KeyEvent event) {
            if (event.isEscape()) {
                ModalManager.getInstance().closeModal();
                return true;
            }

            if (event.isConfirmation() && confirmSoleResult()) return true;

            GuiEventListener focused = getFocused();

            return focused != null && focused.keyPressed(event);
        }

        @Override
        public boolean charTyped(@NonNull CharacterEvent event) {
            GuiEventListener focused = getFocused();

            return focused != null && focused.charTyped(event);
        }

        @Override
        public @Nullable AkytheraEditBox<String> searchBox() {
            return searchBox;
        }

        @Override
        public boolean confirmSoleResult() {
            List<RowList.Row> values = list.children().stream().filter(row -> row.value != null).toList();

            if (values.size() != 1) return false;

            values.getFirst().pick();

            return true;
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return searchBox == null ? List.of(list) : List.of(searchBox, list);
        }

        @Override
        protected int contentHeight() {
            return getHeight();
        }

        @Override
        protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            resizer.fitWidthToVisibleRows(list, list.children(), RowList.Row::textWidth, list.scrollbarWidth() + 3);

            g.fill(getX(), getY(), getRight(), getBottom(), 0xFF000000);
            if (searchBox != null) searchBox.extractRenderState(g, mouseX, mouseY, a);
            list.extractRenderState(g, mouseX, mouseY, a);
            g.outline(getX(), getY(), getWidth(), getHeight(), 0xFFFFFFFF);

            resizer.requestCursor(g, mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        private final class RowList extends AbstractSelectionList<RowList.@NonNull Row> {
            private final Map<T, Row> rowByValue = new HashMap<>();

            /// The query the rows are listed for. Null until the first [#rebuild].
            private @Nullable String query;

            RowList() {
                super(Minecraft.getInstance(), owner.getWidth() - 2, ENTRY_H, 0, ENTRY_H);
            }

            /// Lists the rows whose label contains `query`, ignoring case. A heading stays only above a
            /// listed row, and the "(none)" row only while nothing is typed. The same query again keeps
            /// the rows and the scroll as they are.
            void rebuild(String query) {
                if (query.equals(this.query)) return;

                this.query = query;
                clearEntries();
                rowByValue.clear();

                Component emptyMessage = owner.emptyMessageIfNothingToPick();

                // Nothing to pick: one inert line saying why, instead of a lone "(none)" that reads as
                // "this field has no values" rather than "there is a step still missing".
                if (emptyMessage != null) {
                    addEntry(new Row(null, emptyMessage));
                    return;
                }

                String lower = query.toLowerCase(Locale.ROOT);

                // Sentinel "(none)" row at the top — selecting it clears the binding.
                if (owner.clearable && lower.isEmpty()) addEntry(new Row(null, null));

                @Nullable Component category = null;

                for (T item : owner.items) {
                    if (!owner.displayStringMapper.apply(item).toLowerCase(Locale.ROOT).contains(lower)) continue;

                    @Nullable Component itemCategory = owner.categoryMapper == null ? null : owner.categoryMapper.apply(item);

                    if (itemCategory != null && !itemCategory.equals(category)) addEntry(new Row(null, itemCategory));
                    category = itemCategory;

                    Row row = new Row(item, null);
                    rowByValue.put(item, row);
                    addEntry(row);
                }

                setScrollAmount(0.0D);
                updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());

                // Pre-select current value so keyboard nav has a starting row.
                if (owner.selected != null) setSelected(rowByValue.get(owner.selected));
            }

            @Override public int getRowWidth() { return getWidth(); }
            @Override public int getRowLeft() { return getX(); }
            @Override public int scrollbarWidth() { return 5; }
            @Override protected int scrollBarX() { return getRight() - scrollbarWidth(); }
            @Override protected boolean entriesCanBeSelected() { return false; }
            @Override protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

            @Override
            protected int contentHeight() {
                int totalHeight = 0;

                for (Row child : children()) {
                    totalHeight += child.getHeight();
                }

                return totalHeight;
            }

            @Override
            protected void extractListBackground(@NonNull GuiGraphicsExtractor g) {
            }

            @Override
            protected void extractListSeparators(@NonNull GuiGraphicsExtractor g) {
            }

            @Override
            protected void extractScrollbar(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
                if (!scrollable()) return;

                int sbX = scrollBarX();
                int sh = scrollerHeight();
                int sy = scrollBarY();
                boolean hov = mouseX >= sbX && mouseX < sbX + scrollbarWidth() && mouseY >= sy && mouseY < sy + sh;

                g.fill(sbX, getY(), sbX + scrollbarWidth(), getBottom(), 0xFF1A1A1A);
                g.fill(sbX + 1, sy, sbX + scrollbarWidth() - 1, sy + sh, hov ? 0xFF6A6A6A : 0xFF505050);
            }

            final class Row extends AbstractSelectionList.Entry<@NonNull Row> {
                @Nullable
                private final T value;

                /// Non-null on an inert row, the "nothing to pick" line or a category heading, which carries
                /// no value and takes no click.
                @Nullable
                private final Component message;

                Row(@Nullable T value, @Nullable Component message) {
                    this.value = value;
                    this.message = message;
                }

                /// The message on an inert row, "(none)" on the sentinel row, the value's name otherwise.
                String label() {
                    return message != null
                        ? message.getString()
                        : value == null
                            ? Component.translatable(LangKeys.GUI_COMBO_BOX_NONE).getString()
                            : owner.displayStringMapper.apply(value);
                }

                /// Whether this is a category heading: an inert row of a list that has items.
                boolean isHeading() {
                    return message != null && !owner.items.isEmpty();
                }

                /// Pixels the label is inset by: [#CATEGORY_INDENT] for a value listed under a heading.
                int indent() {
                    return value != null && owner.categoryMapper != null && owner.categoryMapper.apply(value) != null
                        ? CATEGORY_INDENT
                        : 0;
                }

                /// Width the row needs to show its label unclipped.
                int textWidth() {
                    return indent() + owner.theme.textCellWidth(owner.font, label(), owner.iconFor(value), false);
                }

                @Override
                public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                    boolean isSelected = message == null && (value == null ? owner.selected == null : getSelected() == this);
                    int bg = isSelected
                        ? 0xFF1E4D72
                        : isHeading() ? HEADING_COLOR : hovered && message == null ? 0xFF262626 : 0xFF000000;

                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);

                    int color = value == null ? 0xFF888888 : 0xFFFFFFFF;

                    // Same renderer as the collapsed box (sans arrow), so the icon is embedded identically in
                    // the open list and the closed display.
                    owner.theme.renderText(
                        g, owner.font, getX() + indent(), getY(), getWidth() - indent(), getHeight(),
                        label(), owner.iconFor(value), color, false
                    );
                }

                @Override
                public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                    if (event.button() != 0 || message != null) return false;

                    pick();

                    return true;
                }

                void pick() {
                    setSelected(this);
                    owner.setValue(value);
                    ModalManager.getInstance().closeModal();
                }
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1;
        private Unit x2;
        private Unit y1;
        private Unit y2;
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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

        /// Lays the search box, if any, and the list out in the rect the units just produced.
        @Override
        public void relocate(ScreenRectangle screenRectangle) {
            AnchoredWidget.super.relocate(screenRectangle);

            if (searchBox != null) searchBox.relocate(getRectangle());
            list.updateSizeAndPosition(getWidth() - 2, getHeight() - searchHeight - 2, getX() + 1, getY() + searchHeight + 1);
        }
    }
}
