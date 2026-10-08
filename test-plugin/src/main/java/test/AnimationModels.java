package test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

final class AnimationModels {
	private AnimationModels() {
	}


	static byte[] robot() {
		Project project = new Project("robot", 32, 32);
		int body = project.texture("robot_body", robotBody(), "default", 0);
		int screen = project.texture("robot_screen", robotScreen(), "default", 6);
		int lights = project.texture("robot_lights", lights(0xFF40E0FF), "emissive", 0);

		Group rightLeg = project.group("right_leg", null, v(2, 6, 0));
		project.cube(rightLeg, v(0.5f, 0, -1.5f), v(3.5f, 6, 1.5f), null, null, Faces.all(body, 16, 0));
		Group leftLeg = project.group("left_leg", null, v(-2, 6, 0));
		project.cube(leftLeg, v(-3.5f, 0, -1.5f), v(-0.5f, 6, 1.5f), null, null, Faces.all(body, 16, 0));

		Group torso = project.group("body", null, v(0, 6, 0));
		project.cube(torso, v(-4, 6, -2.5f), v(4, 14, 2.5f), null, null, Faces.all(body, 0, 0).set("south", body, 0, 16, 0));
		project.cube(torso, v(-1.5f, 9, -3), v(1.5f, 12, -2.5f), null, null, Faces.all(lights, 0, 0));

		Group head = project.group("head", null, v(0, 14, 0));
		project.cube(head, v(-3.5f, 14, -3.5f), v(3.5f, 20, 3.5f), null, null,
				Faces.all(body, 0, 0).set("north", screen, 0, 0, 0).set("up", body, 16, 16, 90));
		project.pyramid(head, "crest", v(0, 20, 0), 2, 3, lights);
		Group antenna = project.group("antenna", head, v(2.5f, 20, 0));
		project.cube(antenna, v(2, 20, -0.5f), v(3, 24, 0.5f), v(2.5f, 20, 0), v(0, 0, -15), Faces.all(body, 16, 0));
		project.cube(antenna, v(1.5f, 24, -1), v(3.5f, 26, 1), v(2.5f, 25, 0), v(35, 45, 0), Faces.all(lights, 0, 0));
		project.locator(antenna, "antenna_tip", v(2.5f, 26, 0));

		Group rightArm = project.group("right_arm", null, v(5, 13, 0));
		project.cube(rightArm, v(4, 6, -1.5f), v(7, 14, 1.5f), null, null, Faces.all(body, 16, 0));
		project.locator(rightArm, "hand", v(5.5f, 6.5f, -0.5f));
		Group leftArm = project.group("left_arm", null, v(-5, 13, 0));
		project.cube(leftArm, v(-7, 6, -1.5f), v(-4, 14, 1.5f), null, null, Faces.all(body, 16, 0));

		Animation idle = project.animation("idle", "loop", 2);
		idle.rotation(head, 0, "bezier", 0, 0, 0).rotation(head, 0.7f, "bezier", 5, 25, 0).rotation(head, 1.4f, "bezier", -5, -25, 0).rotation(head, 2, "bezier", 0, 0, 0);
		idle.rotation(antenna, 0, "catmullrom", 0, 0, 0).rotation(antenna, 0.5f, "catmullrom", 0, 0, 12).rotation(antenna, 1, "catmullrom", 0, 0, -12)
				.rotation(antenna, 1.5f, "catmullrom", 0, 0, 12).rotation(antenna, 2, "catmullrom", 0, 0, 0);
		idle.position(torso, 0, "linear", 0, 0, 0).position(torso, 1, "linear", 0, 0.4f, 0).position(torso, 2, "linear", 0, 0, 0);
		idle.position(head, 0, "linear", 0, 0, 0).position(head, 1, "linear", 0, 0.4f, 0).position(head, 2, "linear", 0, 0, 0);

		String swing = "math.sin(query.anim_time * 360) * 25 * (1 + variable.speed)";
		String back = "-math.sin(query.anim_time * 360) * 25 * (1 + variable.speed)";
		Animation walk = project.animation("walk", "loop", 1);
		walk.rotation(rightLeg, 0, "linear", swing, 0, 0).rotation(leftLeg, 0, "linear", back, 0, 0);
		walk.rotation(rightArm, 0, "linear", back, 0, 0).rotation(leftArm, 0, "linear", swing, 0, 0);
		walk.position(torso, 0, "linear", 0, "math.abs(math.sin(query.anim_time * 360)) * 0.6", 0);
		walk.position(head, 0, "linear", 0, "math.abs(math.sin(query.anim_time * 360)) * 0.6", 0);

		Animation wave = project.animation("wave", "once", 1.6f);
		wave.rotation(rightArm, 0, "bezier", 0, 0, 0).rotation(rightArm, 0.35f, "bezier", -160, 0, -15).rotation(rightArm, 0.6f, "bezier", -160, 0, 20)
				.rotation(rightArm, 0.85f, "bezier", -160, 0, -15).rotation(rightArm, 1.1f, "bezier", -160, 0, 20).rotation(rightArm, 1.6f, "bezier", 0, 0, 0);
		wave.rotation(head, 0, "linear", 0, 0, 0).rotation(head, 0.4f, "linear", 0, -20, 8).rotation(head, 1.2f, "linear", 0, -20, 8).rotation(head, 1.6f, "linear", 0, 0, 0);
		wave.sound(0.05f, "minecraft:entity.villager.ambient");
		wave.particle(0.6f, "minecraft:heart", "hand").particle(1.1f, "minecraft:heart", "hand").particle(0.9f, "minecraft:end_rod", "antenna_tip");
		wave.timeline(1.5f, "wave_done");
		return project.bytes();
	}


