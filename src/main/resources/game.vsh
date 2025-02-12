#version 300 es
precision highp float;

layout (location = 0) in vec3 aPos;

// Values that stay constant for the whole mesh.
uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec3 fragPos; // Pass the vertex position to the fragment shader

void main() {
    fragPos = vec3(model * vec4(aPos, 1.0));  // Calculate the vertex position in world space
    mat4 mvp = projection * view * model;

    gl_Position = mvp * vec4(aPos.x, aPos.y, aPos.z, 1.0);
}