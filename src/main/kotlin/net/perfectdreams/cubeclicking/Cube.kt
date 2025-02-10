package net.perfectdreams.cubeclicking

import org.joml.Vector3f

class Cube(val mesh: Mesh) {
    // These are WORLD COORDINATES
    val aabbMin = mesh.position.sub(Vector3f(1f, 1f, 1f), Vector3f())
    val aabbMax = mesh.position.add(Vector3f(1f, 1f, 1f), Vector3f())

    var isActive = false
}