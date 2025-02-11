#version 330 core

layout (location = 0) in vec2 vertex; // vec2<position>, vec2<texCoords>

uniform mat4 model;
uniform mat4 projection;

void main() {
    gl_Position = projection * model * vec4(vertex.x, vertex.y, 0.0f, 1.0f);
}