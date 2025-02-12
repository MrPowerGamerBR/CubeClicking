package net.perfectdreams.cubeclicking

import net.perfectdreams.harmony.math.Matrix4f
import net.perfectdreams.harmony.math.Vector4f
import org.joml.*
import org.lwjgl.Version
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengles.GLES
import org.lwjgl.opengles.GLES32
import org.lwjgl.opengles.GLES32.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengles.GLES32.GL_DEPTH_BUFFER_BIT
import org.lwjgl.opengles.GLES32.*
import org.lwjgl.opengles.GLES32.glBindVertexArray
import org.lwjgl.opengles.GLES32.glUseProgram
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.system.MemoryUtil
import java.lang.Math
import java.nio.IntBuffer
import java.util.Random
import kotlin.math.max
import kotlin.math.min

class CubeClicking {
    // The window handle
    private var window: Long = 0
    val shaderManager = ShaderManager()

    val windowWidth = 1920
    val windowHeight = 1080

    val loadedCubes = mutableListOf<Cube>()

    // Camera position
    lateinit var cameraPosition: Vector3f

    // Projection Matrix
    lateinit var projection: Matrix4f
    var useOrthographicProjection = false

    // Camera matrix
    lateinit var view: Matrix4f
    var cameraRotationY = 20.0f

    fun run() {
        println("Hello LWJGL " + Version.getVersion() + "!")

        init()
        loop()

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window)
        glfwDestroyWindow(window)

