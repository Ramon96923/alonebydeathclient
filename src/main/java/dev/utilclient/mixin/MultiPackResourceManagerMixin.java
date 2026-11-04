package dev.utilclient.mixin;

import dev.utilclient.module.ModuleManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** CustomTotem Image: serves your picture as donutwaffen:textures/item/totem_image.png. */
@Mixin(MultiPackResourceManager.class)
public abstract class MultiPackResourceManagerMixin {
    private static Resource dw$swap(Identifier id, Resource orig) {
        byte[] b = ModuleManager.CUSTOM_TOTEM.imageFor(id);
        if (b == null || orig == null) return null;
        return new Resource(orig.source(), () -> new ByteArrayInputStream(b));
    }

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true)
    private void dw$get(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
        try {
            Optional<Resource> r = cir.getReturnValue();
            if (r == null || r.isEmpty()) return;
            Resource s = dw$swap(id, r.get());
            if (s != null) cir.setReturnValue(Optional.of(s));
        } catch (Throwable ignored) {
            // vanilla resource
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true)
    private void dw$list(String path, net.minecraft.server.packs.resources.ResourceManager.Selector filter,
                         CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
        try {
            Map<Identifier, Resource> m = cir.getReturnValue();
            if (m == null || !path.startsWith("textures")) return;
            Map<Identifier, Resource> out = null;
            for (var e : m.entrySet()) {
                Resource s = dw$swap(e.getKey(), e.getValue());
                if (s == null) continue;
                if (out == null) out = new HashMap<>(m);
                out.put(e.getKey(), s);
            }
            if (out != null) cir.setReturnValue(out);
        } catch (Throwable ignored) {
            // vanilla resources
        }
    }
}
