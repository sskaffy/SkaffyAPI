# .sfy shaders for Skaffy's API

A shader file is a `.sfy` file that changes how a player's game is drawn. The player's client compiles it and hands it to Minecraft's own shader compiler, which builds it for **Vulkan** (SPIR-V) or **OpenGL** (GLSL 330), whichever the player uses. You write one file, and it works on both. No Iris, Sodium or OptiFine is involved.

Shader files reach a player two ways:

- **From a server:** a plugin sends them as text and turns them on per player ([The plugin API](#the-plugin-api)).
- **As a shader pack:** the player puts a zip or folder in `.minecraft/shaders/` and picks it in Video Settings → Shader Packs, like Iris ([Shader packs](#shader-packs)). Vista, the shader built into the mod, is one too.

Both use the same language. A server's shaders always draw on top of the player's own.

A file can do two things, alone or together:

- **Screen passes** (`@Pass`): draw over the finished picture, like a filter. Blur, bloom, color grading, fog from the depth buffer, outlines, god rays.
- **World hooks** (`@Vertex`, `@Fragment`): change how the world itself is drawn. Waving leaves and grass, rolling water, your own lighting from the sun, a painted sky.

The wire format is in PROTOCOL.md (`skaffy:shaders`). Complete examples are in `test-plugin/src/main/resources/shaders/skaffytest/` (`Pretty.sfy` uses nearly everything).

---

## A first shader

`shaders/effects/Gray.sfy` in your plugin's jar:

```java
package effects;

import skaffy.shader.*;

public class Gray {
    @Uniform float amount = 1.0;      // the server can set and animate this

    @Pass
    vec4 gray(vec2 uv) {
        vec3 color = texture(SCREEN, uv).rgb;
        return vec4(mix(color, vec3(luminance(color)), amount), 1.0);
    }
}
```

The plugin:

```java
SkaffyShaders shaders = SkaffyAPI.get().getShaders();
shaders.addFiles(this, "shaders");                    // shaders/effects/Gray.sfy is effects.Gray

shaders.enable(player, "effects.Gray");               // on
shaders.animate(player, "effects.Gray", "amount", 0.0, 2000, Easing.EASE_IN_OUT_SINE);  // fade it out over 2 seconds
shaders.disable(player, "effects.Gray");              // off
```

---

## The file

```java
package effects;                    // must match the class name: effects.Dream

import skaffy.shader.*;             // every shader file imports the shader library

public class Dream {                // one class, named like the file
    const float PI2 = 6.2831853;                          // constants (also: static final float ...)
    @Blocks("minecraft:water") const int WATER = 1;       // a block material (world hooks)

    @Uniform float strength = 0.6;                        // values the server sets
    @Varying float wave;                                  // passed from @Vertex to @Fragment hooks
    @Texture("noise.png") Texture noise;                  // a picture to read
    @Target(scale = 0.5) Texture half;                    // a picture passes draw into

    record Ray(vec3 origin, vec3 direction) {}            // records become structs

    @Pass vec4 apply(vec2 uv) { ... }                     // a screen pass
    @Vertex(TERRAIN) void sway(Vertex v) { ... }          // a world hook
    @Fragment(TERRAIN) vec4 shade(Fragment f) { ... }     // a world hook
    float helper(float x) { return x * x; }               // anything else is a helper
}
```

- **Imports**: `import skaffy.shader.*;` brings in everything Skaffy adds; single names work too (`import skaffy.shader.time;`, `import skaffy.shader.Pass;`), and then only those can be used. GLSL's own types and functions (`vec3`, `mix`, `sin`, `texture`, ...) never need an import. A file that imports `skaffy.gui` is a GUI file and can't be used as a shader.
- Fields are always one of: a constant, `@Uniform`, `@Varying`, `@Texture` or `@Target`.
- Methods with `@Pass`, `@Vertex` or `@Fragment` are run by the game; your code can't call them. Every other method is a helper anything can call (no recursion: GLSL forbids it).

---

## The language

It looks like Java, but the values are GLSL's: shaders run on the graphics card, millions of times per frame.

- **Types:** `float`, `int`, `bool`, vectors `vec2` `vec3` `vec4` (floats), `ivec2`-`ivec4` (ints), `bvec2`-`bvec4` (bools), square matrices `mat2` `mat3` `mat4`, `Texture`, your records, and arrays of those (`float[]`). There's no `double`, `long`, `String`, `List`, `null` or exceptions.
- **Numbers:** `1.0`, `.5`, `2e3` and `1.0f` are floats, `3` and `0xFF` ints. Ints turn into floats where a float is expected (`vec2(1, 0)`, `x * 2`), never the other way: write `int(x)`.
- **Colors:** `#FF8800` is a `vec3` (`vec3(1.0, 0.533, 0.0)`), `#80FF8800` a `vec4` with alpha first like Minecraft (`vec4(1.0, 0.533, 0.0, 0.5)`).
- **Vectors:** made with `vec3(1.0)` (all three), `vec3(xy, z)`, `vec4(color, 1.0)`; read and written with swizzles: `v.x`, `v.xy`, `color.rgb`, `p.zyx`, `v[1]`. `+ - * /` work on vectors component by component, and with a number on every component. `mat * vec` and `vec * mat` multiply properly, `m[0]` is a column.
- **Arrays:** `float[] weights = {0.2, 0.5, 0.2};`, `new float[4]` (all zero), `weights.length`, `for (float w : weights)`.
- **Constants:** `const float X = ...;` must be worked out while compiling (numbers, other constants, `vec3(...)`, a few math functions). Locals can be `const` too, or `final` (can't change, value worked out while running).
- **Control flow:** `if/else`, `for`, for-each over arrays, `while`, `do/while`, `switch` on ints (`case 1:` or `case 1 ->`), `break`, `continue`, `return`, `? :`, and `discard;` (drop this pixel; passes and @Fragment hooks only).
- **Comparisons:** `<` `>` `<=` `>=` compare numbers; for vectors use `lessThan(a, b)` and friends, `any(...)`, `all(...)`. `==` works on anything but textures.
- `%`, `&`, `|`, `^`, `<<`, `>>` and `~` are for ints; use `mod(a, b)` for floats.
- **GLSL functions** all keep their names, so GLSL snippets paste with few changes: `radians` `degrees` `sin` `cos` `tan` `asin` `acos` `atan` `sinh` ... `pow` `exp` `log` `exp2` `log2` `sqrt` `inversesqrt` `abs` `sign` `floor` `trunc` `round` `ceil` `fract` `mod` `min` `max` `clamp` `mix` `step` `smoothstep` `isnan` `isinf` `length` `distance` `dot` `cross` `normalize` `faceforward` `reflect` `refract` `matrixCompMult` `outerProduct` `transpose` `determinant` `inverse` `lessThan` `lessThanEqual` `greaterThan` `greaterThanEqual` `equal` `notEqual` `any` `all` `not` `texture` `textureLod` `textureSize` `texelFetch` `floatBitsToInt` `intBitsToFloat`, and in passes and @Fragment hooks `dFdx` `dFdy` `fwidth`.

### Mistakes

Every mistake is reported with its place, to the player's game log and the server console (`SkaffyShaderErrorEvent`), and the file isn't used:

```
Dream.sfy:12:22: Expected vec3 but this is vec4 (use xyz to take the first values, like v.xyz)
Dream.sfy:13:21: % needs ints, not int and float (use mod(a, b) for decimals)
Dream.sfy:23:19: dFdx only works in @Fragment hooks and passes
```

If an effect fails on the graphics card (rare: a driver that refuses something), it turns itself off and reports why.

---

## Screen passes

```java
@Pass(stage = SCREEN, output = SCREEN, blend = REPLACE)
vec4 name(vec2 uv) { ... }
```

A pass runs once for every pixel of its output and returns that pixel's color. `uv` goes from (0, 0) at the bottom left to (1, 1) at the top right. Passes run in the order they're written.

| Option | Values |
|---|---|
| `stage` | `DEFERRED`: after solid blocks and entities, before see-through things (water, glass, clouds, particles) and the hand. `WORLD`: after the world, before the hand (the hand isn't affected). `SCREEN` (default): after the hand, before the HUD. `FINAL`: over everything, the HUD and open menus too. |
| `output` | `SCREEN` (default) or a `@Target` field. Writing the screen only changes red, green and blue. |
| `blend` | `REPLACE` (default), `ADD` (adds to what's there), `ALPHA` (mixes by the returned alpha), `MULTIPLY`. |
| `when` | A bool `@Uniform` or constant of the file (often an [@Option](#options)): the pass only runs while it's true. A constant that's false leaves the pass out completely. |

What passes read:

- `texture(SCREEN, uv)`: the picture so far.
- `texture(DEPTH, uv).r`: the depth buffer; easier through `linearDepth(uv)` (blocks from the camera, straight ahead), `viewPos(uv)`, `relativePos(uv)` (position relative to the camera, in world directions) and `worldPos(uv)` (world coordinates). These work the same on Vulkan and OpenGL. `isSky(uv)` is true where nothing was drawn. In `FINAL` passes, `DEPTH` is the world's depth from before the HUD.
- Your `@Target` and `@Texture` fields.

- `SOLID` and `SOLID_DEPTH`: the picture and depth with only the solid things, before see-through ones were drawn (for water that bends what's under it).
- In `DEFERRED` passes, `SCREEN` is the solid world so far, and `@Output` pictures the world hooks wrote (below).

A pass may read the texture it writes; it then reads a copy made just before.

A pass can also draw into more `@Target` fields at once with `write(target, value)`, next to the color it returns: every pixel of those targets gets what the pass wrote there (or see-through black where it didn't). They must be the same size as the pass's output, the pass can't read them, and they're always replaced (no blending).

```java
@Target Texture bright;

@Pass
vec4 grade(vec2 uv) {
    vec3 color = texture(SCREEN, uv).rgb;
    write(bright, vec4(color * step(0.8, luminance(color)), 1.0));   // a second picture, in the same pass
    return vec4(color, 1.0);
}
```

### Targets

```java
@Target(scale = 0.5) Texture half;                         // half the screen's size
@Target(width = 1, height = 1) Texture exposure;           // a fixed size in pixels
@Target(persistent = true) Texture history;                // keeps its picture from the last frame
@Target(format = RGBA8, filter = NEAREST) Texture mask;
```

| Option | Default | |
|---|---|---|
| `scale` | 1 | Size compared to the screen (above 0, at most 4) |
| `width`, `height` | | A fixed size instead (both, 1 to 8192) |
| `format` | `RGBA16F` | `RGBA16F` (half floats, can go above 1), `RGBA32F` (full floats, for positions and other precise values) or `RGBA8` |
| `persistent` | false | Keep last frame's picture; otherwise it starts every frame see-through black |
| `filter` | `LINEAR` | How reading between pixels works: `LINEAR` or `NEAREST` |

### Textures

```java
@Texture("noise.png") Texture noise;                                   // an asset (AssetRegistry)
@Texture("minecraft:block/stone") Texture stone;                       // a vanilla texture (textures/block/stone.png)
@Texture(value = "minecraft:misc/vignette", filter = NEAREST, wrap = CLAMP) Texture edge;
```

Vanilla textures come from Minecraft's own files, never resource packs. `wrap` is `REPEAT` (default) or `CLAMP`; `filter` is `LINEAR` (default) or `NEAREST`. Textures work in passes and world hooks. A missing texture is reported and reads as white.

---

## World hooks

```java
@Vertex(TERRAIN)
void sway(Vertex v) {
    if (v.material == LEAVES) {
        v.position.x += sin(time * 2.0 + v.worldPos.z) * 0.05;
    }
}

@Fragment(TERRAIN)
vec4 light(Fragment f) {
    float sun = max(dot(f.normal, sunDirection), 0.0) * f.skyLight;
    return vec4(f.albedo.rgb * (f.lightmap.rgb * 0.6 + vec3(1.0, 0.9, 0.7) * sun), f.albedo.a);
}
```

Hooks plug into Skaffy's copies of vanilla's shaders, so every vanilla variant keeps working (cutout leaves, translucent water and glass, "Improved Transparency", glint, the red hurt flash, ...). Only what you write changes.

**Programs**, one or more per hook (`@Vertex({TERRAIN, BLOCK})`):

| Program | What it draws |
|---|---|
| `TERRAIN` | Every block in chunks, water and lava included |
| `BLOCK` | Blocks outside chunks: falling blocks, pistons moving blocks |
| `ENTITY` | Mobs, players, armor, chests, signs and other block entities, animation entities |
| `ITEM` | Items: dropped, in item frames, held, the first-person hand |
| `PARTICLE` | Particles, rain and snow |
| `SKY` | The sky, sunrise glow, stars, sun, moon, the End sky |
| `CLOUDS` | Clouds |
| `LINES` | Block outlines, hitboxes and other lines, leads |
| `TEXT` | Text in the world: signs, name tags (also the ones seen through walls) |
| `BEACON` | Beacon beams |
| `PORTAL` | End portals and End gateways |
| `LIGHTNING` | Lightning bolts |
| `GLINT` | The enchantment glint drawn over items and armor |
| `WEATHER` | Rain and snow (without `WEATHER` hooks, a file's `PARTICLE` hooks draw them) |
| `BREAKING` | The cracks on a block being broken |
| `WORLD_BORDER` | The world border |
| `EYES` | Glowing layers of entities: spider and Enderman eyes, charged creepers, ... (without `EYES` hooks, a file's `ENTITY` hooks draw them; `f.emissive` is true there) |
| `SHADOW` | What draws the shadow map ([Shadow casters](#shadow-casters)) |

They change the world and the first-person hand, never items or mobs drawn in menus or the HUD. If several enabled files hook the same program, the one with the highest order wins that program (its other programs and passes still run). `LINES` and `LIGHTNING` have no texture, so their hooks can't read `SOURCE`.

**`Vertex v`**, changeable: `position` (vec3, relative to the camera), `color` (vec4), `uv` (vec2). Read only: `worldPos` (where the vertex was, in world coordinates), `normal`, `material` (int), `top` (bool: in the upper half of its block, so plants bend at the top and stay planted), `blockLight`, `skyLight` (0 to 1), `isHand`, `entity` and `item` (the [@Entities and @Items](#entities-items-biomes-and-dimensions) numbers).

**`Fragment f`**, all read only: `albedo` (texture × tint, without light), `color` (vanilla's tint with its shading and ambient occlusion), `texture`, `lightmap` (vanilla's light color), `blockLight`, `skyLight`, `normal` (facing the camera), `position` (relative to the camera), `worldPos`, `uv`, `material`, `vanilla` (exactly what vanilla would draw, before fog), `isHand`, `entity` and `item` (the [@Entities and @Items](#entities-items-biomes-and-dimensions) numbers), `emissive` (a glowing layer, see `EYES`), and for the sky `direction` (where that pixel looks) and `part` (`SKY`, `SUNRISE`, `STARS`, `SUN`, `MOON`, `END_SKY`, `END_FLASH`).

Models with their own materials (animation entities drawn from Blockbench projects and glTF models, in `ENTITY`) also fill in their material, so lighting can use it: `hasMaterial` (true when the part has a normal map or a metal/roughness/specular map), `mappedNormal` (the normal with its normal map applied; `normal` everywhere else), `roughness` (0 mirror-smooth to 1), `metallic` (0 to 1), `specular` (the color it reflects head-on: 0.04 for non-metals, its own color for metal; for specular-glossiness materials the map's strength times the material's specular color), `emission` (light it gives off by itself: a glTF emissive map, a Blockbench emissive texture or a MER map's glow; already in `vanilla`) and `unlit` (drawn without light, like glTF's unlit materials: `vanilla` is its plain color). Model parts without maps keep their material's plain values (glTF's roughness and metallic factors). Everything that isn't such a model has `hasMaterial` and `unlit` false, `mappedNormal` equal to `normal`, `roughness` 1, `metallic` 0, `specular` 0.04 and `emission` 0. Vista uses all of them.

A `@Fragment` hook returns the color; vanilla's fog is added afterwards, unless it's `@Fragment(value = TERRAIN, fog = false)`. `discard;` drops the pixel. A hook that only wants to change some things returns `f.vanilla` for the rest.

### Block materials

```java
@Blocks("#minecraft:leaves") const int LEAVES = 1;
@Blocks({"minecraft:short_grass", "#minecraft:small_flowers"}) const int PLANTS = 2;
```

`v.material` and `f.material` are the number of the block being drawn (0 if it isn't listed), on `TERRAIN`. Blocks are ids or `#tags`, numbers 1 to 4095. Turning a file with `TERRAIN` hooks on or off reloads the chunks once (a short stutter, like changing the render distance).

Ids and tags can name block states too, like the `/setblock` syntax: `"minecraft:repeater[powered=true]"` or `"minecraft:copper_bulb[lit=true]"`. Several states are separated by commas and all must match; a tag's blocks that don't have the state are left out. A block named by its id wins over the same block in a tag.

### Entities, items, biomes and dimensions

```java
@Entities({"minecraft:zombie", "minecraft:husk"}) const int ZOMBIES = 1;
@Entities("#minecraft:skeletons") const int SKELETONS = 2;
@Items("#minecraft:swords") const int SWORDS = 1;
@Biomes("#minecraft:is_ocean") const int OCEAN = 1;
@Dimensions("mymod:moon") const int MOON = 4;
```

Like `@Blocks`, each names ids or `#tags` (dimensions only ids) and gives them a number; an id wins over a tag.

- `@Entities` (1 to 2047): `v.entity` / `f.entity` in `ENTITY` and `EYES` hooks is the number of the entity being drawn, and in `ITEM` hooks of the entity holding the item (0 when it isn't listed). Block entities (chests, signs, ...) are 0.
- `@Items` (1 to 2047): `item` in `ITEM` hooks (and `ENTITY` hooks for items drawn with entity models, like shields) is the number of the item being drawn. `heldItem` and `heldItemOffhand` are the numbers of what the player holds.
- `@Biomes` (1 to 4095): `biome` is the number of the biome at the camera. When it changes, `previousBiome` keeps the old one and `biomeBlend` goes from 0 to 1 over two seconds, so colors can change smoothly.
- `@Dimensions` (4 to 4095): `dimension` is this number in a listed dimension (0 to 3 stay `OVERWORLD`, `NETHER`, `END` and `OTHER_DIMENSION`).

Entity and item numbers need both `ENTITY` and `ITEM` to be hooked (by any enabled files); they're 0 otherwise.

### Light types

```java
@Light({"minecraft:torch", "minecraft:wall_torch", "#minecraft:candles"})
@Light(value = {"minecraft:repeater[powered=true]", "minecraft:comparator[powered=true]"}, level = 4)
@Light(value = "minecraft:redstone_wire", level = 6, levelFrom = "power")
const vec3 FIRE = #FF9A4A;

@LightFilter({"minecraft:purple_stained_glass", "minecraft:purple_stained_glass_pane"}) const vec3 PURPLE_GLASS = #8040C0;
```

Each `@Light` field is one light type (up to 8) that spreads from its blocks like vanilla block light, one level less per block, stopped by walls. A field can have several `@Light`s, each a group of blocks with its own brightness: no `level` means the block's own light (by state, so a lit furnace glows and an unlit one doesn't), `level = 1` to `15` sets it, and `levelFrom = "property"` scales `level` with a number property of the block (the property's highest value gives `level`, 0 gives nothing). `@LightFilter` blocks (up to 32) tint the light passing through them. The color can be a constant or a `@Uniform`.

| Function or value | |
|---|---|
| `coloredLight(vec3 world)` | The light at a world position: every type's level times its color, through any filters |
| `lightLevel(TYPE, vec3 world)` | One type's level there, 0 to 1 (`TYPE` is the field, like `FIRE`) |
| `lightLevel(vec3 world)` | The strongest type's level there, 0 to 1, before filters (how bright, without the glass's color) |
| `inLightVolume(vec3 world)` | Whether the position is in the box around the camera where light types are worked out (`coloredLightRange` blocks wide, default 128, up to 256, but never wider than twice the player's render distance) |
| `heldLight`, `heldLightColor` | The brightest light block in your hands, 0 to 1 (its type's level if it's one of yours, else vanilla's), and its type's color (white when it isn't one of your types) |

Reading blends the light of the nearby blocks smoothly, but only of air, see-through blocks and light sources: walls next to a spot don't darken the light read there.

### The shadow map

```java
@Uniform float shadowDistance = 512.0;    // how far shadows reach, in blocks (never past the player's render distance)
@Uniform int shadowResolution = 2048;     // pixels per cascade (a power of two, up to 4096)
```

Using `SHADOW_MAP` (or `shadowPos`) turns the sun's shadow map on. It has three cascades: a sharp one close to the camera, a middle one, and one reaching `shadowDistance`, capped at the player's render distance (`shadowRange` is what it really is this frame). `shadowPos(relative)` picks the sharpest cascade the position is in and returns where to look in `SHADOW_MAP`, `SHADOW_MAP_SOLID` and `SHADOW_COLOR` (x and y), and its depth (z); x and y are below 0 when it's outside every cascade. Read the maps only through `shadowPos`: `shadowMatrix` alone is the far cascade before it's placed in the texture.

### Shadow casters

A `@Fragment(SHADOW)` hook returns what a pixel of a block casts: alpha 1 blocks the sun, alpha below 1 lets it through tinted by the color (read back as `SHADOW_COLOR`). For cutout blocks (glass, leaves, plants) the texture's holes are cut out, unless the hook returns alpha below 1 for them: that's how clear glass can cast one even, soft shadow instead of its frame's pattern.

### World outputs

```java
@Output Texture normals;                        // a screen-sized picture world hooks write next to the color
@Output(format = RGBA8) Texture surface;

@Fragment(TERRAIN) vec4 shade(Fragment f) {
    write(normals, vec4(f.normal * 0.5 + 0.5, 1.0));
    return f.vanilla;
}
```

`@Output` fields are drawn into while the world is drawn, like a G-buffer: `@Fragment` hooks `write(output, value)`, and passes read them (best in `DEFERRED` and later stages). They start see-through black every frame. Enabled files can have 7 together (`RGBA16F` by default, `RGBA8`, `RGBA32F`; `filter = NEAREST` by default).

### Varyings

```java
@Varying float wave;                  // smoothly blended across the triangle
@Varying(flat = true) vec2 seed;      // the same everywhere on the triangle (ints always are)

@Vertex(TERRAIN) void move(Vertex v) { wave = sin(v.worldPos.x); }
@Fragment(TERRAIN) vec4 shade(Fragment f) { return f.vanilla * (1.0 + wave * 0.1); }
```

Set them in `@Vertex` hooks, read them in `@Fragment` hooks of the same program. There's room for 7 (a vector counts one, a matN counts N).

### The sun's path

```java
const float sunPathRotation = 30.0;              // degrees
@Uniform float sunPathRotation = 30.0;           // or a @Uniform, so the server can change or animate it
```

Vanilla's sun goes straight over your head, so at noon light that follows `sunDirection` only reaches the tops of blocks. Like in shader packs, `sunPathRotation` tilts the sun's path so it never stands straight overhead: walls facing it get light all day. The sun, moon and stars in the sky tilt with it, and `sunDirection`, `moonDirection` and `lightDirection` follow. The highest enabled file that has it decides; without one there's no tilt.

---

## Frame settings

Fields with these names set up the whole frame, like in shader packs. Only the enabled file can have them (not a class it imports); the highest running file that has one decides.

| Field | |
|---|---|
| `const bool hdr = true;` | The world and passes draw into half-float pictures that can go above 1 (a pass should tone-map before `FINAL`); clamped before the HUD. Internal only: it isn't an HDR screen setting |
| `const bool taa = true;` | The camera shifts a little every frame, for temporal anti-aliasing (see `taaOffset` and `previousProjectionMatrix`) |
| `sunPathRotation` | [The sun's path](#the-suns-path) |
| `shadowResolution`, `shadowDistance` | [The shadow map](#the-shadow-map) |
| `const int coloredLightRange = 128;` | [Light types](#light-types) |

## Options

```java
@Option(name = "Wind", description = "How much plants move", min = 0.0, max = 3.0, step = 0.1) @Uniform float wind = 1.0;
@Option(name = "Bloom") @Uniform bool bloom = true;
@Option(name = "Shadow quality", values = {1024, 2048, 4096}) @Uniform int shadowResolution = 2048;
@Option(name = "Tint") @Uniform vec3 tint = #FFF4E8;
@Option(name = "Waving plants") const bool WAVING = true;
```

`@Option` puts a field in a shader pack's settings (Shader Packs → Settings...), saved for the player in `.minecraft/shaders/settings/`. A bool is a switch, an int or float a slider (`min`, `max`, `step`) or a choice (`values`), a `vec3`/`vec4` a color. On a `@Uniform` it changes right away; on a constant (bool, int or float) the shader is built again with the new value, so loops and `@Pass(when = ...)` can depend on it. Helper classes can have options too (they're listed after the file's). For files sent by a server, `@Option` does nothing: the server sets uniforms itself.

## Built-in values

| Name | Type | |
|---|---|---|
| `time` | float | Seconds, smooth; goes back to 0 every hour |
| `frameTime` | float | Seconds since the last frame |
| `dayTime` | float | 0 to 24000 like `/time` (0 sunrise, 6000 noon, 18000 midnight) |
| `moonPhase` | int | 0 full moon to 7 |
| `partialTick` | float | How far between game ticks this frame is |
| `screenSize` | vec2 | The screen in pixels |
| `cameraPosition`, `previousCameraPosition` | vec3 | World coordinates of the camera (now, last frame) |
| `cameraDirection` | vec3 | Where the camera looks |
| `viewMatrix`, `projectionMatrix` and their `inverse...` and `previous...` versions | mat4 | Camera-relative world → view → screen |
| `sunDirection`, `moonDirection` | vec3 | Unit vectors towards the sun and moon (with `sunPathRotation`'s tilt) |
| `lightDirection` | vec3 | The sun while it's up, else the moon |
| `rain`, `thunder` | float | 0 to 1 |
| `fogColor` | vec4 | Vanilla's fog color right now |
| `fogStart`, `fogEnd` | float | Vanilla's fog distances (water, lava, blindness, ...) |
| `renderDistance` | float | In blocks |
| `fov` | float | Degrees, as on screen |
| `near`, `far` | float | Camera planes in blocks |
| `eyeBlockLight`, `eyeSkyLight` | float | Light at the camera, 0 to 1 |
| `dimension` | int | `OVERWORLD`, `NETHER`, `END` or `OTHER_DIMENSION` |
| `isInWater`, `isInLava`, `isInPowderSnow` | bool | Where the camera is |
| `cloudHeight` | float | The bottom of vanilla's cloud layer (y) |
| `shadowRange` | float | How far the shadow map reaches this frame, in blocks |
| `frameCounter` | int | Counts frames |
| `taaOffset` | vec2 | This frame's camera shift with `taa` (in pixels) |
| `wetness` | float | 0 to 1: rises while it rains on the player, dries slowly |
| `heldLight`, `heldLightColor` | float, vec3 | See [Light types](#light-types) |
| `biomeTemperature`, `biomeDownfall` | float | Of the biome at the camera |
| `isSnowing` | bool | It's snowing at the camera |
| `nightVision`, `darkness` | float | How strong those effects are, 0 to 1 |
| `cameraVelocity` | vec3 | How fast the camera moves, blocks per second |
| `playerHealth`, `playerHunger`, `playerAir` | float | 0 to 1 |
| `hurtTime` | float | 1 right after the player is hurt, down to 0 |
| `isSneaking`, `isSprinting`, `isGliding`, `isRiding`, `isSpectator` | bool | |
| `eyeSkyLightSmooth`, `eyeBlockLightSmooth` | float | `eyeSkyLight` and `eyeBlockLight` smoothed over about a second (for eye adaptation) |
| `lightningFlash`, `lightningPosition` | float, vec3 | 1 while a lightning bolt is there (fading quickly after; 0 with the Hide Lightning Flashes option), and where the nearest one is |
| `skyColor`, `cloudColor` | vec3 | Vanilla's sky and cloud colors right now |
| `bossDarkening` | float | How much a boss fight darkens the sky, 0 to 1 |
| `gamma` | float | The player's Brightness option, 0 to 1 |
| `aspectRatio` | float | Screen width divided by height |
| `biome`, `previousBiome`, `biomeBlend` | int, int, float | See [@Biomes](#entities-items-biomes-and-dimensions) |
| `heldItem`, `heldItemOffhand` | int | See [@Items](#entities-items-biomes-and-dimensions) |
| `PI`, `TAU` | float | |
| `SCREEN`, `DEPTH` | Texture | Passes only |

With `taa` on, `projectionMatrix` includes this frame's small camera shift (so `relativePos` matches the depth), and `previousProjectionMatrix` is last frame's projection with *this* frame's shift. That way `previousProjectionMatrix * previousViewMatrix * vec4(world - previousCameraPosition, 1.0)` is where a point sits in last frame's picture (your TAA history) and the shift cancels out instead of shaking the picture; `uv` minus that spot is the point's real movement (for motion blur).

## Built-in functions

| Function | |
|---|---|
| `luminance(vec3)` | Brightness of a color |
| `texelSize(Texture)` | The size of one pixel in uv (1 / size) |
| `random(vec2)`, `random(vec3)` | A number 0 to 1 that's always the same for the same input |
| `noise(vec2)`, `noise(vec3)` | Smooth noise, 0 to 1 |
| `toScreen(vec3)` | Where a camera-relative position is on screen (uv) |
| `inFront(vec3)` | Whether a camera-relative position is in front of the camera |
| `linearDepth(uv)`, `viewPos(uv)`, `relativePos(uv)`, `worldPos(uv)`, `isSky(uv)` | Passes only, see above |
| `cloudShadow(vec3 world)` | How much vanilla's clouds cover the sun (or moon) at a world position, 0 to 1. Follows the cloud height, the clouds' movement and the player's clouds setting (0 with clouds off) |
| `cloudCover(vec3 world)` | How much of vanilla's cloud layer is above that spot (x and z), 0 to 1, smoothly blended between its 12-block cells; for drawing your own clouds where vanilla's are. 0 with clouds off |

`toScreen(sunDirection * 100.0)` is where the sun is on screen (good for god rays).

---

## Shader packs

A shader pack is a zip or a folder in `.minecraft/shaders/`. Players add one by dropping it on the Shader Packs menu (it's copied there) or by putting it there themselves, and pick it in the menu; one is on at a time (or Vanilla: none). It works in single player and on every server, below the server's own shaders.

```
Ocean Dream/                       ← the folder (in a zip: the zip's only folder)
├── shaders/
│   └── oceandream/                ← your pack's package (the name doesn't show in game)
│       ├── OceanDream.sfy         ← the main file
│       ├── World.sfy              ← shader files
│       ├── Post.sfy
│       └── lib/Noise.sfy          ← subfolders are subpackages: oceandream.lib.Noise
└── resources/
    ├── icon.png
    └── textures/waves.png         ← @Texture("textures/waves.png")
```

- A zip holds exactly one folder with `shaders/` and `resources/` in it; it's read into memory and never unpacked. A folder pack is that folder itself, read from disk again whenever the player presses Reload (handy while you work on it).
- `shaders/` holds one folder: your top package. Every `.sfy` file's package must match its folders.
- `resources/` holds PNGs, in folders if you like: `@Texture("...")` and `icon(...)` name them by their path inside `resources/`.
- `shaders/skaffy/` belongs to the mod.

### The main file

One class in the pack extends `Shader`. It's ordinary .sfy code like a GUI file (it runs on the CPU, see SFY.md for the language), and it imports `skaffy.shader.*`:

```java
package oceandream;

import skaffy.shader.*;

public class OceanDream extends Shader {
    public void onEnable() {
        name("Ocean Dream");
        icon("icon.png");                       // resources/icon.png
        author("You");
        version("1.2");
        description("Calm seas and <aqua>bright water</aqua>.");   // MiniMessage, like GUI text
        license("CC BY 4.0");
        link("Website", "https://example.com");
        register(World.class);                  // the shader classes it draws with, in draw order
        register(Post.class);
    }

    public void onDisable() {
    }
}
```

- `onEnable` runs when the pack loads: when the game starts, when it's added, and on Reload. Whether the pack is on is a separate choice the player makes in the menu. `onDisable` (optional) runs when the game closes, before a Reload, and when the pack is removed.
- `name(...)` is required (at most 64 characters). `icon`, `author`, `version`, `description`, `license` and `link(label, url)` (https only, at most 8) are optional; the menu shows them.
- `register(Class.class)` adds a shader class the pack draws with (at most 16). Draw order is register order: the first one is drawn first. `World.class` names a class in the main file's package; others need their full name (`oceandream.lib.Thing.class`) or an import (`import oceandream.lib.Thing;`). Helper classes are only imported by the classes that use them, not registered.
- `LOGGER.info(...)` writes to the game's log.

### Mistakes in packs

Everything wrong with a pack (its layout, the main file, a registered class that doesn't compile, a problem while it runs) shows under Problems in the menu and goes to the game's log. Nothing about a player's packs is ever sent to a server.

### Limits for packs

256 MiB per pack (zipped and unpacked), 4096 files, 512 KiB per `.sfy` file, 16 MiB per PNG, 16 registered classes.

## The plugin API

`SkaffyAPI.get().getShaders()`:

| Method | Does |
|---|---|
| `addFile(className, source)` | Adds or replaces a file for everyone; players with that effect on switch to the new version, keeping uniform values that still fit |
| `addFiles(plugin, folder)` | Every `.sfy` in a folder of the jar, then of the data folder (those win) |
| `removeFile(className)`, `getFiles()` | |
| `enable(player, className)`, `enable(player, className, order)`, `enable(player, className, order, uniforms)` | Turns an effect on (or changes its order). It starts once the client built it |
| `disable(player, className)`, `disableAll(player)` | |
| `set(player, className, uniform, value)`, `set(player, className, values)` | Right away |
| `animate(player, className, uniform, value, ms, easing)`, `animate(player, className, values, ms, easing)` | Smoothly, with the 31 easings of easings.net |
| `getEnabled(player)`, `isRunning(player, className)`, `isSupported(player)` | |

Uniform values: numbers, booleans, lists of numbers for vectors and matrices (matrices column by column), `org.bukkit.Color` or `"#RRGGBB"` / `"#AARRGGBB"` for `vec3`/`vec4`, `org.bukkit.util.Vector` for `vec3`. Animated ints round, bools switch at the end.

| `setClientShaders(player, mode)`, `getClientShaderMode(player)` | What the player's own shader may do this session (below) |
| `isClientShaderOn(player)` | Whether the player has their own shader on (never which one) |

Events: `SkaffyShaderErrorEvent` (compile, runtime or request problems; cancel it to keep them out of the console), `SkaffyShaderStateEvent` (an effect started or stopped drawing) and `SkaffyClientShaderChangeEvent` (the player's client said whether their own shader is on: when they join, after `setClientShaders`, and when they pick one, `isByPlayer()`).

Effects run in order, lower first, and last until they're turned off or the player leaves; they stay on through death, respawn and changing worlds. Nothing is saved.

### The player's own shader

Players pick their own shader in Video Settings → Shader Packs: Vista or one of their [shader packs](#shader-packs). Your effects always draw on top of it, and a world hook of yours replaces its hook for that program. You can't see which shader it is, read it or set its uniforms; you can only turn it off for the session:

| `ClientShaderMode` | |
|---|---|
| `ALLOWED` | The player's choice (the default) |
| `OFF` | Their shader is turned off; they can pick one again in the menu |
| `FORCED_OFF` | Their shader is off and the Shader Packs button can't be used until you allow it again or they leave |

Nothing changes on their side: when you allow it again, the shader they used last comes back. Classes in `skaffy.` packages are the mod's: they can't be added, enabled, disabled or set.

### Vista

Vista is the shader built into the mod, a full shader pack (its files are in the mod's `skaffy_shaders/vista/`, as an example of a big one): soft shadows, colored light, reflections, puddles, soft clouds, bloom and more, with its own settings (quality, effect distance, shadows, colors, ...).

## Limits

- 512 KiB per file, 8 MiB of shader files per server ([packs](#limits-for-packs) have their own).
- 16 passes, 16 `@Target` fields and 8 `@Texture` fields per file.
- A world pipeline reads at most 16 textures in all (Macs refuse more, and so may other drivers). Minecraft's own pipelines and Skaffy's models already use up to 5 of them for `TERRAIN` and up to 7 for `ENTITY` and `ITEM`, so a file's hooks for those can read 11 and 9: its `@Texture` and `@Target` fields and the built-in pictures they use (the shadow map, colored light, ...). Vista reads 8 in its entity hook.
- Loops have no limit, but a shader that takes too long freezes the player's game (the driver may reset it).

## Vulkan and OpenGL

Players pick in Options → Video Settings → Graphics API (it needs a restart). Your file is the same for both: Minecraft compiles it to SPIR-V for Vulkan and turns that into GLSL 330 for OpenGL.

## Editor support and license

The `editor/sfy` coloring package colors shader files too (see SFY.md). The same license terms as GUI files apply: your files are yours.
