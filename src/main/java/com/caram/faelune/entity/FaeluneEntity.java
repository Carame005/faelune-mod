package com.caram.faelune.entity;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.ai.FaeluneDepositGoal;
import com.caram.faelune.entity.ai.FaeluneFarmerGoal;
import com.caram.faelune.entity.ai.FaeluneFollowOwnerGoal;
import com.caram.faelune.entity.ai.FaeluneGuardTargetGoal;
import com.caram.faelune.entity.ai.FaeluneLumberjackGoal;
import com.caram.faelune.entity.ai.FaeluneResupplyGoal;
import com.caram.faelune.entity.ai.FaeluneSeekItemGoal;
import com.caram.faelune.entity.ai.FaeluneStayNearStationGoal;
import com.caram.faelune.item.StellarTunerItem;
import com.caram.faelune.menu.FaeluneMenu;
import com.caram.faelune.registry.ModEntities;
import com.caram.faelune.registry.ModItems;
import com.caram.faelune.work.GearScore;
import com.caram.faelune.work.WorkTask;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class FaeluneEntity extends TamableAnimal implements GeoEntity {
    /** 1 weapon slot + 15 storage slots (5x3, like a chested donkey). */
    public static final int INVENTORY_SIZE = 16;
    public static final int WEAPON_SLOT = 0;
    public static final int STORAGE_SLOTS = 15;
    public static final int STORAGE_COLUMNS = 5;

    private static final EntityDataAccessor<Boolean> DATA_CAN_PICKUP_ITEMS =
            SynchedEntityData.defineId(FaeluneEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("walking");
    private static final RawAnimation SIT_ANIM = RawAnimation.begin().thenPlayAndHold("idleSit");
    private static final RawAnimation BREAKING_ANIM = RawAnimation.begin().thenLoop("breakingIdle");

    private static final EntityDataAccessor<Boolean> DATA_BREAKING_BLOCK =
            SynchedEntityData.defineId(FaeluneEntity.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private @Nullable BlockPos stationPos;
    private static final EntityDataAccessor<Boolean> DATA_AUTO_HEAL =
            SynchedEntityData.defineId(FaeluneEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int COOKIE_RESERVE = 64;
    public static final int COOKIE_REFILL_BELOW = 16;
    /** Stellar cookies heal fully, so she waits until she has lost half her health. */
    private static final float AUTO_HEAL_THRESHOLD = 0.5f;
    private static final int AUTO_HEAL_COOLDOWN = 40;
    private int autoHealCooldown;
    private int workIdleTicks;
    private static final int SUPPLY_REQUEST_TICKS = 200;
    private final Set<Item> supplyRequests = new HashSet<>();
    private int supplyRequestTick;
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            syncWeaponSlot();
        }
    };
    /** Chestplate and leggings: they only count for defense and enchantments, they aren't rendered. */
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS};
    private final SimpleContainer armorInventory = new SimpleContainer(ARMOR_SLOTS.length) {
        @Override
        public void setChanged() {
            super.setChanged();
            syncArmorSlots();
        }
    };

    public FaeluneEntity(EntityType<? extends FaeluneEntity> type, Level level) {
        super(type, level);
        // Match break anim / right_hand bone; Mob can spawn ~5% left-handed.
        this.setLeftHanded(false);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            this.setDropChance(slot, 0.0f);
        }
        this.setCanPickUpLoot(false);
    }

    public Container getArmorInventory() {
        return this.armorInventory;
    }

    private void syncArmorSlots() {
        if (!this.level().isClientSide()) {
            // Same stack references, so durability lost while equipped shows up in the menu.
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                this.setItemSlot(ARMOR_SLOTS[i], this.armorInventory.getItem(i));
            }
        }
    }

    /** Mobs don't wear their armor down by default; she does, like a player. */
    @Override
    protected void hurtArmor(DamageSource source, float damage) {
        this.doHurtEquipment(source, damage, ARMOR_SLOTS);
        this.armorInventory.setChanged();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_CAN_PICKUP_ITEMS, false);
        entityData.define(DATA_BREAKING_BLOCK, false);
        entityData.define(DATA_AUTO_HEAL, true);
    }

    public boolean isBreakingBlock() {
        return this.entityData.get(DATA_BREAKING_BLOCK);
    }

    public void setBreakingBlock(boolean breaking) {
        this.entityData.set(DATA_BREAKING_BLOCK, breaking);
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    public boolean canPickupItems() {
        return this.entityData.get(DATA_CAN_PICKUP_ITEMS);
    }

    public void setCanPickupItems(boolean enabled) {
        this.entityData.set(DATA_CAN_PICKUP_ITEMS, enabled);
        this.setCanPickUpLoot(enabled);
    }

    public void toggleCanPickupItems() {
        this.setCanPickupItems(!this.canPickupItems());
    }

    public boolean isAutoHealEnabled() {
        return this.entityData.get(DATA_AUTO_HEAL);
    }

    public void setAutoHealEnabled(boolean enabled) {
        this.entityData.set(DATA_AUTO_HEAL, enabled);
    }

    public void toggleAutoHeal() {
        this.setAutoHealEnabled(!this.isAutoHealEnabled());
    }

    public static boolean isStellarCookie(ItemStack stack) {
        return stack.is(ModItems.STELLAR_COOKIE.get());
    }

    public int countStellarCookies() {
        int count = 0;
        for (int i = 1; i < INVENTORY_SIZE; i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (isStellarCookie(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** With auto-heal on she keeps a full stack of stellar cookies, refilled from the beacon. */
    public int missingStellarCookies() {
        return this.isAutoHealEnabled() ? Math.max(0, COOKIE_RESERVE - this.countStellarCookies()) : 0;
    }

    /** Only worth a trip to the beacon once she is running low. */
    public boolean needsStellarCookies() {
        return this.isAutoHealEnabled() && this.countStellarCookies() < COOKIE_REFILL_BELOW;
    }

    private void tryAutoHeal(ServerLevel level) {
        if (!this.isTame() || !this.isAutoHealEnabled() || this.autoHealCooldown > 0
                || this.getHealth() > this.getMaxHealth() * AUTO_HEAL_THRESHOLD) {
            return;
        }
        int slot = this.findStorageSlot(FaeluneEntity::isStellarCookie);
        if (slot < 0) {
            return;
        }
        this.inventory.getItem(slot).shrink(1);
        this.inventory.setChanged();
        this.autoHealCooldown = AUTO_HEAL_COOLDOWN;
        this.playSound(SoundEvents.GENERIC_EAT.value(), 0.8f, 1.2f);
        this.healFully(level);
    }

    private void healFully(ServerLevel level) {
        this.setHealth(this.getMaxHealth());
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 15, 0.3, 0.6, 0.3, 0.03);
        this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.3f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                // Base unarmed damage; held weapons add their MAINHAND attack modifiers on top.
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                // Lets Efficiency on her tools speed up her work, as it does for players.
                .add(Attributes.MINING_EFFICIENCY);
    }

    /** Anything that can go in the hand slot: weapons plus work tools. */
    public static boolean isToolSlotItem(ItemStack stack) {
        return isWeapon(stack)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS);
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
            return true;
        }

        final boolean[] hasAttackDamage = {false};
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier, display) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.amount() > 0.0) {
                hasAttackDamage[0] = true;
            }
        });
        return hasAttackDamage[0];
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.addGoal(3, new FaeluneResupplyGoal(this, 1.1));
        this.goalSelector.addGoal(3, new FaeluneDepositGoal(this, 1.1));
        this.goalSelector.addGoal(4, new FaeluneSeekItemGoal(this, 1.15));
        this.goalSelector.addGoal(5, new FaeluneLumberjackGoal(this, 1.0));
        this.goalSelector.addGoal(5, new FaeluneFarmerGoal(this, 1.0));
        this.goalSelector.addGoal(6, new FaeluneStayNearStationGoal(this, 1.0));
        this.goalSelector.addGoal(6, new FaeluneFollowOwnerGoal(this, 1.0, 10.0f, 2.0f));
        this.goalSelector.addGoal(7, new TemptGoal(this, 1.0, stack -> stack.is(Items.COOKIE), false));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        // Assist owner in combat while following (not while sitting).
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new FaeluneGuardTargetGoal(this));
    }

    public Container getInventory() {
        return this.inventory;
    }

    /** Never fight a Faelune with the same owner (e.g. when the owner hits one by accident). */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof FaeluneEntity other && this.isSameOwner(other)) {
            return false;
        }
        return super.canAttack(target);
    }

    /** Same rules as wolves: don't follow the owner's attacks onto friendly pets, players or creepers. */
    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Creeper || target instanceof Ghast || target instanceof ArmorStand) {
            return false;
        }
        if (target instanceof FaeluneEntity other) {
            return !other.isTame() || other.getOwner() != owner;
        }
        if (target instanceof Player playerTarget && owner instanceof Player playerOwner && !playerOwner.canHarmPlayer(playerTarget)) {
            return false;
        }
        if (target instanceof AbstractHorse horse && horse.isTamed()) {
            return false;
        }
        return !(target instanceof TamableAnimal animal && animal.isTame());
    }

    private boolean isSameOwner(FaeluneEntity other) {
        var mine = this.getOwnerReference();
        var theirs = other.getOwnerReference();
        return this.isTame() && other.isTame() && mine != null && theirs != null
                && mine.getUUID().equals(theirs.getUUID());
    }

    public ItemStack getToolStack() {
        return this.inventory.getItem(WEAPON_SLOT);
    }

    // ---------------------------------------------------------------- Work station

    public @Nullable BlockPos getStationPos() {
        return this.stationPos;
    }

    public void setStation(BlockPos pos) {
        this.stationPos = pos.immutable();
        this.setOrderedToSit(false);
        this.setTarget(null);
        this.getNavigation().stop();
        this.workIdleTicks = 0;
    }

    public void clearStation() {
        this.stationPos = null;
        this.clearHome();
        this.workIdleTicks = 0;
    }

    /** The linked beacon, if its chunk is loaded and it still exists. */
    public @Nullable StellarBeaconBlockEntity getStation() {
        BlockPos pos = this.stationPos;
        if (pos == null || !this.level().isLoaded(pos)) {
            return null;
        }
        return this.level().getBlockEntity(pos) instanceof StellarBeaconBlockEntity beacon ? beacon : null;
    }

    public WorkTask getWorkTask() {
        StellarBeaconBlockEntity station = this.getStation();
        return station == null ? WorkTask.NONE : station.getTask();
    }

    /** Linked, not sitting, has a task and carries the tool that task needs. */
    public boolean isWorking() {
        if (!this.isTame() || this.isOrderedToSit() || this.isPassenger()) {
            return false;
        }
        WorkTask task = this.getWorkTask();
        return task != WorkTask.NONE && task.hasRequiredTool(this.getToolStack());
    }

    public boolean isWorkingOn(WorkTask task) {
        return this.getWorkTask() == task && this.isWorking();
    }

    public void markWorkBusy() {
        this.workIdleTicks = 0;
    }

    public int getWorkIdleTicks() {
        return this.workIdleTicks;
    }

    public void resetWorkIdle() {
        this.workIdleTicks = 0;
    }

    /** Can take care of her work gear: linked to a beacon with a task, tame, free to move. */
    public boolean canManageWorkGear() {
        return this.isTame() && !this.isOrderedToSit() && !this.isPassenger() && this.getWorkTask() != WorkTask.NONE;
    }

    /**
     * Work goals call this when they lack a supply (a sapling for a stump, seeds for farmland), so she
     * fetches it from the beacon. {@link Items#AIR} means any supply of the current task.
     */
    public void requestSupply(Item item) {
        if (this.tickCount - this.supplyRequestTick > SUPPLY_REQUEST_TICKS) {
            this.supplyRequests.clear();
        }
        this.supplyRequests.add(item);
        this.supplyRequestTick = this.tickCount;
    }

    public Set<Item> getSupplyRequests() {
        if (this.tickCount - this.supplyRequestTick > SUPPLY_REQUEST_TICKS) {
            this.supplyRequests.clear();
        }
        return this.supplyRequests;
    }

    public void clearSupplyRequests() {
        this.supplyRequests.clear();
    }

    /**
     * Swaps the item in the hand with the one in {@code slot} of {@code container}, so whatever she was
     * holding stays where the new tool was.
     */
    public void swapToolWith(Container container, int slot) {
        this.swapGear(container, slot, this.inventory, WEAPON_SLOT);
    }

    public void swapArmorWith(Container container, int slot, int armorIndex) {
        this.swapGear(container, slot, this.armorInventory, armorIndex);
    }

    private void swapGear(Container from, int fromSlot, Container gear, int gearSlot) {
        ItemStack incoming = from.getItem(fromSlot);
        ItemStack outgoing = gear.getItem(gearSlot);
        from.setItem(fromSlot, outgoing);
        gear.setItem(gearSlot, incoming);
        from.setChanged();
        gear.setChanged();
        this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8f, 1.2f);
    }

    public double currentToolScore() {
        return GearScore.tool(this.getWorkTask(), this.getToolStack());
    }

    public double currentArmorScore(int armorIndex) {
        return GearScore.armor(this, ARMOR_SLOTS[armorIndex], this.armorInventory.getItem(armorIndex));
    }

    /** Slot of {@code container} holding a better tool for the current task than the one in her hand, or -1. */
    public int findToolUpgrade(Container container, int fromSlot) {
        WorkTask task = this.getWorkTask();
        double best = this.currentToolScore();
        int bestSlot = -1;
        for (int i = fromSlot; i < container.getContainerSize(); i++) {
            double score = GearScore.tool(task, container.getItem(i));
            if (GearScore.isUpgrade(score, best)) {
                best = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Slot of {@code container} holding a better piece for that armor slot than the one she wears, or -1. */
    public int findArmorUpgrade(Container container, int fromSlot, int armorIndex) {
        double best = this.currentArmorScore(armorIndex);
        int bestSlot = -1;
        for (int i = fromSlot; i < container.getContainerSize(); i++) {
            double score = GearScore.armor(this, ARMOR_SLOTS[armorIndex], container.getItem(i));
            if (GearScore.isUpgrade(score, best)) {
                best = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Puts on the best tool and armor she carries in her own storage. */
    public void equipBestGearFromStorage() {
        int slot = this.findToolUpgrade(this.inventory, 1);
        if (slot >= 0) {
            this.swapToolWith(this.inventory, slot);
        }
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            slot = this.findArmorUpgrade(this.inventory, 1, i);
            if (slot >= 0) {
                this.swapArmorWith(this.inventory, slot, i);
            }
        }
    }

    /** First storage slot (never the tool slot) whose stack matches, or -1. */
    public int findStorageSlot(Predicate<ItemStack> filter) {
        for (int i = 1; i < INVENTORY_SIZE; i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    public boolean isStorageFull() {
        for (int i = 1; i < INVENTORY_SIZE; i++) {
            if (this.inventory.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Called after a work tool takes durability damage, so the inventory stays in sync. */
    public void damageTool(int amount) {
        ItemStack tool = this.getToolStack();
        if (!tool.isEmpty()) {
            tool.hurtAndBreak(amount, this, EquipmentSlot.MAINHAND);
            if (tool.isEmpty()) {
                this.inventory.setItem(WEAPON_SLOT, ItemStack.EMPTY);
            }
            this.inventory.setChanged();
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);

        if (this.autoHealCooldown > 0) {
            this.autoHealCooldown--;
        }
        this.tryAutoHeal(level);

        if (this.stationPos != null && this.tickCount % 20 == 0) {
            if (level.isLoaded(this.stationPos) && !(level.getBlockEntity(this.stationPos) instanceof StellarBeaconBlockEntity)) {
                this.clearStation();
            }
        }

        StellarBeaconBlockEntity station = this.getStation();
        if (station != null && this.isWorking()) {
            if (this.getTarget() != null) {
                this.markWorkBusy();
            } else {
                this.workIdleTicks++;
            }
            if (!this.hasHome() || !this.getHomePosition().equals(this.stationPos) || this.getHomeRadius() != station.getRadius()) {
                this.setHomeTo(this.stationPos, station.getRadius());
            }
            if (this.tickCount % 40 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0, this.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
            }
        } else if (this.hasHome()) {
            this.clearHome();
            this.workIdleTicks = 0;
        }
    }

    private void syncWeaponSlot() {
        if (!this.level().isClientSide()) {
            // Same stack reference so attack damage modifiers and durability stay in sync.
            this.setItemSlot(EquipmentSlot.MAINHAND, this.inventory.getItem(WEAPON_SLOT));
        }
    }

    @Override
    public boolean canPickUpLoot() {
        return this.canPickupItems() || this.isWorking();
    }

    @Override
    public boolean wantsToPickUp(ServerLevel level, ItemStack itemStack) {
        return this.canPickUpLoot() && this.hasStorageSpaceFor(itemStack);
    }

    @Override
    protected void pickUpItem(ServerLevel level, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        int before = stack.getCount();
        ItemStack remaining = this.insertIntoStorage(stack);
        int taken = before - remaining.getCount();
        if (taken > 0) {
            this.onItemPickup(entity);
            this.take(entity, taken);
            if (remaining.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(remaining);
            }
        }
    }

    public boolean hasStorageSpaceFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 1; i < INVENTORY_SIZE; i++) {
            ItemStack slot = this.inventory.getItem(i);
            if (slot.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() < Math.min(slot.getMaxStackSize(), this.inventory.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    /** Inserts into storage slots only (never the weapon slot). Returns leftover stack. */
    public ItemStack insertIntoStorage(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();

        for (int i = 1; i < INVENTORY_SIZE && !remaining.isEmpty(); i++) {
            ItemStack slot = this.inventory.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, remaining)) {
                int max = Math.min(slot.getMaxStackSize(), this.inventory.getMaxStackSize());
                int canAdd = Math.min(remaining.getCount(), max - slot.getCount());
                if (canAdd > 0) {
                    slot.grow(canAdd);
                    remaining.shrink(canAdd);
                    this.inventory.setChanged();
                }
            }
        }

        for (int i = 1; i < INVENTORY_SIZE && !remaining.isEmpty(); i++) {
            if (this.inventory.getItem(i).isEmpty()) {
                int put = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), this.inventory.getMaxStackSize()));
                this.inventory.setItem(i, remaining.split(put));
            }
        }

        return remaining;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (stack.is(ModItems.STELLAR_TUNER.get())) {
            return this.useTuner(player, stack);
        }

        if (stack.is(ModItems.STELLAR_COOKIE.get())) {
            return this.eatStellarCookie(player, stack);
        }

        if (this.isTame()) {
            if (this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
                this.feed(player, hand, stack, 1.0f, 2.0f);
                return InteractionResult.SUCCESS;
            }

            if (this.isOwnedBy(player) && !this.level().isClientSide()) {
                if (player.isShiftKeyDown()) {
                    this.openInventory(player);
                } else {
                    this.setOrderedToSit(!this.isOrderedToSit());
                    this.jumping = false;
                    this.getNavigation().stop();
                    this.setTarget(null);
                }
                return InteractionResult.SUCCESS_SERVER;
            }

            return InteractionResult.SUCCESS;
        }

        if (!this.level().isClientSide() && stack.is(Items.COOKIE)) {
            stack.consume(1, player);
            if (this.random.nextInt(3) == 0) {
                this.tame(player);
                this.getNavigation().stop();
                this.setTarget(null);
                this.setOrderedToSit(true);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
            this.setPersistenceRequired();
            return InteractionResult.SUCCESS_SERVER;
        }

        if (stack.is(Items.COOKIE)) {
            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    private InteractionResult useTuner(Player player, ItemStack tuner) {
        if (!this.isTame() || !this.isOwnedBy(player)) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            if (this.stationPos != null) {
                this.clearStation();
                player.sendOverlayMessage(Component.translatable("message.faelune.tuner.unlinked", this.getDisplayName()));
            } else {
                player.sendOverlayMessage(Component.translatable("message.faelune.tuner.not_linked", this.getDisplayName()));
            }
        } else {
            StellarTunerItem.storeFaelune(tuner, this.getUUID(), this.getDisplayName());
            player.sendOverlayMessage(Component.translatable("message.faelune.tuner.stored", this.getDisplayName()));
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private InteractionResult eatStellarCookie(Player player, ItemStack cookie) {
        if (this.isTame() && this.getHealth() >= this.getMaxHealth()) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        cookie.consume(1, player);
        if (!this.isTame()) {
            this.tame(player);
            this.getNavigation().stop();
            this.setTarget(null);
            this.setOrderedToSit(true);
            this.setPersistenceRequired();
            this.level().broadcastEntityEvent(this, (byte) 7);
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            this.healFully(serverLevel);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private void openInventory(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, playerInventory, ignored) -> new FaeluneMenu(containerId, playerInventory, this),
                    Component.translatable("container.faelune.inventory")
            ), buf -> buf.writeVarInt(this.getId()));
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.COOKIE);
    }

    @Override
    public @Nullable FaeluneEntity getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        FaeluneEntity baby = ModEntities.FAELUNE.get().create(level, EntitySpawnReason.BREEDING);
        if (baby != null && this.getOwner() instanceof Player owner) {
            baby.tame(owner);
        }
        return baby;
    }

    @Override
    protected void dropEquipment(ServerLevel level) {
        super.dropEquipment(level);
        for (SimpleContainer container : new SimpleContainer[] {this.inventory, this.armorInventory}) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    this.spawnAtLocation(level, stack);
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        ContainerHelper.saveAllItems(output.child("Inventory"), this.inventory.getItems());
        ContainerHelper.saveAllItems(output.child("Armor"), this.armorInventory.getItems());
        output.putBoolean("CanPickupItems", this.canPickupItems());
        output.putBoolean("AutoHeal", this.isAutoHealEnabled());
        if (this.stationPos != null) {
            output.store("Station", BlockPos.CODEC, this.stationPos);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.child("Inventory").ifPresent(inventoryInput ->
                ContainerHelper.loadAllItems(inventoryInput, this.inventory.getItems()));
        input.child("Armor").ifPresent(armorInput ->
                ContainerHelper.loadAllItems(armorInput, this.armorInventory.getItems()));
        this.setCanPickupItems(input.getBooleanOr("CanPickupItems", false));
        this.setAutoHealEnabled(input.getBooleanOr("AutoHeal", true));
        this.stationPos = input.read("Station", BlockPos.CODEC).orElse(null);
        this.syncWeaponSlot();
        this.syncArmorSlots();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("main", 5, state -> {
            if (this.isInSittingPose()) {
                return state.setAndContinue(SIT_ANIM);
            }
            if (this.isBreakingBlock()) {
                return state.setAndContinue(BREAKING_ANIM);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK_ANIM);
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
