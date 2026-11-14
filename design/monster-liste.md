# Monster-Liste (Stand rc28, 2026-11-14)

Alle Dateien liegen in `app/src/main/res/drawable-nodpi/` (WebP, transparent). Zuordnung im Code: `ui/adventure/MonsterSprites.kt` (handgemachte Monster) und `ui/adventure/GenMonsterSprites.kt` + `BiomeStyles.kt` (generierte Monster). Quelle der Bilder: `tools/render_items` (Raytracing).

## A) Handgemachte Monster (20 Bilder, Zonen 1-5)

Ein Bild pro Monster, Ansicht von vorn, Füße am unteren Rand, Höhe 720 px (Breite je nach Monster). Das Bild wird in eine Box von 200 Einheiten eingepasst, die Füße stehen auf dem Boden der Szene.

| Zone | Rolle | Name im Spiel | Datei | Aussehen |
|---|---|---|---|---|
| 1 Wiese | normal | Schleim | `monster_slime` | Grüner, glänzender Schleimklumpen mit zwei orangen Augen und breitem Maul voller spitzer Zähne |
| 1 Wiese | normal | Riesenratte | `monster_giant_rat` | Große braungraue Ratte, struppiges Fell, rosa Rundohren, rote Augen, lange Schnurrhaare, Krallen |
| 1 Wiese | normal | Wildschwein | `monster_boar` | Wilder Eber mit Borstenfell und Borstenkamm, Hauern, leuchtende Augen |
| 1 Wiese | Boss | Schleimkönig | `monster_slime_king` | Blauer (türkiser) Schleimklumpen wie der Schleim, trägt eine goldene Krone mit Edelsteinen |
| 2 Wald | normal | Grauwolf | `monster_wolf` | Dunkelgrauer, zotteliger Wolf, gelbe Augen, Zunge hängt heraus |
| 2 Wald | normal | Waldspinne | `monster_forest_spider` | Große dunkelbraune Spinne, acht lange Beine, rote Augen, Giftzähne |
| 2 Wald | normal | Pilzling | `monster_mushroom` | Pilz mit rotem Hut und weißen Punkten, kleiner cremefarbener Körper mit Armen und Füßen, gelbe Augen |
| 2 Wald | Boss | Uralter Baumhüter | `monster_treant` | Baumriese aus braunem Holz mit grüner Blätterkrone, gelbe Augen, Moosgürtel, lange Astarme |
| 3 Sumpf | normal | Giftschleim | `monster_toxic_slime` | Gelbgrüner Schleimklumpen mit Blasen oben, violette Augen, Zahnmaul |
| 3 Sumpf | normal | Moorkröte | `monster_mire_toad` | Große olivgrüne Kröte mit Warzen, gelbe Schlitzaugen |
| 3 Sumpf | normal | Irrlicht | `monster_wisp` | Weiße/türkise Flamme in Tropfenform mit Eiskristall-Spitzen, dunkle Augen und Mund, schwebender Ring |
| 3 Sumpf | Boss | Sumpfhydra | `monster_hydra` | Dunkelgrüner Körper mit drei Schlangenköpfen auf langen Hälsen, gelbe Augen |
| 4 Gebirge | normal | Steingolem | `monster_stone_golem` | Grauer, blockiger Felsriese, Moos auf dem Kopf, ein gelbes Auge |
| 4 Gebirge | normal | Schneewolf | `monster_snow_wolf` | Weißer Wolf (Umfärbung des Grauwolfs), blaue Augen |
| 4 Gebirge | normal | Eisgeist | `monster_ice_wisp` | Wie das Irrlicht, aber eisblau, Ring und Spitzen in Eisfarben |
| 4 Gebirge | Boss | Frostyeti | `monster_yeti` | Weiß-grauer zotteliger Affenriese, blaue Augen, Reißzähne |
| 5 Höhle | normal | Riesenfledermaus | `monster_bat` | Kleine dunkle Fledermaus mit rotviolett Flügeln, rote Augen, spitze Ohren (Bild ist breit: 1456x720) |
| 5 Höhle | normal | Höhlenspinne | `monster_cave_spider` | Grau-violette Spinne (Umfärbung der Waldspinne), türkise Augen |
| 5 Höhle | normal | Kristallgolem | `monster_crystal_golem` | Dunkelgrauer Golem mit violetten Kristallstacheln, ein violettes Auge |
| 5 Höhle | Boss | Höhlendrache | `monster_dragon` | Roter Drache mit Hörnern, offenem Maul, großen Flügeln, Stacheln |

