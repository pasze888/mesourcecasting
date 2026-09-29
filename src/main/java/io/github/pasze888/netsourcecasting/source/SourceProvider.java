package io.github.pasze888.netsourcecasting.source;

import net.minecraft.network.chat.Component;

/**
 * 一次施法中的一路魔源来源——一个 ME 网络，或超越维度的一个维度网络。
 *
 * <p>实现只回答两件事：能取多少（{@link #simulate}）与取走多少（{@link #extract}）。
 * 来源之间谁先出、凑不够时怎么办，都由 {@link SourceChain} 决定，本接口不关心。
 */
public interface SourceProvider {

    /**
     * 模拟可取出量，不改变网络内容。
     *
     * @param amount 本次最多要多少
     * @return 可取出量，不会为负
     */
    long simulate(long amount);

    /**
     * 实际取出。
     *
     * @param amount 本次最多要多少
     * @return 真正取到的量，不会为负
     */
    long extract(long amount);

    /**
     * 该来源本身一滴魔源都没有时给玩家的提示。
     *
     * <p>只在「来源可达、但取不出一滴」时使用；来源整个不可达的提示由 {@link SourceChain} 给出。
     */
    Component emptySourceReason();
}
