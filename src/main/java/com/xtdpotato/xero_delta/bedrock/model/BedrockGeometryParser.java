package com.xtdpotato.xero_delta.bedrock.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BedrockGeometryParser {
    public BedrockModel parse(JsonObject root) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = integer(description, "texture_width", 64);
        int textureHeight = integer(description, "texture_height", 64);
        List<BedrockBone> bones = new ArrayList<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            String parent = bone.has("parent") ? bone.get("parent").getAsString() : null;
            List<BedrockCube> cubes = new ArrayList<>();
            if (bone.has("cubes")) {
                for (JsonElement cube : bone.getAsJsonArray("cubes")) {
                    cubes.add(parseCube(cube.getAsJsonObject()));
                }
            }
            List<BedrockLocator> locators = parseLocators(bone);
            bones.add(new BedrockBone(name, parent, vector(bone.get("pivot"), BedrockVec3.ZERO),
                vector(bone.get("rotation"), BedrockVec3.ZERO), cubes, locators));
        }
        return new BedrockModel(description.get("identifier").getAsString(), textureWidth, textureHeight, bones);
    }

    private List<BedrockLocator> parseLocators(JsonObject bone) {
        if (!bone.has("locators")) return List.of();
        List<BedrockLocator> result = new ArrayList<>();
        for (var entry : bone.getAsJsonObject("locators").entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonArray()) {
                result.add(new BedrockLocator(entry.getKey(), vector(value, BedrockVec3.ZERO), BedrockVec3.ZERO));
            } else {
                JsonObject locator = value.getAsJsonObject();
                result.add(new BedrockLocator(entry.getKey(), vector(locator.get("offset"), BedrockVec3.ZERO),
                    vector(locator.get("rotation"), BedrockVec3.ZERO)));
            }
        }
        return List.copyOf(result);
    }

    private BedrockCube parseCube(JsonObject cube) {
        BedrockVec3 origin = vector(cube.get("origin"), BedrockVec3.ZERO);
        BedrockVec3 size = vector(cube.get("size"), BedrockVec3.ZERO);
        Map<String, BedrockFace> faces = cube.get("uv").isJsonArray()
            ? boxFaces(cube.getAsJsonArray("uv"), size)
            : explicitFaces(cube.getAsJsonObject("uv"));
        return new BedrockCube(origin, size, vector(cube.get("pivot"), BedrockVec3.ZERO),
            vector(cube.get("rotation"), BedrockVec3.ZERO), number(cube, "inflate", 0),
            cube.has("mirror") && cube.get("mirror").getAsBoolean(), Map.copyOf(faces));
    }

    private Map<String, BedrockFace> explicitFaces(JsonObject uv) {
        Map<String, BedrockFace> faces = new HashMap<>();
        for (String name : List.of("north", "south", "east", "west", "up", "down")) {
            if (!uv.has(name)) continue;
            JsonObject face = uv.getAsJsonObject(name);
            JsonArray point = face.getAsJsonArray("uv");
            JsonArray size = face.getAsJsonArray("uv_size");
            faces.put(name, new BedrockFace(point.get(0).getAsDouble(), point.get(1).getAsDouble(),
                size.get(0).getAsDouble(), size.get(1).getAsDouble()));
        }
        return faces;
    }

    private Map<String, BedrockFace> boxFaces(JsonArray uv, BedrockVec3 size) {
        double u = uv.get(0).getAsDouble();
        double v = uv.get(1).getAsDouble();
        double x = size.x(), y = size.y(), z = size.z();
        return Map.of(
            "west", new BedrockFace(u, v + z, z, y),
            "north", new BedrockFace(u + z, v + z, x, y),
            "east", new BedrockFace(u + z + x, v + z, z, y),
            "south", new BedrockFace(u + z + x + z, v + z, x, y),
            "up", new BedrockFace(u + z, v, x, z),
            "down", new BedrockFace(u + z + x, v, x, z));
    }

    public static BedrockVec3 vector(JsonElement element, BedrockVec3 fallback) {
        if (element == null || !element.isJsonArray()) return fallback;
        JsonArray array = element.getAsJsonArray();
        return new BedrockVec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static int integer(JsonObject object, String name, int fallback) {
        return object.has(name) ? object.get(name).getAsInt() : fallback;
    }

    private static double number(JsonObject object, String name, double fallback) {
        return object.has(name) ? object.get(name).getAsDouble() : fallback;
    }
}
