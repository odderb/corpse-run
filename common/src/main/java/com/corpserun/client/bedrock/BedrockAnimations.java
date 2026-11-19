package com.corpserun.client.bedrock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Loads one animation from a Bedrock animation file ({@code .animation.json}, as exported by Blockbench) into a
 * vanilla {@link AnimationDefinition}.
 * <p>
 * Supported: position/rotation/scale channels made of numeric keyframes (a constant, or a map of time to value),
 * {@code pre}/{@code post} keyframes (the post value is used), linear and catmullrom interpolation (step and bezier
 * fall back to linear). Molang expressions aren't evaluated: a value that isn't a plain number counts as 0.
 */
public final class BedrockAnimations {

    private BedrockAnimations() {
    }

    /**
     * Finds the animation called {@code name}, matching either the full key or its last dot-separated part, so
     * {@code "float"} finds {@code "animation.ghost.float"}. Returns null if the file has no such animation.
     */
    @Nullable
    public static AnimationDefinition load(ResourceManager resources, ResourceLocation location, String name) throws IOException {
        try (Reader reader = resources.openAsReader(location)) {
            JsonObject animations = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                String key = entry.getKey();
                if (key.equals(name) || key.endsWith("." + name)) {
                    return parse(entry.getValue().getAsJsonObject());
                }
            }
            return null;
        } catch (IllegalStateException | ClassCastException | NullPointerException e) {
            throw new IOException("Malformed animation file " + location + ": " + e, e);
        }
    }

    static AnimationDefinition parse(JsonObject animation) {
        JsonElement loop = animation.get("loop");
        // "hold_on_last_frame" isn't looping: vanilla clamps a finished non-looping animation at its end anyway.
        boolean looping = loop != null && loop.isJsonPrimitive() && loop.getAsJsonPrimitive().isBoolean() && loop.getAsBoolean();
        float length = animation.has("animation_length") ? animation.get("animation_length").getAsFloat() : 0.0F;

        List<Map.Entry<String, AnimationChannel>> channels = new ArrayList<>();
        float lastKeyframe = 0.0F;
        if (animation.has("bones")) {
            for (Map.Entry<String, JsonElement> bone : animation.getAsJsonObject("bones").entrySet()) {
                JsonObject channelsJson = bone.getValue().getAsJsonObject();
                for (Channel type : Channel.values()) {
                    JsonElement channel = channelsJson.get(type.key);
                    if (channel != null) {
                        Keyframe[] keyframes = keyframes(channel, type);
                        lastKeyframe = Math.max(lastKeyframe, keyframes[keyframes.length - 1].timestamp());
                        channels.add(Map.entry(bone.getKey(), new AnimationChannel(type.target, keyframes)));
                    }
                }
            }
        }

        AnimationDefinition.Builder builder = AnimationDefinition.Builder.withLength(length > 0.0F ? length : Math.max(lastKeyframe, 0.05F));
        if (looping) {
            builder.looping();
        }
        channels.forEach(e -> builder.addAnimation(e.getKey(), e.getValue()));
        return builder.build();
    }

    private enum Channel {
        POSITION("position", AnimationChannel.Targets.POSITION),
        ROTATION("rotation", AnimationChannel.Targets.ROTATION),
        SCALE("scale", AnimationChannel.Targets.SCALE);

        final String key;
        final AnimationChannel.Target target;

        Channel(String key, AnimationChannel.Target target) {
            this.key = key;
            this.target = target;
        }

        /** Bedrock value to what the vanilla target adds (see BedrockGeometry for the axis flips). */
        Vector3f convert(Vector3f v) {
            return switch (this) {
                case POSITION -> new Vector3f(-v.x, -v.y, v.z);
                case ROTATION -> new Vector3f(-v.x * Mth.DEG_TO_RAD, -v.y * Mth.DEG_TO_RAD, v.z * Mth.DEG_TO_RAD);
                case SCALE -> new Vector3f(v.x - 1.0F, v.y - 1.0F, v.z - 1.0F);
            };
        }
    }

    private static Keyframe[] keyframes(JsonElement channel, Channel type) {
        List<Keyframe> keyframes = new ArrayList<>();
        if (channel.isJsonObject() && !isKeyframeObject(channel.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> entry : channel.getAsJsonObject().entrySet()) {
                keyframes.add(keyframe(Float.parseFloat(entry.getKey()), entry.getValue(), type));
            }
            keyframes.sort(Comparator.comparingDouble(Keyframe::timestamp));
        } else {
            keyframes.add(keyframe(0.0F, channel, type));
        }
        return keyframes.toArray(Keyframe[]::new);
    }

    /** A single keyframe written as {"post": ..., "lerp_mode": ...} rather than a time map. */
    private static boolean isKeyframeObject(JsonObject object) {
        return object.has("post") || object.has("pre") || object.has("vector") || object.has("lerp_mode");
    }

    private static Keyframe keyframe(float time, JsonElement value, Channel type) {
        AnimationChannel.Interpolation interpolation = AnimationChannel.Interpolations.LINEAR;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("lerp_mode") && "catmullrom".equals(object.get("lerp_mode").getAsString())) {
                interpolation = AnimationChannel.Interpolations.CATMULLROM;
            }
            value = object.has("post") ? object.get("post") : object.has("vector") ? object.get("vector") : object.get("pre");
            if (value != null && value.isJsonObject()) {   // {"post": {"vector": [...]}}
                value = value.getAsJsonObject().get("vector");
            }
        }
        return new Keyframe(time, type.convert(vector(value)), interpolation);
    }

    private static Vector3f vector(@Nullable JsonElement value) {
        if (value == null) {
            return new Vector3f();
        }
        if (value.isJsonArray()) {
            return new Vector3f(number(value.getAsJsonArray().get(0)), number(value.getAsJsonArray().get(1)),
                    number(value.getAsJsonArray().get(2)));
        }
        float uniform = number(value);   // e.g. "scale": 1.2
        return new Vector3f(uniform, uniform, uniform);
    }

    private static float number(JsonElement element) {
        try {
            return element.getAsFloat();
        } catch (NumberFormatException | UnsupportedOperationException | IllegalStateException e) {
            return 0.0F;   // Molang expression
        }
    }
}