	static byte[] turret() {
		Project project = new Project("turret", 32, 32);
		String material = project.textureGroup("turret_metal");
		int metal = project.texture("turret_metal", turretMetal(), "default", 0, material, "color");
		int lens = project.texture("turret_lens", lights(0xFFFF3020), "emissive", 0);
		project.texture("turret_metal_normal", panelNormals(), "default", 0, material, "normal");
		project.texture("turret_metal_mer", mer(), "default", 0, material, "mer");

		Group base = project.group("base", null, v(0, 0, 0));
		project.cube(base, v(-6, 0, -6), v(6, 4, 6), null, null, Faces.all(metal, 0, 0));
		project.cube(base, v(-2, 4, -2), v(2, 10, 2), null, null, Faces.all(metal, 16, 0));
		Group head = project.group("head", null, v(0, 13, 0));
		project.cube(head, v(-4, 10, -4), v(4, 16, 4), null, null, Faces.all(metal, 0, 16));
		project.cube(head, v(-2, 12, -4.5f), v(2, 15, -4), null, null, Faces.all(lens, 0, 0));
		Group barrel = project.group("barrel", head, v(0, 13, -4));
		project.cube(barrel, v(-1, 12, -12), v(1, 14, -4), null, null, Faces.all(metal, 16, 16));
		project.cube(barrel, v(-1.5f, 11.5f, -13), v(1.5f, 14.5f, -11), v(0, 13, -12), v(0, 0, 45), Faces.all(metal, 16, 0));
		project.locator(barrel, "muzzle", v(0, 13, -13.5f));

		Animation fire = project.animation("fire", "once", 0.45f);
		fire.position(barrel, 0, "linear", 0, 0, 0).position(barrel, 0.05f, "linear", 0, 0, 3).position(barrel, 0.45f, "bezier", 0, 0, 0);
		fire.sound(0, "minecraft:entity.firework_rocket.blast");
		fire.particle(0, "minecraft:flame", "muzzle").particle(0.02f, "minecraft:smoke", "muzzle");
		fire.timeline(0.05f, "shot");
		return project.bytes();
	}


	static byte[] explosion() {
		Project project = new Project("explosion", 16, 16);
		int core = project.texture("boom_core", gradient(0xFFFFF080, 0xFFFF8000), "emissive", 0);
		int shell = project.texture("boom_shell", gradient(0xFFFF6020, 0xFF901000), "emissive", 0);

		Group coreGroup = project.group("core", null, v(0, 8, 0));
		project.cube(coreGroup, v(-4, 4, -4), v(4, 12, 4), null, null, Faces.all(core, 0, 0));
		Group shellGroup = project.group("shell", null, v(0, 8, 0));
		project.cube(shellGroup, v(-5, 3, -5), v(5, 13, 5), v(0, 8, 0), v(45, 0, 45), Faces.all(shell, 0, 0));
		project.cube(shellGroup, v(-5, 3, -5), v(5, 13, 5), v(0, 8, 0), v(0, 45, 30), Faces.all(shell, 0, 0));
		project.cube(shellGroup, v(-5, 3, -5), v(5, 13, 5), v(0, 8, 0), v(-30, 20, 0), Faces.all(shell, 0, 0));

		Animation boom = project.animation("boom", "once", 1.2f);
		boom.scale(coreGroup, 0, "linear", 0.2f, 0.2f, 0.2f).scale(coreGroup, 0.25f, "bezier", 2.2f, 2.2f, 2.2f).scale(coreGroup, 1.2f, "linear", 0, 0, 0);
		boom.scale(shellGroup, 0, "linear", 0.5f, 0.5f, 0.5f).scale(shellGroup, 0.35f, "bezier", 3, 3, 3).scale(shellGroup, 1.2f, "linear", 0, 0, 0);
		boom.rotation(shellGroup, 0, "linear", 0, 0, 0).rotation(shellGroup, 1.2f, "linear", 90, 360, 0);
		boom.sound(0, "minecraft:entity.generic.explode");
		boom.particle(0, "minecraft:explosion_emitter", "");
		return project.bytes();
	}


	static byte[] backpack() {
		Project project = new Project("backpack", 32, 32);
		int leather = project.texture("backpack_leather", backpackLeather(), "default", 0);

		Group pack = project.group("pack", null, v(0, 0, 0));
		project.cube(pack, v(-4, 0, -2), v(4, 9, 2), null, null, Faces.all(leather, 0, 0));
		project.cube(pack, v(-3, 1, 2), v(3, 6, 3.5f), null, null, Faces.all(leather, 16, 0));
		Group flap = project.group("flap", pack, v(0, 9, 2));
		project.cube(flap, v(-4.25f, 6, 2), v(4.25f, 9.5f, 2.5f), v(0, 9, 2), v(-22.5f, 0, 0), Faces.all(leather, 0, 16));

		Animation bounce = project.animation("bounce", "loop", 0.6f);
		bounce.position(pack, 0, "catmullrom", 0, 0, 0).position(pack, 0.3f, "catmullrom", 0, 0.5f, 0).position(pack, 0.6f, "catmullrom", 0, 0, 0);
		bounce.rotation(flap, 0, "catmullrom", 0, 0, 0).rotation(flap, 0.3f, "catmullrom", 12, 0, 0).rotation(flap, 0.6f, "catmullrom", 0, 0, 0);
		return project.bytes();
	}


