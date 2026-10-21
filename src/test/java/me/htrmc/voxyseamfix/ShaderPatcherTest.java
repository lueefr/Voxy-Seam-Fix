package me.htrmc.voxyseamfix;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests use synthetic shader text shaped like voxy's {@code setupQuad}, not voxy's own files
 * (voxy is all-rights-reserved and is not vendored here). They cover the two layouts seen in the
 * wild -- the indentation line as-is, and the variant that already divides by {@code lodScale} and
 * carries comments -- plus every way the patch is supposed to decline.
 */
class ShaderPatcherTest {

    /** Shaped like voxy 0.2.x / the 1.21.1 NeoForge port. */
    private static final String UPSTREAM_STYLE = String.join("\n",
            "vec4 getFaceSize(uint faceData) { return vec4(1.0); }",
            "",
            "void setupQuad(out QuadData quad, const in Quad rawQuad, uvec2 sPos, bool generateAttributes) {",
            "    uint lodLevel = getLoDLevel(sPos);",
            "    float lodScale = 1<<lodLevel;",
            "    ivec3 baseSection = (getLoDPosition(sPos)<<lodLevel) - baseSectionPos;",
            "    uint face = extractFace(rawQuad);",
            "    BlockModel model = modelData[extractStateId(rawQuad)];",
            "    uint faceData = model.faceData[face];",
            "",
            "    vec4 faceSize = getFaceSize(faceData);",
            "    vec3 quadStart = extractPos(rawQuad);",
            "    float depthOffset = extractFaceIndentation(faceData);",
            "    quadStart += swizzelDataAxis(face>>1, vec3(faceSize.xz, mix(depthOffset, 1-depthOffset, float(face&1u))));",
            "",
            "    quad.lodScale = lodScale;",
            "    quad.axis = face>>1;",
            "    quad.basePoint = (quadStart*lodScale)+vec3(baseSection<<5);",
            "    quad.uvCorner = faceSize.xz;",
            "}");

    /** Shaped like a port that already applied the indentation fix and left comments around. */
    private static final String PRE_DIVIDED_STYLE = String.join("\n",
            "void setupQuad(out QuadData quad, const in Quad rawQuad, uvec2 sPos, bool generateAttributes) {",
            "    float lodScale = 1<<getLoDLevel(sPos);",
            "    ivec3 baseSection = (getLoDPosition(sPos)<<getLoDLevel(sPos)) - baseSectionPos;",
            "    uint face = extractFace(rawQuad);",
            "    BlockModel model = modelData[extractStateId(rawQuad)];",
            "    uint faceData = model.faceData[face];",
            "    vec3 quadStart = extractPos(rawQuad);",
            "    // Indentation is a real distance in blocks, so pre-divide it by the LOD scale.",
            "    float depthOffset = extractFaceIndentation(faceData) / lodScale;",
            "    quadStart += swizzelDataAxis(face>>1, vec3(getFaceSize(faceData).xz, mix(depthOffset, 1.0-depthOffset, float(face&1u))));",
            "",
            "    quad.lodScale = lodScale;",
            "    quad.axis = face>>1;",
            "    quad.basePoint = (quadStart*lodScale)+vec3(baseSection<<5);",
            "}");

    @Test
    void patchesUpstreamLayout() {
        ShaderPatcher.Result result = ShaderPatcher.patch(UPSTREAM_STYLE, 63, false);

        assertEquals(ShaderPatcher.Status.PATCHED, result.status());
        assertTrue(result.changed());

        String out = result.source();
        // The indentation is divided out before it is scaled back up.
        assertTrue(out.contains("float voxyseamfix_indentation = extractFaceIndentation(faceData);"));
        assertTrue(out.contains("float depthOffset = voxyseamfix_indentation/lodScale;"));
        // The pin uses the configured surface and the fluid flag.
        assertTrue(out.contains("float voxyseamfix_surfaceY = 63.00 - float(baseSectionPos.y)*32.0;"));
        assertTrue(out.contains("if (face == 1u && ((model.flagsA & 16u) != 0)) {"));
        assertTrue(out.contains("quad.basePoint.y = voxyseamfix_surfaceY - voxyseamfix_indentation;"));
        // ... and it lands after quad.basePoint, so the write is not overwritten again.
        assertTrue(out.indexOf("quad.basePoint = (quadStart*lodScale)")
                < out.indexOf("quad.basePoint.y = voxyseamfix_surfaceY"));
        assertBalanced(out);
    }

    @Test
    void patchesLayoutThatAlreadyDividesByLodScale() {
        ShaderPatcher.Result result = ShaderPatcher.patch(PRE_DIVIDED_STYLE, 62, false);

        assertEquals(ShaderPatcher.Status.PATCHED, result.status());
        assertTrue(result.source().contains("float depthOffset = voxyseamfix_indentation/lodScale;"));
        assertTrue(result.source().contains("voxyseamfix_surfaceY = 62.00"));
        assertBalanced(result.source());
    }

    @Test
    void appliesEachLayoutExactlyOnce() {
        for (String source : new String[]{UPSTREAM_STYLE, PRE_DIVIDED_STYLE}) {
            String once = ShaderPatcher.patch(source, 63, false).source();
            ShaderPatcher.Result twice = ShaderPatcher.patch(once, 63, false);

            assertEquals(ShaderPatcher.Status.ALREADY_PATCHED, twice.status());
            assertEquals(once, twice.source());
        }
    }

    @Test
    void leavesShadersWithoutSetupQuadAlone() {
        String compute = String.join("\n",
                "#version 460 core",
                "void main() {",
                "    uint face = extractFace(someQuad);",
                "}");
        ShaderPatcher.Result result = ShaderPatcher.patch(compute, 63, false);

        assertEquals(ShaderPatcher.Status.NOT_A_QUAD_SHADER, result.status());
        assertEquals(compute, result.source());
    }

    @Test
    void refusesToHalfPatchAnUnknownLayout() {
        // #endif dropped, or a port that rewrote the base point line: better no fix than a shader
        // that does not compile.
        String mangled = UPSTREAM_STYLE.replace(
                "    quad.basePoint = (quadStart*lodScale)+vec3(baseSection<<5);",
                "    quad.basePoint = someOtherWay(quadStart, lodScale, baseSection);");

        ShaderPatcher.Result result = ShaderPatcher.patch(mangled, 63, false);

        assertEquals(ShaderPatcher.Status.ANCHORS_MISSING, result.status());
        assertEquals(mangled, result.source());
    }

    @Test
    void fallsBackToTranslucentModelsWhenAsked() {
        String out = ShaderPatcher.patch(UPSTREAM_STYLE, 63, true).source();

        assertTrue(out.contains("if (face == 1u && (((model.flagsA & 16u) != 0) || ((model.flagsA & 4u) != 0))) {"));
        assertBalanced(out);
    }

    @Test
    void handlesNullAndEmptySources() {
        assertSame(null, ShaderPatcher.patch(null, 63, false).source());
        assertEquals("", ShaderPatcher.patch("", 63, false).source());
        assertFalse(ShaderPatcher.patch("", 63, false).changed());
    }

    /** Cheap GLSL sanity: the generated block must not unbalance the file. */
    private static void assertBalanced(String source) {
        assertEquals(countOf(source, '{'), countOf(source, '}'), "unbalanced braces in patched shader");
        assertEquals(countOf(source, '('), countOf(source, ')'), "unbalanced parentheses in patched shader");
    }

    private static int countOf(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }
}
