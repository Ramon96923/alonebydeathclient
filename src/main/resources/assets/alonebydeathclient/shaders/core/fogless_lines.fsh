#version 330
#extension GL_ARB_separate_shader_objects : require
// Adapted from Wurst Client (https://github.com/Wurst-Imperium/Wurst7), GPL-3.0.

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor * ColorModulator;
}
