package com.caram.faelune.client.screen;

import com.caram.faelune.Faelune;
import com.caram.faelune.menu.StellarHopperMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class StellarHopperScreen extends AbstractContainerScreen<StellarHopperMenu> {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Faelune.MODID, "textures/gui/stellar_hopper.png");

    private static final int TEXT_LIGHT = 0xFFE6E0FF;
    private static final int TEXT_ACCENT = 0xFF7FE8FF;
    private static final int TOOLTIP_WIDTH = 160;

    private static final Component FILTER_LABEL = Component.translatable("gui.faelune.hopper.filter");
    private static final Component FILTER_TOOLTIP = Component.translatable("gui.faelune.hopper.filter.tooltip");

    public StellarHopperScreen(StellarHopperMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 158);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredSlot != null
                && !this.hoveredSlot.hasItem()
                && this.menu.getCarried().isEmpty()
                && this.menu.isFilterSlot(this.hoveredSlot)) {
            graphics.setTooltipForNextFrame(this.font, this.font.split(FILTER_TOOLTIP, TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_LIGHT, false);
        graphics.text(
                this.font, FILTER_LABEL,
                StellarHopperMenu.FILTER_X - 5 - this.font.width(FILTER_LABEL), StellarHopperMenu.FILTER_Y + 4,
                TEXT_ACCENT, false
        );
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT_LIGHT, false);
    }
}
