import com.twoi.combatdoll.companion.client.gui.CanvasTextLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Actual helper probe. Only the pure geometry production class is loaded. */
public final class CanvasTextLayoutProbe {
    static final double EPS = 0.001;
    static long checks;
    static long failures;
    static long centerStressWarnings;
    static double largestPixelResidual;
    static final List<String> errors = new ArrayList<>();
    static final List<String> centerExamples = new ArrayList<>();
    static String where;

    static final int[][] SIZES = {
        {320,240},{640,480},{800,450},{854,480},{960,540},{1024,768},
        {1280,720},{1280,800},{1366,768},{1600,900},{1920,1080},
        {2560,1440},{3440,1440},{321,241},{799,449},{853,479},
        {1365,767},{1919,1079},{2560,1920},{3440,2160}
    };
    // x, y, width, height, requested font size, numeric font line height
    static final double[][] REGIONS = {
        {67,342,660,54,1.25,9}, {172,122,450,181,1,11},
        {86,305,122,28,1,9}, {208,164,384,34,1,9},
        {254,410,74,24,1,9}
    };

    static void check(boolean pass, String detail) {
        checks++;
        if (!pass) {
            failures++;
            if (errors.size() < 25) errors.add(where + ": " + detail);
        }
    }

    static int actualGui(int w, int h, int requested, boolean unicode) {
        int value = 1;
        while (value != requested && value < w && value < h && w / (value+1) >= 320 && h / (value+1) >= 240) value++;
        return unicode && value % 2 != 0 ? value + 1 : value;
    }

    static double projectedX(CanvasTextLayout text, double x) {
        return (text.left() + text.x(x) * text.canvasScale()) * text.guiScale();
    }

    static double projectedY(CanvasTextLayout text, double y) {
        return (text.top() + text.y(y) * text.canvasScale()) * text.guiScale();
    }

    static void integerPixel(double value, String label) {
        double residual = Math.abs(value - Math.rint(value));
        largestPixelResidual = Math.max(largestPixelResidual, residual);
        check(residual <= EPS, label + " residual=" + residual);
    }

    static void checkPages(int lines, int rows) {
        int max = Math.max(0, lines-rows);
        int scroll = 0;
        int coveredEnd = 0;
        for (int limit = 0; limit <= lines+1; limit++) {
            check(scroll <= coveredEnd, "page gap at " + scroll + ", end=" + coveredEnd);
            check(scroll >= 0 && scroll <= max, "page outside scroll clamp");
            coveredEnd = Math.max(coveredEnd, Math.min(lines, scroll+rows));
            if (scroll == max) break;
            scroll = Math.min(max, scroll+rows);
        }
        check(coveredEnd == lines, "missing tail of history");
    }

