package cn.remix.util.player;

import cn.remix.util.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

@UtilityClass
public class RayCastUtil implements IMinecraft {

    public boolean overBlock(BlockPos pos, Direction side, boolean strict) {
        if (mc.player == null || mc.world == null || mc.crosshairTarget == null) return false;
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return false;
        if (!hit.getBlockPos().equals(pos)) return false;
        return !strict || hit.getSide() == side;
    }

    public boolean overEntity(Entity target) {
        if (mc.player == null || mc.world == null || mc.crosshairTarget == null) return false;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) return false;
        return hit.getEntity().equals(target);
    }
}
