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
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * ME 网络这一路来源的直通存取。
 *
 * <p>本类引用 AE2 与 Ars Énergistique（{@link SourceKey} 由后者注册）的类型，因此<b>只在
 * {@link SourceChain} 确认这两个模组都在场之后才会被触碰</b>——只装超越维度的玩家根本不会走到这里。
 * 纯算术的换算见 {@link SourceCost}，也不放在本类。
 *
 * <p>魔源在 ME 网络中以 {@link SourceKey} 存储；网络侧的定量抽取沿用 AE2 的标准写法
 * {@link StorageHelper#poweredExtraction}（同时受网络可用能量与存储量约束）。
 *
 * <p>本模组的语义是「直通网络」：不缓存魔源，每次施法按需现取。
 */
public final class MESourceHelper {

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
     * 把玩家背包里第一台可用的无线终端所指的网络包成一路来源。
     *
     * <p>调用方必须先确认 AE2 与 Ars Énergistique 都在场——本类一被触碰就会用到它们的类型。
     *
     * @param reasonOut 找不到时把不可用的原因写入其中
     * @return 可用的来源；找不到时返回 {@code null}
     */
    public static @Nullable SourceProvider resolve(ServerPlayer player, Consumer<Component> reasonOut) {
        AtomicReference<Component> terminalError = new AtomicReference<>();
        IGrid grid = findGrid(player, terminalError::set);
        if (grid != null) {
            return new MESourceProvider(grid, player);
        }
        reasonOut.accept(meFailureReason(player, terminalError.get()));
        return null;
    }

    /**
     * ME 一路不可用的原因：优先用 AE2 自己给出的解释，
     * 其次区分「没有绑定终端」与「绑定了但接入点已不可达」。
     */
    private static Component meFailureReason(ServerPlayer player, @Nullable Component terminalError) {
        if (terminalError != null) {
            return terminalError;
        }
        return message(hasLinkedTerminal(player) ? MSG_NETWORK_NOT_FOUND : MSG_TERMINAL_NOT_LINKED);
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
