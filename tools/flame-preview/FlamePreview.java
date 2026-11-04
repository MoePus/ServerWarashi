import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.lwjgl.opengl.GL33.*;

/**
 * Offline renderer for the world flame overlay (world_perf_object.vsh/.fsh).
 *
 * <p>Compiles the real shader resources, fills the marker texture buffer exactly like
 * WorldPerfObjectBuffers.encodeMarker, draws the batches with the game's blend state into a
 * virtual game-sized framebuffer over a furnace on stone, then crops around each flame.
 * Launch it through preview.sh; see README.md for the options.
 */
public class FlamePreview {
    private static final int[] SEVERITY_COLORS = {0x40DC78, 0xFFD640, 0xFF9120, 0xF44336, 0xBE2DDC};
    private static final int TEXELS_PER_MARKER = 4;
    private static final String[] BLOCK_TEXTURES = {"furnace_front_on", "furnace_side", "furnace_top", "stone"};
    private static final String DEFAULT_BATCHES =
            "0:0:SPARK_PARTICLE_START;1:SPARK_PARTICLE_START:SPARK_PARTICLE_COUNT";

    private record Batch(int pass, int start, int slots) {
    }

    private static final Map<String, String> options = new HashMap<>();
    private static int screenWidth;
    private static int screenHeight;
    private static int cropWidth;
    private static int cropHeight;
    private static float fov;
    private static float yaw;
    private static float pitch;
    private static float distance;
    private static int program;
    private static int blockProgram;
    private static int quadVao;
    private static int markerBuffer;
    private static int markerTexture;
    private static int glyphTexture;
    private static int framebuffer;
    private static int blockVao;
    private static final Map<String, Integer> blockTextures = new HashMap<>();
    private static final List<int[]> blockRanges = new ArrayList<>();
    private static List<Batch> batches;

    public static void main(String[] args) throws Exception {
        for (String argument : args) {
            int split = argument.indexOf('=');
            if (split < 0) throw new IllegalArgumentException("Expected key=value, got " + argument);
            options.put(argument.substring(0, split), argument.substring(split + 1));
        }
        int[] screen = size(option("screen", "1920x1080"));
        screenWidth = screen[0];
        screenHeight = screen[1];
        int[] crop = size(option("crop", "220x380"));
        cropWidth = crop[0];
        cropHeight = crop[1];
        fov = Float.parseFloat(option("fov", "70"));
        yaw = Float.parseFloat(option("yaw", "25"));
        pitch = Float.parseFloat(option("pitch", "12"));
        distance = Float.parseFloat(option("distance", "6"));
        int zoom = Integer.parseInt(option("zoom", "1"));
        double[] strengths = Arrays.stream(option("strengths", "1,2,3,4,5").split(","))
                .mapToDouble(Double::parseDouble).toArray();
        String[] backgrounds = option("bg", "day,night").split(",");
        int columns = Integer.parseInt(option("cols", "6"));
        float timeStep = Float.parseFloat(option("dt", "0.12"));
        float startTime = Float.parseFloat(option("t0", "3.0"));
        float gifSeconds = Float.parseFloat(option("gif", "0"));
        String out = options.get("out");

        Path shaderDir = Path.of(options.get("shaders"));
        String vertex = Files.readString(shaderDir.resolve("world_perf_object.vsh"));
        String fragment = Files.readString(shaderDir.resolve("world_perf_object.fsh"));
        for (String assignment : option("set", "").split(";")) {
            if (assignment.isBlank()) continue;
            String[] pair = assignment.split("=", 2);
            Pattern constant = Pattern.compile("(const\\s+\\w+\\s+" + pair[0].trim() + "\\s*=\\s*)[^;]+;");
            if (!constant.matcher(vertex).find() && !constant.matcher(fragment).find()) {
                throw new IllegalArgumentException("No shader constant " + pair[0]);
            }
            String replacement = "$1" + Matcher.quoteReplacement(pair[1].trim()) + ";";
            vertex = constant.matcher(vertex).replaceAll(replacement);
            fragment = constant.matcher(fragment).replaceAll(replacement);
        }
        batches = parseBatches(option("batches", DEFAULT_BATCHES), parseConstants(vertex));

        initGl();
        program = link(vertex, fragment, "Position");
        blockProgram = link(BLOCK_VERTEX, BLOCK_FRAGMENT, "Position", "UV", "Shade");
        createResources();

        writeSheet(out + "_sheet.png", strengths, backgrounds, columns, startTime, timeStep, zoom);
        if (gifSeconds > 0) {
            writeAnimation(out + ".gif", strengths, backgrounds, startTime, gifSeconds);
        }
        GLFW.glfwTerminate();
    }

