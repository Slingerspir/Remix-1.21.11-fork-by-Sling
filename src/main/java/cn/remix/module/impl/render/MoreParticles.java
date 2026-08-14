package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;

public final class MoreParticles extends Module {
    private final ModeValue type = new ModeValue("Type", "Crit", "Crit", "Sharpness", "Heart", "Flame", "Smoke");
    private final NumberValue amount = new NumberValue("Amount", 15, 1, 50, 1);

    public MoreParticles() {
        super("MoreParticles", Category.Render);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        setSuffix(type.getValue());
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.world == null) return;

        Entity target = event.getEntity();

        if (target instanceof LivingEntity) {
            setSuffix(type.getValue());

            double x = target.getX();
            double y = target.getBodyY(0.5);
            double z = target.getZ();

            int count = amount.getValue().intValue();

            for (int i = 0; i < count; i++) {
                double offsetX = (mc.world.random.nextFloat() * 2.0F - 1.0F) * 0.5;
                double offsetY = (mc.world.random.nextFloat() * 2.0F - 1.0F) * 0.5;
                double offsetZ = (mc.world.random.nextFloat() * 2.0F - 1.0F) * 0.5;

                double motionX = (mc.world.random.nextFloat() * 2.0F - 1.0F) * 0.15;
                double motionY = mc.world.random.nextFloat() * 0.2;
                double motionZ = (mc.world.random.nextFloat() * 2.0F - 1.0F) * 0.15;

                switch (type.getValue()) {
                    case "Crit" ->
                            mc.world.addParticleClient(ParticleTypes.CRIT, x + offsetX, y + offsetY, z + offsetZ, motionX, motionY, motionZ);

                    case "Sharpness" ->
                            mc.world.addParticleClient(ParticleTypes.ENCHANTED_HIT, x + offsetX, y + offsetY, z + offsetZ, motionX, motionY, motionZ);

                    case "Heart" ->
                            mc.world.addParticleClient(ParticleTypes.HEART, x + offsetX, y + offsetY, z + offsetZ, motionX, motionY + 0.05, motionZ);

                    case "Flame" ->
                            mc.world.addParticleClient(ParticleTypes.FLAME, x + offsetX, y + offsetY, z + offsetZ, motionX * 0.5, motionY, motionZ * 0.5);

                    case "Smoke" ->
                            mc.world.addParticleClient(ParticleTypes.SMOKE, x + offsetX, y + offsetY, z + offsetZ, motionX * 0.3, motionY * 0.3, motionZ * 0.3);
                }
            }
        }
    }
}