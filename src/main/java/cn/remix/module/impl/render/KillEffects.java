package cn.remix.module.impl.render;

import cn.remix.event.base.annotation.EventTarget;
import cn.remix.event.impl.AttackEvent;
import cn.remix.event.impl.UpdateEvent;
import cn.remix.event.impl.WorldEvent;
import cn.remix.module.Category;
import cn.remix.module.Module;
import cn.remix.module.value.impl.BoolValue;
import cn.remix.module.value.impl.ModeValue;
import cn.remix.module.value.impl.NumberValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KillEffects extends Module {
    private final BoolValue onlyTargeted = new BoolValue("Only Targeted", true);
    private final NumberValue targetTimeout = new NumberValue("Target Timeout", 3.5, 0.5, 10.0, 0.5);
    private final BoolValue lightning = new BoolValue("Lightning", true);
    private final NumberValue lightningAmount = new NumberValue("Lightning Amount", 1, 1, 10, 1);
    private final BoolValue particles = new BoolValue("Particles", true);
    private final ModeValue particle = new ModeValue("Particle", "End Rod", "Crit", "End Rod", "Flame", "Heart", "Totem", "Explosion", "Sonic Boom");
    private final ModeValue shape = new ModeValue("Shape", "Burst", "Burst", "Sphere", "Spiral", "Column", "Halo", "Ring");
    private final NumberValue particleCount = new NumberValue("Particle Count", 40, 5, 200, 1);
    private final NumberValue particleSpeed = new NumberValue("Particle Speed", 0.2, 0.0, 2.0, 0.05);
    private final BoolValue sound = new BoolValue("Sound", true);
    private final ModeValue soundType = new ModeValue("Sound", "Thunder", "Thunder", "Explosion", "Totem", "Level Up", "Anvil", "Shield Break");
    private final NumberValue volume = new NumberValue("Volume", 1.0, 0.0, 10.0, 0.1);
    private final NumberValue pitch = new NumberValue("Pitch", 1.0, 0.5, 2.0, 0.05);
    private final BoolValue firework = new BoolValue("Firework", false);
    private final BoolValue explosionSmoke = new BoolValue("Explosion Smoke", false);

    private final Set<Integer> processedEntities = new HashSet<>();
    private final Map<Integer, Long> attackedTargets = new HashMap<>();

    public KillEffects() {
        super("KillEffects", Category.Render);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @EventTarget
    public void onWorld(WorldEvent event) {
        reset();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            attackedTargets.put(event.getEntity().getId(), System.currentTimeMillis());
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.world == null || mc.player == null) return;

        setSuffix(particle.getValue());

        if (onlyTargeted.getValue()) {
            long threshold = (long) (targetTimeout.getValue() * 1000.0f);
            long now = System.currentTimeMillis();
            attackedTargets.entrySet().removeIf(entry -> now - entry.getValue() > threshold);
        }

        List<Entity> entities = new ArrayList<>();
        mc.world.getEntities().forEach(entities::add);

        for (Entity entity : entities) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player) continue;
            if (!living.isDead() && living.getHealth() > 0.0f) continue;
            if (processedEntities.contains(entity.getId())) continue;

            if (onlyTargeted.getValue() && !attackedTargets.containsKey(entity.getId())) {
                processedEntities.add(entity.getId());
                continue;
            }

            renderEffects(living);
            processedEntities.add(entity.getId());
        }
    }

    private void renderEffects(LivingEntity entity) {
        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

        if (lightning.getValue()) {
            int amount = lightningAmount.getValue().intValue();
            for (int i = 0; i < amount; i++) {
                LightningEntity lightningEntity = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
                double offsetX = i == 0 ? 0.0 : (mc.world.random.nextDouble() - 0.5) * 0.5;
                double offsetZ = i == 0 ? 0.0 : (mc.world.random.nextDouble() - 0.5) * 0.5;
                lightningEntity.setPosition(pos.x + offsetX, pos.y, pos.z + offsetZ);
                mc.world.addEntity(lightningEntity);
            }
        }

        if (particles.getValue()) {
            spawnParticles(entity, getParticle());
        }

        if (sound.getValue()) {
            mc.world.playSound(
                    mc.player,
                    BlockPos.ofFloored(pos),
                    SoundEvent.of(Identifier.of("minecraft", getSoundId())),
                    SoundCategory.PLAYERS,
                    volume.getValue(),
                    pitch.getValue()
            );
        }

        if (firework.getValue()) {
            mc.world.addEntity(new net.minecraft.entity.projectile.FireworkRocketEntity(mc.world, new ItemStack(Items.FIREWORK_ROCKET), entity));
            mc.world.addParticleClient(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 0.0, 0.0, 0.0);
        }

        if (explosionSmoke.getValue()) {
            mc.world.addParticleClient(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 0.0, 0.0, 0.0);
        }
    }

    private ParticleEffect getParticle() {
        return switch (particle.getValue()) {
            case "Crit" -> ParticleTypes.CRIT;
            case "Flame" -> ParticleTypes.FLAME;
            case "Heart" -> ParticleTypes.HEART;
            case "Totem" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "Explosion" -> ParticleTypes.EXPLOSION;
            case "Sonic Boom" -> ParticleTypes.SONIC_BOOM;
            default -> ParticleTypes.END_ROD;
        };
    }

    private String getSoundId() {
        return switch (soundType.getValue()) {
            case "Explosion" -> "entity.generic.explode";
            case "Totem" -> "item.totem.use";
            case "Level Up" -> "entity.player.levelup";
            case "Anvil" -> "block.anvil.land";
            case "Shield Break" -> "item.shield.break";
            default -> "entity.lightning_bolt.thunder";
        };
    }

    private void spawnParticles(LivingEntity entity, ParticleEffect effect) {
        Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
        int count = particleCount.getValue().intValue();
        double speed = particleSpeed.getValue();
        double height = entity.getHeight();
        double width = entity.getWidth();

        switch (shape.getValue()) {
            case "Sphere" -> {
                for (int i = 0; i < count; i++) {
                    double u = mc.world.random.nextDouble();
                    double v = mc.world.random.nextDouble();
                    double theta = 2.0 * Math.PI * u;
                    double phi = Math.acos(2.0 * v - 1.0);
                    double dx = Math.sin(phi) * Math.cos(theta);
                    double dy = Math.sin(phi) * Math.sin(theta);
                    double dz = Math.cos(phi);
                    mc.world.addParticleClient(effect, pos.x + dx, pos.y + dy + height / 2.0, pos.z + dz, 0.0, 0.0, 0.0);
                }
            }
            case "Spiral" -> {
                for (int i = 0; i < count; i++) {
                    double yOffset = (double) i / count * 2.5;
                    double angle = yOffset * 5.0;
                    mc.world.addParticleClient(effect, pos.x + Math.cos(angle) * 0.8, pos.y + yOffset, pos.z + Math.sin(angle) * 0.8, 0.0, 0.05, 0.0);
                }
            }
            case "Column" -> {
                for (int i = 0; i < count; i++) {
                    mc.world.addParticleClient(effect, pos.x + randomOffset(width), pos.y + 0.1, pos.z + randomOffset(width), 0.0, speed, 0.0);
                }
            }
            case "Halo" -> {
                for (int i = 0; i < count; i++) {
                    double angle = (double) i / count * Math.PI * 2.0;
                    mc.world.addParticleClient(effect, pos.x + Math.cos(angle) * 0.7, pos.y + height + 0.5, pos.z + Math.sin(angle) * 0.7, 0.0, 0.0, 0.0);
                }
            }
            case "Ring" -> {
                for (int i = 0; i < count; i++) {
                    double angle = (double) i / count * Math.PI * 2.0;
                    double dx = Math.cos(angle);
                    double dz = Math.sin(angle);
                    mc.world.addParticleClient(effect, pos.x + dx * 0.2, pos.y + 0.1, pos.z + dz * 0.2, dx * speed * 2.0, 0.0, dz * speed * 2.0);
                }
            }
            default -> {
                for (int i = 0; i < count; i++) {
                    mc.world.addParticleClient(
                            effect,
                            pos.x + randomOffset(width),
                            pos.y + mc.world.random.nextDouble() * height,
                            pos.z + randomOffset(width),
                            randomOffset(speed),
                            randomOffset(speed),
                            randomOffset(speed)
                    );
                }
            }
        }
    }

    private double randomOffset(double scale) {
        return (mc.world.random.nextDouble() - 0.5) * scale;
    }

    private void reset() {
        processedEntities.clear();
        attackedTargets.clear();
    }
}