Hinweis: Schneewolf, Eisgeist und Höhlenspinne sind Umfärbungen von Grauwolf, Irrlicht und Waldspinne.

## B) Generierte Monster (Zonen 6-100)

Hier gibt es keine eigenen Bilder pro Monster. Es gibt **8 Körperformen**, jede in **2 Varianten** (0 = ruhig, 1 = furchteinflößend, mit Stacheln/Hörnern/glühenden Rissen). Die Varianten sind neutral grau gerendert und werden im Spiel pro Biom eingefärbt (Haut, Augen, Glühen). Jede Form besteht aus 4 Ebenen:

`gen_<form>_<0|1>_<skin|eye|glow|fixed>.webp`, z. B. `gen_hound_1_skin`, `gen_hound_1_eye`, `gen_hound_1_glow`, `gen_hound_1_fixed` (64 Dateien).

Ebenen: `skin` = graue Haut (wird mit dem Biom-Farbverlauf eingefärbt), `eye` = Augen, `glow` = Leuchten/Risse, `fixed` = Teile mit fester Farbe (Zähne, Klauen, Hörner).

Variante 1 gilt für alle Bosse und für Monster ab Furcht 0,5 (spätere Zonen), sonst Variante 0.

| Form | Aussehen |
|---|---|
| `blob` | Gelartige Masse |
| `hound` | Vierbeiniges Raubtier/Hund |
| `humanoid` | Gebückter Unhold mit langen Armen, Klauen, schwerem Kiefer, zerrissenem Lendentuch |
| `insect` | Gepanzerter Käfer/Mantis mit Zangen und sechs Beinen |
| `winged` | Wyvern/Drache mit S-Hals, Keilkopf, offenem Maul, Flügeln |
| `serpent` | Aufgerichtete Kobra mit Haube, Körper am Boden gewunden |
| `wraith` | Kapuzengeist mit zerfetztem Umhang, Skeletthänden, leuchtenden Augen |
| `golem` | Blockiger Steinriese |

### Zuordnung Biom -> Körperform

Pro Biom 3 normale Rollen (1, 2, 3) und 1 Boss; der Boss hat 5 Namen (einer je Stufe 1-5 des Bioms, gleiche Körperform).

