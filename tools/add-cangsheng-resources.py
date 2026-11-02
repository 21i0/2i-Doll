"""Deterministic native Minecraft item, recipe, language and sound declarations."""
from pathlib import Path
import json

root = Path(__file__).resolve().parent.parent / 'src/main/resources'
assets = root/'assets/two_i_combat_doll'
def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

zh = {
 'item.two_i_combat_doll.cangsheng_axolotl':'苍生美西螈',
 'entity.two_i_combat_doll.cangsheng_axolotl':'苍生美西螈',
 'item.two_i_combat_doll.cangsheng_axolotl_flute':'苍生之笛',
 'item.two_i_combat_doll.cangsheng_axolotl.tooltip':'掌心里的活宠物。丢给自己的萧萧可成为坐骑；右键放出泰坦，以苍生之笛召回。',
 'item.two_i_combat_doll.cangsheng_axolotl_flute.tooltip':'吹响笛子，召回自己的苍生美西螈；背包满时会安全等待。',
 'message.two_i_combat_doll.axolotl_unavailable':'这只美西螈已在外面，或属于另一位主人。',
 'message.two_i_combat_doll.axolotl_pending':'美西螈正等着回到你的掌心，请先腾出一个背包空位。',
 'message.two_i_combat_doll.axolotl_not_found':'附近没有可以召回的苍生美西螈。',
 'subtitles.two_i_combat_doll.cangsheng_awaken':'云海升起，晶山苏醒',
 'subtitles.two_i_combat_doll.cangsheng_roar':'苍生美西螈：守护者怒吼',
 'subtitles.two_i_combat_doll.cangsheng_impact':'晶山泰坦压顶',
 'subtitles.two_i_combat_doll.cangsheng_flute':'苍生之笛轻鸣',
}
en = {
 'item.two_i_combat_doll.cangsheng_axolotl':'Cangsheng Axolotl',
 'entity.two_i_combat_doll.cangsheng_axolotl':'Cangsheng Axolotl',
 'item.two_i_combat_doll.cangsheng_axolotl_flute':'Cangsheng Flute',
 'item.two_i_combat_doll.cangsheng_axolotl.tooltip':'A living palm pet. Give it to your Xiaoxiao as a mount, or release a titan with right click. Recall it with the flute.',
 'item.two_i_combat_doll.cangsheng_axolotl_flute.tooltip':'Call your Cangsheng Axolotl home. A full inventory keeps it safely waiting.',
 'message.two_i_combat_doll.axolotl_unavailable':'This axolotl is already outside, or belongs to another owner.',
 'message.two_i_combat_doll.axolotl_pending':'Your axolotl is waiting to return. Make one inventory slot available.',
 'message.two_i_combat_doll.axolotl_not_found':'No Cangsheng Axolotl nearby can be recalled.',
 'subtitles.two_i_combat_doll.cangsheng_awaken':'Crystal peaks awaken above the clouds',
 'subtitles.two_i_combat_doll.cangsheng_roar':'Cangsheng: guardian roar',
 'subtitles.two_i_combat_doll.cangsheng_impact':'Crystal titan descends',
 'subtitles.two_i_combat_doll.cangsheng_flute':'Cangsheng flute calls',
}
for language, additions in [('zh_cn',zh),('zh_tw',zh),('en_us',en)]:
    path=assets/f'lang/{language}.json'
    value=json.loads(path.read_text(encoding='utf-8-sig'));value.update(additions);save(path,value)
sounds=json.loads((assets/'sounds.json').read_text(encoding='utf-8-sig'))
for name in ['awaken','roar','impact','flute']:
    full='cangsheng_'+name
    sounds[full]={'subtitle':'subtitles.two_i_combat_doll.'+full,'sounds':[{'name':'two_i_combat_doll:'+full,'attenuation_distance':64}]}
save(assets/'sounds.json',sounds)
save(root/'data/two_i_combat_doll/recipe/cangsheng_axolotl.json', {
 'type':'minecraft:crafting_shaped','category':'equipment','pattern':['ACA','NWN','ACA'],
 'key':{'A':{'item':'minecraft:amethyst_shard'},'C':{'item':'minecraft:cherry_sapling'},'N':{'item':'minecraft:nether_star'},'W':{'item':'minecraft:axolotl_bucket'}},
 'result':{'id':'two_i_combat_doll:cangsheng_axolotl','count':1}})
save(root/'data/two_i_combat_doll/recipe/cangsheng_axolotl_flute.json', {
 'type':'minecraft:crafting_shaped','category':'equipment','pattern':['  A',' B ','B  '],
 'key':{'A':{'item':'minecraft:amethyst_shard'},'B':{'item':'minecraft:bamboo'}},
 'result':{'id':'two_i_combat_doll:cangsheng_axolotl_flute','count':1}})
print('Cangsheng languages, recipes and sound declarations written.')
