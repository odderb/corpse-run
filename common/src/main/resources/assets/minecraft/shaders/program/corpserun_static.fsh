#version 150

// CorpseRun ghost effect: the world in shades of one color (periwinkle) under chunky, pixel-art TV static.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;      // 0..1, loops every second
uniform float Strength;  // how much static is mixed in
uniform float Cells;     // static "pixels" from top to bottom of the screen (resolution independent)
uniform float Levels;    // gray shades the static is quantized to
uniform vec3 Tint;       // the color the world is shown in; only its hue matters, brightness is kept
uniform float Shadows;   // contrast curve on brightness: 1 = unchanged, higher = darker shadows and corners
uniform float Vibrance;  // how far the tint is pushed from gray: 1 = Tint as given, higher = more saturated
uniform float Rate;      // new static patterns per second (a whole number, so the loop is seamless)

in vec2 texCoord;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float gray = dot(scene, vec3(0.299, 0.587, 0.114));
    // Pushes dark values down much more than bright ones (pure white stays white), deepening ambient occlusion,
    // crevices and caves. Done on screen so it works the same with any chunk renderer or shader pack.
    gray = pow(gray, Shadows);

    // Square cells sized from the screen height, so the static looks the same at any resolution.
    float cellSize = max(1.0, floor(OutSize.y / Cells));
    vec2 cell = floor(texCoord * OutSize / cellSize);
    // A few patterns per second, each fading into the next, so the static drifts rather than flickers. The next
    // pattern wraps back to 0 with Time, keeping the one-second loop seamless.
    float t = Time * Rate;
    float frame = floor(t);
    float next = mod(frame + 1.0, Rate);
    float a = floor(hash(vec3(cell, frame)) * Levels) / (Levels - 1.0);
    float b = floor(hash(vec3(cell, next)) * Levels) / (Levels - 1.0);
    float noise = mix(a, b, smoothstep(0.0, 1.0, fract(t)));

    float value = mix(gray, noise, Strength);
    // Tint scaled to unit brightness, so tinting changes the color without darkening the scene, then pushed
    // away from gray by Vibrance.
    const vec3 luma = vec3(0.299, 0.587, 0.114);
    vec3 tint = Tint / dot(Tint, luma);
    tint = mix(vec3(1.0), tint, Vibrance);
    vec3 color = value * tint;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
