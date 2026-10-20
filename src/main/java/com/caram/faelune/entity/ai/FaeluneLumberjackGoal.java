package com.caram.faelune.entity.ai;

import com.caram.faelune.block.entity.StellarBeaconBlockEntity;
import com.caram.faelune.entity.FaeluneEntity;
import com.caram.faelune.work.WorkTask;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fells natural trees inside the station radius: Faelune only chops the base log, then the whole
 * tree (every connected log and its natural leaves) comes down in a quick cascade. Stumps get replanted.
 */
public class FaeluneLumberjackGoal extends FaeluneBlockWorkGoal {
    private static final int MAX_CLUSTER_CHECKS = 32;
    private static final int MAX_TREE_LOGS = 256;
    private static final int MAX_TREE_LEAVES = 1024;
    /** Trees growing over the edge of the radius are still felled completely. */
    private static final int TREE_MARGIN = 4;
    private static final double CHOP_REACH_HORIZONTAL = 4.5;
    private static final double CHOP_REACH_UP = 18.0;
    private static final int LOGS_PER_TICK = 2;
    private static final int LEAVES_PER_TICK = 16;
    /** Leaf columns she may cut while working her way under a low canopy to one trunk. */
    private static final int MAX_TRIMS_PER_TREE = 24;
    private static final int TRIM_TICKS = 6;
    private static final double TRIM_REACH = 4.0;

    private enum Mode { CHOP, FELL, PLANT, TRIM }

    private Mode mode = Mode.CHOP;
    private int breakTime;
    private @Nullable BlockPos trimBase;
    private @Nullable BlockPos lastChopBase;
    private int trimsLeft;
    private int trimFeetY;
    private final List<BlockPos> fellingStumps = new ArrayList<>();
    private boolean fellingSaplingFromLeaves;
    private final Set<BlockPos> currentTree = new LinkedHashSet<>();
    private final ArrayDeque<BlockPos> fellingLogs = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> fellingLeaves = new ArrayDeque<>();

    public FaeluneLumberjackGoal(FaeluneEntity faelune, double speedModifier) {
        super(faelune, speedModifier);
    }

    @Override
    protected WorkTask task() {
        return WorkTask.LUMBERJACK;
    }

    // ------------------------------------------------------------------ target selection

