package io.github.pasze888.netsourcecasting.source;

/**
 * 魔力与魔源之间的换算。
 *
 * <p>刻意单独成类、不放进 {@link MESourceHelper}：后者引用了 AE2 与 Ars Énergistique 的类型，
 * 而这里只是算术——不该因为玩家没装那两个模组就找不到类。
 */
public final class SourceCost {

    /** 1 点魔源折算 1 点 Ars Nouveau 魔力。 */
    public static final int SOURCE_PER_MANA = 1;

    private SourceCost() {
    }

    /**
     * 把「魔力缺口」折算成需要取出的魔源量。
     *
     * <p>向上取整：宁可多取一点，也不能因取整让玩家少付而出现负数魔力。
     *
     * @param manaShortfall 玩家魔力不足的部分（&le; 0 表示魔力充足，需要 0 魔源）
     * @return 需要取出的魔源量；魔力充足时返回 {@code 0}
     */
    public static long fromManaShortfall(double manaShortfall) {
        if (manaShortfall <= 0.0D) {
            return 0L;
        }
        return (long) Math.ceil(manaShortfall) * SOURCE_PER_MANA;
    }
}
