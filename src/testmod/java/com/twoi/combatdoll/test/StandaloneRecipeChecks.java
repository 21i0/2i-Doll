package com.twoi.combatdoll.test;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real loaded RecipeManager matching and assembly; no filesystem/datapack manifest reads. */
public final class StandaloneRecipeChecks {
    private StandaloneRecipeChecks(){}
    public static void run(ServerLevel level){
        Map<String,Item> dolls=Map.ofEntries(
                Map.entry("baimo",Items.WHITE_WOOL),Map.entry("chuanshuo",Items.BROWN_WOOL),
                Map.entry("fenghua",Items.RED_WOOL),Map.entry("longhuan",Items.PURPLE_WOOL),Map.entry("luomu",Items.LIME_WOOL),
                Map.entry("nadir",Items.LIGHT_BLUE_WOOL),
                Map.entry("ninxin",Items.MAGENTA_WOOL),Map.entry("ooolala",Items.YELLOW_WOOL),
                Map.entry("piston",Items.GRAY_WOOL),Map.entry("qijici",Items.CYAN_WOOL),
                Map.entry("qijiu",Items.BLUE_WOOL),Map.entry("shaniao",Items.BLACK_WOOL),Map.entry("sishier",Items.ORANGE_WOOL),
                Map.entry("xiaoxiao",Items.PINK_WOOL));
        for(var doll:dolls.entrySet())verify(level,doll.getKey()+":"+doll.getKey()+"_doll",
                matrix(List.of(" W ","WSW"," W "),Map.of('W',doll.getValue(),'S',Items.STRING)));
        verify(level,"two_i_combat_doll:astral_edict",matrix(List.of(" N ","AEA"," S "),Map.of('N',Items.NETHER_STAR,'A',Items.AMETHYST_SHARD,'E',Items.END_ROD,'S',Items.NETHERITE_INGOT)));
        verify(level,"two_i_combat_doll:boundary_grimoire",matrix(List.of(" E ","ABA"," G "),Map.of('E',Items.ENDER_EYE,'A',Items.AMETHYST_SHARD,'B',Items.BOOK,'G',Items.EMERALD)));
        verify(level,"two_i_combat_doll:cangsheng_axolotl",matrix(List.of("ACA","NWN","ACA"),Map.of('A',Items.AMETHYST_SHARD,'C',Items.CHERRY_SAPLING,'N',Items.NETHER_STAR,'W',Items.AXOLOTL_BUCKET)));
        verify(level,"two_i_combat_doll:cangsheng_axolotl_flute",matrix(List.of("  A"," B ","B  "),Map.of('A',Items.AMETHYST_SHARD,'B',Items.BAMBOO)));
        for(String namespace:List.of("lonya","lonya_plush")){
            check(level.getRecipeManager().getRecipes().stream().noneMatch(holder->holder.id().getNamespace().equals(namespace)),"removed doll recipe namespace stays absent: "+namespace);
            check(!BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(namespace,namespace+"_doll")),"removed doll ingredient stays unregistered: "+namespace);
        }
        LogUtils.getLogger().info("STANDALONE SURVIVAL RECIPE MATRIX CHECKS PASSED: real RecipeManager uniquely matches and assembles all 14 colored-wool dolls plus four native weapons/pet/flute from vanilla ingredients, 18 exact registry outputs; lonya/lonya_plush recipes absent; no optional provider or local manifest needed");
    }
    private static CraftingInput matrix(List<String> rows,Map<Character,Item> keys){
        var items=new ArrayList<ItemStack>();for(String row:rows)for(char token:row.toCharArray())items.add(token==' '?ItemStack.EMPTY:new ItemStack(keys.get(token)));
        return CraftingInput.of(3,3,items);
    }
    private static void verify(ServerLevel level,String id,CraftingInput input){
        var expected=ResourceLocation.parse(id);var manager=level.getRecipeManager();
        var matching=manager.getAllRecipesFor(RecipeType.CRAFTING).stream().filter(holder->holder.value().matches(input,level)).toList();
        check(matching.size()==1,"input has exactly one real matching recipe for "+id+", matched="+matching.stream().map(holder->holder.id().toString()).toList());
        var selected=manager.getRecipeFor(RecipeType.CRAFTING,input,level).orElseThrow();
        check(selected.id().equals(expected),"real first-match lookup resolves the intended recipe "+id+", got="+selected.id());
        ItemStack result=selected.value().assemble(input,level.registryAccess());
        check(!result.isEmpty()&&result.getCount()==1&&BuiltInRegistries.ITEM.getKey(result.getItem()).equals(expected),"real assemble produces one intended registered item "+id);
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError("Standalone survival recipes: "+message);}
}
