package com.example.threebythree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ThreeByThree implements ModInitializer {
    public static final String MOD_ID = "threebythree";

    public static final ResourceKey<Enchantment> EXCAVATOR = key("excavator");
    public static final ResourceKey<Enchantment> TREE_AXE = key("treeaxe");

    /** Max logs removed per tree, so huge forests / jungle giants can't lag the server. */
    private static final int MAX_TREE_BLOCKS = 256;

    /** Guards against our own extra breaks re-triggering the event. */
    private static boolean breaking = false;

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(MOD_ID, path));
    }

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.BEFORE.register(ThreeByThree::onBeforeBreak);
    }

    private static boolean onBeforeBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (breaking) return true;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) return true;
        if (sp.isShiftKeyDown()) return true; // sneak = normal single-block break

        ItemStack tool = sp.getMainHandItem();
        if (tool.isEmpty()) return true;

        HolderLookup.RegistryLookup<Enchantment> enchants = serverLevel.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        breaking = true;
        try {
            if (state.is(BlockTags.LOGS) && hasEnchant(enchants, TREE_AXE, tool)) {
                fellTree(serverLevel, sp, pos);
            } else if (hasEnchant(enchants, EXCAVATOR, tool)) {
                mine3x3(serverLevel, sp, pos);
            }
        } finally {
            breaking = false;
        }
        return true; // the originally targeted block breaks as normal
    }

    private static boolean hasEnchant(HolderLookup.RegistryLookup<Enchantment> lookup, ResourceKey<Enchantment> key, ItemStack tool) {
        return lookup.get(key)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, tool) > 0)
                .orElse(false);
    }

    // ---------------------------------------------------------------- tree axe

    private static void fellTree(ServerLevel level, ServerPlayer sp, BlockPos origin) {
        // Flood-fill every connected log (including diagonals, so branches count).
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && logs.size() < MAX_TREE_BLOCKS) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos next = current.offset(dx, dy, dz).immutable();
                        if (!seen.add(next)) continue;
                        if (level.getBlockState(next).is(BlockTags.LOGS)) {
                            logs.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }

        for (BlockPos target : logs) {
            ItemStack tool = sp.getMainHandItem();
            if (tool.isEmpty()) return;
            // Keep the axe alive: stop with 1 durability left instead of breaking it.
            if (tool.isDamageableItem() && tool.getDamageValue() >= tool.getMaxDamage() - 1) return;
            if (!canBreak(level, sp, target)) continue;
            sp.gameMode.destroyBlock(target);
        }
    }

    // --------------------------------------------------------------------- 3x3

    private static void mine3x3(ServerLevel level, ServerPlayer sp, BlockPos pos) {
        // Work out which face the player is mining so the 3x3 is a flat plane facing them.
        Direction face;
        HitResult hit = sp.pick(sp.blockInteractionRange(), 1.0F, false);
        if (hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos)) {
            face = bhr.getDirection();
        } else {
            face = Direction.getApproximateNearest(sp.getLookAngle()).getOpposite();
        }

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue; // centre block is broken normally

                BlockPos target = switch (face.getAxis()) {
                    case Y -> pos.offset(a, 0, b);
                    case X -> pos.offset(0, a, b);
                    case Z -> pos.offset(a, b, 0);
                };

                ItemStack tool = sp.getMainHandItem();
                if (tool.isEmpty()) return; // tool broke, stop

                BlockState targetState = level.getBlockState(target);
                if (tool.getDestroySpeed(targetState) <= 1.0F) continue; // tool must be effective
                if (!canBreak(level, sp, target)) continue;

                sp.gameMode.destroyBlock(target);
            }
        }
    }

    // ------------------------------------------------------------------ shared

    private static boolean canBreak(ServerLevel level, ServerPlayer sp, BlockPos target) {
        BlockState s = level.getBlockState(target);
        if (s.isAir()) return false;
        if (s.getDestroySpeed(level, target) < 0) return false;          // bedrock etc.
        if (level.getBlockEntity(target) != null) return false;          // never nuke chests
        if (!level.mayInteract(sp, target)) return false;                // spawn protection
        return !sp.blockActionRestricted(level, target, sp.gameMode.getGameModeForPlayer());
    }
}