    private static String option(String key, String fallback) {
        return options.getOrDefault(key, fallback);
    }

    private static int[] size(String value) {
        String[] parts = value.split("x");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    // ------------------------------------------------------------------ output

    private static void writeSheet(String file, double[] strengths, String[] backgrounds,
                                   int columns, float startTime, float timeStep, int zoom)
            throws Exception {
        int tileWidth = cropWidth * zoom;
        int tileHeight = cropHeight * zoom;
        int rows = backgrounds.length * strengths.length;
        BufferedImage sheet = new BufferedImage(columns * (tileWidth + 2) + 2,
                rows * (tileHeight + 2) + 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int row = 0;
        for (String background : backgrounds) {
            for (double strength : strengths) {
                for (int column = 0; column < columns; column++) {
                    BufferedImage tile = renderTile(strength, background, startTime + column * timeStep);
                    graphics.drawImage(scale(tile, zoom), 2 + column * (tileWidth + 2),
                            2 + row * (tileHeight + 2), null);
                }
                graphics.setColor(Color.WHITE);
                graphics.drawString(String.format("strength %.1f, %s", strength, background),
                        6, 16 + row * (tileHeight + 2));
                row++;
            }
        }
        graphics.drawString(String.format("distance %.0f, dt %.2fs", distance, timeStep),
                6, sheet.getHeight() - 6);
        graphics.dispose();
        ImageIO.write(sheet, "png", new File(file));
        System.out.println("wrote " + file);
    }

    private static void writeAnimation(String file, double[] strengths, String[] backgrounds,
                                       float startTime, float seconds) throws Exception {
        int frames = Math.round(seconds * 20);
        List<BufferedImage> images = new ArrayList<>();
        for (int frame = 0; frame < frames; frame++) {
            BufferedImage image = new BufferedImage(strengths.length * cropWidth,
                    backgrounds.length * cropHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            for (int row = 0; row < backgrounds.length; row++) {
                for (int column = 0; column < strengths.length; column++) {
                    graphics.drawImage(renderTile(strengths[column], backgrounds[row],
                            startTime + frame / 20.0F), column * cropWidth, row * cropHeight, null);
                }
            }
            graphics.dispose();
            images.add(image);
        }
        writeGif(images, new File(file), 5);
        System.out.println("wrote " + file);
    }

    // ------------------------------------------------------------------ rendering

    private static BufferedImage renderTile(double strength, String background, float time) {
        float[] projection = perspective((float) Math.toRadians(fov),
                (float) screenWidth / screenHeight, 0.05F, 512F);
        float targetY = 0.35F;
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        float[] eye = {
                (float) (Math.sin(yawRadians) * Math.cos(pitchRadians) * distance),
                (float) (targetY + Math.sin(pitchRadians) * distance),
                (float) (Math.cos(yawRadians) * Math.cos(pitchRadians) * distance)};
        float[] view = lookAt(eye, new float[]{0, targetY, 0});

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glViewport(0, 0, screenWidth, screenHeight);
        boolean night = background.equals("night");
        if (night) glClearColor(0.045F, 0.05F, 0.085F, 1F);
        else glClearColor(0.47F, 0.65F, 1.0F, 1F);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawScene(multiply(projection, view), night ? 0.35F : 1.0F);
        uploadMarker(strength, (int) (strength * 977) & 0x7FFF);
        drawOverlay(projection, view, time);

        float[] clip = transform(multiply(projection, view), new float[]{0, 0.04F, 0, 1});
        int markerX = Math.round((clip[0] / clip[3] * 0.5F + 0.5F) * screenWidth);
        int markerY = Math.round((clip[1] / clip[3] * 0.5F + 0.5F) * screenHeight);
        int x0 = Math.max(0, Math.min(screenWidth - cropWidth, markerX - cropWidth / 2));
        int y0 = Math.max(0, Math.min(screenHeight - cropHeight, markerY - Math.round(cropHeight * 0.22F)));
        ByteBuffer pixels = MemoryUtil.memAlloc(cropWidth * cropHeight * 4);
        glReadPixels(x0, y0, cropWidth, cropHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(cropWidth, cropHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < cropHeight; y++) {
            for (int x = 0; x < cropWidth; x++) {
                int index = ((cropHeight - 1 - y) * cropWidth + x) * 4;
                image.setRGB(x, y, (pixels.get(index) & 0xFF) << 16
                        | (pixels.get(index + 1) & 0xFF) << 8 | (pixels.get(index + 2) & 0xFF));
            }
        }
        MemoryUtil.memFree(pixels);
        return image;
    }

    private static void drawOverlay(float[] projection, float[] view, float time) {
        glUseProgram(program);
        // Same split as WorldFlameAnimationClock.sample, driven by continuous time.
        double ticks = time * 20.0;
        long gameTime = 1_000_000L + (long) Math.floor(ticks);
        float partialTick = (float) (ticks - Math.floor(ticks));
        setMatrix("ModelViewMat", view);
        setMatrix("ProjMat", projection);
        setFloat2("ScreenSize", screenWidth, screenHeight);
        setFloat("LabelPixels", 36F);
        setFloat2("FadeDistances", 80, 96);
        setFloat2("LabelDistances", 8, 12);
        setFloat2("LabelFadeDistances", 20, 40);
        setInt("OverflowValue", 1_000_000_000);
        setInt("LabelWordBase", 32768);
        setFloat("GlyphCount", 17);
        setFloat("StrengthWordScale", 1024);
        setInt("ParticlePhaseBase", 512);
        setFloat("MaxFlameStrength", 32767F / 1024F);
        setFloat("MaxFlameViewportFraction", 0.18F);
        setFloat("PartialTick", partialTick);
        setFloat("ParticleTime", (Math.floorMod(gameTime, 140L) + partialTick) / 20F);
        setInt("ParticleEpoch", (int) Math.floorDiv(gameTime, 140L));
        setInt("MarkerBase", 0);
        setInt("Sampler0", 0);
        setInt("MarkerData", 2);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, glyphTexture);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_BUFFER, markerTexture);

        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glEnable(GL_CULL_FACE);
        glBindVertexArray(quadVao);
        for (Batch batch : batches) {
            setInt("RenderPass", batch.pass());
            setInt("ParticleStart", batch.start());
            setInt("SlotsPerMarker", batch.slots());
            glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, batch.slots());
        }
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
    }

    /** Mirrors WorldPerfObjectBuffers.encodeMarker for one marker at the origin. */
    private static void uploadMarker(double strength, int phase) {
        int rgb = severityColor(strength);
        int packedStrength = (int) Math.round(Math.max(1, Math.min(32767, strength * 1024)));
        double maxStrength = 32767.0 / 1024.0;
        float labelStrength = Math.round(Math.max(0, Math.min(1, strength / maxStrength)) * 255) / 255F;
        FloatBuffer data = MemoryUtil.memAllocFloat(TEXELS_PER_MARKER * 4);
        data.put(0).put(0.04F).put(0).put(packedStrength);
        data.put((rgb >> 16 & 0xFF) / 255F).put((rgb >> 8 & 0xFF) / 255F).put((rgb & 0xFF) / 255F)
                .put(phase & 511);
        data.put(1234).put(-1).put(labelStrength).put(0);
        data.put(0).put(0.04F).put(0).put(0);
        data.flip();
        glBindBuffer(GL_TEXTURE_BUFFER, markerBuffer);
        glBufferData(GL_TEXTURE_BUFFER, data, GL_STREAM_DRAW);
        MemoryUtil.memFree(data);
    }

    /** Mirrors WorldOverlayMath.colorRgb. */
    private static int severityColor(double strength) {
        double position = Math.max(1, Math.min(5, strength)) - 1;
        int lower = Math.min((int) Math.floor(position), SEVERITY_COLORS.length - 1);
        int upper = Math.min(lower + 1, SEVERITY_COLORS.length - 1);
        double amount = position - lower;
        int result = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int from = SEVERITY_COLORS[lower] >> shift & 0xFF;
            int to = SEVERITY_COLORS[upper] >> shift & 0xFF;
            result |= (int) Math.round(from + (to - from) * amount) << shift;
        }
        return result;
    }

