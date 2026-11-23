# 3x3 + Tree Axe Enchantments (Fabric, Minecraft 26.3)

- **3x3** (pickaxes and axes): breaking a block also breaks the 8 around it on the face you're mining.
- **Tree Axe** (axes): chopping a log fells every connected log (up to 256). Leaves decay on their own.
- Sneak to break a single block with either.

## Build
Requires JDK 25. Generate the Gradle wrapper once (Gradle 9.8+), then build:

    gradle wrapper --gradle-version 9.8.2
    ./gradlew build

Jar ends up in `build/libs/`. Put it in `mods/` with Fabric API 0.161.0+26.3.

## Test
    /enchant @s threebythree:excavator
    /enchant @s threebythree:treeaxe

## Tweaking
- Tools: `data/threebythree/tags/item/enchantable/*.json`
- Cost/rarity: `data/threebythree/enchantment/*.json`
- Names: `assets/threebythree/lang/en_us.json`
- Logic / max tree size: `ThreeByThree.java` (`MAX_TREE_BLOCKS`)
