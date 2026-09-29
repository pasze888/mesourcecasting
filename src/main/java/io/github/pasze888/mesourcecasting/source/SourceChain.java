package io.github.pasze888.mesourcecasting.source;

import appeng.api.networking.IGrid;
import io.github.pasze888.mesourcecasting.MESourceCastingConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一次施法可用的魔源来源链：按配置的顺序列出能取魔源的地方，并在它们之间分配抽取量。
 *
 * <p><b>判定与扣费必须共用同一条链、同一个顺序</b>（{@link SourceChain#resolve} 的结果），
 * 两处若不一致就会出现「判定通过但扣费时凑不出魔源」。
 *
 * <p>链上各来源的可取量由各自模拟得出，彼此独立；ME 那一路受网络能量约束，模拟与实际之间
 * 存在固有偏差，因此「预检通过、扣费时已不够」在理论上可达，处理方式与 Ars Nouveau 原生一致：
 * 取不满就让余额回落玩家魔力，够不上就不施法。
 */
public final class SourceChain {

    /** 两路来源都在场却都不可用时的提示键：这种情况提哪一路都不合适。 */
    private static final String MSG_NO_SOURCE_AVAILABLE = "message.mesourcecasting.no_source_available";

    private final List<SourceProvider> providers;
    private final @Nullable Component unavailableReason;

    private SourceChain(List<SourceProvider> providers, @Nullable Component unavailableReason) {
        this.providers = providers;
        this.unavailableReason = unavailableReason;
    }

    /**
     * 解析玩家当前可用的来源链，顺序由配置 {@code sources.me_first} 决定。
     *
     * <p>不可用的来源会被跳过并记下原因：整条链都空时，只有一路在场就报那一路的具体原因，
     * 两路都在场则给一条通用提示（否则玩家会只被告知其中一半）。
     */
    public static SourceChain resolve(ServerPlayer player) {
        List<SourceProvider> providers = new ArrayList<>(2);
        List<Component> reasons = new ArrayList<>(2);

        if (MESourceCastingConfig.ME_FIRST.get()) {
            addMe(player, providers, reasons);
            addBeyondDimensions(player, providers, reasons);
        } else {
            addBeyondDimensions(player, providers, reasons);
            addMe(player, providers, reasons);
        }

        if (!providers.isEmpty()) {
            return new SourceChain(List.copyOf(providers), null);
        }
        Component reason = reasons.size() == 1
                ? reasons.getFirst()
                : Component.translatable(MSG_NO_SOURCE_AVAILABLE);
        return new SourceChain(List.of(), reason);
    }

    private static void addMe(ServerPlayer player,
                              List<SourceProvider> providers, List<Component> reasons) {
        AtomicReference<Component> terminalError = new AtomicReference<>();
        IGrid grid = MESourceHelper.findGrid(player, terminalError::set);
        if (grid != null) {
            providers.add(new MESourceProvider(grid, player));
            return;
        }
        reasons.add(meFailureReason(player, terminalError.get()));
    }

    /**
     * ME 一路不可用的原因，口径与原实现一致：优先用 AE2 自己给出的解释，
     * 其次区分「没有绑定终端」与「绑定了但接入点已不可达」。
     */
    private static Component meFailureReason(ServerPlayer player, @Nullable Component terminalError) {
        if (terminalError != null) {
            return terminalError;
        }
        return MESourceHelper.message(MESourceHelper.hasLinkedTerminal(player)
                ? MESourceHelper.MSG_NETWORK_NOT_FOUND
                : MESourceHelper.MSG_TERMINAL_NOT_LINKED);
    }

    private static void addBeyondDimensions(ServerPlayer player,
                                            List<SourceProvider> providers, List<Component> reasons) {
        if (!ModList.get().isLoaded(BeyondDimensionsSource.MODID)) {
            // 没装超越维度：这一路不参与，也不会计入失败原因，提示保持改造前的样子。
            return;
        }
        BeyondDimensionsSource source = BeyondDimensionsSource.ofPrimaryNet(player);
        if (source != null) {
            providers.add(source);
            return;
        }
        reasons.add(Component.translatable(BeyondDimensionsSource.MSG_NO_PRIMARY_NET));
    }

    /** 一路来源都没有。 */
    public boolean isEmpty() {
        return providers.isEmpty();
    }

    /** 没有一路来源可用时给玩家的提示；{@link #isEmpty()} 为假时无意义。 */
    public Component unavailableReason() {
        return unavailableReason;
    }

    /**
     * 按链序模拟整条链总共能提供多少，不改变网络内容。
     *
     * @param amount 本次最多要多少
     * @return 能提供的量，不会超过 {@code amount}
     */
    public long availableFor(long amount) {
        long remaining = amount;
        for (SourceProvider provider : providers) {
            if (remaining <= 0L) {
                break;
            }
            remaining -= provider.simulate(remaining);
        }
        return amount - remaining;
    }

    /**
     * 按链序实际抽取，凑够 {@code amount} 即停。
     *
     * @return 真正取到的总量
     */
    public long extract(long amount) {
        long remaining = amount;
        long taken = 0L;
        for (SourceProvider provider : providers) {
            if (remaining <= 0L) {
                break;
            }
            long got = provider.extract(remaining);
            taken += got;
            remaining -= got;
        }
        return taken;
    }

    /**
     * 来源可达但一滴魔源都取不出来时的提示，用链首来源的说法。
     *
     * <p>只在 {@code availableFor(1) <= 0} 时使用。
     */
    public Component emptySourceReason() {
        return providers.getFirst().emptySourceReason();
    }
}
