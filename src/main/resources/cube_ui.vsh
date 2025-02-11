#version 330 core

layout (location = 0) in vec3 vertex;
layout (location = 1) in vec3 colors;

uniform mat4 model;
uniform mat4 projection;

out vec3 fragColors;

void main() {
    fragColors = colors;
    gl_Position = projection * model * vec4(vertex.x, vertex.y, vertex.z, 1.0f);
}