    static void inspect(int w, int h, int requested, boolean unicode) {
        int gui = actualGui(w,h,requested,unicode);
        int screenW = (int)Math.ceil(w / (double)gui), screenH = (int)Math.ceil(h / (double)gui);
        // Match existing Screen.scale()/left()/top() float arithmetic.
        float scale = Math.min(screenW / 800F, screenH / 450F);
        float left = (screenW - 800 * scale) / 2, top = (screenH - 450 * scale) / 2;
        for (int region = 0; region < REGIONS.length; region++) {
            double[] r = REGIONS[region];
            where = w+"x"+h+", requested="+requested+", actual="+gui+", unicode="+unicode+", region="+region;
            var text = CanvasTextLayout.create(gui,scale,left,top,r[4]);
            int budget = text.wrapWidth(r[0],r[2]);
            int rows = text.rows(r[1],r[3],(int)r[5]);
            double first = Math.round((left + r[0]*scale)*gui);
            double end = Math.floor((left + (r[0]+r[2])*scale)*gui);
            double farY = Math.floor((top + (r[1]+r[3])*scale)*gui);
            check(text.pixelScale() == Math.max(1,(int)Math.round(gui*scale*r[4])), "wrong physical text scale");
            check(budget == Math.max(1,(int)Math.floor((end-first)/text.pixelScale())), "wrap differs from snapped edges");
            check(projectedX(text,r[0])+budget*text.pixelScale() <= end+EPS, "wrap exceeds far edge");
            check(projectedY(text,text.lineY(r[1],rows-1,(int)r[5])) + r[5]*text.pixelScale() <= farY+EPS,
                    "last visible row exceeds region");

            for (double x : new double[]{r[0], r[0]+r[2], r[0]+r[2]/2, 0.5, 147.5}) {
                double anchor = projectedX(text,x);
                integerPixel(anchor,"x origin");
                for (int fontEdge : new int[]{0,1,3,6,8,9,12,127,budget}) {
                    // This includes the float returned to PoseStack.scale().
                    double edge = anchor + fontEdge*text.relativeScale()*scale*gui;
                    integerPixel(edge,"projected glyph edge");
                }
            }
            for (int row = 0; row < rows; row++) integerPixel(projectedY(text,text.lineY(r[1],row,(int)r[5])),"line y");
            for (int fontWidth = 0; fontWidth <= budget; fontWidth++) {
                double centered = projectedX(text,text.centeredLeft(r[0]+r[2]/2,fontWidth));
                integerPixel(centered,"centered left origin");
                double bounded = projectedX(text,text.centeredLeftWithin(r[0],r[2],fontWidth));
                integerPixel(bounded,"bounded centered left origin");
                check(bounded >= first-EPS && bounded+fontWidth*text.pixelScale() <= end+EPS,
                        "bounded centered text exceeds same snapped wrap bounds");
                // Stress-only advisory: wrapping the entire unpadded region is
                // not the current button call site, which has 8px inner padding.
                if (centered < first-EPS || centered+fontWidth*text.pixelScale() > end+EPS) {
                    centerStressWarnings++;
                    if (centerExamples.size()<10) centerExamples.add(where+", fontWidth="+fontWidth+", first="+first+", end="+end+", centered="+centered+", drawEnd="+(centered+fontWidth*text.pixelScale()));
                }
            }
            // Current speaker is "2i"; choices/controls truncate to inner region.
            if (region == 2) {
                double speakerX = projectedX(text,text.centeredLeft(147,12));
                check(speakerX >= first-EPS && speakerX+12*text.pixelScale() <= end+EPS,"2i speaker leaves name frame");
            }
            if (region >= 3) {
                double center = r[0]+r[2]/2;
                double labelX = projectedX(text,text.centeredLeft(center,budget));
                // r is the button's padded inner region, so allow its outer 8 canvas pixels.
                double outerLeft = (left+(r[0]-8)*scale)*gui;
                double outerRight = (left+(r[0]+r[2]+8)*scale)*gui;
                check(labelX >= outerLeft-EPS && labelX+budget*text.pixelScale() <= outerRight+EPS,"truncated button leaves outer bounds");
            }
            for (int count : new int[]{0,1,rows,rows+1,rows*2-1,rows*2,512}) checkPages(count,rows);
        }
    }

    static String quoted(String value) {
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
    }

    static String list(List<String> values) {
        return "["+String.join(",",values.stream().map(CanvasTextLayoutProbe::quoted).toList())+"]";
    }

    public static void main(String[] args) throws Exception {
        int cases = 0;
        for (int[] size : SIZES) for (int requested=0; requested<=8; requested++) for (boolean unicode : new boolean[]{false,true}) {
            inspect(size[0],size[1],requested,unicode);
            cases++;
        }
        String status = failures == 0 ? "PRODUCTION_GEOMETRY_PROBE_PASSED" : "PRODUCTION_GEOMETRY_PROBE_FAILED";
        String output = "{\n  \"status\":"+quoted(status)+",\n  \"cases\":"+cases+",\n  \"checks\":"+checks+",\n  \"failures\":"+failures
            +",\n  \"max_pixel_residual\":"+largestPixelResidual+",\n  \"numeric_tolerance_pixels\":"+EPS
            +",\n  \"center_full_width_stress_advisories\":"+centerStressWarnings+",\n  \"center_examples\":"+list(centerExamples)
            +",\n  \"failure_examples\":"+list(errors)
            +",\n  \"helper_source_sha256\":"+quoted(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(args[1])))))
            +",\n  \"glyph_geometry_contract\":\"Integer-coordinate glyph vertices and integer advances only; vanilla Unihex oversample=2 and other fractional glyph bearings are outside this alignment guarantee.\""
            +",\n  \"minecraft_started\":false,\n  \"actual_font_split_tested\":false\n}\n";
        Files.writeString(Path.of(args[0]),output);
        System.out.println(status+": "+cases+" cases, "+checks+" checks, failures="+failures+", max residual="+largestPixelResidual+", center stress advisories="+centerStressWarnings);
        if (failures != 0) System.exit(1);
    }
}