| Biom | Rolle 1 | Rolle 2 | Rolle 3 | Boss-Form | Bossnamen (Stufe 1-5) |
|---|---|---|---|---|---|
| Dornenheide | Dornenkriecher (insect) | Heidewolf (hound) | Brombeerunhold (golem) | humanoid | Dornenmutter, Heidehexe, Blutdorn-Matriarchin, Moorfürstin Veyra, Dornenkönigin Salthara |
| Dünenmeer | Sandskorpion (insect) | Wüstenschakal (hound) | Mumienwächter (humanoid) | serpent | Sandwurm Gorrak, Skarabäenfürst, Sphinx der Rätsel, Pharao Amun-Zul, Sonnenverschlinger Apophis |
| Aschelande | Aschekriecher (blob) | Lavahund (hound) | Schlackengolem (golem) | winged | Glutgeist Ignar, Magmabestie, Feuerriese Surtak, Aschedrache Vulkarion, Weltenbrenner Ragnaros-Zeth |
| Verfluchte Gruft | Skelettkrieger (humanoid) | Leichenfresser (hound) | Grabwächter (golem) | wraith | Totengräber Mordo, Leichenfürst, Knochenritter Aldrek, Lich Malgoroth, Todesfürst Nekrazar |
| Frostödland | Eiswolf (hound) | Frostelementar (wraith) | Gletscherriese (golem) | winged | Eisbär Ursak, Schneekönigin, Frostwyrm Glacius, Winterfürst Borealis, Ewiger Winter Skadi-Nul |
| Giftdschungel | Lianenwürger (serpent) | Giftfrosch (blob) | Dschungelraubtier (hound) | insect | Würgeranke, Riesenmantis, Schlangengott Quetzal, Urwaldtitan, Das Grüne Grauen |
| Versunkene Ruinen | Ruinenkobold (humanoid) | Steinschreiter (golem) | Gargyle (winged) | golem | Wächterstatue, Ruinenkönig, Gargylenfürst Vorn, Titan der Trümmer, Stadtverschlinger Kol-Thar |
| Leuchtpilz-Tiefen | Sporenschleicher (blob) | Pilzhund (hound) | Myzelkoloss (insect) | golem | Sporenmutter, Kapuzenwürger, Myzel-Gott Fungorr, Herr der Fäulnis, Die Große Verwesung |
| Sturmküste | Strandkrabbe (insect) | Sirenenbrut (humanoid) | Tiefseehai (hound) | serpent | Krakenkind, Sturmharpyie, Leviathan Thalassor, Sirenenkönigin, Meeresverschlinger Dagon-Ur |
| Knochenwüste | Knochenhyäne (hound) | Geierdämon (winged) | Rippenläufer (insect) | winged | Knochenhüter, Aasfürst, Skelettdrache Ossarion, Schädelthron, Der Hungernde Gott |
| Blutgarten | Rosenwürger (serpent) | Fleischblume (blob) | Dornenfratze (humanoid) | humanoid | Gärtner des Grauens, Blutrose, Karminfürstin Sanguis, Ernte-Koloss, Der Rote Gärtner |
| Sturmgipfel | Windgeist (wraith) | Donnervogel (winged) | Wolkenwächter (golem) | winged | Blitzfalke, Sturmriese Tormund, Donnerdrache Raijin, Himmelsrichter, Gewitterthron Zephyros |
| Höllenschlund | Imp (winged) | Höllenhund (hound) | Dämonenbrut (humanoid) | humanoid | Pfortenwächter, Peiniger Vrax, Erzdämon Baalmoth, Fürst der Qualen, Höllenkönig Azmodrak |
| Schattenreich | Schattenschleicher (wraith) | Nachtalb (hound) | Schemenkrieger (humanoid) | wraith | Dunkelelf Zhar, Schattenweberin, Nachtkönig Umbral, Schleier-Herrscher, Die Ewige Nacht |
| Uhrwerk-Festung | Zahnradwächter (insect) | Dampfhund (hound) | Eisenkoloss (golem) | golem | Räderwerk-Meister, Dampfgolem Kessler, Belagerungsmaschine, Der Konstrukteur, Zeitmaschine Chronos-9 |
| Albtraumwelt | Traumfresser (blob) | Schlafwandler (humanoid) | Fratzengesicht (insect) | serpent | Der Schlafende, Spiegelmonster, Albtraumfürst Morpheth, Verzerrte Königin, Der Träumer ohne Gesicht |
| Sternenleere | Sternensplitter (wraith) | Kometenhund (hound) | Astralwächter (golem) | winged | Nebelgeist, Sternenjäger, Kosmischer Richter, Galaxienfresser Orion-X, Sternenschlund Yog-Ahn |
| Chaosriss | Splitterbrut (blob) | Risswandler (insect) | Chaoswirbel (wraith) | serpent | Riss-Hüter, Wirklichkeitsfresser, Chaosdrache Kaotharion, Entropie-Fürst, Das Nichts selbst |
| Thron der Götterdämmerung | Götterdiener (humanoid) | Weltenbrecher (golem) | Himmelsscherge (winged) | humanoid | Torwächter des Endes, Dämmerungsrichter, Gefallener Gott, Der Letzte Titan, Ur-Gott Aeon, Ende aller Dinge |

Die 5 Stufen eines Bioms teilen sich Bilder und Namen der drei normalen Rollen; nur Farbvariation und Bossname ändern sich.

## Austausch durch neue Bilder

- Handgemachte Monster (A): gleiche Dateinamen, transparentes WebP, Ansicht von vorn, Füße am unteren Bildrand, ca. 720 px hoch. Dann nur die Dateien ersetzen, kein Codewechsel nötig.
- Generierte Monster (B): entweder alle 4 Ebenen pro Form/Variante ersetzen (Haut grau, damit die Biom-Färbung greift) oder, wenn du fertig eingefärbte Bilder pro Monster hast, sag Bescheid: dann baue ich eine Zuordnung pro Zone/Monster (Name -> Bild) und lasse die Färbung für diese Monster weg. Das sind bis zu 19 x 4 = 76 verschiedene Monster plus 95 Bosse.
- Hintergründe sind getrennt (`scene_*.webp`, 5 handgemachte + 19 Biome).