    @Override
    protected @Nullable BlockPos findTarget(ServerLevel level, StellarBeaconBlockEntity station) {
        BlockPos replant = this.findReplantSpot(level, station);
        if (replant != null) {
            this.mode = Mode.PLANT;
            return replant;
        }

        AABB area = station.getWorkArea();
        List<BlockPos> logs = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(area.minX), Mth.floor(area.minY), Mth.floor(area.minZ),
                Mth.floor(area.maxX) - 1, Mth.floor(area.maxY) - 1, Mth.floor(area.maxZ) - 1)) {
            if (level.getBlockState(pos).is(BlockTags.LOGS) && !this.isIgnored(pos)) {
                logs.add(pos.immutable());
            }
        }
        logs.sort(Comparator.comparingDouble(this::distanceSqrToCenter));

        // Each log cluster is evaluated once, so a nearby house or leftover wood can't hide real trees.
        Set<BlockPos> rejected = new HashSet<>();
        int clusters = 0;
        for (BlockPos log : logs) {
            if (rejected.contains(log)) {
                continue;
            }
            if (clusters++ >= MAX_CLUSTER_CHECKS) {
                break;
            }
            Set<BlockPos> tree = collectTree(level, station, log);
            rejected.addAll(tree);
            if (!hasNaturalLeaves(level, tree)) {
                continue;
            }
            BlockPos base = this.findBase(tree);
            if (base != null) {
                this.mode = Mode.CHOP;
                this.currentTree.clear();
                this.currentTree.addAll(tree);
                if (!base.equals(this.lastChopBase)) {
                    this.lastChopBase = base;
                    this.trimsLeft = MAX_TRIMS_PER_TREE;
                }
                return base;
            }
        }
        return null;
    }

    @Override
    protected @Nullable BlockPos findNextTarget(ServerLevel level, StellarBeaconBlockEntity station, BlockPos finished) {
        if (this.mode == Mode.TRIM) {
            BlockPos base = this.trimBase;
            this.trimBase = null;
            this.mode = Mode.CHOP;
            if (base != null && level.getBlockState(base).is(BlockTags.LOGS) && !this.isIgnored(base)) {
                return base;
            }
        }
        return this.findTarget(level, station);
    }

    /**
     * Low canopies (leaves one block above the ground) don't leave room for her to walk up to the
     * trunk, so she cuts the leaves in her way, walking from {@code from} straight to the base.
     */
    @Override
    protected @Nullable BlockPos findObstruction(ServerLevel level, BlockPos pos, Vec3 from) {
        if (this.mode != Mode.CHOP || this.trimsLeft <= 0) {
            return null;
        }
        double dx = pos.getX() + 0.5 - from.x;
        double dz = pos.getZ() + 0.5 - from.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-3) {
            return null;
        }
        int feetY = Mth.floor(from.y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (double t = 0.0; t <= length; t += 0.25) {
            int x = Mth.floor(from.x + dx * t / length);
            int z = Mth.floor(from.z + dz * t / length);
            if (x == pos.getX() && z == pos.getZ()) {
                break;
            }
            for (int y = feetY; y <= feetY + 1; y++) {
                cursor.set(x, y, z);
                if (!isNaturalLeaves(level.getBlockState(cursor)) || this.isIgnored(cursor)) {
                    continue;
                }
                if (distanceSqrToCenter(from, cursor) > TRIM_REACH * TRIM_REACH) {
                    return null;
                }
                this.trimsLeft--;
                this.trimBase = pos.immutable();
                this.trimFeetY = feetY;
                this.mode = Mode.TRIM;
                return cursor.immutable();
            }
        }
        return null;
    }

    /** Lowest log of the tree (closest one for 2x2 trees), skipping bases she already failed to reach. */
    private @Nullable BlockPos findBase(Set<BlockPos> tree) {
        BlockPos best = null;
        for (BlockPos pos : tree) {
            if (this.isIgnored(pos)) {
                continue;
            }
            if (best == null
                    || pos.getY() < best.getY()
                    || pos.getY() == best.getY() && this.distanceSqrToCenter(pos) < this.distanceSqrToCenter(best)) {
                best = pos;
            }
        }
        return best;
    }

    @Override
    protected boolean isValidTarget(ServerLevel level, BlockPos pos) {
        return switch (this.mode) {
            case CHOP -> level.getBlockState(pos).is(BlockTags.LOGS);
            case FELL -> true;
            case PLANT -> {
                StellarBeaconBlockEntity station = this.faelune.getStation();
                Item wanted = station == null ? null : station.getPendingReplant(pos);
                yield wanted != null
                        && level.getBlockState(pos).canBeReplaced()
                        && this.findSaplingSlot(wanted) >= 0;
            }
            case TRIM -> isNaturalLeaves(level.getBlockState(pos));
        };
    }

    @Override
    protected boolean isInReachFrom(Vec3 from, BlockPos pos) {
        return switch (this.mode) {
            case FELL -> true;
            case TRIM -> distanceSqrToCenter(from, pos) <= TRIM_REACH * TRIM_REACH;
            case PLANT -> distanceSqrToCenter(from, pos) <= 2.5 * 2.5;
            case CHOP -> {
                double dy = pos.getY() - from.y;
                yield horizontalDistanceSqr(from, pos) <= CHOP_REACH_HORIZONTAL * CHOP_REACH_HORIZONTAL
                        && dy >= -3.0
                        && dy <= CHOP_REACH_UP;
            }
        };
    }

    @Override
    protected boolean workOn(ServerLevel level, BlockPos pos) {
        return switch (this.mode) {
            case CHOP -> this.chopBase(level, pos);
            case FELL -> this.fellStep(level, LOGS_PER_TICK, LEAVES_PER_TICK);
            case PLANT -> this.plant(level, pos);
            case TRIM -> this.trimLeaves(level, pos);
        };
    }

    @Override
    protected boolean isBreakingWork() {
        return this.mode == Mode.CHOP || this.mode == Mode.TRIM;
    }

    @Override
    protected void onTargetAbandoned(ServerLevel level, BlockPos pos) {
        switch (this.mode) {
            case CHOP, TRIM -> level.destroyBlockProgress(this.faelune.getId(), pos, -1);
            // Never leave half a tree floating: finish the cascade instantly.
            case FELL -> this.fellStep(level, Integer.MAX_VALUE, Integer.MAX_VALUE);
            case PLANT -> {
            }
        }
    }

    // ------------------------------------------------------------------ chopping and felling

    private boolean chopBase(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ItemStack tool = this.faelune.getToolStack();

        if (this.workTicks == 0) {
            float speed = tool.getDestroySpeed(state);
            if (speed > 1.0f) {
                speed += (float) this.faelune.getAttributeValue(Attributes.MINING_EFFICIENCY);
            }
            speed = Math.max(1.0f, speed);
            // Bigger trees take a bit longer to bring down.
            int sizeBonus = Math.min(30, this.currentTree.size() / 2);
            this.breakTime = Mth.ceil(30.0f / speed) + 6 + sizeBonus;
        }
        this.workTicks++;

        if (this.workTicks % 5 == 1) {
            this.faelune.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.NEUTRAL, 0.6f, 0.8f);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.35, 0.35, 0.35, 0.01);
        }
        level.destroyBlockProgress(this.faelune.getId(), pos, Math.min(9, this.workTicks * 10 / this.breakTime));

        if (this.workTicks < this.breakTime) {
            return false;
        }
        level.destroyBlockProgress(this.faelune.getId(), pos, -1);
        this.startFelling(level);
        return false;
    }

    /** Clears the leaves of one column at her body height so she can step closer to the trunk. */
    private boolean trimLeaves(ServerLevel level, BlockPos pos) {
        if (++this.workTicks == 1) {
            this.faelune.swing(InteractionHand.MAIN_HAND);
        }
        level.destroyBlockProgress(this.faelune.getId(), pos, Math.min(9, this.workTicks * 10 / TRIM_TICKS));
        if (this.workTicks < TRIM_TICKS) {
            return false;
        }
        level.destroyBlockProgress(this.faelune.getId(), pos, -1);

        ItemStack tool = this.faelune.getToolStack();
        for (int y = this.trimFeetY; y <= this.trimFeetY + 1; y++) {
            BlockPos leaf = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(leaf);
            if (!isNaturalLeaves(state)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, leaf, null, this.faelune, tool);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, leaf, Block.getId(state));
            level.setBlock(leaf, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            this.storeOrDrop(level, drops);
        }
        return true;
    }

    private void startFelling(ServerLevel level) {
        this.fellingLogs.clear();
        this.fellingLeaves.clear();

        List<BlockPos> logs = new ArrayList<>(this.currentTree);
        logs.sort(Comparator.comparingInt(BlockPos::getY));
        this.fellingLogs.addAll(logs);
        this.fellingLeaves.addAll(collectLeaves(level, this.currentTree));

        this.fellingStumps.clear();
        this.fellingSaplingFromLeaves = false;
        Item guessed = saplingForLog(level.getBlockState(logs.getFirst()));
        BlockState probe = (guessed == Items.AIR ? Blocks.OAK_SAPLING : Block.byItem(guessed)).defaultBlockState();
        StellarBeaconBlockEntity station = this.faelune.getStation();
        for (BlockPos log : logs) {
            // Saplings only look at the block below, so this works while the log is still standing.
            if (station != null && !level.getBlockState(log.below()).is(BlockTags.LOGS) && probe.canSurvive(level, log)) {
                station.addPendingReplant(log, guessed);
                this.fellingStumps.add(log);
            }
        }
        this.currentTree.clear();

        level.playSound(null, this.faelune.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0f, 0.7f);
        this.mode = Mode.FELL;
    }

    /** Breaks the next few blocks of the falling tree. Returns true once the whole tree is down. */
    private boolean fellStep(ServerLevel level, int maxLogs, int maxLeaves) {
        ItemStack tool = this.faelune.getToolStack();

        for (int i = 0; i < maxLogs && !this.fellingLogs.isEmpty(); i++) {
            BlockPos pos = this.fellingLogs.poll();
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), this.faelune, tool);
            level.destroyBlock(pos, false, this.faelune, 512);
            this.storeOrDrop(level, drops);
            this.faelune.damageTool(1);
            tool = this.faelune.getToolStack();
        }

        for (int i = 0; i < maxLeaves && !this.fellingLeaves.isEmpty(); i++) {
            BlockPos pos = this.fellingLeaves.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof LeavesBlock) || state.getValue(LeavesBlock.PERSISTENT)) {
                continue;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, this.faelune, tool);
            this.learnSaplingFromLeaves(drops);
            if (i % 4 == 0) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            }
            level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            this.storeOrDrop(level, drops);
        }

        if (this.fellingLogs.isEmpty() && this.fellingLeaves.isEmpty()) {
            this.mode = Mode.CHOP;
            return true;
        }
        return false;
    }

    /** Flood-fills every log connected to {@code start} (including diagonal oak branches). */
    private static Set<BlockPos> collectTree(ServerLevel level, StellarBeaconBlockEntity station, BlockPos start) {
        BlockPos center = station.getBlockPos();
        int maxHorizontal = station.getRadius() + TREE_MARGIN;
        Set<BlockPos> tree = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        tree.add(start);
        queue.add(start);

        while (!queue.isEmpty() && tree.size() < MAX_TREE_LOGS) {
            BlockPos pos = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (tree.contains(next)
                                || Math.abs(next.getX() - center.getX()) > maxHorizontal
                                || Math.abs(next.getZ() - center.getZ()) > maxHorizontal
                                || next.getY() < center.getY() - 4
                                || next.getY() > center.getY() + 40
                                || !level.getBlockState(next).is(BlockTags.LOGS)) {
                            continue;
                        }
                        tree.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return tree;
    }

    /**
     * Natural leaves that belong to this tree. Leaves store their distance to the nearest log, so walking
     * outward only through leaves whose distance keeps growing stays inside this tree's canopy and
     * leaves neighbouring trees alone.
     */
    private static List<BlockPos> collectLeaves(ServerLevel level, Set<BlockPos> logs) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        for (BlockPos log : logs) {
            for (Direction direction : Direction.values()) {
                BlockPos next = log.relative(direction);
                if (visited.add(next) && isNaturalLeaves(level.getBlockState(next))) {
                    queue.add(next);
                    result.add(next);
                }
            }
        }

        while (!queue.isEmpty() && result.size() < MAX_TREE_LEAVES) {
            BlockPos pos = queue.poll();
            int distance = level.getBlockState(pos).getValue(LeavesBlock.DISTANCE);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!visited.add(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (isNaturalLeaves(state) && state.getValue(LeavesBlock.DISTANCE) > distance) {
                    queue.add(next);
                    result.add(next);
                }
            }
        }
        return result;
    }

    private static boolean isNaturalLeaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT);
    }

    private static boolean hasNaturalLeaves(ServerLevel level, Set<BlockPos> tree) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockPos log : tree) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (isNaturalLeaves(level.getBlockState(cursor.setWithOffset(log, dx, dy, dz)))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ replanting

    /** The tree's own leaves are the most reliable source (azaleas, for example, grow on oak logs). */
    private void learnSaplingFromLeaves(List<ItemStack> drops) {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        if (this.fellingSaplingFromLeaves || this.fellingStumps.isEmpty() || station == null) {
            return;
        }
        for (ItemStack drop : drops) {
            if (WorkTask.isSapling(drop)) {
                this.fellingSaplingFromLeaves = true;
                for (BlockPos stump : this.fellingStumps) {
                    station.setPendingReplantSapling(stump, drop.getItem());
                }
                return;
            }
        }
    }

    /** spruce_log -> spruce_sapling, mangrove_log -> mangrove_propagule... AIR if there is no match. */
    private static Item saplingForLog(BlockState log) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(log.getBlock());
        String path = id.getPath();
        for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae"}) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String candidate : new String[] {path + "_sapling", path + "_propagule"}) {
            Item item = BuiltInRegistries.ITEM.getValue(id.withPath(candidate));
            if (item != Items.AIR && WorkTask.isSapling(new ItemStack(item))) {
                return item;
            }
        }
        return Items.AIR;
    }

    /** Slot with the exact sapling a stump needs; any sapling if the type is unknown. */
    private int findSaplingSlot(@Nullable Item wanted) {
        if (wanted == null || wanted == Items.AIR) {
            return this.faelune.findStorageSlot(WorkTask::isSapling);
        }
        return this.faelune.findStorageSlot(stack -> stack.is(wanted));
    }

    private boolean plant(ServerLevel level, BlockPos pos) {
        StellarBeaconBlockEntity station = this.faelune.getStation();
        Item wanted = station == null ? null : station.getPendingReplant(pos);
        if (wanted == null) {
            return true;
        }
        int slot = this.findSaplingSlot(wanted);
        if (slot < 0) {
            // Leave the stump pending for a coworker that does carry the sapling.
            return true;
        }
        station.removePendingReplant(pos);

        ItemStack sapling = this.faelune.getInventory().getItem(slot);
        BlockState saplingState = Block.byItem(sapling.getItem()).defaultBlockState();
        if (level.getBlockState(pos).canBeReplaced() && saplingState.canSurvive(level, pos)) {
            level.setBlock(pos, saplingState, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            this.faelune.swing(InteractionHand.MAIN_HAND);
            sapling.shrink(1);
            this.faelune.getInventory().setChanged();
        }
        return true;
    }

    private @Nullable BlockPos findReplantSpot(ServerLevel level, StellarBeaconBlockEntity station) {
        if (station.getPendingReplants().isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, Item> entry : List.copyOf(station.getPendingReplants().entrySet())) {
            BlockPos pos = entry.getKey();
            if (!station.isInWorkArea(pos) || !level.getBlockState(pos).canBeReplaced()) {
                station.removePendingReplant(pos);
                continue;
            }
            if (this.isIgnored(pos)) {
                continue;
            }
            if (this.findSaplingSlot(entry.getValue()) < 0) {
                this.faelune.requestSupply(entry.getValue());
                continue;
            }
            double dist = this.distanceSqrToCenter(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    /** Drops go straight into her storage; overflow lands at her feet so she can pick it up later. */
    private void storeOrDrop(ServerLevel level, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemStack leftover = this.faelune.insertIntoStorage(drop);
            if (!leftover.isEmpty()) {
                Block.popResource(level, this.faelune.blockPosition(), leftover);
            }
        }
    }
}
