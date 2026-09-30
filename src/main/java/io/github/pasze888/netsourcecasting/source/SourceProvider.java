package io.github.pasze888.netsourcecasting.source;

/**
 * 一次施法中的一路魔源来源——一个 ME 网络，或超越维度的一个维度网络。
 *
 * <p>实现只回答两件事：能取多少（{@link #simulate}）与取走多少（{@link #extract}）。
 * 来源之间谁先出、凑不够时怎么办，都由 {@link SourceChain} 决定，本接口不关心。
 * 也不回答「为什么取不到」——本模组不发失败提示，一律交由 Ars Nouveau 原版报错。
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
}
