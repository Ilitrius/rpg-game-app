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
