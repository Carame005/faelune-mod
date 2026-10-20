package com.caram.faelune.client.screen;

import com.caram.faelune.Faelune;
import com.caram.faelune.menu.FaeluneMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class FaeluneScreen extends AbstractContainerScreen<FaeluneMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Faelune.MODID, "textures/gui/faelune_inventory.png");
    private static final int BUTTON_HEIGHT = 13;
    private static final int PICKUP_BUTTON_WIDTH = 26;
    private static final int HEAL_BUTTON_WIDTH = 15;

    private float xMouse;
    private float yMouse;
    private Button pickupButton;
    private Button autoHealButton;

    public FaeluneScreen(FaeluneMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        // Top-right corner, on the title row above the storage grid.
        int buttonY = this.topPos + 3;
        int healX = this.leftPos + this.imageWidth - 7 - HEAL_BUTTON_WIDTH;
        this.pickupButton = this.addRenderableWidget(
                Button.builder(this.pickupLabel(), button -> this.clickButton(FaeluneMenu.BUTTON_TOGGLE_PICKUP))
                        .bounds(healX - 2 - PICKUP_BUTTON_WIDTH, buttonY, PICKUP_BUTTON_WIDTH, BUTTON_HEIGHT)
                        .tooltip(Tooltip.create(Component.translatable("gui.faelune.pickup.tooltip")))
                        .build()
        );
        this.autoHealButton = this.addRenderableWidget(
                Button.builder(this.autoHealLabel(), button -> this.clickButton(FaeluneMenu.BUTTON_TOGGLE_AUTO_HEAL))
                        .bounds(healX, buttonY, HEAL_BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build()
        );
    }

    private void clickButton(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.pickupButton != null) {
            this.pickupButton.setMessage(this.pickupLabel());
            this.pickupButton.setTooltip(Tooltip.create(Component.translatable(
                    this.menu.getFaelune().canPickupItems()
                            ? "gui.faelune.pickup.tooltip.on"
                            : "gui.faelune.pickup.tooltip.off"
            )));
        }
        if (this.autoHealButton != null) {
            this.autoHealButton.setMessage(this.autoHealLabel());
            this.autoHealButton.setTooltip(Tooltip.create(Component.translatable(
                    this.menu.getFaelune().isAutoHealEnabled()
                            ? "gui.faelune.auto_heal.tooltip.on"
                            : "gui.faelune.auto_heal.tooltip.off"
            )));
        }
    }

    private Component pickupLabel() {
        return Component.translatable(
                this.menu.getFaelune().canPickupItems() ? "gui.faelune.pickup.on" : "gui.faelune.pickup.off"
        );
    }

    private Component autoHealLabel() {
        return Component.translatable(
                this.menu.getFaelune().isAutoHealEnabled() ? "gui.faelune.auto_heal.on" : "gui.faelune.auto_heal.off"
        );
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        InventoryScreen.extractEntityInInventoryFollowsMouse(
                graphics,
                x + 26,
                y + 18,
                x + 78,
                y + 70,
                17,
                0.25f,
                this.xMouse,
                this.yMouse,
                this.menu.getFaelune()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.xMouse = mouseX;
        this.yMouse = mouseY;
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
