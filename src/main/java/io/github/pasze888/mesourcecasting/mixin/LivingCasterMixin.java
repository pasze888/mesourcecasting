package io.github.pasze888.mesourcecasting.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import com.hollingsworth.arsnouveau.api.mana.IManaCap;
import com.hollingsworth.arsnouveau.api.spell.wrapped_caster.LivingCaster;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.pasze888.mesourcecasting.MESourceCasting;
import io.github.pasze888.mesourcecasting.MESourceCastingConfig;
import io.github.pasze888.mesourcecasting.source.MESourceHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 让 Ars Nouveau 的施法把 ME 网络魔源当作魔力来源。
 *
 * <p>为什么必须 Mixin 而不是用事件：关键不在「事件有没有触发」，而在 <b>事件挂不到判定与扣费上</b>。
 * {@code SpellCostCalcEvent.Pre} 只在 {@code SpellResolver.getResolveCost()} 里触发，
 * {@code Post} 只在 {@code getExpendedCost()} / {@code LivingCaster.expendMana} 里触发；
 * 而弓与弩根本不走这两个方法——{@code SpellBow} 调的是 {@code resolver.canCast(...)}（内部是
 * {@code SpellResolver.enoughMana} → {@code getResolveCost}，路径尚可覆盖），
 * {@code SpellCrossbow} 则直接调 {@code context.getCaster().enoughMana(cost)}
 * （拿到的是 {@link LivingCaster} 这个普通方法，没有任何事件可监听），
 * 且它在 {@code :99} 先用 {@code getExpendedCost()} 试算、{@code :100} 才判定，
 * 事件顺序与其它路径都不一致。
 *
 * <p>结论：要同时满足「网络与玩家魔力都不足 → 不施法」和「网络优先、网络不够才用玩家魔力」，
 * 唯一稳定的接管点就是 {@link LivingCaster} 的 {@code enoughMana} / {@code expendMana} 这一对方法。
 */
@Mixin(LivingCaster.class)
public abstract class LivingCasterMixin {

    /**
     * 原文（{@code LivingCaster.expendMana}）：
     * <pre>{@code IManaCap mana = CapabilityRegistry.getMana(livingEntity);
     * if (mana != null) {
     *     mana.removeMana(totalCost);   // ← 本注入点
     * }}</pre>
     */
    private static final String REMOVE_MANA_CALL =
            "Lcom/hollingsworth/arsnouveau/api/mana/IManaCap;removeMana(D)D";

    /**
     * 施法前的「魔力是否足够」判定：把 ME 网络可提供的魔源一并计入，并给出失败提示。
     *
     * <p>原逻辑只看玩家自身魔力（{@code totalCost <= mana.getCurrentMana()}），
     * 这里改为「网络可提供魔源 >= 玩家魔力缺口」。非玩家实体、没有魔力能力值、
     * 或玩家魔力本来就够时，一律原样返回。
     */
    @Inject(method = "enoughMana", at = @At("RETURN"), cancellable = true)
    private void mesourcecasting$enoughMana(int totalCost, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        LivingEntity entity = ((LivingCaster) (Object) this).livingEntity;
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        IManaCap mana = CapabilityRegistry.getMana(entity);
        if (mana == null) {
            return;
        }

        long needed = mesourcecasting$sourceNeeded(totalCost, mana.getCurrentMana());
        if (needed <= 0L) {
            return;
        }

        // 只模拟不实扣：判定阶段可能被探测多次，或被后续事件取消。
        // 模拟量与扣费阶段实际要取的量保持一致，避免判定按 Long.MAX_VALUE、扣费按缺口这种不一致。
        AtomicReference<Component> errorOut = new AtomicReference<>();
        IGrid grid = MESourceHelper.findGrid(player, errorOut::set);
        if (grid == null) {
            mesourcecasting$reportNoNetwork(player, errorOut.get());
            return;
        }
        if (MESourceHelper.extractSource(grid, player, needed, Actionable.SIMULATE) >= needed) {
            cir.setReturnValue(true);
            return;
        }
        // TODO(临时诊断，验证通过后删除)
        MESourceCasting.LOGGER.info(
                "[mesourcecasting-diag] enoughMana 判定失败: needed={} 网络可取={}",
                needed, MESourceHelper.extractSource(grid, player, needed, Actionable.SIMULATE));
        mesourcecasting$reportNoSource(player, grid);
    }

