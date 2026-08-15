package cn.remix.ui.screen.impl;

import cn.remix.Client;
import cn.remix.ui.font.TrueTypeFont;
import cn.remix.util.render.Render2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.path.PathUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.rule.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public final class OwnSelectWorldScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwnSelectWorldScreen.class);
    public static final GeneratorOptions DEBUG_GENERATOR_OPTIONS = new GeneratorOptions((long) "test1".hashCode(), true, false);

    private static final Identifier BACKGROUND = Identifier.of("remix", "textures/mainmenu/background.png");

    protected final Screen parent;
    private final ThreePartsLayoutWidget layout;
    private @Nullable ButtonWidget deleteButton;
    private @Nullable ButtonWidget selectButton;
    private @Nullable ButtonWidget editButton;
    private @Nullable ButtonWidget recreateButton;
    protected @Nullable TextFieldWidget searchBox;
    private @Nullable WorldListWidget levelList;

    public OwnSelectWorldScreen(Screen parent) {
        super(Text.translatable("selectWorld.title"));
        Objects.requireNonNull(MinecraftClient.getInstance().textRenderer);
        this.layout = new ThreePartsLayoutWidget(this, 8 + 9 + 8 + 20 + 4, 60);
        this.parent = parent;
    }

    @Override
    protected void init() {
        DirectionalLayoutWidget directionalLayoutWidget = this.layout.addHeader(DirectionalLayoutWidget.vertical().spacing(4));
        directionalLayoutWidget.getMainPositioner().alignHorizontalCenter();
        directionalLayoutWidget.add(new TextWidget(this.title, this.textRenderer));

        DirectionalLayoutWidget directionalLayoutWidget2 = directionalLayoutWidget.add(DirectionalLayoutWidget.horizontal().spacing(4));
        directionalLayoutWidget2.add(this.createDebugRecreateButton());

        this.searchBox = directionalLayoutWidget2.add(new TextFieldWidget(
                this.textRenderer, this.width / 2 - 100, 22, 200, 20,
                this.searchBox, Text.translatable("selectWorld.search")
        ));
        this.searchBox.setChangedListener(search -> {
            if (this.levelList != null) {
                this.levelList.setSearch(search);
            }
        });
        this.searchBox.setPlaceholder(Text.translatable("gui.selectWorld.search").setStyle(TextFieldWidget.SEARCH_STYLE));

        Consumer<WorldListWidget.WorldEntry> consumer = WorldListWidget.WorldEntry::play;
        this.levelList = this.layout.addBody(
                (new WorldListWidget.Builder(this.client, this))
                        .width(this.width)
                        .height(this.layout.getContentHeight())
                        .search(this.searchBox.getText())
                        .predecessor(this.levelList)
                        .selectionCallback(this::worldSelected)
                        .confirmationCallback(consumer)
                        .toWidget()
        );

        this.addButtons(consumer, this.levelList);

        this.layout.forEachChild(this::addDrawableChild);

        this.refreshWidgetPositions();
        this.worldSelected(null);
    }

    private void addButtons(Consumer<WorldListWidget.WorldEntry> playAction, WorldListWidget levelList) {
        GridWidget gridWidget = this.layout.addFooter(new GridWidget().setColumnSpacing(8).setRowSpacing(4));
        gridWidget.getMainPositioner().alignHorizontalCenter();
        GridWidget.Adder adder = gridWidget.createAdder(4);

        this.selectButton = adder.add(
                ButtonWidget.builder(LevelSummary.SELECT_WORLD_TEXT, button -> {
                    levelList.getSelectedAsOptional().ifPresent(playAction);
                }).build(),
                2
        );

        adder.add(
                ButtonWidget.builder(Text.translatable("selectWorld.create"), button -> {
                    CreateWorldScreen.show(this.client, levelList::refresh);
                }).build(),
                2
        );

        this.editButton = adder.add(
                ButtonWidget.builder(Text.translatable("selectWorld.edit"), button -> {
                    levelList.getSelectedAsOptional().ifPresent(WorldListWidget.WorldEntry::edit);
                }).width(71).build()
        );

        this.deleteButton = adder.add(
                ButtonWidget.builder(Text.translatable("selectWorld.delete"), button -> {
                    levelList.getSelectedAsOptional().ifPresent(WorldListWidget.WorldEntry::deleteIfConfirmed);
                }).width(71).build()
        );

        this.recreateButton = adder.add(
                ButtonWidget.builder(Text.translatable("selectWorld.recreate"), button -> {
                    levelList.getSelectedAsOptional().ifPresent(WorldListWidget.WorldEntry::recreate);
                }).width(71).build()
        );

        adder.add(
                ButtonWidget.builder(ScreenTexts.BACK, button -> {
                    this.client.setScreen(this.parent);
                }).width(71).build()
        );
    }

    private ButtonWidget createDebugRecreateButton() {
        return ButtonWidget.builder(Text.literal("DEBUG recreate"), button -> {
            try {
                if (this.levelList != null && !this.levelList.children().isEmpty()) {
                    WorldListWidget.Entry entry = this.levelList.children().getFirst();
                    if (entry instanceof WorldListWidget.WorldEntry worldEntry) {
                        if (worldEntry.getLevelDisplayName().equals("DEBUG world")) {
                            worldEntry.delete();
                        }
                    }
                }

                LevelInfo levelInfo = new LevelInfo(
                        "DEBUG world",
                        GameMode.SPECTATOR,
                        false,
                        Difficulty.NORMAL,
                        true,
                        new GameRules(DataConfiguration.SAFE_MODE.enabledFeatures()),
                        DataConfiguration.SAFE_MODE
                );
                String string2 = PathUtil.getNextUniqueName(
                        this.client.getLevelStorage().getSavesDirectory(),
                        "DEBUG world",
                        ""
                );
                this.client.createIntegratedServerLoader().createAndStart(
                        string2,
                        levelInfo,
                        DEBUG_GENERATOR_OPTIONS,
                        WorldPresets::createDemoOptions,
                        this
                );
            } catch (IOException iOException) {
                LOGGER.error("Failed to recreate the debug world", iOException);
            }
        }).width(90).build();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = this.width;
        int height = this.height;

        Render2D.drawTexture(context, BACKGROUND, 0, 0, width, height, 0, 0, 1, 1, 0xFFFFFFFF);
        Render2D.drawRect(context, 0, 0, width, height, new Color(0, 0, 0, 60).getRGB());

        TrueTypeFont font24 = Client.instance.getFontManager().getFont(24);
        String title = "Select World";
        float titleX = (width - font24.getStringWidth(title)) / 2f;
        font24.drawString(context, title, titleX, 16, new Color(255, 255, 255, 180).getRGB(), false);

        if (this.levelList != null) {
            float listY = 58;
            float listHeight = height - 105;
            Render2D.drawRect(context, 20, listY, width - 40, listHeight, new Color(0, 0, 0, 50).getRGB());
            Render2D.drawOutline(context, 20, listY, width - 40, listHeight, 1.0f, new Color(255, 255, 255, 15).getRGB());

            this.levelList.position(this.width, this.layout);
            this.levelList.render(context, mouseX, mouseY, delta);
        }

        if (this.searchBox != null) {
            this.searchBox.render(context, mouseX, mouseY, delta);
        }

        for (var child : this.children()) {
            if (child instanceof ButtonWidget button) {
                drawStyledButton(context, button, mouseX, mouseY);
            }
        }

        TrueTypeFont font12 = Client.instance.getFontManager().getFont(12);
        String footer = "Worlds are stored in .minecraft/saves";
        float footerX = (width - font12.getStringWidth(footer)) / 2f;
        font12.drawString(context, footer, footerX, height - 12, new Color(180, 185, 200, 50).getRGB(), false);
    }

    private void drawStyledButton(DrawContext context, ButtonWidget button, int mouseX, int mouseY) {
        if (button == null) return;

        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();

        boolean hovered = mouseX >= x && mouseX <= x + width &&
                mouseY >= y && mouseY <= y + height;
        boolean active = button.active;

        int bgAlpha = active ? (hovered ? 40 : 20) : 10;
        int bgColor = new Color(255, 255, 255, bgAlpha).getRGB();
        Render2D.drawRect(context, x, y, width, height, bgColor);

        int borderAlpha = active ? (hovered ? 80 : 30) : 15;
        int borderColor = new Color(255, 255, 255, borderAlpha).getRGB();
        Render2D.drawOutline(context, x, y, width, height, 1.0f, borderColor);

        TrueTypeFont font14 = Client.instance.getFontManager().getFont(14);
        String text = button.getMessage().getString();
        int textColor = active ?
                (hovered ? new Color(255, 255, 255, 255).getRGB() :
                        new Color(200, 200, 220, 200).getRGB()) :
                new Color(150, 150, 170, 120).getRGB();

        float textX = x + (width - font14.getStringWidth(text)) / 2f;
        float textY = y + (height - font14.getHeight()) / 2f + 1f;
        font14.drawString(context, text, textX, textY, textColor, false);
    }

    @Override
    protected void refreshWidgetPositions() {
        if (this.levelList != null) {
            this.levelList.position(this.width, this.layout);
        }
        this.layout.refreshPositions();
    }

    @Override
    protected void setInitialFocus() {
        if (this.searchBox != null) {
            this.setInitialFocus(this.searchBox);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    public void worldSelected(@Nullable LevelSummary levelSummary) {
        if (this.selectButton != null && this.editButton != null &&
                this.recreateButton != null && this.deleteButton != null) {
            if (levelSummary == null) {
                this.selectButton.setMessage(LevelSummary.SELECT_WORLD_TEXT);
                this.selectButton.active = false;
                this.editButton.active = false;
                this.recreateButton.active = false;
                this.deleteButton.active = false;
            } else {
                this.selectButton.setMessage(levelSummary.getSelectWorldText());
                this.selectButton.active = levelSummary.isSelectable();
                this.editButton.active = levelSummary.isEditable();
                this.recreateButton.active = levelSummary.isRecreatable();
                this.deleteButton.active = levelSummary.isDeletable();
            }
        }
    }

    @Override
    public void removed() {
        if (this.levelList != null) {
            this.levelList.children().forEach(WorldListWidget.Entry::close);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}