package cn.remix.util.animation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EasingAnimation {

    private Easing easing;
    private long duration;
    private long millis;
    private long startTime;

    private double startValue;
    private double destinationValue;
    private double value;
    private boolean finished;

    public EasingAnimation(final Easing easing, final long duration) {
        this.easing = easing;
        this.startTime = System.currentTimeMillis();
        this.duration = duration;
    }

    public void run(final double destinationValue) {
        this.millis = System.currentTimeMillis();
        if (this.destinationValue != destinationValue) {
            this.destinationValue = destinationValue;
            this.reset();
        } else {
            this.finished = this.millis - this.duration > this.startTime;
            if (this.finished) {
                this.value = destinationValue;
                return;
            }
        }

        final double result = this.easing.getFunction().apply(this.getProgress());
        if (this.value > destinationValue) {
            this.value = this.startValue - (this.startValue - destinationValue) * result;
        } else {
            this.value = this.startValue + (destinationValue - this.startValue) * result;
        }
    }

    public double getProgress() {
        return (double) (System.currentTimeMillis() - this.startTime) / (double) this.duration;
    }

    public Double getValue() {
        return this.value;
    }

    public void reset() {
        this.startTime = System.currentTimeMillis();
        this.startValue = value;
        this.finished = false;
    }
}