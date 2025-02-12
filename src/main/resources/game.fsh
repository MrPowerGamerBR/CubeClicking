#version 300 es
precision highp float;

in vec3 fragPos;
out vec4 FragColor;

uniform vec3 cameraPos; // Camera position in world space
uniform bool isActive;
uniform float time;

void main()
{
    // Calculate the distance between the fragment (mesh vertex) and the camera
    float distance = length(fragPos - cameraPos); // Euclidean distance

    if (isActive) {
        float pulsation = (sin(time * 2.0) + 1.0) / 2.0; // Normalize sine to 0-1 range
        FragColor = vec4(0.0f, (0.25f * pulsation) + 0.75f, 0.0f, 1.0f);
    } else {
        float targetColor = 1.0f - (distance * 0.05f);
        FragColor = vec4(targetColor, targetColor, targetColor, 1.0f);
    }
}