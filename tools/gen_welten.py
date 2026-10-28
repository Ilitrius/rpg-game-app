# -*- coding: utf-8 -*-
import os

R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','app','src','main')+'/'
STAGES_DE=['Randgebiet','Mittelland','Tiefland','Kerngebiet','Herz']
STAGES_EN=['Outskirts','Midlands','Lowlands','Core','Heart']
# id: (de, en, [3 monsters de], [3 en], [5 bosses de], [5 en])
B=[
('heide','Dornenheide','Thornheath',['Dornenkriecher','Heidewolf','Brombeerunhold'],['Thorn Crawler','Heath Wolf','Bramble Fiend'],
 ['Dornenmutter','Heidehexe','Blutdorn-Matriarchin','Moorfürstin Veyra','Dornenkönigin Salthara'],['Thorn Mother','Heath Hag','Bloodthorn Matriarch','Moor Lady Veyra','Thorn Queen Salthara']),
('wueste','Dünenmeer','Dune Sea',['Sandskorpion','Wüstenschakal','Mumienwächter'],['Sand Scorpion','Desert Jackal','Mummy Guardian'],
 ['Sandwurm Gorrak','Skarabäenfürst','Sphinx der Rätsel','Pharao Amun-Zul','Sonnenverschlinger Apophis'],['Sandworm Gorrak','Scarab Lord','Sphinx of Riddles','Pharaoh Amun-Zul','Sundevourer Apophis']),
('vulkan','Aschelande','Ashlands',['Aschekriecher','Lavahund','Schlackengolem'],['Ash Crawler','Lava Hound','Slag Golem'],
 ['Glutgeist Ignar','Magmabestie','Feuerriese Surtak','Aschedrache Vulkarion','Weltenbrenner Ragnaros-Zeth'],['Ember Spirit Ignar','Magma Beast','Fire Giant Surtak','Ash Dragon Vulkarion','Worldburner Zeth']),
('gruft','Verfluchte Gruft','Cursed Crypt',['Skelettkrieger','Leichenfresser','Grabwächter'],['Skeleton Warrior','Ghoul','Tomb Guard'],
 ['Totengräber Mordo','Leichenfürst','Knochenritter Aldrek','Lich Malgoroth','Todesfürst Nekrazar'],['Gravedigger Mordo','Corpse Lord','Bone Knight Aldrek','Lich Malgoroth','Death Lord Nekrazar']),
('frost','Frostödland','Frozen Wastes',['Eiswolf','Frostelementar','Gletscherriese'],['Ice Wolf','Frost Elemental','Glacier Giant'],
 ['Eisbär Ursak','Schneekönigin','Frostwyrm Glacius','Winterfürst Borealis','Ewiger Winter Skadi-Nul'],['Ice Bear Ursak','Snow Queen','Frostwyrm Glacius','Winter Lord Borealis','Eternal Winter Skadi-Nul']),
('dschungel','Giftdschungel','Venom Jungle',['Lianenwürger','Giftfrosch','Dschungelraubtier'],['Vine Strangler','Poison Frog','Jungle Predator'],
 ['Würgeranke','Riesenmantis','Schlangengott Quetzal','Urwaldtitan','Das Grüne Grauen'],['Strangler Vine','Giant Mantis','Serpent God Quetzal','Primeval Titan','The Green Horror']),
('ruinen','Versunkene Ruinen','Sunken Ruins',['Ruinenkobold','Steinschreiter','Gargyle'],['Ruin Goblin','Stone Stalker','Gargoyle'],
 ['Wächterstatue','Ruinenkönig','Gargylenfürst Vorn','Titan der Trümmer','Stadtverschlinger Kol-Thar'],['Guardian Statue','Ruin King','Gargoyle Lord Vorn','Titan of Rubble','City Devourer Kol-Thar']),
('glutpilz','Leuchtpilz-Tiefen','Glowcap Depths',['Sporenschleicher','Pilzhund','Myzelkoloss'],['Spore Creeper','Fungus Hound','Mycelium Colossus'],
 ['Sporenmutter','Kapuzenwürger','Myzel-Gott Fungorr','Herr der Fäulnis','Die Große Verwesung'],['Spore Mother','Cap Strangler','Mycelium God Fungorr','Lord of Rot','The Great Decay']),
('kueste','Sturmküste','Stormcoast',['Strandkrabbe','Sirenenbrut','Tiefseehai'],['Beach Crab','Siren Spawn','Deep Sea Shark'],
 ['Krakenkind','Sturmharpyie','Leviathan Thalassor','Sirenenkönigin','Meeresverschlinger Dagon-Ur'],['Kraken Spawn','Storm Harpy','Leviathan Thalassor','Siren Queen','Sea Devourer Dagon-Ur']),
('knochen','Knochenwüste','Bone Desert',['Knochenhyäne','Geierdämon','Rippenläufer'],['Bone Hyena','Vulture Fiend','Rib Runner'],
 ['Knochenhüter','Aasfürst','Skelettdrache Ossarion','Schädelthron','Der Hungernde Gott'],['Bone Keeper','Carrion Lord','Skeleton Dragon Ossarion','Skull Throne','The Starving God']),
('blutgarten','Blutgarten','Blood Garden',['Rosenwürger','Fleischblume','Dornenfratze'],['Rose Strangler','Flesh Bloom','Thorn Grotesque'],
 ['Gärtner des Grauens','Blutrose','Karminfürstin Sanguis','Ernte-Koloss','Der Rote Gärtner'],['Gardener of Horror','Blood Rose','Crimson Lady Sanguis','Harvest Colossus','The Red Gardener']),
('himmel','Sturmgipfel','Stormpeaks',['Windgeist','Donnervogel','Wolkenwächter'],['Wind Spirit','Thunderbird','Cloud Warden'],
 ['Blitzfalke','Sturmriese Tormund','Donnerdrache Raijin','Himmelsrichter','Gewitterthron Zephyros'],['Lightning Hawk','Storm Giant Tormund','Thunder Dragon Raijin','Sky Judge','Tempest Throne Zephyros']),
('unterwelt','Höllenschlund','Hellmaw',['Imp','Höllenhund','Dämonenbrut'],['Imp','Hellhound','Demon Spawn'],
 ['Pfortenwächter','Peiniger Vrax','Erzdämon Baalmoth','Fürst der Qualen','Höllenkönig Azmodrak'],['Gatekeeper','Tormentor Vrax','Archdemon Baalmoth','Lord of Torment','Hell King Azmodrak']),
('schatten','Schattenreich','Shadow Realm',['Schattenschleicher','Nachtalb','Schemenkrieger'],['Shadow Stalker','Nightelf','Shade Warrior'],
 ['Dunkelelf Zhar','Schattenweberin','Nachtkönig Umbral','Schleier-Herrscher','Die Ewige Nacht'],['Dark Elf Zhar','Shadow Weaver','Night King Umbral','Veil Sovereign','The Eternal Night']),
('uhrwerk','Uhrwerk-Festung','Clockwork Citadel',['Zahnradwächter','Dampfhund','Eisenkoloss'],['Gear Sentinel','Steam Hound','Iron Colossus'],
 ['Räderwerk-Meister','Dampfgolem Kessler','Belagerungsmaschine','Der Konstrukteur','Zeitmaschine Chronos-9'],['Master of Gears','Steam Golem Kessler','Siege Engine','The Constructor','Time Engine Chronos-9']),
('traum','Albtraumwelt','Nightmare Realm',['Traumfresser','Schlafwandler','Fratzengesicht'],['Dream Eater','Sleepwalker','Grinning Horror'],
 ['Der Schlafende','Spiegelmonster','Albtraumfürst Morpheth','Verzerrte Königin','Der Träumer ohne Gesicht'],['The Sleeper','Mirror Monster','Nightmare Lord Morpheth','Warped Queen','The Faceless Dreamer']),
('astral','Sternenleere','Starvoid',['Sternensplitter','Kometenhund','Astralwächter'],['Star Shard','Comet Hound','Astral Guardian'],
 ['Nebelgeist','Sternenjäger','Kosmischer Richter','Galaxienfresser Orion-X','Sternenschlund Yog-Ahn'],['Nebula Spirit','Star Hunter','Cosmic Judge','Galaxy Eater Orion-X','Star Maw Yog-Ahn']),
('leere','Chaosriss','Chaos Rift',['Splitterbrut','Risswandler','Chaoswirbel'],['Shard Brood','Rift Walker','Chaos Vortex'],
 ['Riss-Hüter','Wirklichkeitsfresser','Chaosdrache Kaotharion','Entropie-Fürst','Das Nichts selbst'],['Rift Keeper','Reality Eater','Chaos Dragon Kaotharion','Lord of Entropy','The Nothing Itself']),
('ende','Thron der Götterdämmerung','Twilight Throne',['Götterdiener','Weltenbrecher','Himmelsscherge'],['God Servant','Worldbreaker','Heaven Herald'],
 ['Torwächter des Endes','Dämmerungsrichter','Gefallener Gott','Der Letzte Titan','Ur-Gott Aeon, Ende aller Dinge'],['Gatekeeper of the End','Judge of Dusk','Fallen God','The Last Titan','Primordial God Aeon, End of All Things']),
]
assert len(B)==19
def esc(s): return s.replace("'","\\'").replace('&','&amp;').replace('<','&lt;')
def xml(lang):
    L=['<?xml version="1.0" encoding="utf-8"?>','<!-- GENERATED by gen.py: names of zones 6..100 (biome scheme). Do not edit by hand. -->','<resources>']
    for i,b in enumerate(B):
        bid=b[0]; name=b[1] if lang=='de' else b[2]
        mon=b[3] if lang=='de' else b[4]; boss=b[5] if lang=='de' else b[6]
        st=STAGES_DE if lang=='de' else STAGES_EN
        L.append('    <string name="biome_%s">%s</string>'%(bid,esc(name)))
        for r in range(3): L.append('    <string name="monster_%s_%d">%s</string>'%(bid,r+1,esc(mon[r])))
        for s in range(5):
            L.append('    <string name="boss_%s_%d">%s</string>'%(bid,s+1,esc(boss[s])))
            L.append('    <string name="zone_%s_%d">%s</string>'%(bid,s+1,esc(name+' · '+st[s])))
    L.append('</resources>')
    return '\n'.join(L)+'\n'
