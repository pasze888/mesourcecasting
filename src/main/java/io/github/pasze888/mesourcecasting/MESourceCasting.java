package io.github.pasze888.mesourcecasting;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * ME Source Casting —— 让玩家施法时直接消耗存储网络中的魔源。
 *
 * <p>本模组是纯行为模组：不注册任何物品、方块、菜单或配方。它只做两件事：
 * <ol>
 *   <li>解析出这次施法可用的魔源来源链——背包里已绑定的 AE2 无线终端所指向的 ME 网络，
 *       以及玩家在超越维度中设为主网络的「维度网络」（未装超越维度时只有前者）；</li>
 *   <li>在 Ars Nouveau 判定与扣除施法魔力时，按链序优先从这些网络中扣除，
 *       不足的部分才回落到玩家自身魔力。</li>
 * </ol>
 *
 * <p>ME 网络中的魔源由 Ars Energistique 提供（它注册了 ME 魔源键 {@code arseng:source}），
 * 因此本模组对 Ars Energistique 是硬依赖；超越维度则是可选依赖。
 */
@Mod(MESourceCasting.MODID)
public class MESourceCasting {

    /** 模组 ID，必须与 {@code META-INF/neoforge.mods.toml} 中的 modId 一致。 */
    public static final String MODID = "mesourcecasting";

    public static final Logger LOGGER = LogUtils.getLogger();

    public MESourceCasting(IEventBus modEventBus, ModContainer modContainer) {
        // 通用配置：控制网络是「付整笔」还是「只补缺口」。必须在这里注册，NeoForge 才会加载它。
        modContainer.registerConfig(ModConfig.Type.COMMON, MESourceCastingConfig.SPEC);
        LOGGER.debug("ME Source Casting 已加载：施法将优先消耗存储网络中的魔源");
    }
}
