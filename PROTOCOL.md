# Skaffy's API protocol

Protocol version **1**. This is everything a server needs to talk to the Skaffy's API client mod, whatever software it runs on. The Java reference implementation is in `protocol/` (`ClientSession`, `ServerSession`, `CoreCodec`).

## Transport

All messages are Minecraft custom payload packets ("plugin messages"):

| Channel | Used for |
|---|---|
| `skaffy:core` | Handshake, join lifecycle, files |
| `skaffy:<feature>` | One channel per feature, e.g. `skaffy:blocks`, `skaffy:particles`, `skaffy:keybinds`, `skaffy:entity_models`, `skaffy:fov`, `skaffy:name_tags`, `skaffy:gui`, `skaffy:shaders`, `skaffy:animations` |

Every payload is `[VarInt packet id][fields]`. Field types use Minecraft's encodings:

| Type | Encoding |
|---|---|
| VarInt / VarLong | Minecraft variable-length integer |
| Boolean | 1 byte, `0` or `1` |
| Byte / Short / Int / Long / Float / Double | Big-endian, floats in IEEE 754 |
| String(n) | VarInt byte length + UTF-8, at most `n` characters |
| Bytes(n) | VarInt length + raw bytes, at most `n` bytes |
| Hash | 32 raw bytes, SHA-256 |
| List\<T\> | VarInt count + elements |

Vanilla limits apply: server → client payloads at most 1 MiB, client → server payloads at most 32767 bytes.

## Session lifecycle

A session lives for one configuration phase plus the play phase after it.

1. **Configuration starts.** The client drops everything from any previous session. It announces `skaffy:core` with `minecraft:register`, then sends **Hello**. This happens on every configuration phase: first join, proxy server switch, reconfiguration.
2. **Server answers with Welcome.** A server that doesn't know the mod never answers, and the client stays inactive.
3. **Registration.** In any order, the server sends feature definitions (on feature channels), **AssetQuery**, **AssetData**, **AssetDelete**. It ends with **RegistrationEnd**. Nothing registered can change after that until the next session.
4. **Loading.** The client loads everything and sends **Ready**, or **Failed** with a reason.
5. **Servers should hold the player in configuration** until Ready or Failed, so all content exists before the world loads. The Paper plugin fails the session after 45 seconds without progress.
6. If configuration ends before the client is Ready, the client abandons the session. On disconnect everything is unloaded.

**Play-phase fallback.** If nothing was received on `skaffy:core` during configuration, the client announces the channel and sends Hello once more when the play phase starts. Servers that can't use the configuration phase answer that one; everything works the same, except that the world is already loaded.

A failed session is not fatal: the player continues without Skaffy features unless the server kicks them.

## Core packets (`skaffy:core`)

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | Hello | VarInt protocol version, String(32) mod version, List\<Feature range\> features |
| 1 | AssetReport | VarInt query id, List\<Cached file\> files, Boolean last |
| 2 | AssetAck | VarLong total AssetData bytes received this session |
| 3 | Ready | – |
| 4 | Failed | String(256) reason |

- **Feature range:** String(32) id, VarInt min version, VarInt max version.
- **Cached file:** String(64) id, VarInt size, Boolean has hash, [Hash].

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | Welcome | VarInt protocol version, List\<Feature version\> enabled features |
| 1 | AssetQuery | VarInt query id, List\<String(64)\> ids (at most 256), Boolean include hashes |
| 2 | AssetTransfer | VarLong bytes of AssetData about to follow (only for the progress display) |
| 3 | AssetData | String(64) id, VarInt file size, VarInt offset, Bytes(262144) data |
| 4 | AssetDelete | List\<String(64)\> ids (at most 1024) |
| 5 | RegistrationEnd | – |

- **Feature version:** String(32) id, VarInt version.

Hello and Welcome may get new fields at the end in later protocol versions, so readers must ignore extra bytes after them. All other packets must be read completely.

## Versions and features

- If Welcome's protocol version differs from the client's, the client fails the session. A server that gets a Hello with a protocol version it doesn't speak answers Welcome with its own version and no features.
- Features are versioned separately. Hello lists every feature the client supports with a version range. Welcome enables features at one version inside that range, and the server picks. Enabling a feature the client didn't offer fails the session.
- Only enabled features' channels may be used. The client drops (and logs once) packets for anything else.
- Feature ids are 1–32 characters of `a-z 0-9 _` and double as the channel path.

## Files (assets)

The server decides everything about files. The client never checks content by itself. It answers queries, saves what it is sent, and deletes what it is told to delete.

- **Ids** are used literally as file names inside the server's cache folder, never as paths. So they are limited to what is a plain file name on every OS: 1–64 characters of `a-z 0-9 _ - .`, not starting or ending with `.`, and not a name Windows reserves (`con`, `nul.png`, `com1`, …). Lowercase only, because Windows and macOS treat `A.png` and `a.png` as the same file. Clients reject other ids.
- **AssetQuery** asks which of the listed files the client has. An empty list asks for all of them. The client answers with one or more AssetReport packets (same query id, `last` set on the final one) listing only files it has. Hashes are included only if the server asked for them, since that makes the client read the files.
- **AssetData** sends files one at a time, each from offset 0 up to its size in order. The part that completes a file saves it, replacing any file with that id. A zero-size file is a single AssetData with no data. Starting a new file before the previous one is complete is an error.
- **AssetAck** is sent after every AssetData. Servers should limit how much data is unacknowledged (the Paper plugin keeps at most 4 MiB in flight). While loading after RegistrationEnd, clients repeat the last AssetAck every 10 seconds to show they're still working.
- **AssetTransfer** announces how many bytes are coming so the client can show "Downloading server content (x / y MiB)". It is optional.
- **Client safety limits** per server: 16 MiB per file, 2 GiB and 8192 files in total. Going over fails the session. The client never deletes files on its own, so servers that retire files should delete them.