    /**
     * 扣费：把原版「全额扣玩家魔力」改成「ME 网络魔源优先支付，网络付不掉的部分才扣玩家魔力」。
     *
     * <p>用 {@code @WrapOperation} 精确替换 {@code expendMana} 里那一行
     * {@code mana.removeMana(totalCost)}，而不是 {@code @Inject} + {@code @Local} 捕获局部变量。
     * 原因：{@code @Local} 只能在变量已被赋值的位置捕获，注入点选在 {@code RETURN} 时读到的
     * {@code getCurrentMana()} 已经是<b>扣费之后</b>的值，会把差额算成全额、让网络替玩家多付一份；
     * 选在 {@code HEAD} 又因变量尚未赋值而找不到候选变量、直接导致 Mixin 挂载失败。
     * {@code @WrapOperation} 恰好在扣费调用点拿到未扣除的魔力，且不需要取消整段方法。
     *
     * <p><b>顺序语义</b>：必须先把网络那份抽出来，再决定扣玩家多少。早期版本顺序写反了：它算对了
     * 要取多少魔源，却把这笔抽取放在「读当前魔力之后、扣玩家魔力之前」，且扣费时仍传玩家现有魔力，
     * 于是玩家先被清空、网络只在玩家见底后才出力，表现为「优先消耗玩家魔力」。
     *
     * <p>要取多少由配置 {@code payment.network_pays_full_cost} 决定（见
     * {@link #mesourcecasting$sourceNeeded(double, double)}）：默认取整笔花费，网络够用时玩家魔力
     * 一点不掉；关掉后只取缺口。无论哪种，玩家都只付「网络没付掉的那部分」与「自己现有魔力」中的
     * 较小者，因此<b>绝不会透支</b>。
     *
     * <p>若网络一点都取不出来（未绑定 / 接入点不可达 / 没有魔源存储），则整笔回落玩家魔力，
     * 即 Ars Nouveau 原版行为。此时不额外提示：判定阶段（{@code enoughMana}）已经给过提示，
     * 避免一次施法出现两条消息。
     */
    @WrapOperation(method = "expendMana", at = @At(value = "INVOKE", target = REMOVE_MANA_CALL))
    private double mesourcecasting$payFromNetwork(
            IManaCap mana, double totalCost, Operation<Double> original) {

        LivingEntity entity = ((LivingCaster) (Object) this).livingEntity;
        if (!(entity instanceof ServerPlayer player)) {
            return original.call(mana, totalCost);
        }

        double playerMana = mana.getCurrentMana();
        long needed = mesourcecasting$sourceNeeded(totalCost, playerMana);
        if (needed <= 0L) {
            // 按配置本次不需要网络参与：整笔走玩家，与 Ars Nouveau 原版行为一致。
            return original.call(mana, totalCost);
        }

        long paidByNetwork = 0L;
        IGrid grid = MESourceHelper.findGrid(player, null);
        if (grid != null) {
            paidByNetwork = MESourceHelper.extractSource(grid, player, needed, Actionable.MODULATE);
        }

        // 网络付剩下的部分归玩家；玩家只可能付自己已有的魔力（宁可少付也不透支）。
        double playerPays = Math.min(playerMana, Math.max(0.0D, totalCost - paidByNetwork));
        // TODO(临时诊断，验证通过后删除)
        MESourceCasting.LOGGER.info(
                "[mesourcecasting-diag] totalCost={} playerMana={} needed={} paidByNetwork={} playerPays={} 付整笔={}",
                totalCost, playerMana, needed, paidByNetwork, playerPays,
                MESourceCastingConfig.NETWORK_PAYS_FULL_COST.get());
        return original.call(mana, playerPays);
    }

    /**
     * 本次施法要向 ME 网络索取多少魔源，由配置决定：
     *
     * <ul>
     *   <li>{@code network_pays_full_cost = true}（默认）—— 索取<b>整笔</b>花费：网络够用时玩家魔力一点不掉；</li>
     *   <li>{@code network_pays_full_cost = false} —— 只索取玩家付不起的<b>缺口</b>：玩家魔力照常先扣。</li>
     * </ul>
     *
     * <p>判定（{@code enoughMana}）与扣费（{@code expendMana}）必须共用本方法：两处若用不同口径，
     * 会出现「判定通过但扣费时网络取不到」这类不一致。
     *
     * @return 需要从网络取出的魔源量；{@code 0} 表示本次不需要网络参与
     */
    private static long mesourcecasting$sourceNeeded(double totalCost, double playerMana) {
        double demand = MESourceCastingConfig.NETWORK_PAYS_FULL_COST.get()
                ? totalCost
                : totalCost - playerMana;
        return MESourceHelper.toSourceAmount(demand);
    }

    /** 没有可用的 ME 网络：区分「没绑定」与「绑定的接入点已不在」两种情况提示。 */
    private static void mesourcecasting$reportNoNetwork(ServerPlayer player, Component reason) {
        if (reason != null) {
            player.displayClientMessage(reason, true);
            return;
        }
        if (!MESourceHelper.hasLinkedTerminal(player)) {
            player.displayClientMessage(
                    MESourceHelper.message(MESourceHelper.MSG_TERMINAL_NOT_LINKED), true);
        } else {
            player.displayClientMessage(
                    MESourceHelper.message(MESourceHelper.MSG_NETWORK_NOT_FOUND), true);
        }
    }

    /** 网络可达但魔源不够：若该网络根本没有魔源存储，额外说明一句。 */
    private static void mesourcecasting$reportNoSource(ServerPlayer player, IGrid grid) {
        if (MESourceHelper.extractSource(grid, player, 1L, Actionable.SIMULATE) <= 0L) {
            player.displayClientMessage(
                    MESourceHelper.message(MESourceHelper.MSG_NO_SOURCE_STORAGE), true);
        }
    }
}