open(R+'res/values/strings_spielwelt_welten.xml','w',encoding='utf-8').write(xml('de'))
os.makedirs(R+'res/values-en',exist_ok=True)
open(R+'res/values-en/strings_spielwelt_welten.xml','w',encoding='utf-8').write(xml('en'))
K=['package com.idlerpg.game.ui.adventure','','import com.idlerpg.game.R','',
'/** GENERATED by gen.py: string resources of the generated zones, indexed by biome ordinal (and stage / role). */',
'internal object WorldNames {']
def arr(name,f):
    K.append('    val %s: Array<IntArray> = arrayOf('%name)
    for b in B: K.append('        intArrayOf(%s),'%', '.join(f(b[0],i) for i in f.__code__.co_consts and range(f.n)))
    K.append('    )')
def mk(fmt,n):
    def f(bid,i): return 'R.string.'+fmt%(bid,i+1)
    f.n=n; return f
arr('zone',mk('zone_%s_%d',5)); arr('boss',mk('boss_%s_%d',5)); arr('monster',mk('monster_%s_%d',3))
K.append('    val biome: IntArray = intArrayOf(%s)'%', '.join('R.string.biome_'+b[0] for b in B))
K.append('}')
open(R+'java/com/idlerpg/game/ui/adventure/WorldNames.kt','w',encoding='utf-8').write('\n'.join(K)+'\n')
