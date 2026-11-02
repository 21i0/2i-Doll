package com.twoi.combatdoll.mixin;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.List;
import java.util.Set;

/** Skip every stretcher integration before resolving an optional target class. */
public final class CombatMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        var loading=FMLLoader.getLoadingModList();
        if(target.startsWith("com.customdoll.xiaoxiao."))return loading!=null&&loading.getMods().stream().anyMatch(m->m.getModId().equals("xiaoxiao"));
        if(target.startsWith("com.customdoll.longhuan."))return loading!=null&&loading.getMods().stream().anyMatch(m->m.getModId().equals("longhuan"));
        if(target.startsWith("com.hollingsworth.arsnouveau."))return loading!=null&&loading.getMods().stream().anyMatch(m->m.getModId().equals("ars_nouveau"));
        if(target.startsWith("cy.jdkdigital.productivebees."))return loading!=null&&loading.getMods().stream().anyMatch(m->m.getModId().equals("productivebees"));
        if (target.startsWith("com.sorrowmist.useless.stretcher.")) {
            var mods=FMLLoader.getLoadingModList();
            return mods!=null && mods.getMods().stream().anyMatch(m->m.getModId().equals("useless_stretcher"));
        }
        if(target.startsWith("com.sorrowmist.useless."))return loading!=null&&loading.getMods().stream().anyMatch(m->m.getModId().equals("useless_mod"));
        return true;
    }
    @Override public void onLoad(String p) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String>a,Set<String>b) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t,ClassNode c,String m,IMixinInfo i) { }
    @Override public void postApply(String t,ClassNode c,String m,IMixinInfo i) { }
}