        // Terminate GLFW and free the error callback
        glfwTerminate()
        glfwSetErrorCallback(null)!!.free()
    }

    private fun init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set()

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        check(glfwInit()) { "Unable to initialize GLFW" }

        // Configure GLFW
        glfwDefaultWindowHints() // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE) // the window will be resizable

        // Set GLFW to use OpenGL Core Profile
        // OpenGL 3.3
        glfwWindowHint(GLFW_CONTEXT_CREATION_API, GLFW_EGL_CONTEXT_API)
        glfwWindowHint(GLFW_CLIENT_API, GLFW_OPENGL_ES_API)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 0)

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, "Hello World!", MemoryUtil.NULL, MemoryUtil.NULL)
        if (window == MemoryUtil.NULL) throw RuntimeException("Failed to create the GLFW window")

        // Setup a key callback. It will be called every time a key is pressed, repeated or released.
        glfwSetKeyCallback(window) { window, key, scancode, action, mods ->
            // We will detect this in the rendering loop
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(
                    window,
                    true
                )
            }

            if (key == GLFW_KEY_A && action == GLFW_RELEASE) {
                cameraRotationY += 10.0f
                updateViewMatrix()
            }

            if (key == GLFW_KEY_D && action == GLFW_RELEASE) {
                cameraRotationY -= 10.0f
                updateViewMatrix()
            }

            if (key == GLFW_KEY_W && action == GLFW_RELEASE) {
                useOrthographicProjection = !useOrthographicProjection
                updateProjectionMatrix()
            }
        }

        glfwSetMouseButtonCallback(window) { window, button, action, mods ->
            val mousePos = MemoryStack.stackPush().use { stack ->
                val mouseXBuffer = stack.mallocDouble(1)
                val mouseYBuffer = stack.mallocDouble(1)

                GLFW.glfwGetCursorPos(window, mouseXBuffer, mouseYBuffer)

                // These are screen coordinates
                val mouseX = mouseXBuffer.get()
                val mouseY = mouseYBuffer.get()

                Vector2f(mouseX.toFloat(), mouseY.toFloat())
            }

            processMouseClick(mousePos, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        }

        stackPush().use { stack ->
            val pWidth: IntBuffer = stack.mallocInt(1) // int*
            val pHeight: IntBuffer = stack.mallocInt(1) // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight)

            // Get the resolution of the primary monitor
            val vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor())!!

            // Center the window
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth[0]) / 2,
                (vidmode.height() - pHeight[0]) / 2
            )
        }

        // Make the OpenGL context current
        glfwMakeContextCurrent(window)
        // Enable v-sync
        glfwSwapInterval(1)

        // Make the window visible
        glfwShowWindow(window)
    }

    var cubeVAO = -1

    private fun loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.
        GLES.createCapabilities()
        glEnable(GLES32.GL_DEPTH_TEST)

        updateProjectionMatrix()
        updateViewMatrix()

        val programId = shaderManager.loadShader("game.vsh", "game.fsh")
        val programUIId = shaderManager.loadShader("ui.vsh", "ui.fsh")
        val programCubeUIId = shaderManager.loadShader("cube_ui.vsh", "cube_ui.fsh")

        val cubeVAO = initRender()
        this.cubeVAO = cubeVAO

        // Set the clear color
        glClearColor(1.0f, 0.0f, 0.0f, 0.0f)

        val cube1 = Cube(Mesh(cubeVAO, Vector3f(0f, 0f, 0f)))
        val cube2 = Cube(Mesh(cubeVAO, Vector3f(-3f, 0f, 0f)))
        val cube3 = Cube(Mesh(cubeVAO, Vector3f(-3f, 0f, -3f)))

        cube1.isActive = true

        val spriteVAO = initRenderSprite()

        // This is intentionally in reverse to test the mouse click raytracing depth checks
        // Example: Clicking on the cube1 while cube3 is behind it should click cube1, NOT cube3!
        loadedCubes.add(cube3)
        loadedCubes.add(cube2)
        loadedCubes.add(cube1)

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT) // clear the framebuffer

            for (cube in loadedCubes) {
                drawCube(
                    programId,
                    cube.mesh.vaoId,
                    cube.mesh.position,
                    cube.isActive,
                    12 * 3
                )
            }

            drawSprite(programUIId, spriteVAO, 1, Vector2f(0f, 0f), Vector2f(32f, 32f))

            drawCubeAsUIElement(programCubeUIId, cubeVAO, Vector2f(windowWidth - 32f, windowHeight - 32f), Vector2f(32f, 32f))

            glfwSwapBuffers(window) // swap the color buffers

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents()
        }
    }

    /**
     * Updates the current projection matrix
     */
    private fun updateProjectionMatrix() {
        // We have a function to update the view matrix because we can switch the projection during runtime
        val projection = Matrix4f()

        if (this.useOrthographicProjection) {
            // We don't use the window width/height because that changes the aspect ratio of the projection and that looks a bit wonky
            projection.ortho(-4f, 4f, -4f, 4f, 0.5f, 10000.0f, false)
        } else {
            // Projection matrix: 45° Field of View, 4:3 ratio, display range: 0.1 unit <-> 100 units
            projection.perspectiveGeneric(Math.toRadians(45.0).toFloat(), windowWidth.toFloat() / windowHeight.toFloat(), 0.1f, 100.0f, false, projection)
        }
        
        this.projection = projection
    }

    /**
     * Updates the current view matrix
     */
    private fun updateViewMatrix() {
        // We have a function to update the view matrix due to the camera rotation
        this.cameraPosition = Vector3f(4f, 3f, 3f) // Camera is at (4,3,3), in World Space
            .rotateY(Math.toRadians(this.cameraRotationY.toDouble()).toFloat())

        println("Camera Position: ${this.cameraPosition.x}, ${this.cameraPosition.y}, ${this.cameraPosition.z}")

        val newView = Matrix4f()
        newView.lookAtGeneric(
            this.cameraPosition.x, this.cameraPosition.y, this.cameraPosition.z,
            0f, 0f, 0f,
            0f, 1f, 0f, // Head is up (set to 0,-1,0 to look upside-down)
            newView
        )
        this.view = newView
    }

    // VAO = Vertex Array Object
    // VBO = Vertex Buffer Object
    private fun initRender(): Int {
        // A cube!
        val vertices = floatArrayOf(
            -1.0f,-1.0f,-1.0f, // triangle 1 : begin
            -1.0f,-1.0f, 1.0f,
            -1.0f, 1.0f, 1.0f, // triangle 1 : end
            1.0f, 1.0f,-1.0f, // triangle 2 : begin
            -1.0f,-1.0f,-1.0f,
            -1.0f, 1.0f,-1.0f, // triangle 2 : end
            1.0f,-1.0f, 1.0f,
            -1.0f,-1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f, 1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f,-1.0f,
            1.0f,-1.0f, 1.0f,
            -1.0f,-1.0f, 1.0f,
            -1.0f,-1.0f,-1.0f,
            -1.0f, 1.0f, 1.0f,
            -1.0f,-1.0f, 1.0f,
            1.0f,-1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f, 1.0f,-1.0f,
            1.0f,-1.0f,-1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f,-1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f, 1.0f,-1.0f,
            -1.0f, 1.0f,-1.0f,
            1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f,-1.0f,
            -1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 1.0f,
            1.0f,-1.0f, 1.0f
        )

        // Random colors! (Only used for the cube in the UI layer)
        val randomColors = (0 until vertices.size).map {
            Random().nextFloat()
        }.toFloatArray()

        // Create and bind VAO
        val quadVAO = GLES32.glGenVertexArrays()
        glBindVertexArray(quadVAO)

        // Generate two VBOs (one for the vertex positions, another for the colors)
        val vbosArray = IntArray(2)
        glGenBuffers(vbosArray)

        // Position VBO
        glBindBuffer(GL_ARRAY_BUFFER, vbosArray[0])
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        // When reading pointers like this, think like this
        // The "size" is how much is the TARGET array that will be passed to the vertex shader
        // The "stride" is how much data WILL BE READ
        // The "pointer" is WHERE the data is in the ARRAY THAT WAS READ
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(0)

        // Colors VBO
        glBindBuffer(GL_ARRAY_BUFFER, vbosArray[1])
        glBufferData(GL_ARRAY_BUFFER, randomColors, GL_STATIC_DRAW)
        // When reading pointers like this, think like this
        // The "size" is how much is the TARGET array that will be passed to the vertex shader
        // The "stride" is how much data WILL BE READ
        // The "pointer" is WHERE the data is in the ARRAY THAT WAS READ
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(1)

        // Unbind
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        // You technically don't need to disable these
        glDisableVertexAttribArray(0)
        glDisableVertexAttribArray(1)

        return quadVAO
    }

    fun drawCube(programId: Int, quadVAO: Int, position: Vector3f, isActive: Boolean, triangleCount: Int) {
        glUseProgram(programId)

        // val location = glGetUniformLocation(programId, "MVP")
        val modelLocation = glGetUniformLocation(programId, "model")
        val viewLocation = glGetUniformLocation(programId, "view")
        val projectionLocation = glGetUniformLocation(programId, "projection")
        val isActiveLocation = glGetUniformLocation(programId, "isActive")
        val timeLocation = glGetUniformLocation(programId, "time")
        val cameraPositionLocation = glGetUniformLocation(programId, "cameraPos")

        // Model matrix: where the mesh is in the world
        val model = Matrix4f()
            .translate(position.x, position.y, position.z)

        // Our ModelViewProjection: multiplication of our 3 matrices
        // val mvp = projection.mul(view, Matrix4f()).mul(model, Matrix4f()) // Remember, matrix multiplication is the other way around

        glUniformMatrix4fv(modelLocation, false, model.getAsFloatArray())
        glUniformMatrix4fv(viewLocation, false, view.getAsFloatArray())
        glUniformMatrix4fv(projectionLocation, false, projection.getAsFloatArray())
        glUniform1i(isActiveLocation, if (isActive) 1 else 0)
        glUniform1f(timeLocation, GLFW.glfwGetTime().toFloat())
        glUniform3f(cameraPositionLocation, cameraPosition.x, cameraPosition.y, cameraPosition.z)

        glBindVertexArray(quadVAO)
        glDrawArrays(GL_TRIANGLES, 0, triangleCount)
        glBindVertexArray(0)
    }

    private fun processMouseClick(mousePos: Vector2f, spawnCubeIfNotConflicting: Boolean) {
        // OpenGL uses a coordinate system where:
        //    X: [−1,1][−1,1] (left to right)
        //    Y: [−1,1][−1,1] (bottom to top)
        //    Z: [−1,1][−1,1] (near to far)
        println("MouseX: ${mousePos.x}, MouseY: ${mousePos.y}")

        val ndcX = (2.0f * mousePos.x) / windowWidth - 1.0f
        val ndcY = 1.0f - (2.0f * mousePos.y) / windowHeight
        val ndcZ = -1.0f // Start with near plane

        println("Normalized Device Coordinates: $ndcX, $ndcY, $ndcZ")

        val invProjection = Matrix4f(projection).invert()
        val invView = Matrix4f(view).invert()

        // The point near the screen
        val nearPoint = Vector4f(ndcX, ndcY, -1.0f, 1.0f)

        // The point far from the screen
        val farPoint = Vector4f(ndcX, ndcY, 1.0f, 1.0f)

        // Now we need to transform these points into world coordinates!
        nearPoint.mul(invProjection).mul(invView)
        farPoint.mul(invProjection).mul(invView)

        println("Near point: ${nearPoint.x}, ${nearPoint.y}, ${nearPoint.z}")
        println("Far point: ${farPoint.x}, ${farPoint.y}, ${farPoint.z}")

        // Normalize the points
        nearPoint.div(nearPoint.w)
        farPoint.div(farPoint.w)

        val rayOrigin = Vector3f(nearPoint.x, nearPoint.y, nearPoint.z)
        // The ray end does not mean the end of the ray, it is mostly used to find out the direction of the ray
        val rayEnd = Vector3f(farPoint.x, farPoint.y, farPoint.z)
        val rayDirection = rayEnd.sub(rayOrigin).normalize()

        println("Ray Origin: ${rayOrigin.x}, ${rayOrigin.y}, ${rayOrigin.z}")
        println("Ray End: ${rayEnd.x}, ${rayEnd.y}, ${rayEnd.z}")
        println("Ray Direction: ${rayDirection.x}, ${rayDirection.y}, ${rayDirection.z}")

        var intersectedCube: Cube? = null
        var closestCube: Float = Float.MAX_VALUE

        for (cube in loadedCubes) {
            // We use distanceSquared because that's more performant compared to distance
            // (distance uses a sqrt call)
            val distance = rayOrigin.distanceSquared(cube.mesh.position)

            if (closestCube > distance) {
                println("Cube AABB Min: ${cube.aabbMin.x}, ${cube.aabbMin.y}, ${cube.aabbMin.z}")
                println("Cube AABB Max: ${cube.aabbMax.x}, ${cube.aabbMax.y}, ${cube.aabbMax.z}")

                val doesIntersect = rayTrace(rayOrigin, rayDirection, cube.aabbMin, cube.aabbMax)

                println("Does Intersect? $doesIntersect")

                if (doesIntersect) {
                    intersectedCube = cube
                    closestCube = distance
                    // While it may be tempting to do a "break", we CANNOT break here!
                    // The cube that we are querying right now may not be the closest cube, this fixes things like clicking on cube1 while cube3 is behind
                    // The cube1 SHOULD be clicked, NOT cube3!
                }
            }
        }

        for (cube in loadedCubes) {
            cube.isActive = false
        }

        if (intersectedCube != null) {
            intersectedCube.isActive = true
        } else if (spawnCubeIfNotConflicting) {
            // If we didn't intersect any cube, let's attempt to spawn a new cube!
            val t = -rayOrigin.y / rayDirection.y
            val target = rayOrigin.plus(rayDirection.mul(t))

            println("found! ${target.x}, ${target.y}, ${target.z}")
            println("ray origin ${rayOrigin.x}, ${rayOrigin.y}, ${rayOrigin.z}")

            val loadedCube = Cube(Mesh(cubeVAO, Vector3f(target.x, target.y, target.z)))

            loadedCubes.add(loadedCube)
        }
    }

    /**
     * Raytraces from [rayOrigin] looking at [rayDirection], checking if the ray intersects the AABB formed by [min] [max]
     *
     * @param rayOrigin    the origin of the ray
     * @param rayDirection the direction of the ray
     * @param min          min AABB
     * @param max          max AABB
     * @return true if the ray intersects with the bounding box
     */
    // Thanks ChatGPT
    private fun rayTrace(rayOrigin: Vector3f, rayDirection: Vector3f, min: Vector3f, max: Vector3f): Boolean {
        var (txMin, txMax) = getIntersectionRange(min.x, max.x, rayOrigin.x, rayDirection.x)
        val (tyMin, tyMax) = getIntersectionRange(min.y, max.y, rayOrigin.y, rayDirection.y)

        // Quick fail!
        if ((txMin > tyMax) || (tyMin > txMax)) return false

        if (tyMin > txMin) txMin = tyMin
        if (tyMax < txMax) txMax = tyMax

        val (tzMin, tzMax) = getIntersectionRange(min.z, max.z, rayOrigin.z, rayDirection.z)

        if ((txMin > tzMax) || (tzMin > txMax)) return false

        return true
    }

    // Helper function to calculate the intersection range on one axis and return the sorted min/max values.
    private fun getIntersectionRange(min: Float, max: Float, rayOrigin: Float, rayDirection: Float): Pair<Float, Float> {
        // Optimization: saving a few divisions below
        // Thanks CraftBukkit's BoundingBox!
        val div = 1.0f / rayDirection

        val tMin = (min - rayOrigin) * div
        val tMax = (max - rayOrigin) * div
        return minOf(tMin, tMax) to maxOf(tMin, tMax) // Returns the sorted pair (min, max)
    }

    /**
     * Raytraces from [rayOrigin] looking at [rayDirection], checking if the ray intersects the AABB formed by [min] [max]
     *
     * @param rayOrigin    the origin of the ray
     * @param rayDirection the direction of the ray
     * @param min          min AABB
     * @param max          max AABB
     * @return true if the ray intersects with the bounding box
     */
    // This is an alternative ray tracing implementation, based on https://gamedev.stackexchange.com/a/18459/160509
    private fun rayTrace2(rayOrigin: Vector3f, rayDirection: Vector3f, min: Vector3f, max: Vector3f): Boolean {
        // r.dir is unit direction vector of ray
        val dirfrac = Vector3f()
        dirfrac.x = 1.0f / rayDirection.x
        dirfrac.y = 1.0f / rayDirection.y
        dirfrac.z = 1.0f / rayDirection.z

        // lb is the corner of AABB with minimal coordinates - left bottom, rt is maximal corner
        // r.org is origin of ray
        val t1 = (min.x - rayOrigin.x) * dirfrac.x
        val t2 = (max.x - rayOrigin.x) * dirfrac.x
        val t3 = (min.y - rayOrigin.y) * dirfrac.y
        val t4 = (max.y - rayOrigin.y) * dirfrac.y
        val t5 = (min.z - rayOrigin.z) * dirfrac.z
        val t6 = (max.z - rayOrigin.z) * dirfrac.z

        val tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6))
        val tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6))

        // if tmax < 0, ray (line) is intersecting AABB, but the whole AABB is behind us
        if (tmax < 0) {
            return false
        }

        // if tmin > tmax, ray doesn't intersect AABB
        if (tmin > tmax) {
            return false
        }

        return true
    }

    // VAO = Vertex Array Object
    // VBO = Vertex Buffer Object
    private fun initRenderSprite(): Int {
        val vbo = glGenBuffers()
        val vertices = floatArrayOf(
            // pos
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 0.0f,

            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f,
        )

        val quadVAO = GLES32.glGenVertexArrays()

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

        glBindVertexArray(quadVAO)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        return quadVAO
    }

    fun drawSprite(programId: Int, quadVAO: Int, textureId: Int, position: Vector2f, size: Vector2f) {
        glUseProgram(programId)

        // glActiveTexture(GL_TEXTURE0)
        // glBindTexture(GL_TEXTURE_2D, textureId)

        // Model matrix: where the mesh is in the world
        val model = Matrix4f()
        model.translate(position.x, position.y, 0.0f)
        model.scale(size.x, size.y, 1.0f)

        val projection = Matrix4f().ortho(0.0f, windowWidth.toFloat(), windowHeight.toFloat(), 0.0f, -1.0f, 1.0f, false)

        val projectionLocation = glGetUniformLocation(programId, "projection")
        val modelLocation = glGetUniformLocation(programId, "model")
        glUniformMatrix4fv(projectionLocation, false, projection.getAsFloatArray())
        glUniformMatrix4fv(modelLocation, false, model.getAsFloatArray())

        // Our ModelViewProjection: multiplication of our 3 matrices
        // val mvp = projection.mul(view, Matrix4f()).mul(model, Matrix4f()) // Remember, matrix multiplication is the other way around

        glBindVertexArray(quadVAO)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindVertexArray(0)
    }

    fun drawCubeAsUIElement(programId: Int, quadVAO: Int, position: Vector2f, size: Vector2f) {
        glUseProgram(programId)

        // val location = glGetUniformLocation(programId, "MVP")
        val modelLocation = glGetUniformLocation(programId, "model")
        val viewLocation = glGetUniformLocation(programId, "view")
        val projectionLocation = glGetUniformLocation(programId, "projection")
        val isActiveLocation = glGetUniformLocation(programId, "isActive")
        val timeLocation = glGetUniformLocation(programId, "time")
        val cameraPositionLocation = glGetUniformLocation(programId, "cameraPos")

        // Model matrix: where the mesh is in the world
        val model = Matrix4f()
        // stay away from me
        model.translate(position.x, position.y, 0.0f)
        model.scale(size.x, size.y, 1.0f)
        model.rotateY(GLFW.glfwGetTime().toFloat())

        // Our ModelViewProjection: multiplication of our 3 matrices
        // val mvp = projection.mul(view, Matrix4f()).mul(model, Matrix4f()) // Remember, matrix multiplication is the other way around

        glUniformMatrix4fv(modelLocation, false, model.getAsFloatArray())
        glUniformMatrix4fv(viewLocation, false, view.getAsFloatArray())
        // The near/far values are necessary to avoid the box clipping, if the near value is near 0 (heh), the box won't be fully rendered (because it is clipping inside)
        glUniformMatrix4fv(projectionLocation, false, Matrix4f().ortho(0.0f, windowWidth.toFloat(), windowHeight.toFloat(), 0.0f, -16.0f, 1.0f, false).getAsFloatArray())
        glUniform1i(isActiveLocation, 0)
        glUniform1f(timeLocation, GLFW.glfwGetTime().toFloat())
        glUniform3f(cameraPositionLocation, cameraPosition.x, cameraPosition.y, cameraPosition.z)

        glBindVertexArray(quadVAO)
        glDrawArrays(GL_TRIANGLES, 0, 12 * 3)
        glBindVertexArray(0)
    }
}