	static byte[] cameraRig() {
		Project project = new Project("camera_rig", 16, 16);
		Group rig = project.group("rig", null, v(0, 0, 0));
		Group camera = project.group("camera", rig, v(0, 40, 96));
		project.locator(camera, "lens", v(0, 40, 96));

		Animation orbit = project.animation("orbit", "hold", 8);
		orbit.rotation(rig, 0, "linear", 0, 0, 0).rotation(rig, 8, "linear", 0, 360, 0);
		orbit.position(camera, 0, "bezier", 0, 0, 0).position(camera, 8, "bezier", 0, 32, -48);
		orbit.rotation(camera, 0, "bezier", 14, 0, 0).rotation(camera, 8, "bezier", 49, 0, 0);
		orbit.sound(0, "minecraft:ui.toast.in");
		orbit.timeline(7.9f, "end");
		return project.bytes();
	}


	static byte[] bird() {
		MeshBuilder mesh = new MeshBuilder();
		mesh.box(0, 0.6f, 0, 0.24f, 0.2f, 0.5f, 0, 0, 32, 32, x -> new float[] {0, 0}, true);
		mesh.box(0, 0.74f, 0.3f, 0.17f, 0.17f, 0.17f, 0, 0, 32, 32, x -> new float[] {0, 0}, false);
		mesh.box(0, 0.71f, 0.44f, 0.05f, 0.04f, 0.1f, 0, 32, 16, 16, x -> new float[] {0, 0}, false);
		mesh.box(0, 0.62f, -0.33f, 0.18f, 0.03f, 0.16f, 32, 32, 32, 32, x -> new float[] {0, 0}, false);
		mesh.box(0.37f, 0.65f, 0, 0.5f, 0.02f, 0.26f, 32, 0, 32, 32, x -> x < 0.15f ? new float[] {1, 0.5f} : new float[] {1, 1}, false);
		mesh.box(-0.37f, 0.65f, 0, 0.5f, 0.02f, 0.26f, 32, 0, 32, 32, x -> x > -0.15f ? new float[] {2, 0.5f} : new float[] {2, 1}, false);

		Gltf gltf = new Gltf();
		int positions = gltf.floats(mesh.positions(), "VEC3", true);
		int normals = gltf.floats(mesh.normals(), "VEC3", false);
		int uvs = gltf.floats(mesh.uvs(), "VEC2", false);
		int joints = gltf.bytes(mesh.joints(), "VEC4");
		int weights = gltf.floats(mesh.weights(), "VEC4", false);
		int indices = gltf.shorts(mesh.indices());
		int puff = gltf.floats(mesh.puff(), "VEC3", true);
		float[][] binds = {{0, 0.6f, 0}, {0.12f, 0.65f, 0}, {-0.12f, 0.65f, 0}};
		float[] inverseBinds = new float[48];

		for (int j = 0; j < 3; j++) {
			float[] matrix = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, -binds[j][0], -binds[j][1], -binds[j][2], 1};
			System.arraycopy(matrix, 0, inverseBinds, j * 16, 16);
		}

		int inverseBindAccessor = gltf.floats(inverseBinds, "MAT4", false);

		float[] flapTimes = {0, 0.25f, 0.5f};
		int flapInput = gltf.floats(flapTimes, "SCALAR", true);
		int leftFlap = gltf.floats(concat(rotZ(40), rotZ(-30), rotZ(40)), "VEC4", false);
		int rightFlap = gltf.floats(concat(rotZ(-40), rotZ(30), rotZ(-40)), "VEC4", false);
		int bob = gltf.floats(new float[] {0, 0.6f, 0, 0, 0.66f, 0, 0, 0.6f, 0}, "VEC3", false);
		int morph = gltf.floats(new float[] {0, 1, 0}, "SCALAR", false);

		float[] glideTimes = {0, 1, 2};
		int glideInput = gltf.floats(glideTimes, "SCALAR", true);
		float[] zero4 = {0, 0, 0, 0};
		int leftGlide = gltf.floats(concat(zero4, rotZ(10), zero4, zero4, rotZ(4), zero4, zero4, rotZ(10), zero4), "VEC4", false);
		int rightGlide = gltf.floats(concat(zero4, rotZ(-10), zero4, zero4, rotZ(-4), zero4, zero4, rotZ(-10), zero4), "VEC4", false);
		float[] zero3 = {0, 0, 0};
		int glideBob = gltf.floats(concat(zero3, new float[] {0, 0.6f, 0}, zero3, zero3, new float[] {0, 0.7f, 0}, zero3, zero3, new float[] {0, 0.6f, 0}, zero3), "VEC3", false);

		JsonObject json = new JsonObject();
		JsonObject asset = new JsonObject();
		asset.addProperty("version", "2.0");
		asset.addProperty("generator", "SkaffyTest");
		json.add("asset", asset);
		json.addProperty("scene", 0);
		json.add("scenes", array(object("nodes", ints(0))));
		json.add("nodes", array(
				node("bird", null, ints(1, 4)),
				node("body", new float[] {0, 0.6f, 0}, ints(2, 3, 5)),
				node("wing_left", new float[] {0.12f, 0.05f, 0}, null),
				node("wing_right", new float[] {-0.12f, 0.05f, 0}, null),
				with(with(node("bird_mesh", null, null), "mesh", 0), "skin", 0),
				node("seat", new float[] {0, 0.12f, -0.05f}, null)));
		JsonObject skin = object("joints", ints(1, 2, 3));
		skin.addProperty("inverseBindMatrices", inverseBindAccessor);
		skin.addProperty("skeleton", 1);
		json.add("skins", array(skin));

