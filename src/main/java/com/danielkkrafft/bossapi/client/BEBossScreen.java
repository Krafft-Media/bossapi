package com.danielkkrafft.bossapi.client;

import com.danielkkrafft.bossapi.BossApi;
import com.danielkkrafft.bossapi.net.BEBossEditorNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@EventBusSubscriber(Dist.CLIENT)
public final class BEBossScreen extends Screen {
    public static final KeyMapping OPEN_BOSS_EDITOR = new KeyMapping("key.bossapi.open_boss_editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KeyMapping.Category.register(BossApi.id("bossapi")));
    private final List<EditorButton> stateButtons = new ArrayList<>();
    private final List<EditorButton> goalButtons = new ArrayList<>();
    private BEBossEditorNetwork.SnapshotPayload snapshot;
    private final Controls controls = new Controls();
    private static final int BUTTON_STEP = 18 + 3;
    private static final int WINDOW_HEIGHT = 258;
    private static final int BUTTON_HEIGHT = 18;
    private static final int WINDOW_WIDTH = 180;
    private static final int TITLE_HEIGHT = 22;
    private static final int WINDOW_MARGIN = 8;
    private static final int VISIBLE_ROWS = 3;
    private static final int BUTTON_GAP = 3;
    private Layout layout = Layout.EMPTY;
    private Tab selectedTab = Tab.GOALS;
    private int stateDelayTicks = 20;
    private int refreshTicks;
    private int scrollIndex;

    private BEBossScreen(BEBossEditorNetwork.SnapshotPayload snapshot) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("Boss Controller"));
        this.snapshot = snapshot;
    }

    public static void requestOpen() {
        ClientPacketDistributor.sendToServer(BEBossEditorNetwork.RequestSnapshotPayload.INSTANCE);
    }

    private void applySnapshot(BEBossEditorNetwork.SnapshotPayload snapshot) {
        boolean rebuild = this.snapshot.selected() != snapshot.selected() || !this.snapshot.states().equals(snapshot.states()) || !goalIds(this.snapshot.goals()).equals(goalIds(snapshot.goals()));
        this.snapshot = snapshot;
        if (this.width <= 0) return;

        if (rebuild) {
            this.rebuildWidgets();
        } else {
            this.controls.health.setHealth(snapshot.health(), snapshot.maxHealth());
            this.updateWidgetStates();
        }
    }

    private static List<String> goalIds(List<BEBossEditorNetwork.GoalInfo> goals) {
        return goals.stream().map(BEBossEditorNetwork.GoalInfo::id).toList();
    }

    @Override
    public void tick() {
        super.tick();
        if (++this.refreshTicks < 10) return;

        this.refreshTicks = 0;
        requestOpen();
    }

    @Override
    protected void init() {
        super.init();
        this.goalButtons.clear();
        this.stateButtons.clear();
        this.layout = Layout.create(this.width, this.height);

        int halfWidth = this.layout.halfContentWidth();
        int rightColumn = this.layout.contentX() + halfWidth + BUTTON_GAP;

        this.addRenderableWidget(new EditorButton(this.layout.closeX(), this.layout.closeY(), 16, 16, Component.literal("X"), "", ButtonStyle.CLOSE, this::onClose));

        this.controls.health = this.addRenderableWidget(new HealthSlider(this.layout.contentX(), this.layout.healthY(), this.layout.contentWidth(), 21, this.snapshot.health(), this.snapshot.maxHealth(), value -> ClientPacketDistributor.sendToServer(BEBossEditorNetwork.ActionPayload.setHealth(value))));
        this.controls.stateDelay = this.addRenderableWidget(new StateDelaySlider(this.layout.contentX(), this.layout.stateDelayY(), this.layout.contentWidth(), 21, this.stateDelayTicks, ticks -> this.stateDelayTicks = ticks));
        this.controls.freeze = this.addRenderableWidget(new EditorButton(this.layout.contentX(), this.layout.controlsY(), halfWidth, BUTTON_HEIGHT, this.freezeMessage(), "freeze", ButtonStyle.NORMAL, this::toggleFrozen));
        this.controls.resume = this.addRenderableWidget(new EditorButton(rightColumn, this.layout.controlsY(), halfWidth, BUTTON_HEIGHT, Component.literal("Resume AI"), "resume", ButtonStyle.NORMAL, this::resumeAi));
        this.controls.move = this.addRenderableWidget(new EditorButton(this.layout.contentX(), this.layout.targetButtonsY(), halfWidth, BUTTON_HEIGHT, this.moveMessage(), "move", ButtonStyle.NORMAL, this::moveToTarget));
        this.controls.clearTarget = this.addRenderableWidget(new EditorButton(rightColumn, this.layout.targetButtonsY(), halfWidth, BUTTON_HEIGHT, Component.literal("Clear Target"), "clear_target", ButtonStyle.NORMAL, this::clearTarget));
        this.controls.goalsTab = this.addRenderableWidget(new EditorButton(this.layout.contentX(), this.layout.tabsY(), halfWidth, BUTTON_HEIGHT, Component.literal("Goals"), "goals", ButtonStyle.TAB, () -> this.selectTab(Tab.GOALS)));
        this.controls.statesTab = this.addRenderableWidget(new EditorButton(rightColumn, this.layout.tabsY(), halfWidth, BUTTON_HEIGHT, Component.literal("States"), "states", ButtonStyle.TAB, () -> this.selectTab(Tab.STATES)));

        this.createGoalButtons();
        this.createStateButtons();
        this.updateWidgetStates();
        this.updateListPositions();
    }

    private void createGoalButtons() {
        this.goalButtons.add(this.addRenderableWidget(new EditorButton(this.layout.listX(), 0, this.layout.listWidth(), BUTTON_HEIGHT, Component.literal("Clear Forced Goal"), "", ButtonStyle.LIST, () -> this.selectGoal(""))));
        for (BEBossEditorNetwork.GoalInfo goal : this.snapshot.goals()) {
            this.goalButtons.add(this.addRenderableWidget(new EditorButton(this.layout.listX(), 0, this.layout.listWidth(), BUTTON_HEIGHT, Component.literal(displayName(goal.id())), goal.id(), ButtonStyle.LIST, () -> this.selectGoal(goal.id()))));
        }
    }

    private void createStateButtons() {
        for (String state : this.snapshot.states()) {
            this.stateButtons.add(this.addRenderableWidget(new EditorButton(this.layout.listX(), 0, this.layout.listWidth(), BUTTON_HEIGHT, Component.literal(displayName(state)), state, ButtonStyle.LIST, () -> this.selectState(state))));
        }
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractWindow(graphics);
        this.extractTitleBar(graphics);
        this.extractLabels(graphics);
        this.extractTargetInformation(graphics);
        this.extractListBackground(graphics);
        this.extractScrollbar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void extractWindow(GuiGraphicsExtractor graphics) {
        graphics.fill(this.layout.x() + 4, this.layout.y() + 4, this.layout.right() + 4, this.layout.bottom() + 4, 0x70000000);
        XP.drawRaisedPanel(graphics, this.layout.x(), this.layout.y(), this.layout.width(), this.layout.height(), XP.WINDOW_FACE);
    }

    private void extractTitleBar(GuiGraphicsExtractor graphics) {
        int x = this.layout.x() + 3;
        int y = this.layout.y() + 3;
        int width = this.layout.width() - 6;
        int height = TITLE_HEIGHT - 3;

        XP.drawTitleGradient(graphics, x, y, width, height);
        graphics.text(this.font, Component.literal("Boss Controller"), x + 6, y + 5, XP.TITLE_TEXT, true);
    }

    private void extractLabels(GuiGraphicsExtractor graphics) {
        String name = this.snapshot.selected() ? this.snapshot.bossName() : "No boss selected";
        String state = this.snapshot.state().isEmpty() ? "" : " - " + displayName(this.snapshot.state());

        graphics.text(this.font, Component.literal(name + state), this.layout.contentX(), this.layout.nameY(), XP.TEXT, false);

        if (!this.snapshot.selected()) return;
        String mode = this.snapshot.frozen() ? "FROZEN" : (this.snapshot.manualControl() ? "MANUAL" : "AI");
        int modeWidth = this.font.width(mode);
        graphics.text(this.font, Component.literal(mode), this.layout.contentRight() - modeWidth, this.layout.nameY(), this.snapshot.frozen() ? XP.ALERT_TEXT : XP.BLUE_TEXT, false);
    }

    private void extractTargetInformation(GuiGraphicsExtractor graphics) {
        int x = this.layout.contentX();
        int y = this.layout.targetTextY();

        if (!this.snapshot.selected()) {
            graphics.text(this.font, Component.literal(this.snapshot.status()), x, y, XP.DISABLED_TEXT, false);
            return;
        }

        if (!this.snapshot.hasTarget()) {
            graphics.text(this.font, Component.literal("Target: None"), x, y, XP.DISABLED_TEXT, false);
            return;
        }

        graphics.text(this.font, Component.literal("Target: " + this.snapshot.targetName() + " - " + format((float)this.snapshot.targetDistance()) + " blocks"), x, y, XP.TEXT, false);
        graphics.text(this.font, Component.literal(formatPosition(this.snapshot.targetX(), this.snapshot.targetY(), this.snapshot.targetZ())), x, y + 10, XP.DISABLED_TEXT, false);
    }

    private void extractListBackground(GuiGraphicsExtractor graphics) {
        XP.drawSunkenPanel(graphics, this.layout.listFrameX(), this.layout.listTop() - 2, this.layout.listFrameWidth(), this.layout.listHeight() + 4, XP.FIELD_BACKGROUND);
    }

    private void extractScrollbar(GuiGraphicsExtractor graphics) {
        List<EditorButton> buttons = this.getSelectedButtons();
        int maximumScroll = this.getMaximumScroll();
        if (maximumScroll == 0) return;

        int x = this.layout.scrollbarX();
        int y = this.layout.listTop();
        int height = this.layout.listHeight();

        graphics.fill(x, y, x + 8, y + height, XP.SCROLLBAR_TRACK);
        XP.drawSunkenBorder(graphics, x, y, 8, height);

        int thumbHeight = Math.max(14, height * VISIBLE_ROWS / buttons.size());
        int thumbY = y + (height - thumbHeight) * this.scrollIndex / maximumScroll;
        XP.drawRaisedPanel(graphics, x + 1, thumbY, 6, thumbHeight, XP.WINDOW_FACE);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.layout.isOverList(mouseX, mouseY) || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);

        this.scrollIndex = Mth.clamp(this.scrollIndex + (scrollY > 0 ? -1 : 1), 0, this.getMaximumScroll());
        this.updateListPositions();
        return true;
    }

    private void selectTab(Tab tab) {
        if (this.selectedTab == tab) return;

        this.selectedTab = tab;
        this.scrollIndex = 0;
        this.updateWidgetStates();
        this.updateListPositions();
    }

    private void selectGoal(String goal) {
        ClientPacketDistributor.sendToServer(goal.isEmpty() ? BEBossEditorNetwork.ActionPayload.clearGoal() : BEBossEditorNetwork.ActionPayload.forceGoal(goal, this.stateDelayTicks));
    }

    private void selectState(String state) {
        ClientPacketDistributor.sendToServer(BEBossEditorNetwork.ActionPayload.setState(state, this.stateDelayTicks));
    }

    private void toggleFrozen() {
        ClientPacketDistributor.sendToServer(BEBossEditorNetwork.ActionPayload.setFrozen(!this.snapshot.frozen()));
    }

    private void resumeAi() {
        ClientPacketDistributor.sendToServer(BEBossEditorNetwork.ActionPayload.resumeAi());
    }

    private void moveToTarget() {
        ClientPacketDistributor.sendToServer(this.snapshot.moving() ? BEBossEditorNetwork.ActionPayload.stopMove() : BEBossEditorNetwork.ActionPayload.startMove());
    }

    private void clearTarget() {
        ClientPacketDistributor.sendToServer(BEBossEditorNetwork.ActionPayload.clearTarget());
    }

    private void updateWidgetStates() {
        boolean selected = this.snapshot.selected();

        this.controls.goalsTab.setSelected(this.selectedTab == Tab.GOALS);
        this.controls.statesTab.setSelected(this.selectedTab == Tab.STATES);
        this.controls.freeze.setSelected(this.snapshot.frozen());
        this.controls.freeze.setMessage(this.freezeMessage());
        this.controls.move.setMessage(this.moveMessage());

        this.controls.health.active = selected;
        this.controls.freeze.active = selected;
        this.controls.resume.active = selected && this.snapshot.manualControl();
        this.controls.move.active = selected && this.snapshot.hasTarget() && !this.snapshot.frozen();
        this.controls.clearTarget.active = selected && this.snapshot.hasTarget();

        for (EditorButton button : this.goalButtons) {
            button.setSelected(!button.id.isEmpty() && button.id.equals(this.snapshot.forcedGoal()));
            button.active = selected && (button.id.isEmpty() ? !this.snapshot.forcedGoal().isEmpty() : this.isGoalAvailable(button.id));
        }

        for (EditorButton button : this.stateButtons) {
            button.setSelected(button.id.equals(this.snapshot.state()));
            button.active = selected;
        }
    }

    private boolean isGoalAvailable(String id) {
        for (BEBossEditorNetwork.GoalInfo goal : this.snapshot.goals()) {
            if (goal.id().equals(id)) return goal.available();
        }
        return false;
    }

    private void updateListPositions() {
        this.goalButtons.forEach(button -> button.visible = false);
        this.stateButtons.forEach(button -> button.visible = false);

        List<EditorButton> buttons = this.getSelectedButtons();
        this.scrollIndex = Mth.clamp(this.scrollIndex, 0, this.getMaximumScroll());

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = this.scrollIndex + row;
            if (index >= buttons.size()) break;

            EditorButton button = buttons.get(index);
            button.setY(this.layout.listTop() + row * BUTTON_STEP);
            button.visible = true;
        }
    }

    private List<EditorButton> getSelectedButtons() {
        return this.selectedTab == Tab.GOALS ? this.goalButtons : this.stateButtons;
    }

    private int getMaximumScroll() {
        return Math.max(0, this.getSelectedButtons().size() - VISIBLE_ROWS);
    }

    private Component freezeMessage() {
        return Component.literal(this.snapshot.frozen() ? "Unfreeze" : "Freeze");
    }

    private Component moveMessage() {
        return Component.literal(this.snapshot.moving() ? "Stop" : "Move");
    }

    @Override public boolean isPauseScreen() { return false; }

    private static String displayName(String id) {
        String[] parts = id.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }

        return result.toString();
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPosition(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", x, y, z);
    }

    private enum Tab { GOALS, STATES }
    private enum ButtonStyle { NORMAL, LIST, TAB, CLOSE }

    private static final class Controls {
        private EditorButton goalsTab;
        private EditorButton statesTab;
        private EditorButton freeze;
        private EditorButton resume;
        private EditorButton move;
        private EditorButton clearTarget;
        private HealthSlider health;
        private StateDelaySlider stateDelay;
    }

    private record Layout(int x, int y, int width, int height) {
        private static final Layout EMPTY = new Layout(0, 0, 0, 0);

        private static Layout create(int screenWidth, int screenHeight) {
            int width = Math.min(WINDOW_WIDTH, screenWidth - WINDOW_MARGIN * 2);
            int height = Math.min(WINDOW_HEIGHT, screenHeight - WINDOW_MARGIN * 2);
            return new Layout(WINDOW_MARGIN, (screenHeight - height) / 2, width, height);
        }

        private int right() { return this.x + this.width; }
        private int bottom() { return this.y + this.height; }
        private int contentX() { return this.x + 9; }
        private int contentRight() { return this.right() - 9; }
        private int contentWidth() { return this.width - 18; }
        private int halfContentWidth() { return (this.contentWidth() - BUTTON_GAP) / 2; }
        private int closeX() { return this.right() - 21; }
        private int closeY() { return this.y + 5; }
        private int nameY() { return this.y + TITLE_HEIGHT + 7; }
        private int healthY() { return this.nameY() + 13; }
        private int stateDelayY() { return this.healthY() + 25; }
        private int controlsY() { return this.stateDelayY() + 25; }
        private int targetTextY() { return this.controlsY() + BUTTON_HEIGHT + 6; }
        private int targetButtonsY() { return this.targetTextY() + 22; }
        private int tabsY() { return this.targetButtonsY() + BUTTON_HEIGHT + 7; }
        private int listTop() { return this.tabsY() + BUTTON_HEIGHT + 4; }
        private int listHeight() { return VISIBLE_ROWS * BUTTON_STEP - BUTTON_GAP; }
        private int listFrameX() { return this.contentX() - 2; }
        private int listFrameWidth() { return this.contentWidth() + 4; }
        private int scrollbarX() { return this.contentRight() - 8; }
        private int listX() { return this.contentX() + 1; }
        private int listWidth() { return this.contentWidth() - 12; }
        private boolean isOverList(double mouseX, double mouseY) { return mouseX >= this.listFrameX() && mouseX < this.listFrameX() + this.listFrameWidth() && mouseY >= this.listTop() && mouseY < this.listTop() + this.listHeight(); }
    }

    private static final class XP {
        private static final int WINDOW_FACE = 0xFFECE9D8;
        private static final int FIELD_BACKGROUND = 0xFFFFFFFF;
        private static final int LIGHT = 0xFFFFFFFF;
        private static final int MID_LIGHT = 0xFFF1EFE2;
        private static final int SHADOW = 0xFFACA899;
        private static final int DARK_SHADOW = 0xFF716F64;
        private static final int TEXT = 0xFF000000;
        private static final int TITLE_TEXT = 0xFFFFFFFF;
        private static final int DISABLED_TEXT = 0xFF7F7F7F;
        private static final int BLUE_TEXT = 0xFF003399;
        private static final int ALERT_TEXT = 0xFFB00000;
        private static final int TITLE_LEFT = 0xFF0A246A;
        private static final int TITLE_RIGHT = 0xFF3A6EA5;
        private static final int FOCUS = 0xFF316AC5;
        private static final int SELECTED = 0xFFC6D8F0;
        private static final int CLOSE_RED = 0xFFE04343;
        private static final int CLOSE_HOVER = 0xFFF06A5F;
        private static final int SCROLLBAR_TRACK = 0xFFD4D0C8;
        private static final int HEALTH_GREEN = 0xFF3CB043;
        private static final int HEALTH_DARK = 0xFF16821B;

        private static void drawRaisedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int face) {
            graphics.fill(x, y, x + width, y + height, face);
            graphics.fill(x, y, x + width, y + 1, LIGHT);
            graphics.fill(x, y, x + 1, y + height, LIGHT);
            graphics.fill(x + width - 1, y, x + width, y + height, DARK_SHADOW);
            graphics.fill(x, y + height - 1, x + width, y + height, DARK_SHADOW);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, MID_LIGHT);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, MID_LIGHT);
            graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, SHADOW);
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, SHADOW);
        }

        private static void drawSunkenPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int face) {
            graphics.fill(x, y, x + width, y + height, face);
            drawSunkenBorder(graphics, x, y, width, height);
        }

        private static void drawSunkenBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
            graphics.fill(x, y, x + width, y + 1, DARK_SHADOW);
            graphics.fill(x, y, x + 1, y + height, DARK_SHADOW);
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, SHADOW);
            graphics.fill(x + 1, y + 1, x + 2, y + height - 1, SHADOW);
            graphics.fill(x + width - 1, y, x + width, y + height, LIGHT);
            graphics.fill(x, y + height - 1, x + width, y + height, LIGHT);
        }

        private static void drawTitleGradient(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
            for (int row = 0; row < height; row++) {
                float progress = height <= 1 ? 0.0F : (float)row / (height - 1);
                graphics.fill(x, y + row, x + width, y + row + 1, lerpColor(TITLE_LEFT, TITLE_RIGHT, progress));
            }
            graphics.fill(x, y, x + width, y + 1, 0xFF6D9DDB);
        }

        private static int lerpColor(int from, int to, float progress) {
            int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * progress);
            int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * progress);
            int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * progress);
            int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * progress);
            return a << 24 | r << 16 | g << 8 | b;
        }
    }

    private static final class EditorButton extends AbstractWidget {
        private final String id;
        private final ButtonStyle style;
        private final Runnable onPress;
        private boolean selected;

        private EditorButton(int x, int y, int width, int height, Component message, String id, ButtonStyle style, Runnable onPress) {
            super(x, y, width, height, message);
            this.id = id;
            this.style = style;
            this.onPress = onPress;
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (this.style == ButtonStyle.CLOSE) {
                this.extractCloseButton(graphics);
                return;
            }

            int face = !this.active ? XP.WINDOW_FACE : (this.selected ? XP.SELECTED : (this.isHoveredOrFocused() ? 0xFFF7F5EA : XP.WINDOW_FACE));
            if (this.selected) XP.drawSunkenPanel(graphics, this.getX(), this.getY(), this.width, this.height, face);
            else XP.drawRaisedPanel(graphics, this.getX(), this.getY(), this.width, this.height, face);

            if (this.active && this.isHoveredOrFocused()) {
                graphics.outline(this.getX() + 2, this.getY() + 2, this.width - 4, this.height - 4, XP.FOCUS);
            }

            if (this.style == ButtonStyle.TAB && this.selected) {
                graphics.fill(this.getX() + 2, this.getY() + 2, this.getRight() - 2, this.getY() + 4, XP.FOCUS);
            }

            int text = this.active ? XP.TEXT : XP.DISABLED_TEXT;
            int textX = this.getX() + (this.width - Minecraft.getInstance().font.width(this.getMessage())) / 2;
            int textY = this.getY() + (this.height - 9) / 2;
            graphics.text(Minecraft.getInstance().font, this.getMessage(), textX, textY, text, false);
        }

        private void extractCloseButton(GuiGraphicsExtractor graphics) {
            int face = this.isHoveredOrFocused() ? XP.CLOSE_HOVER : XP.CLOSE_RED;
            XP.drawRaisedPanel(graphics, this.getX(), this.getY(), this.width, this.height, face);
            int textX = this.getX() + (this.width - Minecraft.getInstance().font.width(this.getMessage())) / 2;
            graphics.text(Minecraft.getInstance().font, this.getMessage(), textX, this.getY() + 4, XP.TITLE_TEXT, true);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (this.active) this.onPress.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private static final class HealthSlider extends AbstractSliderButton {
        private float maxHealth;
        private final Consumer<Float> onCommitted;
        private boolean dragging;

        private HealthSlider(int x, int y, int width, int height, float health, float maxHealth, Consumer<Float> onCommitted) {
            super(x, y, width, height, Component.empty(), maxHealth <= 0.0F ? 0.0D : health / maxHealth);
            this.maxHealth = Math.max(1.0F, maxHealth);
            this.onCommitted = onCommitted;
            this.updateMessage();
        }

        private void setHealth(float health, float maxHealth) {
            if (this.dragging) return;

            this.maxHealth = Math.max(1.0F, maxHealth);
            this.value = Mth.clamp(health / this.maxHealth, 0.0F, 1.0F);
            this.updateMessage();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            this.dragging = true;
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            if (!this.dragging) return;

            this.dragging = false;
            this.onCommitted.accept((float)(this.value * this.maxHealth));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("Health: " + format((float)(this.value * this.maxHealth)) + " / " + format(this.maxHealth)));
        }

        @Override protected void applyValue() { this.updateMessage(); }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            XP.drawSunkenPanel(graphics, this.getX(), this.getY(), this.width, this.height, XP.FIELD_BACKGROUND);
            graphics.text(Minecraft.getInstance().font, this.getMessage(), this.getX() + 5, this.getY() + 3, this.active ? XP.TEXT : XP.DISABLED_TEXT, false);

            int barX = this.getX() + 5;
            int barY = this.getBottom() - 7;
            int barWidth = this.width - 10;
            int filledWidth = Math.round(barWidth * (float)this.value);

            XP.drawSunkenPanel(graphics, barX, barY, barWidth, 4, XP.WINDOW_FACE);
            for (int x = barX + 1; x < barX + filledWidth - 1; x += 4) {
                int end = Math.min(x + 3, barX + filledWidth - 1);
                graphics.fill(x, barY + 1, end, barY + 3, XP.HEALTH_GREEN);
                graphics.fill(x, barY + 2, end, barY + 3, XP.HEALTH_DARK);
            }

            int handleX = barX + Math.round((barWidth - 1) * (float)this.value);
            XP.drawRaisedPanel(graphics, handleX - 2, barY - 2, 5, 8, XP.WINDOW_FACE);
            this.handleCursor(graphics);
        }
    }

    private static final class StateDelaySlider extends AbstractSliderButton {
        private final Consumer<Integer> onChanged;

        private StateDelaySlider(int x, int y, int width, int height, int ticks, Consumer<Integer> onChanged) {
            super(x, y, width, height, Component.empty(), Mth.clamp(ticks, 0, 100) / (double)100);
            this.onChanged = onChanged;
            this.updateMessage();
        }

        private int getTicks() {
            return Mth.clamp((int)Math.round(this.value * 100), 0, 100);
        }

        @Override
        protected void updateMessage() {
            int ticks = this.getTicks();
            this.setMessage(Component.literal("Delay: " + ticks + " tick" + (ticks == 1 ? "" : "s")));
        }

        @Override
        protected void applyValue() {
            int ticks = this.getTicks();
            this.value = ticks / (double)100;
            this.updateMessage();
            this.onChanged.accept(ticks);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            XP.drawSunkenPanel(graphics, this.getX(), this.getY(), this.width, this.height, XP.FIELD_BACKGROUND);
            graphics.text(Minecraft.getInstance().font, this.getMessage(), this.getX() + 5, this.getY() + 3, this.active ? XP.TEXT : XP.DISABLED_TEXT, false);

            int barX = this.getX() + 5;
            int barY = this.getBottom() - 7;
            int barWidth = this.width - 10;
            int filledWidth = Math.round((barWidth - 2) * (float)this.value);

            XP.drawSunkenPanel(graphics, barX, barY, barWidth, 4, XP.WINDOW_FACE);

            if (filledWidth > 0) graphics.fill(barX + 1, barY + 1, barX + 1 + filledWidth, barY + 3, XP.FOCUS);

            int handleX = barX + Math.round((barWidth - 1) * (float)this.value);
            XP.drawRaisedPanel(graphics, handleX - 2, barY - 2, 5, 8, XP.WINDOW_FACE);
            this.handleCursor(graphics);
        }
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_BOSS_EDITOR);
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        while (OPEN_BOSS_EDITOR.consumeClick()) {
            if (minecraft.gui.screen() == null) requestOpen();
        }
    }

    @SubscribeEvent
    public static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(BEBossEditorNetwork.SnapshotPayload.TYPE, (payload, context) -> {
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.gui.screen() instanceof BEBossScreen screen) screen.applySnapshot(payload);
            else minecraft.gui.setScreen(new BEBossScreen(payload));
        });
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (OPEN_BOSS_EDITOR.isActiveAndMatches(InputConstants.getKey(event))) {
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }
}