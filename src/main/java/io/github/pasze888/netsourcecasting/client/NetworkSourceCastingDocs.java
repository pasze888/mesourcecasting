package io.github.pasze888.netsourcecasting.client;

import com.hollingsworth.arsnouveau.api.documentation.DocCategory;
import com.hollingsworth.arsnouveau.api.documentation.ReloadDocumentationEvent;
import com.hollingsworth.arsnouveau.api.documentation.builder.DocEntryBuilder;
import com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry;
import io.github.pasze888.netsourcecasting.NetworkSourceCasting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * 把本模组的说明写进 Ars Nouveau 的教程书（Worn Notebook）。
 *
 * <p>书的内容由 Ars Nouveau 自己的文档系统管理，第三方模组的接法是它公开的：监听
 * {@link ReloadDocumentationEvent.AddEntries}，注册一个章节与若干条目即可。源码里对这个事件的
 * 注释明确写着「Mods should only listen to the AddEntries and Post events」。
 *
 * <p>事件在客户端、每次进入世界或重载时触发，所以注册必须幂等——{@code registerMainCategory} 与
 * {@code registerEntry} 都按 ID 覆盖写入，重复调用没有副作用。
 */
@EventBusSubscriber(modid = NetworkSourceCasting.MODID, value = Dist.CLIENT)
public final class NetworkSourceCastingDocs {

    /** 本模组在书里的章节。order 取 1100，排在 Ars Nouveau 自带章节之后。 */
    private static final DocCategory CATEGORY = new DocCategory(
            ResourceLocation.fromNamespaceAndPath(NetworkSourceCasting.MODID, "guide"),
            Items.ENDER_CHEST.getDefaultInstance(),
            1100);

    private NetworkSourceCastingDocs() {
    }

    @SubscribeEvent
    public static void onAddEntries(ReloadDocumentationEvent.AddEntries event) {
        DocumentationRegistry.registerMainCategory(CATEGORY);
        register("intro", 0);
        register("me_network", 10);
        register("dimension_network", 20);
        register("config", 30);
    }

    /**
     * 注册一个条目。
     *
     * <p>标题与正文都走语言键：标题由 {@link DocEntryBuilder} 按
     * {@code netsourcecasting.page.<name>} 生成，正文用
     * {@code netsourcecasting.page.<name>.text}。
     *
     * <p>图标用原版物品：本模组是纯行为模组，没有自己的物品可当图标，用可选的 AE2 / 超越维度
     * 物品则会在其中一个缺席时拿不到。
     */
    private static void register(String name, int sortNum) {
        ItemLike icon = Items.ENDER_CHEST;
        DocumentationRegistry.registerEntry(CATEGORY, new DocEntryBuilder(
                        NetworkSourceCasting.MODID, CATEGORY, name)
                .withIcon(icon)
                .withSortNum(sortNum)
                .withTextPage(Component.translatable("netsourcecasting.page." + name + ".text"))
                .build());
    }
}