		JsonObject attributes = new JsonObject();
		attributes.addProperty("POSITION", positions);
		attributes.addProperty("NORMAL", normals);
		attributes.addProperty("TEXCOORD_0", uvs);
		attributes.addProperty("JOINTS_0", joints);
		attributes.addProperty("WEIGHTS_0", weights);
		JsonObject primitive = object("attributes", attributes);
		primitive.addProperty("indices", indices);
		primitive.addProperty("material", 0);
		JsonObject target = new JsonObject();
		target.addProperty("POSITION", puff);
		primitive.add("targets", array(target));
		JsonObject meshJson = object("primitives", array(primitive));
		meshJson.add("weights", floatsJson(0));
		JsonObject extras = new JsonObject();
		extras.add("targetNames", array(new JsonPrimitive("puff")));
		meshJson.add("extras", extras);
		json.add("meshes", array(meshJson));

		JsonObject pbr = new JsonObject();
		pbr.add("baseColorTexture", object("index", 0));
		pbr.add("metallicRoughnessTexture", object("index", 1));
		pbr.addProperty("metallicFactor", 1);
		pbr.addProperty("roughnessFactor", 1);
		JsonObject material = object("pbrMetallicRoughness", pbr);
		material.addProperty("name", "feathers");
		material.add("normalTexture", object("index", 2));
		json.add("materials", array(material));
		JsonObject sampler = new JsonObject();
		sampler.addProperty("magFilter", 9728);
		sampler.addProperty("minFilter", 9984);
		json.add("samplers", array(sampler));
		json.add("textures", array(texture(0), texture(1), texture(2)));
		json.add("images", array(image(birdColor()), image(birdRoughness()), image(birdNormals())));

