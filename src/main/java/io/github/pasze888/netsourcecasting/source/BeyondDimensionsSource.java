package io.github.pasze888.netsourcecasting.source;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.StackKeyRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 超越维度「维度网络」中的魔源。
 *
 * <p>只认玩家的<b>主网络</b>（{@link DimensionsNet#getPrimaryNetFromPlayer}）——与超越维度自身
 * 界面和快捷键的语义一致，玩家用「主网络切换器」就能决定花哪个网络的魔源，不必逐个去猜。
 *
 * <p>魔源键按资源 ID 在 {@link StackKeyRegistry} 里查，而不是直接引用超越维度集成模块里的
 * {@code SourceStackKey}：那个类不在它的 api 包里，按 ID 查可以避免跟它的内部重构绑死。
 * 本类的任何方法都只在确认超越维度已加载后调用，未安装时其他类不会触碰它。
 */
public final class BeyondDimensionsSource implements SourceProvider {

    /** 超越维度的 mod id，调用方拿它判断模组是否在场。 */
    public static final String MODID = "beyonddimensions";

    /** 魔源在维度网络中的资源键，与超越维度 {@code SourceStackKey.ID} 一致。 */
    private static final ResourceLocation SOURCE_KEY_ID =
            ResourceLocation.fromNamespaceAndPath(MODID, "stack_type/source");

    /**
     * 查到的魔源键。查不中时不写值，下次调用会重查——类型注册发生在
     * {@code FMLCommonSetupEvent}，而施法在游戏运行期，实际只会查中一次。
     */
    private static IStackKey<?> sourceKey;

    private final UnifiedStorage storage;

    private BeyondDimensionsSource(UnifiedStorage storage) {
        this.storage = storage;
    }

    /**
     * 玩家的主维度网络所对应的来源。
     *
     * @return 玩家没有主网络（不属于任何网络，或没在切换器里选中）时返回 {@code null}
     */
    public static @Nullable BeyondDimensionsSource ofPrimaryNet(ServerPlayer player) {
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        return net == null ? null : new BeyondDimensionsSource(net.getUnifiedStorage());
    }

    /** 魔源键；超越维度尚未注册该类型（即它没装着）时返回 {@code null}。 */
    private static @Nullable IStackKey<?> sourceKey() {
        if (sourceKey == null) {
            for (IStackKey<?> candidate : StackKeyRegistry.getAllTypes()) {
                if (SOURCE_KEY_ID.equals(candidate.getTypeId())) {
                    sourceKey = candidate;
                    break;
                }
            }
        }
        return sourceKey;
    }

    @Override
    public long simulate(long amount) {
        return transfer(amount, true);
    }

    @Override
    public long extract(long amount) {
        return transfer(amount, false);
    }

    private long transfer(long amount, boolean simulate) {
        IStackKey<?> key = sourceKey();
        if (key == null || amount <= 0L) {
            return 0L;
        }
        KeyAmount moved = storage.extract(key, amount, simulate, false);
        return Math.max(moved.amount(), 0L);
    }
}
