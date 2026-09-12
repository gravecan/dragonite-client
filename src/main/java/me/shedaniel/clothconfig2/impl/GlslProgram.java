package me.shedaniel.clothconfig2.impl;



import com.mojang.blaze3d.systems.RenderSystem;

import java.io.BufferedReader;

import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;

import java.util.Map;

import net.minecraft.client.MinecraftClient;

import net.minecraft.util.Identifier;

import org.joml.Matrix4f;

import org.lwjgl.opengl.GL20;

import org.lwjgl.system.MemoryStack;





public final class GlslProgram {

    private final int programId;

    private final boolean valid;

    private final String lastError;

    private final Map<String, Integer> uniformLocations = new HashMap<>();



    public GlslProgram(Identifier vertexPath, Identifier fragmentPath) {

        int program = 0;

        boolean ok = false;

        String error = null;

        try {

            String vertexSrc = load(vertexPath);

            String fragmentSrc = load(fragmentPath);

            int vs = compile(GL20.GL_VERTEX_SHADER, vertexSrc);

            int fs = compile(GL20.GL_FRAGMENT_SHADER, fragmentSrc);

            program = GL20.glCreateProgram();

            GL20.glAttachShader(program, vs);

            GL20.glAttachShader(program, fs);

            // Bind vertex attribute slots to match Minecraft's fixed vertex format
            // layout (Position=0, UV0=1, Color=2) BEFORE linking. Without this the
            // linker assigns arbitrary locations and BufferRenderer feeds our quad
            // data into the wrong slots, producing garbage UVs / black output.
            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glBindAttribLocation(program, 1, "UV0");
            GL20.glBindAttribLocation(program, 2, "Color");
            GL20.glBindAttribLocation(program, 3, "Normal");
            GL20.glLinkProgram(program);

            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {

                throw new IllegalStateException(GL20.glGetProgramInfoLog(program));

            }

            GL20.glDeleteShader(vs);

            GL20.glDeleteShader(fs);

            ok = true;

        } catch (RuntimeException e) {

            error = vertexPath + " + " + fragmentPath + ": " + e.getMessage();

            System.err.println("[GlassHands] Shader failed: " + error);

            if (program != 0) {

                GL20.glDeleteProgram(program);

                program = 0;

            }

        }

        this.programId = program;

        this.valid = ok;

        this.lastError = error;

    }



    public String getLastError() {

        return lastError;

    }



    public boolean isValid() {

        return valid;

    }



    public void bind() {

        if (!valid) {

            return;

        }

        RenderSystem.assertOnRenderThread();

        GL20.glUseProgram(programId);

    }



    public void unbind() {

        if (!valid) {

            return;

        }

        RenderSystem.assertOnRenderThread();

        GL20.glUseProgram(0);

    }



    public void uniform1f(String name, float value) {

        int loc = uniformLoc(name);

        if (loc >= 0) {

            GL20.glUniform1f(loc, value);

        }

    }



    public void uniform2f(String name, float x, float y) {

        int loc = uniformLoc(name);

        if (loc >= 0) {

            GL20.glUniform2f(loc, x, y);

        }

    }



    public void uniform3f(String name, float x, float y, float z) {

        int loc = uniformLoc(name);

        if (loc >= 0) {

            GL20.glUniform3f(loc, x, y, z);

        }

    }



    public void uniform4f(String name, float x, float y, float z, float w) {

        int loc = uniformLoc(name);

        if (loc >= 0) {

            GL20.glUniform4f(loc, x, y, z, w);

        }

    }



    public void uniform1i(String name, int value) {

        int loc = uniformLoc(name);

        if (loc >= 0) {

            GL20.glUniform1i(loc, value);

        }

    }



    public void uniformMatrix4f(String name, Matrix4f matrix) {

        int loc = uniformLoc(name);

        if (loc < 0) {

            return;

        }

        try (MemoryStack stack = MemoryStack.stackPush()) {

            var buf = stack.mallocFloat(16);

            matrix.get(buf);

            GL20.glUniformMatrix4fv(loc, false, buf);

        }

    }



    private int uniformLoc(String name) {

        if (!valid) {

            return -1;

        }

        Integer cached = uniformLocations.get(name);

        if (cached != null) {

            return cached;

        }

        int loc = GL20.glGetUniformLocation(programId, name);

        uniformLocations.put(name, loc);

        return loc;

    }



    private static String load(Identifier id) {

        MinecraftClient mc = MinecraftClient.getInstance();

        try (var stream = mc.getResourceManager().getResource(id).orElseThrow().getInputStream();

                var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {

                sb.append(line).append('\n');

            }

            return sb.toString();

        } catch (Exception e) {

            throw new IllegalStateException("Failed to load shader " + id, e);

        }

    }



    private static int compile(int type, String source) {

        int shader = GL20.glCreateShader(type);

        GL20.glShaderSource(shader, source);

        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {

            throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));

        }

        return shader;

    }

}

