package com.caram.faelune.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * In-game manual. Pages are translation keys, so the book follows the client language.
 */
public class FaeluneGuideItem extends Item {
    public static final int PAGE_COUNT = 12;

    public FaeluneGuideItem(Item.Properties properties) {
        super(properties.component(DataComponents.WRITTEN_BOOK_CONTENT, createContent()));
    }

    private static WrittenBookContent createContent() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int i = 1; i <= PAGE_COUNT; i++) {
            pages.add(Filterable.passThrough(Component.translatable("book.faelune.guide.page." + i)));
        }
        return new WrittenBookContent(Filterable.passThrough("Faelune"), "Faelune", 0, pages, true);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.openItemGui(player.getItemInHand(hand), hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
