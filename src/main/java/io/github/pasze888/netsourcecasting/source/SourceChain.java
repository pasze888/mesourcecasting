package io.github.pasze888.netsourcecasting.source;

import io.github.pasze888.netsourcecasting.NetworkSourceCastingConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次施法可用的魔源来源链：按配置的顺序列出能取魔源的地方，并在它们之间分配抽取量。
 *
 * <p>链上有两条通道，各自是否参与由「装了哪些模组」决定：ME 网络需要 AE2 与 Ars Énergistique
 * 同时在场（后者提供 ME 魔源键），维度网络需要超越维度。两条都没装时本模组无事可做，
 * {@link #isEmpty()} 为真，调用方静默放行，行为完全等同原版 Ars Nouveau。
 *
 * <p><b>判定与扣费必须共用同一条链、同一个顺序</b>（{@link #resolve} 的结果），两处若不一致
 * 就会出现「判定通过但扣费时凑不出魔源」。
 *
 * <p>本模组不向玩家发失败提示：来源解析不出、或凑不出所需量，都只是「这次没有网络可用」，
 * 判定交还原版，由 {@code ars_nouveau.spell.no_mana} 统一报错。链上各来源的可取量由各自模拟
 * 得出，彼此独立；ME 那一路还受网络能量约束，模拟与实际之间存在固有偏差，因此「预检通过、
 * 扣费时已不够」在理论上可达，处理方式与 Ars Nouveau 原生一致：取不满就让余额回落玩家魔力，
 * 够不上就不施法。两路来源指向同一个池（ME 里插了超越维度的存储元件）时这个偏差会被放大，
 * 见 docs/design/payment-model.md。
 */
public final class SourceChain {

    /** ME 那一路需要的两个模组：AE2 提供网络，Ars Énergistique 提供魔源键 {@code arseng:source}。 */
    private static final String AE2_MODID = "ae2";
    private static final String ARSENG_MODID = "arseng";

    private final List<SourceProvider> providers;

    private SourceChain(List<SourceProvider> providers) {
        this.providers = providers;
    }

    /**
     * 解析玩家当前可用的来源链，顺序由配置 {@code sources.me_first} 决定。
     *
     * <p>没装的那条通道整条跳过；装了却解析不出可用来源（没有已绑定的终端、没有主维度网络）
     * 的同样跳过。两者都只是「少一路来源」，不收集原因、也不提示。
     */
    public static SourceChain resolve(ServerPlayer player) {
        boolean meInstalled = meChannelInstalled();
        boolean bdInstalled = ModList.get().isLoaded(BeyondDimensionsSource.MODID);
        if (!meInstalled && !bdInstalled) {
            // 一条通道都没装：本模组对这名玩家无事可做，静默放行。
            return new SourceChain(List.of());
        }

        List<SourceProvider> providers = new ArrayList<>(2);
        if (NetworkSourceCastingConfig.ME_FIRST.get()) {
            addMe(player, meInstalled, providers);
            addBeyondDimensions(player, bdInstalled, providers);
        } else {
            addBeyondDimensions(player, bdInstalled, providers);
            addMe(player, meInstalled, providers);
        }
        return new SourceChain(List.copyOf(providers));
    }

    /** ME 那一路是否可用——AE2 与 Ars Énergistique 缺一不可。 */
    private static boolean meChannelInstalled() {
        ModList mods = ModList.get();
        return mods.isLoaded(AE2_MODID) && mods.isLoaded(ARSENG_MODID);
    }

    private static void addMe(ServerPlayer player, boolean installed, List<SourceProvider> providers) {
        if (!installed) {
            return;
        }
        SourceProvider provider = MESourceHelper.resolve(player);
        if (provider != null) {
            providers.add(provider);
        }
    }

    private static void addBeyondDimensions(ServerPlayer player, boolean installed,
                                            List<SourceProvider> providers) {
        if (!installed) {
            return;
        }
        BeyondDimensionsSource source = BeyondDimensionsSource.ofPrimaryNet(player);
        if (source != null) {
            providers.add(source);
        }
    }

    /** 一路来源都没有。 */
    public boolean isEmpty() {
        return providers.isEmpty();
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
}
