package io.github.pasze888.mesourcecasting;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的通用配置（{@code config/mesourcecasting-common.toml}）。
 *
 * <p>只有一个开关，用来决定 ME 网络参与施法扣费的程度。两种语义都能自洽，区别只在「网络够用时
 * 玩家还要不要出魔力」，因此交给玩家自己选，而不是替他拍板。
 *
 * <p>配置于 {@code FMLCommonSetupEvent} 之前加载完成，而施法发生在游戏运行期，故
 * {@link #NETWORK_PAYS_FULL_COST} 在 {@code expendMana} / {@code enoughMana} 里读取是安全的。
 */
public final class MESourceCastingConfig {

    /** 配置规格，由主类在构造时注册到 {@code ModConfig.Type.COMMON}。 */
    public static final ModConfigSpec SPEC;

    /**
     * {@code true}（默认）：网络优先，且优先付**整笔**花费——只要网络里的魔源够，施法完全不消耗
     * 玩家自身魔力。
     *
     * <p>{@code false}：网络只补**缺口**——玩家魔力先照常扣，只有玩家付不起的部分才由网络承担；
     * 玩家魔力够付整笔时网络一点不动。
     */
    public static final ModConfigSpec.BooleanValue NETWORK_PAYS_FULL_COST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("ME 网络魔源与玩家自身魔力的扣费分工")
                .push("payment");

        NETWORK_PAYS_FULL_COST = builder
                .comment(
                        "true：网络优先，且优先付整笔花费——网络里的魔源够用时不消耗玩家自身魔力。",
                        "false：网络只补缺口——玩家魔力先照常扣，只有玩家付不起的部分才由网络承担；",
                        "        玩家魔力够付整笔时网络一点不动。",
                        "两种设置下，网络与玩家魔力加起来仍不足以支付时都不会施法。")
                .translation("mesourcecasting.config.network_pays_full_cost")
                .define("network_pays_full_cost", true);

        builder.pop();

        SPEC = builder.build();
    }

    private MESourceCastingConfig() {
    }
}
