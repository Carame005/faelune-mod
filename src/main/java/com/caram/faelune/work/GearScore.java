package com.caram.faelune.work;

import com.caram.faelune.entity.FaeluneEntity;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;

/**
 * Rates gear so Faelune can tell which tool, weapon or armor piece is better: base stats (mining speed,
 * attack damage, armor) plus enchantment levels. Curses and nearly broken items count against it.
 */
public final class GearScore {
    /** Not usable for that purpose at all. */
    public static final double UNUSABLE = -1.0;
    /** Small differences aren't worth a trip to the beacon (and avoid swapping back and forth). */
    private static final double UPGRADE_MARGIN = 0.5;
    private static final double ENCHANTMENT_LEVEL_WEIGHT = 1.0;
    private static final double CURSE_LEVEL_WEIGHT = -3.0;
    /** Below this fraction of durability left an item is kept away from work. */
    private static final double MIN_DURABILITY_FRACTION = 0.05;

    private GearScore() {
    }

    /** Score of {@code stack} as the hand item for {@code task}, or {@link #UNUSABLE}. */
    public static double tool(WorkTask task, ItemStack stack) {
        if (stack.isEmpty() || isNearlyBroken(stack) || !FaeluneEntity.isToolSlotItem(stack)) {
            return UNUSABLE;
        }
        double base = switch (task) {
            case LUMBERJACK -> stack.is(ItemTags.AXES) ? stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()) : UNUSABLE;
            case FARMER -> stack.is(ItemTags.HOES) ? stack.getDestroySpeed(Blocks.HAY_BLOCK.defaultBlockState()) : UNUSABLE;
            case GUARD -> FaeluneEntity.isWeapon(stack) ? attribute(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) : UNUSABLE;
            case NONE, COLLECTOR -> UNUSABLE;
        };
        return base < 0 ? UNUSABLE : base + enchantmentBonus(stack);
    }

    /** Score of {@code stack} worn in {@code slot}, or {@link #UNUSABLE}. */
    public static double armor(FaeluneEntity faelune, EquipmentSlot slot, ItemStack stack) {
        if (stack.isEmpty() || isNearlyBroken(stack) || !faelune.isEquippableInSlot(stack, slot)) {
            return UNUSABLE;
        }
        double protection = attribute(stack, slot, Attributes.ARMOR) + attribute(stack, slot, Attributes.ARMOR_TOUGHNESS);
        // Elytras and the like fit the slot but don't protect: not armor for her.
        return protection <= 0 ? UNUSABLE : protection + enchantmentBonus(stack);
    }

    public static boolean isUpgrade(double candidate, double current) {
        return candidate >= 0 && (current < 0 || candidate > current + UPGRADE_MARGIN);
    }

    private static double attribute(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) {
        double[] total = {0.0};
        stack.forEachModifier(slot, (holder, modifier) -> {
            if (holder.equals(attribute)) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    private static double enchantmentBonus(ItemStack stack) {
        double bonus = 0.0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            double weight = entry.getKey().is(EnchantmentTags.CURSE) ? CURSE_LEVEL_WEIGHT : ENCHANTMENT_LEVEL_WEIGHT;
            bonus += weight * entry.getIntValue();
        }
        return bonus;
    }

    private static boolean isNearlyBroken(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return false;
        }
        int left = stack.getMaxDamage() - stack.getDamageValue();
        return left <= Math.max(1, stack.getMaxDamage() * MIN_DURABILITY_FRACTION);
    }
}
