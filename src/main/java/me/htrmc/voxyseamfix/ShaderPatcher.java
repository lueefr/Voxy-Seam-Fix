package me.htrmc.voxyseamfix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Rewrites voxy's quad setup shader ({@code voxy:lod/quad_util.glsl}, merged into whatever program
 * imports it) so that fluid surfaces land on one plane instead of stepping with the LOD grid.
 *
 * <h2>Why it steps</h2>
 * Voxy builds quads in LOD-voxel space and scales them up by {@code lodScale = 2^lodLevel}. Two
 * independent problems stack up there:
 * <ul>
 *   <li>The per-face indentation (water's top face sits 2/16 of a block low) was multiplied by
 *       {@code lodScale}, so the sea drooped lower on every coarser level.</li>
 *   <li>Mipping never lets air win, so a fluid surface rounds up to the top of whichever LOD voxel
 *       straddles it, which lifts the sea by a whole voxel on every coarser level.</li>
 * </ul>
 * The first is fixed by dividing the indentation out before the scale is applied back in, the
 * second by pinning the top faces of fluid models that straddle the fluid surface to
 * {@code surfaceY - indentation}.
 *
 * <h2>How this stays portable across voxy builds</h2>
 * Only line-level anchors are used (no whole-block string matching), because different voxy ports
 * reformat and re-comment this function. If any anchor is missing, nothing at all is rewritten:
 * half a patch would emit a shader that fails to compile, which is far worse than no fix.
 *
 * The fluid surface height is baked straight into the generated GLSL instead of being fed in
 * through the scene uniform buffer. The uniform layout is <em>not</em> stable between voxy builds
 * (some ports append {@code uEarthRadius}, {@code uColorFix}, ... right after {@code cameraSubPos}),
 * so writing at a hard-coded offset would corrupt somebody else's uniform. The shader already has
 * {@code baseSectionPos}, and {@code quad.basePoint} is built relative to it, so
 * {@code seaLevel - baseSectionPos.y*32} gives the same plane without touching the UBO at all.
 */
public final class ShaderPatcher {
    private ShaderPatcher() {}

    /** {@code vec3 quadStart = extractPos(rawQuad);} */
    private static final Pattern POS_LINE =
            Pattern.compile("^\\s*vec3\\s+quadStart\\s*=\\s*extractPos\\(\\s*rawQuad\\s*\\)\\s*;\\s*$");

    /** {@code float depthOffset = extractFaceIndentation(faceData);}, with or without the /lodScale fix. */
    private static final Pattern INDENTATION_LINE =
            Pattern.compile("^\\s*float\\s+depthOffset\\s*=\\s*extractFaceIndentation\\(\\s*faceData\\s*\\)\\s*(?:/\\s*lodScale\\s*)?;\\s*$");

    /** {@code quad.basePoint = (quadStart*lodScale)+vec3(baseSection<<5);} */
    private static final Pattern BASE_POINT_LINE =
            Pattern.compile("^\\s*quad\\.basePoint\\s*=\\s*\\(\\s*quadStart\\s*\\*\\s*lodScale\\s*\\)\\s*\\+\\s*vec3\\(\\s*baseSection\\s*<<\\s*5\\s*\\)\\s*;\\s*$");

    /** Marker so a source that already went through here is never patched twice. */
    private static final String ALREADY_PATCHED = "voxyseamfix_rawPos";

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * @param id     shader asset id, only used for logging
     * @param source fully merged GLSL source (voxy's {@code ShaderLoader.parse} output)
     * @return the patched source, or the original when this shader does not contain the anchors
     */
    public static String patch(String id, String source) {
        if (source == null || source.isEmpty() || !VoxySeamFix.config().enabled) {
            return source;
        }
        // Only the program that actually builds quads is interesting; the compute/fragment programs
        // share helper files but have nothing to pin.
        if (!source.contains("void setupQuad(") || source.contains(ALREADY_PATCHED)) {
            return source;
        }

        String[] lines = source.split("\n", -1);
        int posIndex = -1;
        int indentationIndex = -1;
        int basePointIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (posIndex < 0 && POS_LINE.matcher(lines[i]).matches()) {
                posIndex = i;
            } else if (indentationIndex < 0 && INDENTATION_LINE.matcher(lines[i]).matches()) {
                indentationIndex = i;
            } else if (basePointIndex < 0 && BASE_POINT_LINE.matcher(lines[i]).matches()) {
                basePointIndex = i;
            }
        }

        if (posIndex < 0 || indentationIndex < 0 || basePointIndex < 0) {
            warnOnce("anchors:" + id, "voxy's setupQuad no longer matches the anchors this build knows"
                    + " (missing: " + (posIndex < 0 ? "quadStart " : "") + (indentationIndex < 0 ? "depthOffset " : "")
                    + (basePointIndex < 0 ? "basePoint" : "") + "), so fluid surfaces are NOT being pinned in "
                    + id + ". This build of voxy-seam-fix is out of date for this voxy version.");
            return source;
        }

        String indent = leadingWhitespace(lines[basePointIndex]);
        String indentationIndent = leadingWhitespace(lines[indentationIndex]);

        List<String> out = new ArrayList<>(lines.length + 16);
        for (int i = 0; i < lines.length; i++) {
            if (i == posIndex) {
                out.add(indent + "vec3 voxyseamfix_rawPos = extractPos(rawQuad);");
                out.add(indent + "vec3 quadStart = voxyseamfix_rawPos;");
            } else if (i == indentationIndex) {
                // The indentation is a distance in blocks (a fluid's top face is 14/16), but the
                // quad is scaled by lodScale below. Divide it out first, otherwise the surface sits
                // 2/16*lodScale low with a different error on every LOD level.
                out.add(indentationIndent + "float voxyseamfix_indentation = extractFaceIndentation(faceData);");
                out.add(indentationIndent + "float depthOffset = voxyseamfix_indentation/lodScale;");
            } else {
                out.add(lines[i]);
                if (i == basePointIndex) {
                    out.addAll(pinBlock(indent));
                }
            }
        }

        VoxySeamFix.LOGGER.info("voxy-seam-fix: pinned fluid surfaces to y={} in {}",
                VoxySeamFix.fluidSurfaceLevel(), id);
        return String.join("\n", out);
    }

    /**
     * The pin itself. It runs after {@code quad.basePoint} has been computed, in the same
     * baseSectionPos-relative space, and only for the top face ({@code face == 1u}) of a model that
     * carries the fluid flag written by {@link FluidModelMarker}.
     */
    private static List<String> pinBlock(String indent) {
        double surfaceLevel = VoxySeamFix.fluidSurfaceLevel();
        // Locale.ROOT: a comma decimal separator would produce a GLSL syntax error.
        String surface = String.format(Locale.ROOT, "%.2f", surfaceLevel);

        String condition = "((model.flagsA & " + VoxySeamFix.FLUID_MODEL_FLAG + "u) != 0)";
        if (VoxySeamFix.config().pinTranslucentModels) {
            // Fallback for voxy builds we cannot flag: water is always a translucent model, so
            // pinning translucent top faces gets the sea back even without the fluid flag.
            condition = "(" + condition + " || ((model.flagsA & 4u) != 0))";
        }

        List<String> block = new ArrayList<>(13);
        block.add(indent + "// voxy-seam-fix: mipping never lets air win, so a fluid surface rounds up to the top");
        block.add(indent + "// of whichever LOD voxel straddles it and the sea ends up a whole voxel higher on every");
        block.add(indent + "// coarser level. Pin those top faces to the one real plane. Everything here is relative to");
        block.add(indent + "// baseSectionPos, which is the space quad.basePoint is built in.");
        block.add(indent + "if (face == 1u && " + condition + ") {");
        block.add(indent + "    float voxyseamfix_surfaceY = " + surface + " - float(baseSectionPos.y)*32.0;");
        block.add(indent + "    float voxyseamfix_voxelTop = (voxyseamfix_rawPos.y + 1.0)*lodScale + float(baseSection.y)*32.0;");
        block.add(indent + "    if (voxyseamfix_voxelTop >= voxyseamfix_surfaceY && voxyseamfix_voxelTop - lodScale < voxyseamfix_surfaceY) {");
        block.add(indent + "        quad.basePoint.y = voxyseamfix_surfaceY - voxyseamfix_indentation;");
        block.add(indent + "    }");
        block.add(indent + "}");
        return block;
    }

    private static String leadingWhitespace(String line) {
        int end = 0;
        while (end < line.length() && (line.charAt(end) == ' ' || line.charAt(end) == '\t')) {
            end++;
        }
        return line.substring(0, end);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            VoxySeamFix.LOGGER.warn("voxy-seam-fix: {}", message);
        }
    }
}
