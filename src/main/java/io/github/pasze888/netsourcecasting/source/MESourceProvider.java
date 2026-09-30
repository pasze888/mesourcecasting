package io.github.pasze888.netsourcecasting.source;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import net.minecraft.server.level.ServerPlayer;

/**
 * 一台已绑定无线终端所指向的 ME 网络。
 *
 * <p>底层沿用 AE2 的 {@code StorageHelper.poweredExtraction}，因此取出量同时受网络可用能量与
 * 魔源储量约束——这也是判定与扣费之间可能存在偏差的来源（见 {@link SourceChain} 的说明）。
 */
public final class MESourceProvider implements SourceProvider {

    private final IGrid grid;
    private final ServerPlayer player;

    public MESourceProvider(IGrid grid, ServerPlayer player) {
        this.grid = grid;
        this.player = player;
    }

    @Override
    public long simulate(long amount) {
        return MESourceHelper.extractSource(grid, player, amount, Actionable.SIMULATE);
    }

    @Override
    public long extract(long amount) {
        return MESourceHelper.extractSource(grid, player, amount, Actionable.MODULATE);
    }
}