Example (the Paper plugin's default):

```
S: AssetQuery(0, [ruby.png, ore.json], hashes)
C: AssetReport(0, [ruby.png 2048 <hash>], last)
S: AssetTransfer(512)                         ore.json is missing
S: AssetData(ore.json, 512, 0, <512 bytes>)
C: AssetAck(512)
S: RegistrationEnd
C: Ready
```

## Feature: blocks (`skaffy:blocks`, version 1)

Custom blocks. The server's world keeps a normal block where a custom block is (a barrier by default, but any block works). Clients with the mod register real blocks for the session and show them instead. Everything else (logic, sounds, drops) is up to the server.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | DefineBlocks | List\<Block definition\> (registration only; numbers continue across packets, the first block is 1) |
| 1 | SetBlocks | Long section position (`SectionPos#asLong`), List\<Placement\> (at most 4096) |
| 2 | StopMining | Long block position (`BlockPos#asLong`); the client stops mining it |

- **Block definition:**
  - String(64) name
  - String(64) model asset id
  - Boolean has collision, [String(64) collision asset id]
  - Boolean has hitbox, [String(64) hitbox asset id] (without one, the collision is used)
  - Byte transparency: 0 solid, 1 cutout, 2 translucent
  - Byte light (0–15)
  - Float hardness (negative = unbreakable)
  - Byte tool: 0 none, 1 pickaxe, 2 axe, 3 shovel, 4 hoe, 5 sword
  - Boolean requires tool
  - Boolean remove when replaced
  - Boolean rotatable
- **Placement:** Short position in the section (`x << 8 | z << 4 | y`), VarInt block number (0 removes the custom block), Byte rotation (`y | x << 2`, quarter turns like blockstate variants).

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | MiningStart | Long block position, VarInt block number |
| 1 | MiningAbort | Long block position |
| 2 | MiningFinish | Long block position, VarInt block number (sent before vanilla's own packet; the client already hides the block) |
| 3 | PickBlock | Long block position, VarInt block number (vanilla's pick packet is not sent) |

### Rules

- Models, collisions and hitboxes are block model JSON (the format Blockbench exports as "Java Block/Item"). Texture references in models are asset ids. Parents and textures that aren't server files are vanilla ones, always taken from Minecraft's built-in files. Collision and hitbox use the boxes (`from`/`to`) of the first model in the parent chain with elements; element rotations are ignored.
- Everything the server sends lives in a hidden resource pack above all others, under a random namespace, so resource packs can't replace it.
- Mining speed follows vanilla's formula with the block's hardness. The tool type borrows a vanilla block (pickaxe: stone, axe: planks, shovel: dirt, hoe: hay block, sword: melon, none: glass) so tool tiers and enchantments behave normally.
- Custom blocks are shown only in chunks the client has. When chunk data arrives, the chunk's custom blocks are dropped, so servers send SetBlocks after the chunk.
- When the server sends a block for a custom block's position, the custom block stays if it's the same block as before. If it's a different block, the custom block is removed when its definition says "remove when replaced"; otherwise it stays and the new block becomes the one underneath.

## Feature: particles (`skaffy:particles`, version 1)

Custom particles. Only clients with the mod see them; the server sends nothing to anyone else. All packets go from server to client.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | DefineParticles | List\<Particle definition\> (registration only; numbers continue across packets, the first particle is 1) |
| 1 | SpawnParticles | see below |

- **Particle definition:**
  - String(64) name
  - List\<String(128)\> textures (1 to 256)
  - Byte frame mode: 0 age (plays through the textures over its life), 1 random (each particle picks one)
  - Byte render: 0 opaque (pixels fully shown or not), 1 translucent
  - Byte facing: 0 camera, 1 vertical (turns only around Y), 2 horizontal (lies flat), 3 fixed
  - [Float yaw, Float pitch] only when facing is fixed
  - Byte light (0–15): the lowest light level it's drawn with; 15 is always fully bright
  - Float min size, Float max size: full width in blocks (a normal vanilla particle is about 0.2)
  - VarInt min lifetime, VarInt max lifetime: ticks, 1 to 6000
  - Float color variation (0–1): each particle is randomly darker by up to this share
  - Float min spin, Float max spin: degrees per tick
  - Boolean random start angle
  - Curve size (multiplier, default 1), Curve alpha (0–1, default 1), Color curve color (default white), Curve spin (multiplier, default 1)
  - Motion
- **Motion:** Float gravity (blocks/tick², positive falls), Float friction (share of the speed kept per tick, at least 0), Boolean collides, Boolean dies on ground, Float velocity randomness, Float wander chance (0–1), Float wander speed, Float sway (blocks/tick), Float sway period (ticks, above 0), Boolean converge, Byte converge easing.
- **Curve:** List\<Float time (0–1), Float value, Byte easing\> (at most 32, sorted by time). **Color curve:** the same with Int `0xRRGGBB` instead of the value. **Easing** (how the value moves from this point to the next): 0 linear, 1 ease in (`t²`), 2 ease out (`1 − (1 − t)²`), 3 ease in-out.
- **SpawnParticles:**
  - VarInt particle number
  - Double x, y, z
  - VarInt count (1 to 16384)
  - Float spread x, y, z (at least 0)
  - Byte spread shape: 0 gaussian (offset = gaussian × spread, like vanilla), 1 box (evenly random from −spread to +spread)
  - Float velocity x, y, z (blocks per tick)
  - Boolean force
  - Boolean has color, [Int `0xRRGGBB`]: multiplied with the color curve
  - Boolean has size, [Float]: multiplies the size
  - Boolean has lifetime, [VarInt ticks]: replaces the lifetime
  - Boolean has rotation, [Float yaw, Float pitch]: replaces the rotation of fixed particles

### Rules

- A texture that is a server file is used as a PNG; a file `<id>.mcmeta` makes it animated like block textures. Anything else is a vanilla particle texture (`minecraft:flame` is `textures/particle/flame.png`), always taken from Minecraft's built-in files. Textures go in the same hidden resource pack as block models, so resource packs can't replace them.
- A curve is read at `age / lifetime`. Before its first point it has the first value, after its last point the last value; an empty curve has the default.
- Each particle is one square facing the camera, or for horizontal and fixed particles two squares (one per side). A fixed particle is turned like the camera of a player looking with that yaw and pitch, so a player's own yaw and pitch make it face them. Spin turns the square around its middle.
- Spawning: each particle starts at the position plus its spread offset, with the spawn velocity plus an even random ±velocity randomness per axis. Without force, like vanilla, particles more than 32 blocks from the camera are skipped, the "Minimal" particles setting skips all of them and "Decreased" about a third. With force, all are spawned. Vanilla's limit of 16384 particles on screen applies either way.
- Every tick, a particle that converges moves from its start to the spawn point, eased by its converge easing over its life, and nothing else moves it. Otherwise: with the wander chance (and always on its first tick) it gets a new random velocity of ±wander speed per axis; gravity is subtracted from its vertical speed; it moves by its velocity plus the sway (`sway × (cos a, 0, sin a)`, where `a` starts random and turns a full circle every sway period, in a random direction), colliding with blocks if it collides; it is removed if it dies on ground and landed; its velocity is multiplied by friction, and by 0.7 horizontally while on the ground.
- SpawnParticles for a particle number the client doesn't know is ignored.

## Feature: keybinds (`skaffy:keybinds`, version 1)

Server keybinds. They show up in the Controls screen like vanilla keybinds, in one category (named by the server) that is always the last one. The client never saves them; servers that want to remember a player's keys store them and send them back.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | DefineKeybinds | String(64) category name, List\<Keybind definition\> (at most 256; registration only, once; the first keybind is 1) |
| 1 | SetKey | VarInt keybind, String(64) key (play only) |
| 2 | SetKeybindState | VarInt keybind, Boolean active, Boolean wins (play only) |

- **Keybind definition:** String(64) name, String(64) default key, String(64) key, Boolean active, Boolean wins.

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | KeyPress | VarInt keybind, String(64) key |
| 1 | KeyRelease | VarInt keybind, String(64) key |
| 2 | KeyChange | VarInt keybind, String(64) key |

### Rules

- Keys are vanilla key names, the same ones options.txt uses: `key.keyboard.g`, `key.keyboard.left.shift`, `key.mouse.middle`, `key.mouse.4`. `key.keyboard.unknown` is unbound. A key the client doesn't know is treated as unbound.
- Names are plain text. A keybind starts on its key; "Reset" in the Controls screen goes back to its default key.
- KeyPress is sent when the key goes down and KeyRelease when it goes up, with vanilla's rules: only while no screen is open, holding a key doesn't repeat, and opening a screen releases held keys. There are no key combinations; servers can track which keybinds are held.
- KeyChange is sent when the player binds a keybind to another key in the Controls screen ("Reset" and "Reset Keys" included). SetKey changes the key without a KeyChange coming back; if the keybind is held, a KeyRelease is sent first.
- A server keybind on the same key as another keybind works like two vanilla keybinds on one key: both trigger, and the Controls screen shows the conflict once either isn't on its default.
- **Active:** an inactive keybind never goes down (no KeyPress) and leaves its key to vanilla. Turning one off while it's held releases it (KeyRelease is sent).
- **Wins:** while an active keybind that wins is on a key, vanilla keybinds on that key don't react to it (Q drops nothing, the left mouse button doesn't attack or mine, F1 doesn't hide the HUD); other server keybinds on it still do. It follows the keybind's key when the player rebinds it, and vanilla keybinds held on that key are let go when it starts winning. In screens (inventory, chat, ...) keys work as usual.

## Feature: entity models (`skaffy:entity_models`, version 1)

Custom models for any entity: Bedrock geometry (Blockbench's "Bedrock Entity" export), Blockbench projects (`.bbmodel`) and glTF models (`.gltf`, `.glb`, from Blender, Sketchfab, ...). The same definitions are used by animation entities (the animations feature). Players only get one through a look of the player looks feature; SetEntityModel on a player does nothing. Only clients with the mod see them; hitboxes stay the server's. All packets go from server to client.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | DefineModels | List\<Model definition\> (registration only; numbers continue across packets, the first model is 1; at most 4096) |
| 1 | SetEntityModel | VarInt entity id, VarInt model (0 = vanilla look) |
| 2 | PlayAnimation | VarInt entity id, VarInt animation (1 = the model's first), Byte mode, Float speed, Float start, Float fade in |
| 3 | StopAnimation | VarInt entity id, VarInt animation (0 = all played ones), Float fade out |
| 4 | SetVariables | VarInt entity id, List\<Variable\> (at most 256) |

- **Model definition:**
  - String(64) name
  - String(64) geometry asset id: Bedrock geometry, a Blockbench project or a glTF model (told apart by their content)
  - Boolean has baby geometry, [String(64) asset id]
  - Boolean has texture, [String(64) asset id (PNG)]: required for Bedrock geometry, absent for the others (they bring their own)
  - Boolean has glowing texture, [String(64) asset id] (Bedrock geometry only)
  - Boolean translucent
  - Float scale (above 0)
  - List\<Animation\> (at most 256): String(64) asset id (a Bedrock `.animation.json`, a Blockbench project or a glTF model, the model's own file or another), String(128) animation name in that file, Boolean replaces vanilla
  - VarInt idle, walk, attack, hurt, death: automatic animations as numbers in the list (0 = none)
- **Mode:** 0 what the animation file says, 1 once, 2 loop, 3 hold (play once and stay on the last frame).
- **Start:** how far into the animation to begin, in animation seconds (so players who see an entity later join in).
- **Fade in / fade out:** seconds the animation blends in or out over (0 to 600; 0 = right away).
- **Variable:** String(64) name (`a-z 0-9 _`, not starting with a digit), Float value.

### Rules

- **Bedrock geometry:** the first geometry in the file, both the `minecraft:geometry` format and the older `geometry.<name>` one. Box UV and per-face UV (faces not listed aren't drawn), inflate, mirror, rotated bones and rotated cubes.
- **Blockbench projects** (any format version; textures must be saved inside the project): groups become bones, with their cubes (rotated at any angle, box UV or per-face UV with face rotation, inflate, mirror), meshes, locators and null objects. Each texture is a material: its render mode "emissive" or "additive" glows, animated textures play (frame time, frame order, interpolation), and a texture group marked as a material adds its normal and MER (metallic, emissive, roughness) maps. Converted exactly like Blockbench's Bedrock export, so a project and its export look the same. Armatures aren't supported (export those as glTF).
- **glTF models** (2.0, `.gltf` with its buffers and images, or one `.glb`): every node is a bone named after it (camera nodes are also locators), meshes keep their materials (base color, alpha mode, double-sided, emissive, normal and metallic-roughness maps, `KHR_materials_unlit`, `KHR_materials_emissive_strength`, `KHR_materials_pbrSpecularGlossiness`, `KHR_texture_transform`), skins bend their meshes and morph targets blend. Files a `.gltf` refers to are assets named like the file name in its URI (or embedded as data URIs). Units: 1 meter is 1 block, y up, the model's front towards +z, its origin on the ground.
- **Mobs** (everything vanilla draws with its mob renderer):
  - The model replaces the mob's model, and babies use the baby geometry or the model at half size.
  - Bones with the vanilla model's part names, nested the same way, keep vanilla's animations. Vanilla parts the geometry lacks are added empty.
  - Kept layers: held items, armor, head items and elytra, which attach to those bones. Dropped: layers that redraw vanilla's shape with another texture (eyes, wool, outer layers, ...).
  - Hurt flash, death rotation, glowing outline and name tags work as in vanilla.
- **Other entities** show the model at their position, turned by their yaw, moved only by custom animations.
- Custom models are drawn even when the entity is invisible.
- **Glowing texture:** drawn fully bright on top, like vanilla's spider eyes.
- **Bedrock animations:** `loop` (true, false, `hold_on_last_frame`), `animation_length` (or the last keyframe), `override_previous_animation`. Bone keyframes for `rotation` (degrees), `position` (pixels) and `scale`, with `linear`, `catmullrom` and `step` transitions and `pre`/`post` values. `sound_effects` play vanilla sounds (`minecraft:entity.zombie.ambient`); `particle_effects` spawn the server's particles (by name) or vanilla ones written like `/particle` takes them, at a locator. `timeline` keyframes are for the server (markers) and do nothing on the client.
- **Blockbench animations** work the same, plus `bezier` keyframes with their handles. **glTF animations** move translation, rotation, scale and morph weights, with `LINEAR`, `STEP` and `CUBICSPLINE`; they loop unless played otherwise.
- **Keyframe values** are numbers or Molang: numbers, `+ - * /`, comparisons, `&& || !`, `? :`, `math.*` (angles in degrees), `query.anim_time`, `query.life_time`, `query.ground_speed`, `query.head_x_rotation`, `query.head_y_rotation`, `query.modified_distance_moved`, and `variable.<name>` (or `v.<name>`) set by SetVariables. Anything else is 0.
- **Order each frame:** vanilla's animation, then the automatic idle/walk loop, then the automatic attack/hurt/death animation, then played animations in the order they started. Bedrock and Blockbench animations add to the pose; one that replaces vanilla (or overrides previous animations) first resets the bones it moves. glTF animations set the pose: several of them are averaged by their weights, so a crossfade between two doesn't sag towards the rest pose. A fading animation counts with its weight (0 to 1).
- **Automatic animations:**
  - Walk plays while the entity moves, idle while it doesn't.
  - Attack plays when a mob swings, hurt when it's hurt, death when it dies (held on the last frame).
- **Remembering:** the client remembers an entity's model from SetEntityModel until the entity is removed, even if it arrives before the entity, and its variables until then too (they're 0 until set). Playing an animation that's already playing restarts it. Packets for unknown entities, models or animations are ignored.
- **Loading:** a model is read the first time something shows it (in the background) and forgotten about a minute after the last thing showing it is gone.
- **Players:** PlayAnimation, StopAnimation and SetVariables work on a player whose look is a custom model (send the look first).

## Feature: fov (`skaffy:fov`, version 1)

A server FOV on top of the player's own, and the player's own FOV settings.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | SetFov | FOV value, Options |
| 1 | AnimateFov | Boolean has start, [FOV value start], FOV value end, VarInt duration (ms, 0 to 600000), Byte easing, Options |
| 2 | ResetFov | VarInt duration (ms, 0 = right away), Byte easing |
| 3 | SetSettings | Boolean has FOV, [VarInt FOV setting, 30 to 110], Boolean has effect scale, [Float FOV Effects, 0 to 1] |
| 4 | QueryFov | VarInt request id |

- **FOV value:** Boolean multiplier, Float value (above 0): degrees, or a multiplier of the player's FOV setting.
- **Options:** Boolean vanilla effects, Boolean zoom hands, Boolean scale sensitivity.
- **Easing:** 0 linear, then sine, quad, cubic, quart, quint, expo, circ, back, elastic, bounce (in this order), each as in, out, in-out: 1 ease-in-sine, 2 ease-out-sine, 3 ease-in-out-sine, 4 ease-in-quad, … 30 ease-in-out-bounce. The formulas are the ones on easings.net.

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | FovInfo | VarInt request id, VarInt FOV setting, Float FOV Effects, Float FOV on screen (degrees), Boolean server FOV active |

### Rules

- The server FOV replaces the player's FOV setting as the base.
- **Vanilla effects** on: sprinting, speed, flying, bows, spyglasses, water and dying still change it on top (scaled by the player's FOV Effects slider). Off: the FOV on screen is exactly the server's.
- The FOV on screen is kept between 1 and 170 degrees.
- **Animations** run every frame by real time. Without a start, they begin at the current base FOV (so animations can follow each other). They stay at the end value afterwards.
- **ResetFov** animates back to the player's own FOV, then the server FOV is gone. Without a server FOV it does nothing.
- **Zoom hands:** the hand and held items (drawn at vanilla's fixed 70°) zoom by the same factor as the world.
- **Scale sensitivity:** mouse movement is multiplied by server FOV ÷ FOV setting while that's below 1.
- **SetSettings** changes and saves the player's options like the options screen does; it's their new normal and can't be reset.
- A server FOV lasts through death, respawning and dimension changes, and ends with the session.

## Feature: name tags (`skaffy:name_tags`, version 1)

Custom name tags for any entity, players included: up to 5 lines of text, sprites and player heads, each piece with its own look, on a configurable background. Only clients with the mod see them. All packets go from server to client.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | DefineSprites | List\<String(64) asset id\> (registration only; numbers continue across packets, the first sprite is 1; at most 4096 in total) |
| 1 | SetNameTag | VarInt entity id, Name tag |
| 2 | SetLine | VarInt entity id, Byte line (0 = top, 0 to 4), Line |
| 3 | RemoveNameTag | VarInt entity id |

- **Name tag:**
  - List\<Line\> lines (at most 5)
  - Byte render mode: 0 default, 1 block, 2 always
  - Byte sneak mode: 0 default, 1 hide, 2 soft hide, 3 show
  - Byte alignment: 0 center, 1 left, 2 right
  - Background
  - Float scale (above 0, at most 64)
  - Float offset x, y, z (blocks, along the world's axes)
  - Float max distance (blocks; 0 = vanilla's)
  - Byte line gap (pixels between lines' boxes, 0 to 64)
  - Boolean full bright, Boolean show when invisible, Boolean show to self
- **Line:** List\<Object\> (at most 200). Text characters (code points) count one each, sprites and heads one each; a line has at most 200.
- **Object:** Byte kind, then by kind, then Style:
  - 0 text: String(400), 1 to 200 characters
  - 1 sprite: VarInt sprite number (numbers that don't exist show the missing texture)
  - 2 vanilla sprite: String(128) texture, `[namespace:]path` of `a-z 0-9 _ . - /` (`minecraft:block/diamond_ore` is `textures/block/diamond_ore.png`)
  - 3 head: Boolean has UUID, [UUID], Boolean has name, [String(16)], Boolean has skin, [String(4096) `textures` property value, Boolean has signature, [String(4096)]], Boolean hat. At least one of UUID, name and skin.
- **Style:**
  - List\<Int `0xRRGGBB`\> colors (at most 16): none is white, one is a plain color, more are a gradient
  - Byte transparency (0 solid to 100 invisible)
  - Byte decorations: 1 bold, 2 italic, 4 underlined, 8 strikethrough, 16 obfuscated
  - Byte font: 0 default, 1 uniform, 2 alt, 3 illageralt
  - Boolean shadow, [Boolean has color, [Int `0xRRGGBB`], Byte shadow transparency (0 to 100)]
- **Background:** Boolean has color, [Int `0xRRGGBB`], Boolean has transparency, [Byte 0 to 100], Byte padding x, Byte padding y (0 to 64), Byte style (0 box, 1 per line), Boolean shadow, [Byte darkness (0 to 100), Byte offset x, Byte offset y (signed, −64 to 64)].

### Rules

- **Replacing vanilla:** an entity with a custom tag never shows vanilla's name, team prefix/suffix or the score below the name, and team name tag visibility doesn't apply. A tag with no lines shows nothing at all. RemoveNameTag brings vanilla's back.
- **Remembering:** the client remembers an entity's tag from SetNameTag until the entity is removed, even if it arrives before the entity. SetLine replaces one line of the tag the client has; it's ignored if there's no tag or no such line.
- **Pixels:** sizes are name tag pixels like vanilla's (a character is 8 tall, 40 pixels are a block at scale 1). The bottom line's text sits where vanilla's name does (the entity's name tag attachment point plus half a block); more lines stack upward, one line every `8 + 2 × padding y + line gap` pixels, so text doesn't move between the two background styles. The tag always faces the camera, and scale grows it from that point.
- **Text** is drawn with the chosen vanilla font, character by character; `§` codes and line breaks mean nothing. Bold, italic, underlined, strikethrough and obfuscated look like vanilla's.
- **Colors:** a gradient's colors are spread evenly from the first to the last character, blended in RGB. Transparency 0 to 100 is alpha 255 to 0; vanilla's text shader drops anything under about 10% opacity.
- **Shadows** are drawn one pixel right and down, like vanilla's text shadow. Without a color they are the object's color at a quarter brightness. Their opacity is the object's times the shadow's own.
- **Sprites** are one line (8 pixels) tall and as wide as their proportions say, 1 pixel apart from what follows, like a character. Colors tint them (white leaves them as they are); a gradient uses its first color. Sprite assets are PNGs, and an asset `<id>.mcmeta` animates them like block textures (frames, frame time, frame size; interpolation is ignored). Vanilla sprites are always read from Minecraft's built-in files, with their `.png.mcmeta`; unknown ones show the missing texture.
- **Heads** are the face of a skin (8×8), with the hat layer on top if asked. With a skin value nothing is looked up; otherwise the client finds the skin by UUID (players it knows first), then by name. The default skin shows until the real one is loaded.
- **Background:** without a color it's black; without a transparency it follows the player's "Text Background Opacity" setting, like vanilla. It reaches `padding` pixels past the text on each side (vanilla's look is padding 1). A box goes around every line that has objects; per line, each line gets its own box and empty lines get none.
- **Background shadow:** the whole tag (backgrounds and objects, as they look) is drawn once more first, behind it, moved by the offset (pixels right and down on screen), with its colors multiplied by `1 − darkness / 100` and the same transparency.
- **Render modes:**
  - Default: like vanilla. The tag is drawn normally, and once more through blocks with its text at vanilla's through-wall opacity (half at the default setting). The normal part has no background; the through-wall part has everything.
  - Block: drawn like a block. Hidden behind blocks, seen through glass and other see-through blocks.
  - Always: drawn after everything else in the world, so nothing in the world covers it. Tags closer to the camera are drawn over farther ones.
- **Sneak modes** (while the entity sneaks): default is vanilla's sneaking look (like block mode, text at the through-wall opacity, lit only by the world, and only within 32 blocks); hide shows nothing; soft hide draws it like block mode; show changes nothing.
- **Light:** like vanilla, tags are lit by the world but never darker than light level 2 (vanilla's sneaking look uses the world's light as is). Full bright is always fully lit.
- **When it shows:** within the max distance (vanilla's: 64 blocks, or a living entity's name tag distance attribute). Not while the HUD is hidden (F1). Not on the entity the player views the world through (themselves in third person) unless show to self is on. Not while the entity is invisible to the player unless show when invisible is on. Riders, the crosshair and teams don't matter.
- **Sprites are fixed per session:** DefineSprites is only accepted during registration, since the client loads them before play.

## Feature: gui (`skaffy:gui`, version 1)

GUIs written as .sfy files: a Java-like language the client compiles and runs (SFY.md describes it). The server sends files as UTF-8 text and never needs to read them, like a web server sending a website. The client keeps them in memory only, never on disk. Files are known by their class name: package and class joined by dots, like `shop.Shop` (Java identifiers, at least one package part, at most 128 characters). The file's `package` line and class name must match it. A GUI file must import `skaffy.gui` (`import skaffy.gui.*;` or single names); files importing `skaffy.shader` are shader files and don't compile as GUIs.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | FileChunk | String(128) class name, VarInt total size (bytes, at most 2 MiB), VarInt offset, Bytes(262144) data |
| 1 | RemoveFile | String(128) class name |
| 2 | DefineFonts | List\<Font\> (at most 64; registration only) |
| 3 | Open | String(128) class name, String(64) method, List\<Value\> arguments |
| 4 | Close | – |
| 5 | Call | VarInt call id (0: no answer wanted), String(128) class name, String(64) method, List\<Value\> arguments |
| 6 | ReplaceMethod | String(128) class name, String(131072) source of one method |
| 7 | OpenLink | String(2048) URL |
| 8 | ShowHud | String(128) class name, String(64) method, List\<Value\> arguments, Int order |
| 9 | HideHud | String(128) class name (empty: every layer) |
| 10 | SetHudPart | Byte part, Boolean visible, Float x, Float y, Float scale (above 0, at most 16) |

- **Font:** String(32) name (`a-z 0-9 _`), String(64) asset id of a TrueType (`.ttf`) file, Float size (above 0, at most 256), Float oversample (above 0, at most 16), Float shift x, Float shift y.

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | Opened | String(128) class name, String(64) method |
| 1 | Closed | String(128) class name, Byte reason: 0 Esc, 1 server (Close), 2 code (`close()`), 3 replaced by another screen, 4 error |
| 2 | Message | String(128) class name, String(64) name, List\<Value\> values |
| 3 | CallResult | VarInt call id, Boolean ok, then [Value result] if ok, else [String(8192) error] |
| 4 | Error | String(128) class name, Byte kind: 0 compile, 1 runtime, 2 request, String(8192) message |
| 5 | Log | String(128) class name, Byte level: 0 info, 1 warn, 2 error, String(4096) message |
| 6 | HudShown | String(128) class name, String(64) method |
| 7 | HudHidden | String(128) class name, Byte reason (like Closed: 1 server, 2 code, 3 replaced, 4 error) |

- **Value:** Byte tag, then: 0 null, 1 false, 2 true, 3 Int, 4 Long, 5 Double, 6 String(32767), 7 List (VarInt count, Values), 8 Map (VarInt count, then key Value and value Value per entry). At most 32 levels deep and 100000 values per packet.

### Rules

- **Files:** a file is complete when its chunks (sent in order from offset 0, each continuing where the last ended) reach its total size; then it's compiled. Sending a class again replaces it: GUIs already open keep the version they started with. Files can be sent during registration (so the first open is instant) and any time in play. The client's limits: 2 MiB per file, 32 MiB of files together; a file over the total is ignored and reported as a compile error. A file that doesn't compile is reported (Error, compile) with every mistake as `File.sfy:line:column: message`, and can't be opened.
- **Opening:** Open runs a `public @Gui` method with those arguments on a new instance of the class (static fields are made once per compiled file). Anything open is replaced (a container is closed properly first). It can't open while the player is dead, the world is loading or there's no world; that's an Error (request) plus Closed (error). Unknown classes or methods and arguments that don't fit are Errors (request).
- **Arguments** are converted to the parameter types: numbers to int, long or double (whole numbers only for int and long), one-character strings to char, maps to records (by component name), strings to enum constants (by name), ints (ARGB) or `#RRGGBB`/`#AARRGGBB` strings to colors, numbers to lengths (pixels) or durations (milliseconds), `"50%"` to a length; lists and maps element by element. Return values and message values go the other way: records become maps of their components, enum constants their names, colors ARGB ints, durations milliseconds, lengths text like `50% - 20`, chars one-character strings. Anything else (elements, lambdas) can't be sent.
- **Screens:** Opened is sent whenever a `@Gui` method starts: from Open, a Call of a `@Gui` method, or code calling one (which empties the screen first, keeping the instance's fields).
- **Calls** run a public method of the open GUI; the class name must be the open one's. CallResult carries its return value (null for void) or why it failed (no such GUI or method, values that don't fit, or what the code threw, with its trace). Call id 0 gets no answer.
- **ReplaceMethod** gives one method of a class new code, for this player, until the file is sent again or the session ends. It must keep the method's name, parameter types and return type; mistakes are reported as compile errors. It applies from the method's next call (the open GUI's class too).
- **Closing:** Esc closes the GUI (Closed, Esc) unless the file set `onEscape`; three Esc presses within a second always close. Another screen opening over it (death screen, a container, loading, another Open) sends Closed (replaced). Nothing is sent when the session ends.
- **Messages** come from `send(name, values...)` in a file: names are 1 to 64 characters of letters, digits and `_`, starting with a letter or `_`. A message that would be larger than a client can send (32767 bytes) throws in the file instead.
- **Errors while running** (Error, runtime) have the message and a trace like `at shop.Shop.buy(Shop.sfy:12)`. Clients send at most 10 errors every 10 seconds per GUI and log the rest locally. Everything reported is also written to the client's log.
- **Logs** come from `LOGGER.info/warn/error(...)` in a file, at most 40 per second.
- **OpenLink** always shows vanilla's "open this link?" prompt, whatever the player's chat link settings are; afterwards the screen that was open (a GUI too) comes back. Only `http` and `https` links are opened.
- **Fonts** are added to the hidden pack when the session loads, so they only exist for sessions that got DefineFonts during registration. Files use them by name.
- **Assets** used by files (images, fonts) are ordinary assets; images are read from the client's cache when first shown.
- **HUD layers:** ShowHud runs a `public @Hud` method on a new instance of the class as a HUD layer: drawn over the game and over vanilla's HUD (lower order first), with no screen, so it never takes the mouse or keys and the player keeps playing. One layer per class: showing a class again replaces its layer (HudHidden, replaced). HudShown is sent whenever a `@Hud` method starts (ShowHud, a Call of one, or code calling one). Calls, ReplaceMethod and messages reach layers like screens (a Call goes to the open screen when it's that class, else to its layer). F1 hides layers like vanilla's HUD. Layers last through death, respawn and world changes, and end with the session (nothing is sent then). It can't show without a world (Error, request, plus HudHidden, error).
- **HUD parts:** SetHudPart shows, hides, moves (x, y in GUI pixels) or scales a part of vanilla's HUD around where it usually is, until the session ends. Parts: 0 crosshair, 1 hotbar, 2 health (hearts), 3 armor, 4 food, 5 air, 6 experience (bar and level, and the locator and jump bars in its place), 7 mount health, 8 held item name, 9 effects, 10 boss bars, 11 scoreboard (sidebar), 12 action bar, 13 title, 14 chat, 15 player list (Tab), 16 subtitles, 17 vignette, 18 helmet overlays (pumpkin, spyglass, ...), 19 sleep fade. Visible with x 0, y 0 and scale 1 is vanilla's look again.
- **Timing:** apart from files and fonts during registration, send gui packets only to clients that enabled `gui` and are ready.

## Feature: perspective (`skaffy:perspective`, version 1)

The player's perspective: vanilla's three (what F5 cycles through) and custom cameras around the player, switched right away or animated, and which of vanilla's F5 may switch to.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | SetPerspective | Byte perspective (0 to 2), VarInt duration (ms, 0 to 600000; 0 = right away), Byte easing |
| 1 | SetCamera | Camera, VarInt duration (ms, 0 to 600000), Byte easing |
| 2 | SetAllowed | Byte mask: bit 0 first person, bit 1 third person back, bit 2 third person front (1 to 7) |
| 3 | QueryPerspective | VarInt request id |

- **Perspective:** 0 first person, 1 third person back, 2 third person front, 3 custom (only in answers).
- **Camera:** Float x, Float y, Float z (blocks from the anchor, each within ±4096), Byte turn (0 none, 1 with the player's yaw, 2 with their yaw and pitch), Look, Float roll (degrees; positive tilts the camera clockwise), Boolean pull in front of walls, Anchor.
- **Look:** Byte kind, then: 0 angles (Float yaw, Float pitch; degrees like Minecraft's: yaw 0 faces south, pitch 90 looks down), 1 point (Float x, y, z from the anchor), 2 the player's eyes, 3 an entity's eyes (VarInt entity id), 4 where the player looks, 5 where the anchor faces (a bone's facing; the player's view for the player anchor).
- **Anchor:** Byte kind, then: 0 the player (their feet), 1 a bone (String(128) animation entity id, String(64) bone or locator name; empty is the entity's origin).
- **Easing:** the table of the fov feature (0 linear, 1 ease-in-sine, ... 30 ease-in-out-bounce).

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | PerspectiveInfo | VarInt request id, Byte perspective (during an animation: where it's going) |
| 1 | PerspectiveChanged | Byte from, Byte to: the player pressed F5 (never sent for the server's own changes) |

### Rules

- **Offsets** count from the player's interpolated position, every frame. Turning with the yaw turns the offset (and a point or angles to look at) by the player's yaw: it's written as if they faced south, so (0, 2, −4) is behind them wherever they look. Turning with yaw and pitch also turns it by their pitch around their eyes, the way their view turns: looking down lifts a camera behind them over their head (like Fortnite's over-the-shoulder camera); fixed angles get the player's pitch added (kept within ±90).
- **A custom camera** draws the player like third person: no hand, no crosshair. The mouse still turns the player. Without "pull in front of walls" the camera goes exactly where it's set, inside blocks too; with it, blocks between the player's eyes and the camera pull it closer like vanilla's third person. An entity that's out of sight leaves the camera looking where it last did.
- **Animations** start from wherever the camera is (any perspective, custom or not, or mid-animation), move relative to the player and run every frame by real time. Rotations take the short way round.
- **F5** only switches to allowed perspectives, in vanilla's order. During a custom camera it does nothing; during an animation to a vanilla perspective it ends the animation and goes on from there. SetAllowed moves a player who's in a perspective that isn't allowed to the next one that is (no PerspectiveChanged). The server may still set any perspective.
- **Bone anchors** (cutscenes animated in Blockbench): the camera rides on an animation entity's bone or locator every frame; its offset is in the bone's space (z where the bone faces, y up, x to its left) and turns with it, and turning with the player doesn't apply. While the entity is missing (not spawned yet, another dimension, model still loading) the camera waits where it was.
- **Lasting:** everything lasts through death, respawning and world changes. When the session ends, everything is allowed again and a custom camera goes back to the vanilla perspective it came from; a vanilla perspective stays.

## Feature: brightness (`skaffy:brightness`, version 1)

A server brightness used instead of the player's Brightness setting, and the setting itself. Brightness is in percent like the Video Settings slider: 0 Moody, 50 the default, 100 Bright.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | SetBrightness | Float brightness (any finite value), Options |
| 1 | AnimateBrightness | Boolean has start, [Float start], Float end, VarInt duration (ms, 0 to 600000), Byte easing, Options |
| 2 | ResetBrightness | VarInt duration (ms, 0 = right away), Byte easing |
| 3 | SetSetting | Float brightness (0 to 100) |
| 4 | QueryBrightness | VarInt request id |

- **Options:** Boolean Darkness effect, Boolean night vision.
- **Easing:** the table of the fov feature.

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | BrightnessInfo | VarInt request id, Float setting (0 to 100), Float brightness the world is lit with now (the server's or the setting, after the Darkness effect), Boolean server brightness active |

### Rules

- **Any value:** the lightmap blends between the plain light curve and vanilla's brightened one by the brightness. Above 100 it overshoots towards full bright (around 1000 almost everything is lit); below 0 it darkens past Moody.
- **Darkness effect** on: the Darkness effect (Wardens, Sculk Shriekers) still takes its share away, like vanilla. Off: it does nothing while the server brightness is set. **Night vision** on: Night Vision and Conduit Power's underwater vision still light the world; off: they don't.
- **Animations** run every frame by real time (the lightmap is made again every frame while they run). Without a start they begin at the current brightness. ResetBrightness animates back to the player's setting, then the server brightness is gone.
- **SetSetting** changes and saves the player's slider like the options screen does; it's their new normal and can't be reset.
- A server brightness lasts through death, respawning and dimension changes, and ends with the session.

## Feature: player looks (`skaffy:player_looks`, version 1)

How players are drawn on clients with the mod: the player model changed (skin, arms, cape, elytra, layers, hidden parts), any other entity type, or a custom model of the entity models feature, and the hitbox there. Sounds, movement and attacks stay the server's. All packets go from server to client.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | SetLook | VarInt entity id (a player), Look |
| 1 | RemoveLook | VarInt entity id |

- **Look:** Byte form, the form's fields, Float scale (0 to 256), Boolean has hitbox, [Float width, Float height (0 to 256 each), Boolean has eye height, [Float eye height (0 to the height)]], Boolean own hitbox, Boolean show to self.
- **Form 0, player model:** Skin, Byte arms (0 from the skin, 1 wide, 2 slim), Cape, Boolean has elytra, [String(64) elytra asset id], Byte forced layers, Byte shown layers (a part of the forced ones), Byte hidden parts.
- **Form 1, entity:** String(128) entity type id (`minecraft:zombie`), String(16384) SNBT like `/summon` takes (empty for none; in 26.3 a falling block is `{BlockState:"minecraft:stone"}`).
- **Form 2, custom model:** VarInt model (the entity models feature's number, from 1).
- **Skin:** Byte 0 the player's own, 1 an asset (String(64) id, a 64×64 PNG), 2 a profile (Profile).
- **Cape:** Byte 0 the player's own, 1 none, 2 an asset (String(64) id, a 64×32 PNG), 3 a profile's cape (Profile).
- **Profile:** Boolean has UUID, [UUID], Boolean has name, [String(16) name], Boolean has skin value, [String(4096) value (the base64 `textures` property), Boolean has signature, [String(4096) signature]].
- **Layers:** 1 hat, 2 jacket, 4 left sleeve, 8 right sleeve, 16 left pants, 32 right pants. **Parts:** 1 head, 2 body, 4 right arm, 8 left arm, 16 right leg, 32 left leg.

### Rules

- **Remembering:** the client keeps a player's look from SetLook until RemoveLook or until the player leaves its world (send it again when they're seen again, and to a player after they respawn or change worlds). A look may arrive before the player does.
- **Player model:** a profile skin is found like name tag heads (online players first, then Mojang; a skin value is used as it is). "From the skin" arms are a profile's own type, wide for an asset, the player's own for their own skin. Asset and profile capes show even if the player turned their cape off. The elytra texture shows on worn elytra; without an elytra asset it follows vanilla: the cape's texture while a cape shows, else a profile's elytra or the default. Forced layers ignore the player's Skin Customization; hidden parts aren't drawn (with their layers), hidden arms not in first person either. The first-person hand shows the look's skin and arm type.
- **Entity:** an entity of that type is made on the client (never added to the world) from the SNBT, and copies the player every frame: position, body and head rotation, walking, sneaking/swimming/crawling/elytra/sleeping pose, arm swings, hurt flash, death, held items and armor (where that entity shows them), fire, invisibility and glowing. The player's name tags (vanilla's and the name tags feature's) and glow color stay. The first-person hand stays the player's. Unknown types or SNBT that doesn't parse leave the player drawn like vanilla; fields Minecraft can't decode are left out. Both are logged.
- **Custom model:** drawn with a copy of the player renderer, like custom mob models: bones named like the player model's parts (`head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`) move with vanilla's player animations; held items, armor, head items and elytra attach to them. The entity models feature's PlayAnimation and StopAnimation work on the player too, and its automatic animations play. The first-person hand stays the player's.
- **Scale** grows the drawn look (around the feet; the player's scale attribute still applies to the player model and custom models). Previews (the inventory, GUI entity views) draw the look at the preview's size.
- **Hitbox:** without one it's the look's size: the player's own times the scale (times the custom model's scale), or the entity's size for its pose times the scale. A custom hitbox is that size, whatever the pose. Sleeping players keep vanilla's. It's used for aiming, hitting, F3+B, pushing and the name tag's height. On the player's own client it only counts with "own hitbox": then it's also their collisions and camera height (the server keeps its own hitbox and may pull them back).
- **Show to self:** off means the player sees themselves like vanilla (third person, inventory, hand); others still see the look.

## Feature: shaders (`skaffy:shaders`, version 1)

Shaders written as .sfy files (SHADERS.md describes the language). Like GUI files, the server sends them as UTF-8 text and never reads them; the client compiles them (into GLSL that Minecraft builds for Vulkan or OpenGL) and keeps them in memory only. Files are known by their class name (package and class joined by dots, like `effects.Dream`, at most 128 characters). A file must import `skaffy.shader`, and may import other shader files (helper classes) by class name or package.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | FileChunk | String(128) class name, VarInt total size (bytes, at most 512 KiB), VarInt offset, Bytes(262144) data |
| 1 | RemoveFile | String(128) class name |
| 2 | Enable | String(128) class name, Int order, Uniforms |
| 3 | Disable | String(128) class name |
| 4 | DisableAll | – |
| 5 | SetUniforms | String(128) class name, Uniforms, VarInt duration (ms, 0 to 600000), Byte easing |
| 6 | SetVista | Byte state: 0 off, 1 on, 2 the player's own setting; Boolean locked |
| 7 | SetShaderDistance | VarInt chunks (2 to 32, or 0 for the player's own setting), Boolean locked |

- **Uniforms:** VarInt count (at most 256), then per uniform String(64) name (a Java identifier, or `Class.field` for a @Uniform of a helper class the file imports) and a Value as in `skaffy:gui`. Numbers for float/int/bool uniforms, booleans for bools, a List of numbers for vectors and matrices (matrices column by column), or a String `#RRGGBB` / `#AARRGGBB` (or an Int ARGB) for `vec3`/`vec4`.
- **Easing:** the 31 easings of easings.net, in the order `LINEAR`, `EASE_IN_SINE`, `EASE_OUT_SINE`, `EASE_IN_OUT_SINE`, then `IN`/`OUT`/`IN_OUT` for `QUAD`, `CUBIC`, `QUART`, `QUINT`, `EXPO`, `CIRC`, `BACK`, `ELASTIC`, `BOUNCE` (as in `skaffy:fov`).

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | Error | String(128) class name, Byte kind: 0 compile, 1 runtime, 2 request, String(8192) message |
| 1 | State | String(128) class name, Boolean running |
| 2 | VistaState | Boolean on, VarInt Shader Distance (chunks, 2 to 32), Boolean by player |

### Rules

- **Files:** chunks are sent in order from offset 0; the file is compiled when its bytes reach the total size. Sending a class again replaces it: an enabled effect switches to the new version and keeps uniform values whose name and size still fit. Files can be sent during registration and any time in play. Client limits: 512 KiB per file, 8 MiB together (a file over the total is ignored and reported as a compile error). A file that doesn't compile is reported (Error, compile) with every mistake as `File.sfy:line:column: message` (mistakes in an imported class name that class's file).
- **Imports:** a file that imports a class the client doesn't have yet isn't reported right away: it compiles again when that class arrives, and its mistakes are reported if it's enabled before then. Sending, replacing or removing a class compiles the files that import it (directly or through other classes) again.
- **Enable** turns an effect on, or changes the order and values of one that's on. The client builds it in the background and sends State (running) once it draws; until then nothing changes on screen. Enabling a file that isn't compiled (not sent, or broken) is reported (Error, request) and remembered: it starts when a working version arrives.
- **Order:** effects run lower order first (ties by class name). Each world program (terrain, block, entity, item, particle, sky, clouds) uses the hooks of the highest enabled effect that has hooks for it. Frame-wide settings (the sun's path, the shadow map, colored light types) come from the highest running effect that has them; hdr and TAA are on when any running effect wants them, and the @Output fields of all enabled effects (at most 7 together, lower order first) are drawn next to the level's color.
- **Frame layout:** when enabling or disabling changes hdr or the @Output fields, every effect is built again and they all pause until each is ready (State is sent for the pause and the restart).
- **SetUniforms** sets values right away (duration 0) or animates them from their current values at the client's frame rate; ints round, bools switch at the end. Unknown uniforms and values that don't fit are reported (Error, request); the other values are still set.
- **Runtime errors:** if building or drawing an effect fails, the client turns it off, sends Error (runtime) and State (not running).
- **Block materials:** while an effect with terrain hooks runs, the client rebuilds chunk meshes once (when it starts and when it stops).
- Effects last through death, respawn and world changes, and end with the session. Disable and DisableAll send State (not running) for effects that were running.
- **Timing:** apart from files during registration, send shaders packets only to clients that enabled `shaders` and are ready.

### Vista and the Shader Distance

Vista (`skaffy.vista.Vista`) is a shader built into the mod. Players turn it on in Video Settings (in the row below Graphics API, next to the Shader Distance slider); it runs in single player and on servers without this protocol too, below every server effect (as if its order were lower than any other).

- **Reserved classes:** classes in `skaffy.` packages belong to the mod. FileChunk, RemoveFile, Enable and Disable for them are reported (Error, request) and ignored; DisableAll doesn't touch Vista. SetUniforms on `skaffy.vista.Vista` works like for any file (names like `wind` or `Clouds.shadows`); values the server set this session are kept while Vista is off and applied when it starts, and Vista's uniforms go back to its own values when the session ends.
- **SetVista** turns Vista on or off (or back to the player's own setting) for this session; nothing is saved on the client. **Locked** greys out the button in Video Settings until a SetVista unlocks it or the session ends. Without a lock the player can still click it: their click is saved as their own setting and replaces the server's.
- **SetShaderDistance** does the same for the Shader Distance slider. The Shader Distance limits every effect: shadows (the file's shadowDistance) and the colored light box (the file's coloredLightRange) never reach further, and files can fade their own effects with the built-in `effectDistance`. It's never past the player's render distance.
- **VistaState** is sent once when the session is ready, after every SetVista and SetShaderDistance (by player false: the state now in effect), and whenever the player changes either in Video Settings (by player true). It says whether Vista is on (it may still be building: State for `skaffy.vista.Vista` says when it draws) and the Shader Distance setting.

## Feature: block shapes (`skaffy:block_shapes`, version 1)

Collision and outline boxes for single block positions. A client with the mod uses them instead of the shapes of the block that's there: the player (and every entity the client moves) bumps into and stands on the collision boxes, the crosshair targets and the block highlight shows the outline boxes, and the third-person camera stops at the collision boxes. The server keeps its own block there (a barrier, so players without the mod bump into the whole block). All packets go from server to client.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | SetShapes | String(128) dimension, VarInt count (at most 8192), then per position: Int x, Int y, Int z, Boxes collision, Boolean own outline, (if own outline) Boxes outline |
| 1 | RemoveShapes | String(128) dimension, VarInt count (at most 8192), then per position: Int x, Int y, Int z |
| 2 | ClearShapes | String(128) dimension (empty: every dimension) |

- **Boxes:** VarInt count (at most 16), then per box Float minX, minY, minZ, maxX, maxY, maxZ, all 0 to 1 inside the block. No collision boxes: nothing to bump into. Without an own outline the outline is the collision boxes; an own outline with no boxes means the position can't be targeted.
- **Dimension:** the world's id, like `minecraft:overworld`. Shapes of other dimensions are kept, but only the dimension the player is in uses its shapes.
- A position whose collision isn't one whole cube stops counting as a full block on the client: it doesn't suffocate, block the view or push players out of it (the way vanilla pushes players out of full blocks), so players can stand right next to thin shapes and walk up slopes made of boxes.
- SetShapes replaces a position's shapes; RemoveShapes gives it its block's own shapes back. A client keeps at most 262144 positions (all dimensions together).
- Shapes last until removed or the session ends.

## Feature: shapes (`skaffy:shapes`, version 1)

Lines, triangles, quads and boxes placed anywhere in a world, known by ids the server picks (at most 128 characters). All packets go from server to client.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | SetShape | String(128) id, Placement, Style, VarInt count (at most 16384), then the parts |
| 1 | MoveShape | String(128) id, Placement |
| 2 | RemoveShape | String(128) id |
| 3 | ClearShapes | String(128) prefix: removes every shape whose id starts with it (all for an empty one) |

- **Placement:** String(128) dimension (like `minecraft:overworld`), Double x, y, z (the shape's origin), Float qx, qy, qz, qw (rotation, a quaternion; normalized by the client, zero is no rotation), Float sx, sy, sz (scale). Parts are in the shape's own space: scaled, then turned, then moved to the origin.
- **Style:** Byte mode (0 overlay, 1 world), Boolean see-through, String(256) texture, Byte render (0 solid, 1 cutout, 2 translucent), Boolean emissive, Boolean double-sided, Float view distance (blocks; 0 is the render distance).
- **Parts:** a Byte type, then
  - 0 Line: Float ax, ay, az, bx, by, bz, Int ARGB, Float width (screen pixels);
  - 1 Triangle: three Vertex, Int ARGB;
  - 2 Quad: four Vertex, Int ARGB;
  - 3 Box: Float minX, minY, minZ, maxX, maxY, maxZ, Int fill ARGB, Int outline ARGB, Float outline width, Float uv scale.
- **Vertex:** Float x, y, z, u, v. Texture positions: 1 is the whole texture, more repeats it.

### Rules

- **Overlay** shapes are flat colors (ARGB, transparency included), drawn after everything see-through in the level (water, clouds, weather), tested against depth, so blocks hide them; see-through ones are drawn over everything in the level. Lines, triangle and quad faces (both sides) and box faces and edges are drawn.
- **World** shapes are textured and lit like entities: the light at the middle of each face (just in front of it), or full bright when emissive. Colors tint the texture. They're drawn with the entities, so world shaders shade them and they cast shadows; translucent ones are sorted with other see-through things. Lines and box outlines are left out. Triangles and quads show their front, where the corners turn counterclockwise, or both sides when double-sided; boxes show their outside.
- **Textures** (world shapes): an asset id, or a vanilla texture as `namespace:path` (`minecraft:block/oak_planks` is `textures/block/oak_planks.png`, read from Minecraft's own files, never resource packs). Animated textures show their first frame. Missing ones show vanilla's missing texture.
- **Box texture positions:** with a uv scale above 0 the texture repeats every that many units of the shape's space, measured from its origin (so boxes side by side line up like blocks); with 0 it's stretched over each face once.
- A shape is drawn only in its dimension, and only within its view distance (measured to the closest it can reach).
- SetShape replaces a shape with the same id. A client keeps at most 65536 shapes. Shapes last until removed or the session ends.

## Feature: animations (`skaffy:animations`, version 1)

Animation entities: models of the entity models feature (which must be enabled too) placed anywhere by the server, existing only on clients. The server has no entity for them; it names each with an id it picks (at most 128 characters) and tells the client what to play, where to go and what to follow, and the client moves and animates them by itself, every frame.

### Server → client

| Id | Packet | Fields |
|---|---|---|
| 0 | Spawn | String(128) id, VarInt model (from DefineModels, 1 = first), Placement, Settings |
| 1 | Remove | String(128) id |
| 2 | Clear | String(128) prefix: removes every animation entity whose id starts with it (all for an empty one) |
| 3 | Move | String(128) id, Placement, VarInt duration (ms, 0 to 600000; 0 = right away), Byte easing |
| 4 | FollowPath | String(128) id, List\<Path point\> (1 to 4096), Boolean smooth, Boolean loop, Boolean face along, VarInt start (ms into the path) |
| 5 | Attach | String(128) id, Attachment |
| 6 | Play | String(128) id, VarInt animation (1 = the model's first), Byte mode, Float speed, Float start, Float fade in, Boolean remove when done |
| 7 | Stop | String(128) id, VarInt animation (0 = all), Float fade out |
| 8 | SetVariables | String(128) id, List\<Variable\> (at most 256) |
| 9 | SetBone | String(128) id, String(64) bone, Boolean hidden, Int tint ARGB, Float rotation x, y, z (degrees), Float position x, y, z (pixels), Float scale x, y, z, VarInt duration (ms), Byte easing |
| 10 | LookAt | String(128) id, String(64) bone, Target, Float max yaw (0 to 180), Float max pitch (0 to 90), Float speed (degrees per second; 0 = right away) |
| 11 | SetItem | String(128) id, String(64) slot, String(64) bone, Display, Float x, y, z (blocks), Float rotation x, y, z (degrees), Float scale |
| 12 | SetSettings | String(128) id, Settings |

- **Placement:** String(128) dimension (like `minecraft:overworld`), Double x, y, z, Float yaw, pitch (like Minecraft entities: yaw 0 faces south, positive pitch looks down), Float roll (degrees; positive leans it right as seen from behind), Float scale x, y, z.
- **Settings:** Float hitbox width, Float hitbox height (0 for none), Boolean full bright, Int glow color ARGB (0 for none), Float shadow radius (blocks, 0 for none), Float view distance (blocks, 0 for the render distance), Boolean show in first person.
- **Path point:** VarInt time (ms from the path's start, never going down), Double x, y, z, Float yaw, pitch, roll.
- **Attachment:** Byte kind, then: 0 none (it stays where it is now); 1 an entity (VarInt entity id, Float x, y, z offset in blocks from its feet, Float yaw, pitch, roll, Boolean turn with its body); 2 a bone of another animation entity (String(128) id, String(64) bone or locator, empty for its origin; Float x, y, z offset in blocks in the bone's space, Float yaw, pitch, roll).
- **Target:** Byte kind, then: 0 none (the bone turns back), 1 an entity's eyes (VarInt entity id), 2 the camera of the player who sees it, 3 a point (Double x, y, z).
- **Display:** Byte kind, then: 0 none (empties the slot), 1 an item (String(32767) item written like `/give` takes it, String(32) display context like `fixed`, `head`, `ground`, `thirdperson_righthand`), 2 a block (String(1024) block state written like `/setblock` takes it, drawn as a 1-block cube from its corner).
- **Mode, start, fades and Variable:** as in the entity models feature. **Easing:** the table of the fov feature.

### Client → server

| Id | Packet | Fields |
|---|---|---|
| 0 | Click | String(128) id, Byte button (0 attack, 1 use), Boolean off hand, Float x, y, z (where the hitbox was hit, blocks from the entity's position), Boolean sneaking |

### Rules

- **Kept per session:** a client keeps animation entities until they're removed or the session ends, at most 8192 (more are left out), each world showing the ones of its dimension. Spawn replaces an entity with the same id and starts it playing nothing. Packets for unknown ids are ignored.
- **Drawing:** like a mob with the model: lit by the light where each part is (or full bright), shaded by world shaders and casting their shadows, sorted with other see-through things. Big models are drawn while any part is within the view distance. The model loads in the background the first time it's needed; until then nothing is drawn.
- **Moving:** Move goes from where it is now (mid-move included) to the placement, eased; a dimension change jumps. Paths go through their points at their times, along straight lines or a smooth curve, from `start` ms in; looping ones start over after the last point (repeat the first point at the end to close a loop), others stay at the last. Face along turns it (yaw and pitch) the way it moves. Move, FollowPath and Attach each end the others, keeping the entity where it is at that moment.
- **Attached** entities follow every frame: an entity's interpolated feet plus the offset, turned with its body yaw (a mob's body, any other entity's yaw) when asked, the offset then written as if it faced south; or a bone of another animation entity, offset and turned in the bone's space. Things attached to the player are hidden in first person unless shown in first person. While the target is missing, the entity stays where it last was.
- **Animations** play like the entity models feature's (up to 16 at once, blending in the order they started); remove when done removes the entity on the client when a one-shot animation ends. Sound and particle keyframes play at the entity (particles at their locator). Variables are per entity.
- **Bones:** SetBone changes a bone (or a locator) on top of its animations, blending from its current setting over the duration: hidden hides it and everything on it, the tint multiplies its color, rotation, position and scale use Blockbench's axes (as Blockbench shows them). All zeros, white and scale 1 is no change. LookAt turns a bone toward a target every frame, at most that far from its animated pose, at that speed.
- **Items and blocks** sit on a bone or locator (empty: the entity's origin), offset, turned and scaled in its space; they follow it every frame.
- **Hitboxes** are boxes centered on the position (width × height, upwards). The crosshair can target them like entities; attacking or using one sends Click instead of vanilla's packet. Without a hitbox an animation entity can't be targeted and nothing collides with it.

## Client cache (informative)

Files are stored in `.minecraft/skaffy-api/<server>/<n>/<id>`: numbered folders with up to 500 files each. `<server>` is the host the player typed, lowercased, with `_<port>` added if the port isn't 25565. Nothing is looked up, so `mc.example.net` and `example.net` are separate. Servers never see paths, only ids.

## Errors

A violation of the core protocol (wrong packet for the current state, malformed data, broken limits) fails the session. The client sends Failed with a reason; the server can end a session at any time. Neither side kicks or crashes because of it.
