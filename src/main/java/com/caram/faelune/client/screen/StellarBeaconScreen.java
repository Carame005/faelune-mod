package com.caram.faelune.client.screen;

import com.caram.faelune.Faelune;
import com.caram.faelune.menu.StellarBeaconMenu;
import com.caram.faelune.registry.ModItems;
import com.caram.faelune.work.WorkTask;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class StellarBeaconScreen extends AbstractContainerScreen<StellarBeaconMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Faelune.MODID, "textures/gui/stellar_beacon.png");

    private static final int TEXT_LIGHT = 0xFFE6E0FF;
    private static final int TEXT_ACCENT = 0xFF7FE8FF;
    private static final int TEXT_OK = 0xFF8CFF9E;
    private static final int TEXT_WARN = 0xFFFF8A8A;
    private static final int SELECTED_OUTLINE = 0xFF7FE8FF;

    private static final int TASK_BUTTON_X = 8;
    private static final int TASK_BUTTON_Y = 16;
    private static final int TASK_BUTTON_SIZE = 20;
    private static final int TASK_BUTTON_STEP = 22;

    private final List<Button> taskButtons = new ArrayList<>();
    private Button unlinkButton;
    private final ItemStack tunerIcon = new ItemStack(ModItems.STELLAR_TUNER.get());

    public StellarBeaconScreen(StellarBeaconMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 216);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.taskButtons.clear();

        for (WorkTask task : WorkTask.values()) {
            int x = this.leftPos + TASK_BUTTON_X + task.id() * TASK_BUTTON_STEP;
            Button button = this.addRenderableWidget(
                    Button.builder(Component.empty(), b -> this.sendButton(task.id()))
                            .bounds(x, this.topPos + TASK_BUTTON_Y, TASK_BUTTON_SIZE, TASK_BUTTON_SIZE)
                            .tooltip(Tooltip.create(task.displayName().copy().append("\n").append(task.description())))
                            .build()
            );
            this.taskButtons.add(button);
        }

        this.unlinkButton = this.addRenderableWidget(
                Button.builder(Component.empty(), b -> this.sendButton(StellarBeaconMenu.BUTTON_UNLINK_ALL))
                        .bounds(this.leftPos + 148, this.topPos + TASK_BUTTON_Y, TASK_BUTTON_SIZE, TASK_BUTTON_SIZE)
                        .tooltip(Tooltip.create(Component.translatable("gui.faelune.beacon.unlink_all")))
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.literal("-"), b -> this.sendButton(StellarBeaconMenu.BUTTON_RADIUS_DOWN))
                        .bounds(this.leftPos + 112, this.topPos + 40, 14, 14)
                        .tooltip(Tooltip.create(Component.translatable("gui.faelune.beacon.radius.down")))
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(Component.literal("+"), b -> this.sendButton(StellarBeaconMenu.BUTTON_RADIUS_UP))
                        .bounds(this.leftPos + 154, this.topPos + 40, 14, 14)
                        .tooltip(Tooltip.create(Component.translatable("gui.faelune.beacon.radius.up")))
                        .build()
        );
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        WorkTask selected = this.menu.getTask();
        for (WorkTask task : WorkTask.values()) {
            Button button = this.taskButtons.get(task.id());
            graphics.item(task.icon(), button.getX() + 2, button.getY() + 2);
            if (task == selected) {
                graphics.outline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, SELECTED_OUTLINE);
            }
        }
        if (this.unlinkButton != null) {
            graphics.item(this.tunerIcon, this.unlinkButton.getX() + 2, this.unlinkButton.getY() + 2);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_LIGHT, false);

        Component taskName = this.menu.getTask().displayName();
        graphics.text(this.font, taskName, this.imageWidth - 8 - this.font.width(taskName), this.titleLabelY, TEXT_ACCENT, false);

        graphics.text(this.font, Component.translatable("gui.faelune.beacon.linked", this.menu.getLinkedCount()), 8, 41, TEXT_LIGHT, false);

        int missing = this.menu.getMissingToolCount();
        Component status;
        int statusColor;
        if (this.menu.getLinkedCount() == 0) {
            status = Component.translatable("gui.faelune.beacon.status.no_link");
            statusColor = TEXT_WARN;
        } else if (missing > 0) {
            status = Component.translatable("gui.faelune.beacon.status.missing_tool", missing);
            statusColor = TEXT_WARN;
        } else {
            status = Component.translatable("gui.faelune.beacon.status.ok");
            statusColor = TEXT_OK;
        }
        graphics.text(this.font, status, 8, 52, statusColor, false);

        Component radius = Component.translatable("gui.faelune.beacon.radius", this.menu.getRadius());
        graphics.text(this.font, radius, 141 - this.font.width(radius) / 2, 43, TEXT_ACCENT, false);

        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT_LIGHT, false);
    }
}