		JsonArray animations = new JsonArray();
		animations.add(animation("flap",
				new int[][] {{flapInput, leftFlap}, {flapInput, rightFlap}, {flapInput, bob}, {flapInput, morph}},
				new Object[][] {{2, "rotation"}, {3, "rotation"}, {1, "translation"}, {4, "weights"}}, "LINEAR"));
		animations.add(animation("glide",
				new int[][] {{glideInput, leftGlide}, {glideInput, rightGlide}, {glideInput, glideBob}},
				new Object[][] {{2, "rotation"}, {3, "rotation"}, {1, "translation"}}, "CUBICSPLINE"));
		json.add("animations", animations);
		gltf.finish(json);
		return json.toString().getBytes(StandardCharsets.UTF_8);
	}


	record Group(String uuid, String name, JsonObject outliner) {
	}

	static final class Faces {
		private final JsonObject json = new JsonObject();

		static Faces all(int texture, int u, int v) {
			Faces faces = new Faces();

			for (String face : new String[] {"north", "east", "south", "west", "up", "down"}) {
				faces.set(face, texture, u, v, 0);
			}

			return faces;
		}

		Faces set(String face, int texture, int u, int v, int rotation) {
			JsonObject json = new JsonObject();
			json.add("uv", floatsJson(u, v, u + 16, v + 16));
			json.addProperty("texture", texture);

			if (rotation != 0) {
				json.addProperty("rotation", rotation);
			}

			this.json.add(face, json);
			return this;
		}
	}

	static final class Project {
		private final JsonObject root = new JsonObject();
		private final JsonArray elements = new JsonArray();
		private final JsonArray groups = new JsonArray();
		private final JsonArray outliner = new JsonArray();
		private final JsonArray textures = new JsonArray();
		private final JsonArray textureGroups = new JsonArray();
		private final JsonArray animations = new JsonArray();

		Project(String name, int width, int height) {
			JsonObject meta = new JsonObject();
			meta.addProperty("format_version", "5.0");
			meta.addProperty("model_format", "free");
			meta.addProperty("box_uv", false);
			root.add("meta", meta);
			root.addProperty("name", name);
			JsonObject resolution = new JsonObject();
			resolution.addProperty("width", width);
			resolution.addProperty("height", height);
			root.add("resolution", resolution);
		}

		String textureGroup(String name) {
			JsonObject group = new JsonObject();
			String uuid = uuid();
			group.addProperty("uuid", uuid);
			group.addProperty("name", name);
			group.addProperty("is_material", true);
			textureGroups.add(group);
			return uuid;
		}

		int texture(String name, BufferedImage image, String renderMode, int frameTime) {
			return texture(name, image, renderMode, frameTime, null, null);
		}

		int texture(String name, BufferedImage image, String renderMode, int frameTime, String group, String channel) {
			JsonObject texture = new JsonObject();
			texture.addProperty("name", name + ".png");
			texture.addProperty("uuid", uuid());
			texture.addProperty("uv_width", image.getWidth());
			texture.addProperty("uv_height", frameTime > 0 ? image.getWidth() : image.getHeight());
			texture.addProperty("render_mode", renderMode);

			if (frameTime > 0) {
				texture.addProperty("frame_time", frameTime);
				texture.addProperty("frame_order_type", "loop");
			}

			if (group != null) {
				texture.addProperty("group", group);
				texture.addProperty("pbr_channel", channel);
			}

			texture.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(SkaffyTest.png(image)));
			textures.add(texture);
			return textures.size() - 1;
		}

		Group group(String name, Group parent, float[] origin) {
			String uuid = uuid();
			JsonObject group = new JsonObject();
			group.addProperty("uuid", uuid);
			group.addProperty("name", name);
			group.add("origin", floatsJson(origin));
			group.add("rotation", floatsJson(0, 0, 0));
			groups.add(group);
			JsonObject node = new JsonObject();
			node.addProperty("uuid", uuid);
			node.add("children", new JsonArray());
			(parent == null ? outliner : parent.outliner().getAsJsonArray("children")).add(node);
			return new Group(uuid, name, node);
		}

		void cube(Group group, float[] from, float[] to, float[] pivot, float[] rotation, Faces faces) {
			JsonObject cube = element(group, "cube", "cube");
			cube.add("from", floatsJson(from));
			cube.add("to", floatsJson(to));

			if (rotation != null) {
				cube.add("origin", floatsJson(pivot));
				cube.add("rotation", floatsJson(rotation));
			}

			cube.add("faces", faces.json);
		}

		void pyramid(Group group, String name, float[] base, float half, float height, int texture) {
			JsonObject mesh = element(group, "mesh", name);
			mesh.add("origin", floatsJson(base));
			mesh.add("rotation", floatsJson(0, 0, 0));
			JsonObject vertices = new JsonObject();
			vertices.add("p0", floatsJson(-half, 0, -half));
			vertices.add("p1", floatsJson(half, 0, -half));
			vertices.add("p2", floatsJson(half, 0, half));
			vertices.add("p3", floatsJson(-half, 0, half));
			vertices.add("top", floatsJson(0, height, 0));
			mesh.add("vertices", vertices);
			JsonObject faces = new JsonObject();
			String[][] corners = {{"p1", "p0", "top"}, {"p2", "p1", "top"}, {"p3", "p2", "top"}, {"p0", "p3", "top"}, {"p0", "p1", "p2", "p3"}};

			for (int i = 0; i < corners.length; i++) {
				JsonObject face = new JsonObject();
				JsonArray keys = new JsonArray();
				JsonObject uv = new JsonObject();
				float[][] spots = corners[i].length == 3 ? new float[][] {{0, 16}, {16, 16}, {8, 0}} : new float[][] {{0, 0}, {16, 0}, {16, 16}, {0, 16}};

				for (int c = 0; c < corners[i].length; c++) {
					keys.add(corners[i][c]);
					uv.add(corners[i][c], floatsJson(spots[c]));
				}

				face.add("vertices", keys);
				face.add("uv", uv);
				face.addProperty("texture", texture);
				faces.add("f" + i, face);
			}

			mesh.add("faces", faces);
		}

		void locator(Group group, String name, float[] position) {
			JsonObject locator = element(group, "locator", name);
			locator.add("position", floatsJson(position));
			locator.add("rotation", floatsJson(0, 0, 0));
		}

		private JsonObject element(Group group, String type, String name) {
			JsonObject element = new JsonObject();
			String uuid = uuid();
			element.addProperty("uuid", uuid);
			element.addProperty("type", type);
			element.addProperty("name", name);
			elements.add(element);
			group.outliner().getAsJsonArray("children").add(uuid);
			return element;
		}

		Animation animation(String name, String loop, float length) {
			JsonObject animation = new JsonObject();
			animation.addProperty("uuid", uuid());
			animation.addProperty("name", name);
			animation.addProperty("loop", loop);
			animation.addProperty("length", length);
			animation.add("animators", new JsonObject());
			animations.add(animation);
			return new Animation(animation.getAsJsonObject("animators"));
		}

		byte[] bytes() {
			root.add("elements", elements);
			root.add("groups", groups);
			root.add("outliner", outliner);
			root.add("texture_groups", textureGroups);
			root.add("textures", textures);
			root.add("animations", animations);
			return root.toString().getBytes(StandardCharsets.UTF_8);
		}
	}

	static final class Animation {
		private final JsonObject animators;

		Animation(JsonObject animators) {
			this.animators = animators;
		}

		Animation rotation(Group group, float time, String interpolation, Object x, Object y, Object z) {
			return keyframe(group, "rotation", time, interpolation, negate(x), negate(y), z);
		}

		Animation position(Group group, float time, String interpolation, Object x, Object y, Object z) {
			return keyframe(group, "position", time, interpolation, negate(x), y, z);
		}

		Animation scale(Group group, float time, String interpolation, Object x, Object y, Object z) {
			return keyframe(group, "scale", time, interpolation, x, y, z);
		}

		Animation sound(float time, String sound) {
			JsonObject point = new JsonObject();
			point.addProperty("effect", sound);
			return effect("sound", time, point);
		}

		Animation particle(float time, String particle, String locator) {
			JsonObject point = new JsonObject();
			point.addProperty("effect", particle);
			point.addProperty("locator", locator);
			return effect("particle", time, point);
		}

		Animation timeline(float time, String script) {
			JsonObject point = new JsonObject();
			point.addProperty("script", script);
			return effect("timeline", time, point);
		}

		private Animation keyframe(Group group, String channel, float time, String interpolation, Object x, Object y, Object z) {
			JsonObject animator = animators.has(group.uuid()) ? animators.getAsJsonObject(group.uuid()) : null;

			if (animator == null) {
				animator = new JsonObject();
				animator.addProperty("name", group.name());
				animator.addProperty("type", "bone");
				animator.add("keyframes", new JsonArray());
				animators.add(group.uuid(), animator);
			}

			JsonObject point = new JsonObject();
			point.add("x", value(x));
			point.add("y", value(y));
			point.add("z", value(z));
			JsonObject keyframe = new JsonObject();
			keyframe.addProperty("channel", channel);
			keyframe.add("data_points", array(point));
			keyframe.addProperty("uuid", uuid());
			keyframe.addProperty("time", time);
			keyframe.addProperty("interpolation", interpolation);

			if (interpolation.equals("bezier")) {
				keyframe.add("bezier_left_time", floatsJson(-0.15f, -0.15f, -0.15f));
				keyframe.add("bezier_left_value", floatsJson(0, 0, 0));
				keyframe.add("bezier_right_time", floatsJson(0.15f, 0.15f, 0.15f));
				keyframe.add("bezier_right_value", floatsJson(0, 0, 0));
			}

			animator.getAsJsonArray("keyframes").add(keyframe);
			return this;
		}

		private Animation effect(String channel, float time, JsonObject point) {
			JsonObject animator = animators.has("effects") ? animators.getAsJsonObject("effects") : null;

			if (animator == null) {
				animator = new JsonObject();
				animator.addProperty("name", "Effects");
				animator.addProperty("type", "effect");
				animator.add("keyframes", new JsonArray());
				animators.add("effects", animator);
			}

			JsonObject keyframe = new JsonObject();
			keyframe.addProperty("channel", channel);
			keyframe.add("data_points", array(point));
			keyframe.addProperty("uuid", uuid());
			keyframe.addProperty("time", time);
			animator.getAsJsonArray("keyframes").add(keyframe);
			return this;
		}

		private static Object negate(Object value) {
			return value instanceof Number number ? -number.floatValue() : "-(" + value + ")";
		}

		private static JsonPrimitive value(Object value) {
			return value instanceof Number number ? new JsonPrimitive(number.floatValue()) : new JsonPrimitive(value.toString());
		}
	}


	interface Weights {
		float[] at(float x);
	}

	static final class MeshBuilder {
		private final List<float[]> corners = new ArrayList<>();
		private final List<Integer> indices = new ArrayList<>();

		void box(float cx, float cy, float cz, float sx, float sy, float sz, int u, int v, int width, int height, Weights weights, boolean puffs) {
			float[][] normals = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

			for (float[] n : normals) {
				float[] a = n[0] != 0 ? new float[] {0, 0, -n[0]} : n[1] != 0 ? new float[] {1, 0, 0} : new float[] {n[2], 0, 0};
				float[] b = cross(n, a);
				int first = corners.size();
				float[][] steps = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

				for (float[] step : steps) {
					float x = cx + (n[0] * sx + step[0] * a[0] * sx + step[1] * b[0] * sx) / 2;
					float y = cy + (n[1] * sy + step[0] * a[1] * sy + step[1] * b[1] * sy) / 2;
					float z = cz + (n[2] * sz + step[0] * a[2] * sz + step[1] * b[2] * sz) / 2;
					float[] weight = weights.at(x);
					corners.add(new float[] {x, y, z, n[0], n[1], n[2], (u + (step[0] + 1) / 2 * width) / 64f, (v + (1 - step[1]) / 2 * height) / 64f,
							weight[0], weight[1], puffs ? 1 : 0, cx, cy, cz});
				}

				for (int i : new int[] {0, 1, 2, 0, 2, 3}) {
					indices.add(first + i);
				}
			}
		}

		float[] positions() {
			return column(0, 3);
		}

		float[] normals() {
			return column(3, 3);
		}

		float[] uvs() {
			return column(6, 2);
		}

		int[] joints() {
			int[] joints = new int[corners.size() * 4];

			for (int i = 0; i < corners.size(); i++) {
				int joint = (int) corners.get(i)[8];
				joints[i * 4] = joint;
				joints[i * 4 + 1] = 0;
			}

			return joints;
		}

		float[] weights() {
			float[] weights = new float[corners.size() * 4];

			for (int i = 0; i < corners.size(); i++) {
				float[] corner = corners.get(i);

				if (corner[8] == 0) {
					weights[i * 4] = 1;
				} else {
					weights[i * 4] = corner[9];
					weights[i * 4 + 1] = 1 - corner[9];
				}
			}

			return weights;
		}

		float[] puff() {
			float[] offsets = new float[corners.size() * 3];

			for (int i = 0; i < corners.size(); i++) {
				float[] corner = corners.get(i);

				if (corner[10] > 0) {
					for (int c = 0; c < 3; c++) {
						offsets[i * 3 + c] = (corner[c] - corner[11 + c]) * 0.3f;
					}
				}
			}

			return offsets;
		}

		int[] indices() {
			return indices.stream().mapToInt(Integer::intValue).toArray();
		}

		private float[] column(int start, int count) {
			float[] values = new float[corners.size() * count];

			for (int i = 0; i < corners.size(); i++) {
				System.arraycopy(corners.get(i), start, values, i * count, count);
			}

			return values;
		}

		private static float[] cross(float[] a, float[] b) {
			return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
		}
	}

	static final class Gltf {
		private final ByteArrayOutputStream bin = new ByteArrayOutputStream();
		private final JsonArray views = new JsonArray();
		private final JsonArray accessors = new JsonArray();

		int floats(float[] values, String type, boolean bounds) {
			ByteBuffer buffer = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);

			for (float value : values) {
				buffer.putFloat(value);
			}

			JsonObject accessor = accessor(view(buffer.array()), 5126, values.length / components(type), type);

			if (bounds) {
				int components = components(type);
				float[] min = new float[components];
				float[] max = new float[components];
				java.util.Arrays.fill(min, Float.MAX_VALUE);
				java.util.Arrays.fill(max, -Float.MAX_VALUE);

				for (int i = 0; i < values.length; i++) {
					min[i % components] = Math.min(min[i % components], values[i]);
					max[i % components] = Math.max(max[i % components], values[i]);
				}

				accessor.add("min", floatsJson(min));
				accessor.add("max", floatsJson(max));
			}

			accessors.add(accessor);
			return accessors.size() - 1;
		}

		int bytes(int[] values, String type) {
			byte[] data = new byte[values.length];

			for (int i = 0; i < values.length; i++) {
				data[i] = (byte) values[i];
			}

			accessors.add(accessor(view(data), 5121, values.length / components(type), type));
			return accessors.size() - 1;
		}

		int shorts(int[] values) {
			ByteBuffer buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);

			for (int value : values) {
				buffer.putShort((short) value);
			}

			accessors.add(accessor(view(buffer.array()), 5123, values.length, "SCALAR"));
			return accessors.size() - 1;
		}

		void finish(JsonObject json) {
			byte[] data = bin.toByteArray();
			JsonObject buffer = new JsonObject();
			buffer.addProperty("byteLength", data.length);
			buffer.addProperty("uri", "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(data));
			json.add("buffers", array(buffer));
			json.add("bufferViews", views);
			json.add("accessors", accessors);
		}

		private int view(byte[] data) {
			while (bin.size() % 4 != 0) {
				bin.write(0);
			}

			JsonObject view = new JsonObject();
			view.addProperty("buffer", 0);
			view.addProperty("byteOffset", bin.size());
			view.addProperty("byteLength", data.length);
			bin.writeBytes(data);
			views.add(view);
			return views.size() - 1;
		}

		private static JsonObject accessor(int view, int componentType, int count, String type) {
			JsonObject accessor = new JsonObject();
			accessor.addProperty("bufferView", view);
			accessor.addProperty("componentType", componentType);
			accessor.addProperty("count", count);
			accessor.addProperty("type", type);
			return accessor;
		}

		private static int components(String type) {
			return switch (type) {
				case "VEC2" -> 2;
				case "VEC3" -> 3;
				case "VEC4" -> 4;
				case "MAT4" -> 16;
				default -> 1;
			};
		}
	}

	private static JsonObject node(String name, float[] translation, JsonArray children) {
		JsonObject node = new JsonObject();
		node.addProperty("name", name);

		if (translation != null) {
			node.add("translation", floatsJson(translation));
		}

		if (children != null) {
			node.add("children", children);
		}

		return node;
	}

	private static JsonObject with(JsonObject json, String key, int value) {
		json.addProperty(key, value);
		return json;
	}

	private static JsonObject texture(int image) {
		JsonObject texture = new JsonObject();
		texture.addProperty("sampler", 0);
		texture.addProperty("source", image);
		return texture;
	}

	private static JsonObject image(BufferedImage image) {
		JsonObject json = new JsonObject();
		json.addProperty("uri", "data:image/png;base64," + Base64.getEncoder().encodeToString(SkaffyTest.png(image)));
		return json;
	}

	private static JsonObject animation(String name, int[][] samplerAccessors, Object[][] targets, String interpolation) {
		JsonArray samplers = new JsonArray();
		JsonArray channels = new JsonArray();

		for (int i = 0; i < samplerAccessors.length; i++) {
			JsonObject sampler = new JsonObject();
			sampler.addProperty("input", samplerAccessors[i][0]);
			sampler.addProperty("output", samplerAccessors[i][1]);
			sampler.addProperty("interpolation", interpolation);
			samplers.add(sampler);
			JsonObject target = new JsonObject();
			target.addProperty("node", (Integer) targets[i][0]);
			target.addProperty("path", (String) targets[i][1]);
			JsonObject channel = new JsonObject();
			channel.addProperty("sampler", i);
			channel.add("target", target);
			channels.add(channel);
		}

		JsonObject animation = new JsonObject();
		animation.addProperty("name", name);
		animation.add("samplers", samplers);
		animation.add("channels", channels);
		return animation;
	}

	private static float[] rotZ(float degrees) {
		double half = Math.toRadians(degrees) / 2;
		return new float[] {0, 0, (float) Math.sin(half), (float) Math.cos(half)};
	}

	private static float[] concat(float[]... parts) {
		int length = 0;

		for (float[] part : parts) {
			length += part.length;
		}

		float[] all = new float[length];
		int at = 0;

		for (float[] part : parts) {
			System.arraycopy(part, 0, all, at, part.length);
			at += part.length;
		}

		return all;
	}


	private static float[] v(float x, float y, float z) {
		return new float[] {x, y, z};
	}

	private static String uuid() {
		return UUID.randomUUID().toString();
	}

	private static JsonArray floatsJson(float... values) {
		JsonArray array = new JsonArray();

		for (float value : values) {
			array.add(value);
		}

		return array;
	}

	private static JsonArray ints(int... values) {
		JsonArray array = new JsonArray();

		for (int value : values) {
			array.add(value);
		}

		return array;
	}

	private static JsonArray array(com.google.gson.JsonElement... elements) {
		JsonArray array = new JsonArray();

		for (com.google.gson.JsonElement element : elements) {
			array.add(element);
		}

		return array;
	}

	private static JsonObject object(String key, com.google.gson.JsonElement value) {
		JsonObject json = new JsonObject();
		json.add(key, value);
		return json;
	}

	private static JsonObject object(String key, int value) {
		JsonObject json = new JsonObject();
		json.addProperty(key, value);
		return json;
	}


	private static BufferedImage robotBody() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int tx = x % 16;
				int ty = y % 16;
				boolean edge = tx == 0 || ty == 0 || tx == 15 || ty == 15;
				int argb;

				if (x < 16 && y < 16) {
					argb = edge ? 0xFF707880 : (tx == 2 || tx == 13) && (ty == 2 || ty == 13) ? 0xFF505860 : 0xFFA8B0B8;
				} else if (y < 16) {
					argb = edge ? 0xFF30343A : (tx + ty) % 4 == 0 ? 0xFF3A3F46 : 0xFF4A5058;
				} else if (x < 16) {
					argb = (ty / 3) % 2 == 0 ? 0xFF3A6FD8 : 0xFF2850A8;
				} else {
					boolean shaft = tx >= 6 && tx <= 9 && ty >= 6 && ty <= 13;
					boolean tip = ty >= 2 && ty < 7 && Math.abs(tx - 7.5) <= ty - 1.5;
					argb = shaft || tip ? 0xFFFFD020 : edge ? 0xFF707880 : 0xFFA8B0B8;
				}

				image.setRGB(x, y, argb);
			}
		}

		return image;
	}

	private static BufferedImage robotScreen() {
		BufferedImage image = new BufferedImage(16, 64, BufferedImage.TYPE_INT_ARGB);
		int[] shifts = {-2, 0, 2, 0};

		for (int frame = 0; frame < 4; frame++) {
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
					int argb = edge ? 0xFF505860 : 0xFF081018;
					int ex = x - shifts[frame];
					boolean eye = (ex >= 3 && ex <= 5 || ex >= 10 && ex <= 12) && (frame == 3 ? y == 7 : y >= 5 && y <= 8);
					boolean mouth = y == 12 && x >= 5 && x <= 10;
					image.setRGB(x, frame * 16 + y, eye || mouth ? 0xFF40E0FF : argb);
				}
			}
		}

		return image;
	}

	private static BufferedImage lights(int color) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double distance = Math.hypot(x - 7.5, y - 7.5);
				image.setRGB(x, y, distance < 3 ? 0xFFFFFFFF : color);
			}
		}

		return image;
	}

	private static BufferedImage gradient(int top, int bottom) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				image.setRGB(x, y, mix(top, bottom, (y + (x * 7 % 3)) / 17f));
			}
		}

		return image;
	}

	private static BufferedImage turretMetal() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				boolean seam = x % 16 == 0 || y % 16 == 0;
				boolean bolt = (x % 16 == 3 || x % 16 == 12) && (y % 16 == 3 || y % 16 == 12);
				image.setRGB(x, y, seam ? 0xFF404830 : bolt ? 0xFFB0B8A0 : (x / 16 + y / 16) % 2 == 0 ? 0xFF6B7A48 : 0xFF5E6C3E);
			}
		}

		return image;
	}

	private static BufferedImage panelNormals() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int nx = 128;
				int ny = 128;

				if (x % 16 == 1) {
					nx = 200;
				} else if (x % 16 == 15) {
					nx = 56;
				}

				if (y % 16 == 1) {
					ny = 56;
				} else if (y % 16 == 15) {
					ny = 200;
				}

				if ((x % 16 == 3 || x % 16 == 12) && (y % 16 == 3 || y % 16 == 12)) {
					nx = 128;
					ny = 128;
				}

				int nz = (int) Math.round(Math.sqrt(Math.max(0, 1 - Math.pow((nx - 128) / 127.0, 2) - Math.pow((ny - 128) / 127.0, 2))) * 127 + 128);
				image.setRGB(x, y, 0xFF000000 | nx << 16 | ny << 8 | Math.min(255, nz));
			}
		}

		return image;
	}

	private static BufferedImage mer() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				boolean bolt = (x % 16 == 3 || x % 16 == 12) && (y % 16 == 3 || y % 16 == 12);
				image.setRGB(x, y, bolt ? 0xFFFF0020 : 0xFF600090);
			}
		}

		return image;
	}

	private static BufferedImage backpackLeather() {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				boolean stitch = (x % 16 == 1 || x % 16 == 14 || y % 16 == 1 || y % 16 == 14) && (x + y) % 2 == 0;
				boolean strap = x % 16 >= 6 && x % 16 <= 9;
				int base = (x * 13 + y * 7) % 5 == 0 ? 0xFF6A4020 : 0xFF7A4A26;
				image.setRGB(x, y, stitch ? 0xFFE0C090 : strap ? 0xFF4A2C14 : base);
			}
		}

		return image;
	}

	private static BufferedImage birdColor() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int argb;

				if (y < 32 && x < 32) {
					argb = (x / 4 + y / 4) % 2 == 0 ? 0xFF3070E0 : 0xFF2860C8;
				} else if (y < 32) {
					argb = (x / 3) % 2 == 0 ? 0xFF1A3C90 : 0xFF2A50B0;
				} else if (x < 32) {
					argb = 0xFFFFC020;
				} else {
					argb = (y / 4) % 2 == 0 ? 0xFF1A3C90 : 0xFFFFFFFF;
				}

				image.setRGB(x, y, argb);
			}
		}

		return image;
	}

	private static BufferedImage birdRoughness() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				image.setRGB(x, y, y >= 32 && x < 32 ? 0xFF003000 : 0xFF00D000);
			}
		}

		return image;
	}

	private static BufferedImage birdNormals() {
		BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int ny = y % 4 == 0 ? 80 : y % 4 == 3 ? 176 : 128;
				image.setRGB(x, y, 0xFF000000 | 128 << 16 | ny << 8 | 240);
			}
		}

		return image;
	}

	private static int mix(int a, int b, float t) {
		int result = 0xFF000000;

		for (int shift = 0; shift <= 16; shift += 8) {
			int ca = a >> shift & 0xFF;
			int cb = b >> shift & 0xFF;
			result |= Math.round(ca + (cb - ca) * Math.min(1, t)) << shift;
		}

		return result;
	}
}