    // ------------------------------------------------------------------ scene: furnace on stone

    private static void drawScene(float[] viewProjection, float light) {
        glUseProgram(blockProgram);
        glUniformMatrix4fv(glGetUniformLocation(blockProgram, "MVP"), false, viewProjection);
        glUniform1f(glGetUniformLocation(blockProgram, "Light"), light);
        glUniform1i(glGetUniformLocation(blockProgram, "Tex"), 3);
        glEnable(GL_DEPTH_TEST);
        glBindVertexArray(blockVao);
        glActiveTexture(GL_TEXTURE3);
        for (int[] range : blockRanges) {
            glBindTexture(GL_TEXTURE_2D, blockTextures.get(BLOCK_TEXTURES[range[2]]));
            glDrawArrays(GL_TRIANGLES, range[0], range[1]);
        }
    }

    private static void buildScene() {
        List<Float> vertices = new ArrayList<>();
        List<Integer> faceTextures = new ArrayList<>();
        // The block spans x,z in [-0.5, 0.5] and y in [-1, 0]; the marker rests on its top.
        face(vertices, faceTextures, 0, new float[]{-.5F, -1, .5F}, new float[]{1, 0, 0}, new float[]{0, 1, 0}, 0.8F);
        face(vertices, faceTextures, 1, new float[]{.5F, -1, -.5F}, new float[]{-1, 0, 0}, new float[]{0, 1, 0}, 0.8F);
        face(vertices, faceTextures, 1, new float[]{.5F, -1, .5F}, new float[]{0, 0, -1}, new float[]{0, 1, 0}, 0.6F);
        face(vertices, faceTextures, 1, new float[]{-.5F, -1, -.5F}, new float[]{0, 0, 1}, new float[]{0, 1, 0}, 0.6F);
        face(vertices, faceTextures, 2, new float[]{-.5F, 0, .5F}, new float[]{1, 0, 0}, new float[]{0, 0, -1}, 1.0F);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x != 0 || z != 0) {
                    face(vertices, faceTextures, 3, new float[]{x - .5F, -1, z + .5F},
                            new float[]{1, 0, 0}, new float[]{0, 0, -1}, 1.0F);
                }
            }
        }
        float[] data = new float[vertices.size()];
        for (int index = 0; index < data.length; index++) data[index] = vertices.get(index);
        blockVao = glGenVertexArrays();
        glBindVertexArray(blockVao);
        glBindBuffer(GL_ARRAY_BUFFER, glGenBuffers());
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
        int stride = 6 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);
        for (int faceIndex = 0; faceIndex < faceTextures.size(); faceIndex++) {
            int texture = faceTextures.get(faceIndex);
            int[] last = blockRanges.isEmpty() ? null : blockRanges.get(blockRanges.size() - 1);
            if (last != null && last[2] == texture) {
                last[1] += 6;
            } else {
                blockRanges.add(new int[]{faceIndex * 6, 6, texture});
            }
        }
    }

    private static void face(List<Float> vertices, List<Integer> faceTextures, int texture,
                             float[] origin, float[] u, float[] v, float shade) {
        faceTextures.add(texture);
        float[][] corners = {origin, add(origin, u), add(add(origin, u), v), add(origin, v)};
        float[][] uvs = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int corner : new int[]{0, 1, 2, 0, 2, 3}) {
            for (float component : corners[corner]) vertices.add(component);
            vertices.add(uvs[corner][0]);
            vertices.add(uvs[corner][1]);
            vertices.add(shade);
        }
    }

    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static final String BLOCK_VERTEX = """
            #version 150
            in vec3 Position;
            in vec2 UV;
            in float Shade;
            uniform mat4 MVP;
            out vec2 uv;
            out float shade;
            void main() { gl_Position = MVP * vec4(Position, 1.0); uv = UV; shade = Shade; }
            """;
    private static final String BLOCK_FRAGMENT = """
            #version 150
            uniform sampler2D Tex;
            uniform float Light;
            in vec2 uv;
            in float shade;
            out vec4 fragColor;
            void main() { fragColor = vec4(texture(Tex, uv).rgb * shade * Light, 1.0); }
            """;

    // ------------------------------------------------------------------ GL setup

    private static void initGl() {
        if (!GLFW.glfwInit()) throw new IllegalStateException("glfwInit failed");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long window = GLFW.glfwCreateWindow(64, 64, "flame-preview", 0, 0);
        if (window == 0) throw new IllegalStateException("Could not create an OpenGL 3.3 context");
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GL: " + glGetString(GL_RENDERER) + " / " + glGetString(GL_VERSION));
    }

    private static void createResources() throws Exception {
        quadVao = glGenVertexArrays();
        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, glGenBuffers());
        glBufferData(GL_ARRAY_BUFFER, new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 1, 0}, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0);

        markerBuffer = glGenBuffers();
        glBindBuffer(GL_TEXTURE_BUFFER, markerBuffer);
        glBufferData(GL_TEXTURE_BUFFER, TEXELS_PER_MARKER * 4L * Float.BYTES, GL_STREAM_DRAW);
        markerTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_BUFFER, markerTexture);
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, markerBuffer);
        glyphTexture = texture(1, 1, new int[]{0});

        File textureDir = new File(options.get("textures"));
        for (String name : BLOCK_TEXTURES) {
            BufferedImage image = ImageIO.read(new File(textureDir, name + ".png"));
            int width = image.getWidth();
            blockTextures.put(name, texture(width, width, image.getRGB(0, 0, width, width, null, 0, width)));
        }
        buildScene();

        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        int color = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, color);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, screenWidth, screenHeight, 0, GL_RGBA,
                GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        int depth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, screenWidth, screenHeight);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer incomplete");
        }
    }

    /** Nearest-filtered, repeating RGBA texture from ARGB pixels. */
    private static int texture(int width, int height, int[] argb) {
        ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
        for (int pixel : argb) {
            buffer.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >> 24));
        }
        buffer.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        MemoryUtil.memFree(buffer);
        return id;
    }

    private static int link(String vertex, String fragment, String... attributes) {
        int id = glCreateProgram();
        glAttachShader(id, compile(GL_VERTEX_SHADER, vertex));
        glAttachShader(id, compile(GL_FRAGMENT_SHADER, fragment));
        for (int index = 0; index < attributes.length; index++) {
            glBindAttribLocation(id, index, attributes[index]);
        }
        glLinkProgram(id);
        if (glGetProgrami(id, GL_LINK_STATUS) == 0) throw new IllegalStateException(glGetProgramInfoLog(id));
        return id;
    }

    private static int compile(int type, String source) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == 0) {
            String stage = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            throw new IllegalStateException(stage + " shader: " + glGetShaderInfoLog(id));
        }
        return id;
    }

    private static void setFloat(String name, float value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform1f(location, value);
    }

    private static void setFloat2(String name, float x, float y) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform2f(location, x, y);
    }

    private static void setInt(String name, int value) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniform1i(location, value);
    }

    private static void setMatrix(String name, float[] matrix) {
        int location = glGetUniformLocation(program, name);
        if (location >= 0) glUniformMatrix4fv(location, false, matrix);
    }

    // ------------------------------------------------------------------ batch layout from the shader

    private static Map<String, Integer> parseConstants(String source) {
        Map<String, Integer> constants = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("const\\s+int\\s+(\\w+)\\s*=\\s*([^;]+);").matcher(source);
        while (matcher.find()) constants.put(matcher.group(1), evaluate(matcher.group(2), constants));
        return constants;
    }

    /** Sums of integer literals and previously declared constants, e.g. "A + B". */
    private static int evaluate(String expression, Map<String, Integer> constants) {
        int total = 0;
        for (String term : expression.split("\\+")) {
            String name = term.trim();
            if (name.matches("-?\\d+")) {
                total += Integer.parseInt(name);
            } else if (constants.containsKey(name)) {
                total += constants.get(name);
            } else {
                throw new IllegalArgumentException("Unknown shader constant " + name);
            }
        }
        return total;
    }

    /** "pass:start:slots;..." where start and slots may name shader int constants. */
    private static List<Batch> parseBatches(String spec, Map<String, Integer> constants) {
        List<Batch> result = new ArrayList<>();
        for (String part : spec.split(";")) {
            String[] fields = part.split(":");
            result.add(new Batch(Integer.parseInt(fields[0]), evaluate(fields[1], constants),
                    evaluate(fields[2], constants)));
        }
        return result;
    }

    // ------------------------------------------------------------------ math (column-major)

    private static float[] perspective(float fovY, float aspect, float near, float far) {
        float focal = (float) (1 / Math.tan(fovY / 2));
        float[] matrix = new float[16];
        matrix[0] = focal / aspect;
        matrix[5] = focal;
        matrix[10] = (far + near) / (near - far);
        matrix[11] = -1;
        matrix[14] = 2 * far * near / (near - far);
        return matrix;
    }

    private static float[] lookAt(float[] eye, float[] target) {
        float[] forward = normalize(new float[]{target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]});
        float[] side = normalize(cross(forward, new float[]{0, 1, 0}));
        float[] up = cross(side, forward);
        float[] matrix = new float[16];
        matrix[0] = side[0];
        matrix[4] = side[1];
        matrix[8] = side[2];
        matrix[1] = up[0];
        matrix[5] = up[1];
        matrix[9] = up[2];
        matrix[2] = -forward[0];
        matrix[6] = -forward[1];
        matrix[10] = -forward[2];
        matrix[12] = -dot(side, eye);
        matrix[13] = -dot(up, eye);
        matrix[14] = dot(forward, eye);
        matrix[15] = 1;
        return matrix;
    }

    private static float[] multiply(float[] a, float[] b) {
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[k * 4 + row] * b[column * 4 + k];
                result[column * 4 + row] = sum;
            }
        }
        return result;
    }

    private static float[] transform(float[] matrix, float[] vector) {
        float[] result = new float[4];
        for (int row = 0; row < 4; row++) {
            result[row] = matrix[row] * vector[0] + matrix[4 + row] * vector[1]
                    + matrix[8 + row] * vector[2] + matrix[12 + row] * vector[3];
        }
        return result;
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float[] normalize(float[] vector) {
        float length = (float) Math.sqrt(dot(vector, vector));
        return new float[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    // ------------------------------------------------------------------ images

    private static BufferedImage scale(BufferedImage image, int factor) {
        if (factor == 1) return image;
        BufferedImage result = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, result.getWidth(), result.getHeight(), null);
        graphics.dispose();
        return result;
    }

    private static void writeGif(List<BufferedImage> frames, File file, int delayCentiseconds)
            throws Exception {
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(file)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            boolean first = true;
            for (BufferedImage frame : frames) {
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromRenderedImage(frame), null);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(delayCentiseconds));
                control.setAttribute("transparentColorIndex", "0");
                if (first) {
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[]{1, 0, 0});
                    child(root, "ApplicationExtensions").appendChild(loop);
                    first = false;
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), null);
            }
            writer.endWriteSequence();
        }
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) node;
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
