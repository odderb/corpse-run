package com.corpserun.client.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads a Bedrock geometry file ({@code .geo.json}, as exported by Blockbench) into a vanilla model.
 * <p>
 * Supported: bones with pivot, rotation and parent; cubes with origin, size, box UV ({@code "uv": [u, v]}), inflate,
 * mirror, and their own pivot/rotation. Not supported: per-face UV, poly meshes, locators.
 * <p>
 * Bedrock space is y-up with the ground at y = 0 and x mirrored compared to vanilla's model space (y-down, ground at
 * y = 24), so positions map to (-x, 24 - y, z) and rotations to (-x, -y, z).
 */
public final class BedrockGeometry {

    private BedrockGeometry() {
    }

    public static ModelPart load(ResourceManager resources, ResourceLocation location) throws IOException {
        try (Reader reader = resources.openAsReader(location)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject()).bakeRoot();
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw new IOException("Malformed geometry file " + location + ": " + e, e);
        }
    }

    private record Bone(String name, String parent, Vector3f pivot, Vector3f rotation, JsonArray cubes) {}

    static LayerDefinition parse(JsonObject root) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = description.get("texture_width").getAsInt();
        int textureHeight = description.get("texture_height").getAsInt();

        Map<String, Bone> bones = new LinkedHashMap<>();
        if (geometry.has("bones")) {
            for (JsonElement element : geometry.getAsJsonArray("bones")) {
                JsonObject bone = element.getAsJsonObject();
                String name = bone.get("name").getAsString();
                bones.put(name, new Bone(name,
                        bone.has("parent") ? bone.get("parent").getAsString() : null,
                        vec(bone, "pivot"), vec(bone, "rotation"),
                        bone.has("cubes") ? bone.getAsJsonArray("cubes") : new JsonArray()));
            }
        }
        Map<String, List<Bone>> children = new HashMap<>();
        List<Bone> roots = new ArrayList<>();
        for (Bone bone : bones.values()) {
            if (bone.parent() != null && bones.containsKey(bone.parent())) {
                children.computeIfAbsent(bone.parent(), k -> new ArrayList<>()).add(bone);
            } else {
                roots.add(bone);
            }
        }

        MeshDefinition mesh = new MeshDefinition();
        for (Bone bone : roots) {
            addBone(mesh.getRoot(), bone, new Vector3f(0.0F, 24.0F, 0.0F), null, children);
        }
        return LayerDefinition.create(mesh, textureWidth, textureHeight);
    }

    /**
     * @param parentPivot the parent's pivot in Bedrock space, or null for a root bone (then {@code rootOrigin} is
     *                    used to place it in model space)
     */
    private static void addBone(PartDefinition parent, Bone bone, Vector3f rootOrigin, Vector3f parentPivot,
                                Map<String, List<Bone>> children) {
        Vector3f p = bone.pivot();
        PartPose pose = parentPivot == null
                ? pose(-p.x + rootOrigin.x, rootOrigin.y - p.y, p.z + rootOrigin.z, bone.rotation())
                : pose(-(p.x - parentPivot.x), -(p.y - parentPivot.y), p.z - parentPivot.z, bone.rotation());

        CubeListBuilder plainCubes = CubeListBuilder.create();
        List<JsonObject> rotatedCubes = new ArrayList<>();
        for (JsonElement element : bone.cubes()) {
            JsonObject cube = element.getAsJsonObject();
            if (cube.has("rotation")) {
                rotatedCubes.add(cube);
            } else {
                addCube(plainCubes, cube, p);
            }
        }
        PartDefinition part = parent.addOrReplaceChild(bone.name(), plainCubes, pose);

        // Vanilla cubes can't rotate on their own, so each rotated cube gets a child part (like Blockbench's Java export).
        for (int i = 0; i < rotatedCubes.size(); i++) {
            JsonObject cube = rotatedCubes.get(i);
            Vector3f cp = cube.has("pivot") ? vec(cube, "pivot") : p;
            CubeListBuilder builder = CubeListBuilder.create();
            addCube(builder, cube, cp);
            part.addOrReplaceChild(bone.name() + "_r" + (i + 1), builder,
                    pose(-(cp.x - p.x), -(cp.y - p.y), cp.z - p.z, vec(cube, "rotation")));
        }

        for (Bone child : children.getOrDefault(bone.name(), List.of())) {
            addBone(part, child, rootOrigin, p, children);
        }
    }

    /** Adds a cube positioned relative to {@code pivot} (Bedrock space). */
    private static void addCube(CubeListBuilder builder, JsonObject cube, Vector3f pivot) {
        Vector3f origin = vec(cube, "origin");
        Vector3f size = vec(cube, "size");
        JsonElement uv = cube.get("uv");
        if (uv == null || !uv.isJsonArray()) {
            throw new IllegalStateException("only box UV is supported (cube at " + origin + " uses per-face UV)");
        }
        JsonArray uvArray = uv.getAsJsonArray();
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0F;
        boolean mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
        builder.texOffs(uvArray.get(0).getAsInt(), uvArray.get(1).getAsInt())
                .mirror(mirror)
                .addBox(pivot.x - origin.x - size.x, pivot.y - origin.y - size.y, origin.z - pivot.z,
                        size.x, size.y, size.z, new CubeDeformation(inflate));
    }

    private static PartPose pose(float x, float y, float z, Vector3f rotationDegrees) {
        return PartPose.offsetAndRotation(x, y, z,
                -rotationDegrees.x * Mth.DEG_TO_RAD, -rotationDegrees.y * Mth.DEG_TO_RAD, rotationDegrees.z * Mth.DEG_TO_RAD);
    }

    private static Vector3f vec(JsonObject object, String key) {
        if (!object.has(key)) {
            return new Vector3f();
        }
        JsonArray array = object.getAsJsonArray(key);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
