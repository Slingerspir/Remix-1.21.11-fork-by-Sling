package injection;

import cn.remix.module.impl.combat.Aura;
import cn.remix.module.impl.combat.TpAura;
import cn.remix.module.impl.render.Animation;
import cn.remix.util.IMinecraft;
import cn.remix.util.player.ItemSpoofUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer implements IMinecraft {
    @Shadow
    private float equipProgressMainHand;
    @Shadow
    private float lastEquipProgressMainHand;
    @Shadow
    private float equipProgressOffHand;
    @Shadow
    private float lastEquipProgressOffHand;
    @Shadow
    private ItemStack offHand;
    @Shadow
    private ItemStack mainHand;

    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, float equipProgress, float swingProgress, Arm arm);
    @Shadow
    protected abstract void renderMapInBothHands(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, float pitch, float equipProgress, float swingProgress);
    @Shadow
    protected abstract void renderMapInOneHand(MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, float equipProgress, Arm arm, float swingProgress, ItemStack stack);
    @Shadow
    protected abstract void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress);
    @Shadow
    protected abstract void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress);
    @Shadow
    protected abstract void applyEatOrDrinkTransformation(MatrixStack matrices, float tickDelta, Arm arm, ItemStack stack, PlayerEntity player);

    @Inject(method = "updateHeldItems", at = @At("HEAD"), cancellable = true)
    private void onUpdateHeldItems(CallbackInfo ci) {
        if (mc.player == null) return;
        Animation animation = instance.getModuleManager().getModule(Animation.class);

        if (animation.isEnabled() && !animation.equipProgress.getValue()) {
            ItemStack mainStack = mc.player.getMainHandStack();
            ItemStack offStack = mc.player.getOffHandStack();
            this.mainHand = mainStack;
            this.lastEquipProgressMainHand = 1;
            this.equipProgressMainHand = 1;
            this.offHand = offStack;
            this.lastEquipProgressOffHand = 1;
            this.equipProgressOffHand = 1;
            ci.cancel();
        }
    }

    @Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("HEAD"), cancellable = true)
    private void renderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        Animation animation = instance.getModuleManager().getModule(Animation.class);
        Aura aura = instance.getModuleManager().getModule(Aura.class);
        TpAura tpAura = instance.getModuleManager().getModule(TpAura.class);

        if (!animation.isEnabled()) {
            return;
        }

        if (!animation.equipProgress.getValue()) {
            equipProgress = 0.0F;
        }

        ci.cancel();

        if (!player.isUsingSpyglass()) {
            boolean bl = hand == Hand.MAIN_HAND;
            Arm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();

            if (bl) { 
                ItemStack spoofedSlot = ItemSpoofUtil.getStack();
                item = spoofedSlot != null ? spoofedSlot : item;
            }

            matrices.push();
            if (item.isEmpty()) {
                if (bl && !player.isInvisible()) {
                    this.renderArmHoldingItem(matrices, orderedRenderCommandQueue, light, equipProgress, swingProgress, arm);
                }
            } else if (item.contains(DataComponentTypes.MAP_ID)) {
                if (bl && this.offHand.isEmpty()) {
                    this.renderMapInBothHands(matrices, orderedRenderCommandQueue, light, pitch, equipProgress, swingProgress);
                } else {
                    this.renderMapInOneHand(matrices, orderedRenderCommandQueue, light, equipProgress, arm, swingProgress, item);
                }
            } else if (item.isOf(Items.CROSSBOW)) {
                boolean bl2 = CrossbowItem.isCharged(item);
                boolean bl3 = arm == Arm.RIGHT;
                int i = bl3 ? 1 : -1;
                if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    matrices.translate((float)i * -0.4785682F, -0.094387F, 0.05731531F);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-11.935F));
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)i * 65.3F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)i * -9.785F));
                    float f = (float)item.getMaxUseTime(player) - ((float)player.getItemUseTimeLeft() - tickDelta + 1.0F);
                    float g = f / (float)CrossbowItem.getPullTime(item, player);
                    if (g > 1.0F) {
                        g = 1.0F;
                    }

                    if (g > 0.1F) {
                        float h = MathHelper.sin((f - 0.1F) * 1.3F);
                        float j = g - 0.1F;
                        float k = h * j;
                        matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                    }

                    matrices.translate(g * 0.0F, g * 0.0F, g * 0.04F);
                    matrices.scale(1.0F, 1.0F, 1.0F + g * 0.2F);
                    matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float)i * 45.0F));
                } else {
                    this.swingArm(swingProgress, equipProgress, matrices, i, arm);
                    if (bl2 && swingProgress < 0.001F && bl) {
                        matrices.translate((float)i * -0.641864F, 0.0F, 0.0F);
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)i * 10.0F));
                    }
                }

                this.renderItem(player, item, bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, orderedRenderCommandQueue, light);
            } else {
                boolean bl2 = arm == Arm.RIGHT;
                int l = bl2 ? 1 : -1;
                if (player.isUsingItem() && player.getItemUseTimeLeft() > 0 && player.getActiveHand() == hand) {
                    switch (item.getUseAction()) {
                        case NONE:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case EAT:
                        case DRINK:
                            this.applyEatOrDrinkTransformation(matrices, tickDelta, arm, item, player);
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case BLOCK:
                            if (item.isIn(ItemTags.SWORDS) && (this.offHand.isEmpty() || aura.isRenderBlock() || tpAura.isRenderBlock())) {
                                this.blockAnimation(swingProgress, equipProgress, matrices, arm, l, animation);
                            } else {
                                this.applyEquipOffset(matrices, arm, equipProgress);
                                if (!(item.getItem() instanceof ShieldItem)) {
                                    matrices.translate((float)l * -0.14142136F, 0.08F, 0.14142136F);
                                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
                                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)l * 13.365F));
                                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)l * 78.05F));
                                }
                            }
                            break;
                        case BOW:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float)l * -0.2785682F, 0.18344387F, 0.15731531F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-13.935F));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)l * 35.3F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)l * -9.785F));
                            float m = (float)item.getMaxUseTime(player) - ((float)player.getItemUseTimeLeft() - tickDelta + 1.0F);
                            float f = m / 20.0F;
                            f = (f * f + f * 2.0F) / 3.0F;
                            if (f > 1.0F) {
                                f = 1.0F;
                            }

                            if (f > 0.1F) {
                                float g = MathHelper.sin((m - 0.1F) * 1.3F);
                                float h = f - 0.1F;
                                float j = g * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(f * 0.0F, f * 0.0F, f * 0.04F);
                            matrices.scale(1.0F, 1.0F, 1.0F + f * 0.2F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float)l * 45.0F));
                            break;
                        case SPEAR:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float)l * -0.5F, 0.7F, 0.1F);
                            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-55.0F));
                            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)l * 35.3F));
                            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)l * -9.785F));
                            float m1 = (float)item.getMaxUseTime(player) - ((float)player.getItemUseTimeLeft() - tickDelta + 1.0F);
                            float f1 = m1 / 10.0F;
                            if (f1 > 1.0F) {
                                f1 = 1.0F;
                            }

                            if (f1 > 0.1F) {
                                float g = MathHelper.sin((m1 - 0.1F) * 1.3F);
                                float h = f1 - 0.1F;
                                float j = g * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(0.0F, 0.0F, f1 * 0.2F);
                            matrices.scale(1.0F, 1.0F, 1.0F + f1 * 0.2F);
                            matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees((float)l * 45.0F));
                            break;
                        case BUNDLE:
                            this.swingArm(swingProgress, equipProgress, matrices, l, arm);
                    }
                } else if (player.isUsingRiptide()) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    matrices.translate((float)l * -0.4F, 0.8F, 0.3F);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)l * 65.0F));
                    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)l * -85.0F));
                } else {
                    boolean isSwordBlock = item.isIn(ItemTags.SWORDS) && mc.options.useKey.isPressed() && this.offHand.isEmpty();
                    if (isSwordBlock || ((aura.isRenderBlock() || tpAura.isRenderBlock()) && hand == Hand.MAIN_HAND && item.isIn(ItemTags.SWORDS))) {
                        this.blockAnimation(swingProgress, equipProgress, matrices, arm, l, animation);
                    } else {
                        this.swingArm(swingProgress, equipProgress, matrices, l, arm);
                    }
                }

                this.renderItem(player, item, bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, orderedRenderCommandQueue, light);
            }

            matrices.pop();
        }
    }

    @Unique
    private void blockAnimation(float swingProgress, float equipProgress, MatrixStack matrices, Arm arm, int armX, Animation animation) {
        this.applyEquipOffset(matrices, arm, equipProgress);

        float process = MathHelper.sin((float) (MathHelper.sqrt(swingProgress) * Math.PI));
        float processSqr = MathHelper.sin((float) (swingProgress * swingProgress * Math.PI));

        switch (animation.blockMode.getValue()) {
            case "1.7" -> {
                this.applySwingOffset(matrices, arm, swingProgress);
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
            }

            case "Stella" -> {
                this.applySwingOffset(matrices, arm, swingProgress);
                matrices.translate(-0.15F, 0.1F, -0.06F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-95.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-278.0F));
            }

            case "Leaked" -> {
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-102.25F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 7.365F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * 78.05F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(process * -10.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * process * 30.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * process * -13.0F));
            }

            case "SideDown" -> {
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * processSqr * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * process * -20.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(process * -80.0F));
            }

            case "Styles" -> {
                this.applySwingOffset(matrices, arm, 0);
                matrices.translate(0.08F * (float) armX, 0.02F, 0.0F);
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-process * 41.0F));
            }

            case "Flux" -> {
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.translate(0.0F, 0.0F, process * -0.25F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(process * -15.0F));
            }

            case "Spin" -> {
                float spin = (System.currentTimeMillis() % 720) / 2.0F;
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * (16.0F + spin)));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
            }

            case "Screw" -> {
                float timeSpin = (System.currentTimeMillis() % 1000) / 2.7F;
                matrices.translate((float) armX * -0.15F, 0.05F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * timeSpin * 2.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(timeSpin));
            }

            case "Swang" -> {
                this.applySwingOffset(matrices, arm, swingProgress);
                matrices.translate((float) armX * -0.15F, 0.15F, 0.1F);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-105.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * 16.0F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) armX * -278.0F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(process * 40.0F));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) armX * process * -10.0F));
            }
        }
    }

    @Unique
    private void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light) {
        if (!stack.isEmpty()) {
            ((HeldItemRenderer)(Object)this).renderItem(entity, stack, renderMode, matrices, orderedRenderCommandQueue, light);
        }
    }

    @Unique
    private void swingArm(float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm) {
        Animation animation = instance.getModuleManager().getModule(Animation.class);

        if (!animation.isEnabled() || animation.swingMode.is("Vanilla")) {
            float f = -0.4F * MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
            float g = 0.2F * MathHelper.sin(MathHelper.sqrt(swingProgress) * ((float) Math.PI * 2F));
            float h = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
            matrices.translate((float) armX * f, g, h);
        }

        this.applyEquipOffset(matrices, arm, equipProgress);
        this.applySwingOffset(matrices, arm, swingProgress);
    }
}
