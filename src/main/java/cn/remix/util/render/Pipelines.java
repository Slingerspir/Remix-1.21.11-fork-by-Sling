package cn.remix.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

@UtilityClass
public final class Pipelines {

    private final RenderPipeline boxPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/box"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private final RenderPipeline visibleBoxPipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/visible_box"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private final RenderPipeline linePipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/line"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    private final RenderPipeline visibleLinePipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("client", "pipeline/visible_line"))
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .build()
    );

    public final RenderLayer box = RenderLayer.of(
            "box",
            RenderSetup.builder(boxPipeline)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .translucent()
                    .build()
    );

    public final RenderLayer visibleBox = RenderLayer.of(
            "visible_box",
            RenderSetup.builder(visibleBoxPipeline)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .translucent()
                    .build()
    );

    public final RenderLayer line = RenderLayer.of(
            "line",
            RenderSetup.builder(linePipeline)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .translucent()
                    .build()
    );

    public final RenderLayer visibleLine = RenderLayer.of(
            "visible_line",
            RenderSetup.builder(visibleLinePipeline)
                    .outputTarget(OutputTarget.MAIN_TARGET)
                    .translucent()
                    .build()
    );
}
