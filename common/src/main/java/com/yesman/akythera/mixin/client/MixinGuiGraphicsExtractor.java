package com.yesman.akythera.mixin.client;

import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor implements GuiGraphicsAccessor {
    @Unique
    private final List<Runnable> akythera$deferredWidgetExtractors = new ArrayList<>();

    @Unique @Override
    public void akythera$setDeferredWidgetRenderer(final Runnable deferredWidgetExtractor) {
        this.akythera$deferredWidgetExtractors.add(deferredWidgetExtractor);
    }

    @Inject(
        at = @At(
            value = "TAIL"
        ),
        method = "extractDeferredElements(IIF)V"
    )
    public void extractDeferredElements(CallbackInfo callbackInfo) {
        // Render deferred widget parts that should always be drawn on the top
        for (Runnable render : this.akythera$deferredWidgetExtractors) {
            render.run();
        }

        this.akythera$deferredWidgetExtractors.clear();
    }
}