## Dateinamen für „Bild pro Monster“ (Zonen 6-100, ab rc29)

Wenn eine Datei mit dem Namen unten in `app/src/main/res/drawable-nodpi/` liegt, wird sie für dieses Monster verwendet (fertig eingefärbt, ohne Biom-Färbung und ohne Aura). Fehlt die Datei, bleibt die bisherige Darstellung. Bilder können einzeln nachgeliefert werden. Format: WebP oder PNG, transparent, Ansicht von vorn, Füße am unteren Rand, am besten ca. 720 px hoch. Dateinamen nur Kleinbuchstaben, Ziffern und Unterstrich.

Die drei normalen Monster eines Bioms teilen sich ihre Bilder über alle fünf Stufen, jede Stufe hat ihren eigenen Boss. Insgesamt 19 x 8 = 152 Bilder. Die 20 Monster der Zonen 1-5 behalten ihre Dateinamen `monster_*` (Liste oben).

| Dateiname (ohne Endung) | Monster | Biom |
|---|---|---|
| `mob_heide_1` | Dornenkriecher | Dornenheide |
| `mob_heide_2` | Heidewolf | Dornenheide |
| `mob_heide_3` | Brombeerunhold | Dornenheide |
| `mob_heide_boss_1` | Dornenmutter | Dornenheide (Boss Stufe 1) |
| `mob_heide_boss_2` | Heidehexe | Dornenheide (Boss Stufe 2) |
| `mob_heide_boss_3` | Blutdorn-Matriarchin | Dornenheide (Boss Stufe 3) |
| `mob_heide_boss_4` | Moorfürstin Veyra | Dornenheide (Boss Stufe 4) |
| `mob_heide_boss_5` | Dornenkönigin Salthara | Dornenheide (Boss Stufe 5) |
| `mob_wueste_1` | Sandskorpion | Dünenmeer |
| `mob_wueste_2` | Wüstenschakal | Dünenmeer |
| `mob_wueste_3` | Mumienwächter | Dünenmeer |
| `mob_wueste_boss_1` | Sandwurm Gorrak | Dünenmeer (Boss Stufe 1) |
| `mob_wueste_boss_2` | Skarabäenfürst | Dünenmeer (Boss Stufe 2) |
| `mob_wueste_boss_3` | Sphinx der Rätsel | Dünenmeer (Boss Stufe 3) |
| `mob_wueste_boss_4` | Pharao Amun-Zul | Dünenmeer (Boss Stufe 4) |
| `mob_wueste_boss_5` | Sonnenverschlinger Apophis | Dünenmeer (Boss Stufe 5) |
| `mob_vulkan_1` | Aschekriecher | Aschelande |
| `mob_vulkan_2` | Lavahund | Aschelande |
| `mob_vulkan_3` | Schlackengolem | Aschelande |
| `mob_vulkan_boss_1` | Glutgeist Ignar | Aschelande (Boss Stufe 1) |
| `mob_vulkan_boss_2` | Magmabestie | Aschelande (Boss Stufe 2) |
| `mob_vulkan_boss_3` | Feuerriese Surtak | Aschelande (Boss Stufe 3) |
| `mob_vulkan_boss_4` | Aschedrache Vulkarion | Aschelande (Boss Stufe 4) |
| `mob_vulkan_boss_5` | Weltenbrenner Ragnaros-Zeth | Aschelande (Boss Stufe 5) |
| `mob_gruft_1` | Skelettkrieger | Verfluchte Gruft |
| `mob_gruft_2` | Leichenfresser | Verfluchte Gruft |
| `mob_gruft_3` | Grabwächter | Verfluchte Gruft |
| `mob_gruft_boss_1` | Totengräber Mordo | Verfluchte Gruft (Boss Stufe 1) |
| `mob_gruft_boss_2` | Leichenfürst | Verfluchte Gruft (Boss Stufe 2) |
| `mob_gruft_boss_3` | Knochenritter Aldrek | Verfluchte Gruft (Boss Stufe 3) |
| `mob_gruft_boss_4` | Lich Malgoroth | Verfluchte Gruft (Boss Stufe 4) |
| `mob_gruft_boss_5` | Todesfürst Nekrazar | Verfluchte Gruft (Boss Stufe 5) |
| `mob_frost_1` | Eiswolf | Frostödland |
| `mob_frost_2` | Frostelementar | Frostödland |
| `mob_frost_3` | Gletscherriese | Frostödland |
| `mob_frost_boss_1` | Eisbär Ursak | Frostödland (Boss Stufe 1) |
| `mob_frost_boss_2` | Schneekönigin | Frostödland (Boss Stufe 2) |
| `mob_frost_boss_3` | Frostwyrm Glacius | Frostödland (Boss Stufe 3) |
| `mob_frost_boss_4` | Winterfürst Borealis | Frostödland (Boss Stufe 4) |
| `mob_frost_boss_5` | Ewiger Winter Skadi-Nul | Frostödland (Boss Stufe 5) |
| `mob_dschungel_1` | Lianenwürger | Giftdschungel |
| `mob_dschungel_2` | Giftfrosch | Giftdschungel |
| `mob_dschungel_3` | Dschungelraubtier | Giftdschungel |
| `mob_dschungel_boss_1` | Würgeranke | Giftdschungel (Boss Stufe 1) |
| `mob_dschungel_boss_2` | Riesenmantis | Giftdschungel (Boss Stufe 2) |
| `mob_dschungel_boss_3` | Schlangengott Quetzal | Giftdschungel (Boss Stufe 3) |
| `mob_dschungel_boss_4` | Urwaldtitan | Giftdschungel (Boss Stufe 4) |
| `mob_dschungel_boss_5` | Das Grüne Grauen | Giftdschungel (Boss Stufe 5) |
| `mob_ruinen_1` | Ruinenkobold | Versunkene Ruinen |
| `mob_ruinen_2` | Steinschreiter | Versunkene Ruinen |
| `mob_ruinen_3` | Gargyle | Versunkene Ruinen |
| `mob_ruinen_boss_1` | Wächterstatue | Versunkene Ruinen (Boss Stufe 1) |
| `mob_ruinen_boss_2` | Ruinenkönig | Versunkene Ruinen (Boss Stufe 2) |
| `mob_ruinen_boss_3` | Gargylenfürst Vorn | Versunkene Ruinen (Boss Stufe 3) |
| `mob_ruinen_boss_4` | Titan der Trümmer | Versunkene Ruinen (Boss Stufe 4) |
| `mob_ruinen_boss_5` | Stadtverschlinger Kol-Thar | Versunkene Ruinen (Boss Stufe 5) |
| `mob_glutpilz_1` | Sporenschleicher | Leuchtpilz-Tiefen |
| `mob_glutpilz_2` | Pilzhund | Leuchtpilz-Tiefen |
| `mob_glutpilz_3` | Myzelkoloss | Leuchtpilz-Tiefen |
| `mob_glutpilz_boss_1` | Sporenmutter | Leuchtpilz-Tiefen (Boss Stufe 1) |
| `mob_glutpilz_boss_2` | Kapuzenwürger | Leuchtpilz-Tiefen (Boss Stufe 2) |
| `mob_glutpilz_boss_3` | Myzel-Gott Fungorr | Leuchtpilz-Tiefen (Boss Stufe 3) |
| `mob_glutpilz_boss_4` | Herr der Fäulnis | Leuchtpilz-Tiefen (Boss Stufe 4) |
| `mob_glutpilz_boss_5` | Die Große Verwesung | Leuchtpilz-Tiefen (Boss Stufe 5) |
| `mob_kueste_1` | Strandkrabbe | Sturmküste |
| `mob_kueste_2` | Sirenenbrut | Sturmküste |
| `mob_kueste_3` | Tiefseehai | Sturmküste |
| `mob_kueste_boss_1` | Krakenkind | Sturmküste (Boss Stufe 1) |
| `mob_kueste_boss_2` | Sturmharpyie | Sturmküste (Boss Stufe 2) |
| `mob_kueste_boss_3` | Leviathan Thalassor | Sturmküste (Boss Stufe 3) |
| `mob_kueste_boss_4` | Sirenenkönigin | Sturmküste (Boss Stufe 4) |
| `mob_kueste_boss_5` | Meeresverschlinger Dagon-Ur | Sturmküste (Boss Stufe 5) |
| `mob_knochen_1` | Knochenhyäne | Knochenwüste |
| `mob_knochen_2` | Geierdämon | Knochenwüste |
| `mob_knochen_3` | Rippenläufer | Knochenwüste |
| `mob_knochen_boss_1` | Knochenhüter | Knochenwüste (Boss Stufe 1) |
| `mob_knochen_boss_2` | Aasfürst | Knochenwüste (Boss Stufe 2) |
| `mob_knochen_boss_3` | Skelettdrache Ossarion | Knochenwüste (Boss Stufe 3) |
| `mob_knochen_boss_4` | Schädelthron | Knochenwüste (Boss Stufe 4) |
| `mob_knochen_boss_5` | Der Hungernde Gott | Knochenwüste (Boss Stufe 5) |
| `mob_blutgarten_1` | Rosenwürger | Blutgarten |
| `mob_blutgarten_2` | Fleischblume | Blutgarten |
| `mob_blutgarten_3` | Dornenfratze | Blutgarten |
| `mob_blutgarten_boss_1` | Gärtner des Grauens | Blutgarten (Boss Stufe 1) |
| `mob_blutgarten_boss_2` | Blutrose | Blutgarten (Boss Stufe 2) |
| `mob_blutgarten_boss_3` | Karminfürstin Sanguis | Blutgarten (Boss Stufe 3) |
| `mob_blutgarten_boss_4` | Ernte-Koloss | Blutgarten (Boss Stufe 4) |
| `mob_blutgarten_boss_5` | Der Rote Gärtner | Blutgarten (Boss Stufe 5) |
| `mob_himmel_1` | Windgeist | Sturmgipfel |
| `mob_himmel_2` | Donnervogel | Sturmgipfel |
| `mob_himmel_3` | Wolkenwächter | Sturmgipfel |
| `mob_himmel_boss_1` | Blitzfalke | Sturmgipfel (Boss Stufe 1) |
| `mob_himmel_boss_2` | Sturmriese Tormund | Sturmgipfel (Boss Stufe 2) |
| `mob_himmel_boss_3` | Donnerdrache Raijin | Sturmgipfel (Boss Stufe 3) |
| `mob_himmel_boss_4` | Himmelsrichter | Sturmgipfel (Boss Stufe 4) |
| `mob_himmel_boss_5` | Gewitterthron Zephyros | Sturmgipfel (Boss Stufe 5) |
| `mob_unterwelt_1` | Imp | Höllenschlund |
| `mob_unterwelt_2` | Höllenhund | Höllenschlund |
| `mob_unterwelt_3` | Dämonenbrut | Höllenschlund |
| `mob_unterwelt_boss_1` | Pfortenwächter | Höllenschlund (Boss Stufe 1) |
| `mob_unterwelt_boss_2` | Peiniger Vrax | Höllenschlund (Boss Stufe 2) |
| `mob_unterwelt_boss_3` | Erzdämon Baalmoth | Höllenschlund (Boss Stufe 3) |
| `mob_unterwelt_boss_4` | Fürst der Qualen | Höllenschlund (Boss Stufe 4) |
| `mob_unterwelt_boss_5` | Höllenkönig Azmodrak | Höllenschlund (Boss Stufe 5) |
| `mob_schatten_1` | Schattenschleicher | Schattenreich |
| `mob_schatten_2` | Nachtalb | Schattenreich |
| `mob_schatten_3` | Schemenkrieger | Schattenreich |
| `mob_schatten_boss_1` | Dunkelelf Zhar | Schattenreich (Boss Stufe 1) |
| `mob_schatten_boss_2` | Schattenweberin | Schattenreich (Boss Stufe 2) |
| `mob_schatten_boss_3` | Nachtkönig Umbral | Schattenreich (Boss Stufe 3) |
| `mob_schatten_boss_4` | Schleier-Herrscher | Schattenreich (Boss Stufe 4) |
| `mob_schatten_boss_5` | Die Ewige Nacht | Schattenreich (Boss Stufe 5) |
| `mob_uhrwerk_1` | Zahnradwächter | Uhrwerk-Festung |
| `mob_uhrwerk_2` | Dampfhund | Uhrwerk-Festung |
| `mob_uhrwerk_3` | Eisenkoloss | Uhrwerk-Festung |
| `mob_uhrwerk_boss_1` | Räderwerk-Meister | Uhrwerk-Festung (Boss Stufe 1) |
| `mob_uhrwerk_boss_2` | Dampfgolem Kessler | Uhrwerk-Festung (Boss Stufe 2) |
| `mob_uhrwerk_boss_3` | Belagerungsmaschine | Uhrwerk-Festung (Boss Stufe 3) |
| `mob_uhrwerk_boss_4` | Der Konstrukteur | Uhrwerk-Festung (Boss Stufe 4) |
| `mob_uhrwerk_boss_5` | Zeitmaschine Chronos-9 | Uhrwerk-Festung (Boss Stufe 5) |
| `mob_traum_1` | Traumfresser | Albtraumwelt |
| `mob_traum_2` | Schlafwandler | Albtraumwelt |
| `mob_traum_3` | Fratzengesicht | Albtraumwelt |
| `mob_traum_boss_1` | Der Schlafende | Albtraumwelt (Boss Stufe 1) |
| `mob_traum_boss_2` | Spiegelmonster | Albtraumwelt (Boss Stufe 2) |
| `mob_traum_boss_3` | Albtraumfürst Morpheth | Albtraumwelt (Boss Stufe 3) |
| `mob_traum_boss_4` | Verzerrte Königin | Albtraumwelt (Boss Stufe 4) |
| `mob_traum_boss_5` | Der Träumer ohne Gesicht | Albtraumwelt (Boss Stufe 5) |
| `mob_astral_1` | Sternensplitter | Sternenleere |
| `mob_astral_2` | Kometenhund | Sternenleere |
| `mob_astral_3` | Astralwächter | Sternenleere |
| `mob_astral_boss_1` | Nebelgeist | Sternenleere (Boss Stufe 1) |
| `mob_astral_boss_2` | Sternenjäger | Sternenleere (Boss Stufe 2) |
| `mob_astral_boss_3` | Kosmischer Richter | Sternenleere (Boss Stufe 3) |
| `mob_astral_boss_4` | Galaxienfresser Orion-X | Sternenleere (Boss Stufe 4) |
| `mob_astral_boss_5` | Sternenschlund Yog-Ahn | Sternenleere (Boss Stufe 5) |
| `mob_leere_1` | Splitterbrut | Chaosriss |
| `mob_leere_2` | Risswandler | Chaosriss |
| `mob_leere_3` | Chaoswirbel | Chaosriss |
| `mob_leere_boss_1` | Riss-Hüter | Chaosriss (Boss Stufe 1) |
| `mob_leere_boss_2` | Wirklichkeitsfresser | Chaosriss (Boss Stufe 2) |
| `mob_leere_boss_3` | Chaosdrache Kaotharion | Chaosriss (Boss Stufe 3) |
| `mob_leere_boss_4` | Entropie-Fürst | Chaosriss (Boss Stufe 4) |
| `mob_leere_boss_5` | Das Nichts selbst | Chaosriss (Boss Stufe 5) |
| `mob_ende_1` | Götterdiener | Thron der Götterdämmerung |
| `mob_ende_2` | Weltenbrecher | Thron der Götterdämmerung |
| `mob_ende_3` | Himmelsscherge | Thron der Götterdämmerung |
| `mob_ende_boss_1` | Torwächter des Endes | Thron der Götterdämmerung (Boss Stufe 1) |
| `mob_ende_boss_2` | Dämmerungsrichter | Thron der Götterdämmerung (Boss Stufe 2) |
| `mob_ende_boss_3` | Gefallener Gott | Thron der Götterdämmerung (Boss Stufe 3) |
| `mob_ende_boss_4` | Der Letzte Titan | Thron der Götterdämmerung (Boss Stufe 4) |
| `mob_ende_boss_5` | Ur-Gott Aeon, Ende aller Dinge | Thron der Götterdämmerung (Boss Stufe 5) |
