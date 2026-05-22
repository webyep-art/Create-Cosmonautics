#version 150

uniform float GameTime;

in vec2 texCoord0;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 x) {
    vec2 p = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float n = p.x + p.y * 57.0;
    return mix(mix(hash(p + vec2(0.0, 0.0)), hash(p + vec2(1.0, 0.0)), f.x),
               mix(hash(p + vec2(0.0, 1.0)), hash(p + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 pUv = floor(texCoord0 * 64.0) / 64.0;
    
    float t = GameTime * 1000.0;
    
    float n1 = noise(pUv * 5.0 + vec2(t * 0.1, t * 0.15));
    float n2 = noise(pUv * 10.0 - vec2(t * 0.2, t * 0.1));
    float n3 = noise(pUv * 20.0 + vec2(t * 0.05, -t * 0.25));
    
    float totalNoise = (n1 * 0.5 + n2 * 0.3 + n3 * 0.2);
    
    vec3 colorCore = vec3(1.0, 0.9, 0.6);
    vec3 colorMid = vec3(1.0, 0.5, 0.1);
    vec3 colorEdge = vec3(0.8, 0.1, 0.0);
    
    vec3 finalColor;
    if (totalNoise > 0.6) {
        finalColor = mix(colorMid, colorCore, (totalNoise - 0.6) * 2.5);
    } else {
        finalColor = mix(colorEdge, colorMid, totalNoise / 0.6);
    }
    
    fragColor = vec4(finalColor, 1.0);
}
