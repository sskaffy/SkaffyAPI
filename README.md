# Skaffy's API

Client sided APIs for servers. Skaffy's API is a Fabric mod for Minecraft 26.3 and a Paper plugin that work together: the plugin gives server developers an API, and players with the mod see and feel what the server adds, without resource packs and without any client setup. The server decides everything; the mod only shows it. Players without the mod join and play as usual, they just don't get the extras.

## What servers can do

- **Custom blocks**: real blocks registered while the player is on the server. The world keeps a normal block (a barrier by default) and the server owns all logic, sounds and drops.
- **Particles**: custom particles with their own textures, physics and curves for size, color, alpha and spin over their life.
- **Keybinds**: server keybinds in the Controls screen, in their own category.
- **Entity models**: Blockbench models (Bedrock export or `.bbmodel` projects) and glTF models (Blender, Sketchfab) on any entity, with animations that keep vanilla's where the bones match.
- **Animation entities**: animated models placed anywhere, which exist only on players' clients: smooth moves and paths, attached to entities or to other models' bones, layered animations with crossfades, bones you can hide, tint, move or turn towards a target, items and blocks on bones, clickable hitboxes, sound and particle keyframes, timeline markers the server reacts to, and cameras mounted on bones for cutscenes. Sketchfab downloads are imported as they are.
- **GUIs**: screens and HUD layers written as `.sfy` files, a small Java-like language the client compiles and runs ([SFY.md](SFY.md)).
- **Shaders**: `.sfy` shaders with screen passes and world hooks, built for Vulkan and OpenGL by Minecraft's own shader compiler ([SHADERS.md](SHADERS.md)). Includes Vista, a built-in shader players can turn on in Video Settings.
- **Name tags**: up to 5 lines of text, sprites and player heads on any entity.
- **Player looks**: change how players are drawn: their model, another entity type, or a custom model.
- **Perspective**: switch players between vanilla's perspectives or custom cameras, animated.
- **FOV and brightness**: a server FOV or brightness, set right away or animated, or the player's own settings changed.
- **Block shapes and shapes**: collision and outline boxes for single block positions, and lines, triangles, quads and boxes drawn anywhere in the world.

## Using it

Players install the mod (`skaffys-api-fabric-<version>.jar`) with Fabric Loader 0.19.5 or newer and Fabric API, on Minecraft 26.3 with Java 25.

Servers install the plugin (`skaffys-api-paper-<version>.jar`) on Paper 26.3 (Folia works too). `/skaffy-api` shows which players have the mod.

Plugins compile against `skaffys-api-paper-api-<version>.jar`, add `depend: [SkaffysAPI]` to their `plugin.yml` and start from `SkaffyAPI.get()`:

```java
SkaffyAPI api = SkaffyAPI.get();
api.getAssets().register(this, "robot.bbmodel", "models/robot.bbmodel");
CustomEntityModel robot = CustomEntityModel.builder("robot", "robot.bbmodel").animation("robot.bbmodel", "walk").build();
api.getEntityModels().register(robot);

AnimationEntity entity = api.getAnimations().spawn("robot", robot, location);
entity.play("walk");
```

Register assets and definitions in `onEnable`: they're sent to players while they join. Every API has Javadoc, and the test plugin (below) uses all of them.

Servers that don't run Paper can talk to the mod directly: [PROTOCOL.md](PROTOCOL.md) describes everything on the wire.

## Building

Needs a JDK 25.

```bash
./gradlew build
```

The jars end up in `fabric/build/libs`, `paper/build/libs` and `paper-api/build/libs`.

## Developing

| Folder | What's in it |
|---|---|
| `protocol` | The wire format, shared by the mod and the plugin |
| `fabric` | The client mod |
| `paper-api` | The API plugins compile against |
| `paper` | The Paper plugin |
| `test-plugin` | SkaffyTest: `/skaffytest` tries every feature (its jar is built into `paper/run/plugins`) |
| `test-models` | Sketchfab models the test plugin imports ([credits and licenses](test-models/CREDITS.md)) |
| `editor/sfy` | Syntax coloring for `.sfy` files in VS Code (copy the folder into `~/.vscode/extensions`) |

Run a development server and client, then join `localhost`:

```bash
./gradlew :test-plugin:jar :paper:runServer
```

```bash
./gradlew :fabric:runClient
```

The first server start imports the test models, which takes about 20 seconds; later starts use the cache.

## License

Skaffy's API uses its own license; read [LICENSE](LICENSE) for the details. In short: the mod may be used on any server, in videos and in modpacks (unmodified, free, linking back here), but not sold, reuploaded or redistributed modified. The server plugin may be forked, modified and shared for free with credit. `.sfy` files belong to whoever wrote them. Plugins that use the API are their authors' own.

The models in `test-models` keep their authors' licenses (see [CREDITS.md](test-models/CREDITS.md)).
