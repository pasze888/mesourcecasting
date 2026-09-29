package io.github.pasze888.mesourcecasting;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本模组的通用配置（{@code config/mesourcecasting-common.toml}）。
 *
 * <p>一个开关决定网络参与施法扣费的程度，另一个决定 ME 网络与超越维度「维度网络」谁先出。
 * 两者各有自洽的语义，区别只在玩家自己的偏好，因此交给玩家选，而不是替他拍板。
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

    /**
     * {@code true}（默认）：先花 ME 网络里的魔源，不够才动超越维度的「维度网络」。
     *
     * <p>{@code false}：反过来，先花维度网络里的魔源，不够才动 ME 网络。
     * 未安装超越维度时该项无效果。
     */
    public static final ModConfigSpec.BooleanValue ME_FIRST;

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

        builder.comment("魔源来源的优先顺序")
                .push("sources");

        ME_FIRST = builder
                .comment(
                        "true（默认）：先花 ME 网络里的魔源，不够才动超越维度的「维度网络」。",
                        "false：反过来，先花维度网络里的魔源，不够才动 ME 网络。",
                        "未安装超越维度时该项无效果。")
                .translation("mesourcecasting.config.me_first")
                .define("me_first", true);

        builder.pop();

        SPEC = builder.build();
    }

    private MESourceCastingConfig() {
    }
}
