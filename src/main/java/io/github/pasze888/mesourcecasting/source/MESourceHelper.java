package io.github.pasze888.mesourcecasting.source;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageHelper;
import appeng.items.tools.powered.WirelessTerminalItem;
import gripe._90.arseng.me.key.SourceKey;
import io.github.pasze888.mesourcecasting.MESourceCasting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * ME 网络魔源的直通存取。
 *
 * <p>魔源在 ME 网络中以 {@link SourceKey} 存储，由 Ars Energistique 注册；网络侧的定量抽取沿用
 * AE2 的标准写法 {@link StorageHelper#poweredExtraction}（同时受网络可用能量与存储量约束）。
 *
 * <p>本模组的语义是「直通网络」：不缓存魔源，每次施法按需现取。
 */
public final class MESourceHelper {

    /** 1 点魔源折算 1 点 Ars Nouveau 魔力。 */
    public static final int SOURCE_PER_MANA = 1;

    private static final String LANG_PREFIX = "message.mesourcecasting.";

    /** 有终端但尚未与无线接入点绑定。 */
    public static final String MSG_TERMINAL_NOT_LINKED = LANG_PREFIX + "terminal_not_linked";
    /** 已绑定，但绑定的接入点已不存在（网络被拆或区块未加载）。 */
    public static final String MSG_NETWORK_NOT_FOUND = LANG_PREFIX + "network_not_found";
    /** 网络可达，但里面没有魔源存储。 */
    public static final String MSG_NO_SOURCE_STORAGE = LANG_PREFIX + "no_source_storage";

    private MESourceHelper() {
    }

    /**
     * 把「魔力缺口」折算成需要取出的魔源量。
     *
     * <p>向上取整：宁可多取一点，也不能因取整让玩家少付而出现负数魔力。
     *
     * @param manaShortfall 玩家魔力不足的部分（&le; 0 表示魔力充足，需要 0 魔源）
     * @return 需要取出的魔源量；魔力充足时返回 {@code 0}
     */
    public static long toSourceAmount(double manaShortfall) {
        if (manaShortfall <= 0.0D) {
            return 0L;
        }
        return (long) Math.ceil(manaShortfall) * SOURCE_PER_MANA;
    }

    /**
     * 判断一个物品是不是「可绑定到 ME 网络的无线终端」。
     *
     * <p>走 AE2 自己的注册表 {@link GridLinkables}，而不是硬编码 {@code instanceof}，
     * 这样 AE2 原生终端以及第三方（含 AE2WTLib）注册的终端都能被认出来。
     */
    public static boolean isLinkableTerminal(ItemStack stack) {
        return !stack.isEmpty() && GridLinkables.get(stack.getItem()) != null;
    }

    /**
     * 找出玩家背包中第一台「已绑定且网络可达」的 AE2 无线终端所对应的 ME 网络。
     *
     * <p>这里刻意 <b>不做接入点范围校验</b>：只要终端绑定过接入点、且该接入点方块仍是有效网格节点，
     * 就认为可用（即「绑一次即可用」）。这是本模组的既定设计。
     *
     * @param errorOut 非空时会把失败原因（未绑定 / 找不到绑定网络）写入其中
     * @return 可用的 ME 网络；找不到则返回 {@code null}
     */
    public static IGrid findGrid(ServerPlayer player, Consumer<Component> errorOut) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!isLinkableTerminal(stack)) {
                continue;
            }
            if (stack.getItem() instanceof WirelessTerminalItem terminal) {
                IGrid grid = terminal.getLinkedGrid(stack, player.level(), errorOut);
                if (grid != null) {
                    return grid;
                }
            }
        }
        return null;
    }

    /**
     * 判断玩家是否至少拥有一台「已绑定」的无线终端。
     *
     * <p>用于区分失败原因：没有终端 / 有终端但没绑定 / 绑定了但网络不可达，
     * 这三种情况的提示语不同。
     */
    public static boolean hasLinkedTerminal(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isLinkableTerminal(stack)
                    && stack.getItem() instanceof WirelessTerminalItem terminal
                    && terminal.getLinkedPosition(stack) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 向网络魔源存储取指定数量的魔源。
     *
     * <p>定量抽取沿用 AE2 的标准写法 {@link StorageHelper#poweredExtraction}：同时受网络可用能量
     * 与存储量约束，取不满时返回实际取到的量。
     *
     * <p>{@code amount} 一律传「本次真正需要的量」，不要传 {@code Long.MAX_VALUE} 之类的哨兵值：
     * 该方法按 {@code retrieved / energyFactor} 计费，量级不同会让模拟与实扣落在不同的能量判定上，
     * 导致「判定通过但实扣为 0」。
     *
     * @param mode {@link Actionable#SIMULATE} 只试探可取出量（不改变网络内容），
     *             {@link Actionable#MODULATE} 实际取出
     * @return 实际（或可）取出量，不会为负
     */
    public static long extractSource(IGrid grid, ServerPlayer player, long amount, Actionable mode) {
        if (amount <= 0L) {
            return 0L;
        }
        long extracted = StorageHelper.poweredExtraction(
                grid.getEnergyService(),
                grid.getStorageService().getInventory(),
                SourceKey.KEY,
                amount,
                IActionSource.ofPlayer(player),
                mode);
        return Math.max(extracted, 0L);
    }

    /** 构造一条可翻译的提示文本。 */
    public static Component message(String key) {
        return Component.translatable(key);
    }

    /** 本模组命名空间下的资源 ID。 */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MESourceCasting.MODID, path);
    }
}
