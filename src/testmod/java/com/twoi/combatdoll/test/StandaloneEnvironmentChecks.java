package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import java.util.*;

/** Fail before creating a world if an isolated case is running with the wrong real providers. */
final class StandaloneEnvironmentChecks {
    private static final Set<String> OWN=Set.of("two_i_combat_doll","baimo","chuanshuo","fenghua","longhuan","luomu","nadir","ninxin","ooolala","piston","qijici","qijiu","shaniao","sishier","xiaoxiao");
    static void run(){
        boolean minimal=Boolean.getBoolean("two_i_combat_doll.standalone_minimal");
        String provider=System.getProperty("two_i_combat_doll.standalone_optional_mod","");
        String declared=System.getProperty("two_i_combat_doll.standalone_expected_mods","");
        Set<String> expected=new TreeSet<>();
        if(!declared.isBlank())expected.addAll(Arrays.asList(declared.split(",")));
        if(minimal){check(provider.isEmpty()&&expected.isEmpty(),"minimal case has no declared optional providers");}
        else{
            check(Set.of("curios","useless_mod","useless_stretcher","doll_chronicle").contains(provider),"optional case requires explicit provider");
            check(expected.contains(provider),"expected provider absent from declared fixture");
            Set<String> required=switch(provider){
                case "curios"->Set.of("curios");
                case "useless_mod"->Set.of("useless_mod","ae2","guideme");
                case "useless_stretcher"->Set.of("useless_stretcher","useless_mod","ae2","guideme");
                case "doll_chronicle"->Set.of("doll_chronicle","ae2","guideme","curios");
                default->throw new AssertionError();
            };
            Set<String> allowed=new HashSet<>(required);if(Set.of("useless_stretcher","useless_mod").contains(provider))allowed.addAll(Set.of("jei","mezz_config"));
            check(expected.containsAll(required)&&allowed.containsAll(expected),"unexpected/missing optional closure "+expected);
        }
        Set<String> actual=new TreeSet<>();
        for(var mod:ModList.get().getMods())if(!OWN.contains(mod.getModId())&&!Set.of("minecraft","neoforge").contains(mod.getModId()))actual.add(mod.getModId());
        check(actual.equals(expected),"real loaded external ModList "+actual+" differs from explicit fixture "+expected);
        for(String id:Set.of("lonya","lonya_plush","chuanshou"))check(!ModList.get().isLoaded(id),"retired mod identity remains absent: "+id);
        for(String id:OWN)check(ModList.get().isLoaded(id),"source-integrated doll absent: "+id);
        LogUtils.getLogger().info("STANDALONE {} CLIENT MOD SET PASSED: provider={} external={} source-integrated=15; lonya/lonya_plush absent",minimal?"MINIMAL":"OPTIONAL",provider,actual);
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError("Standalone environment: "+why);}
}
