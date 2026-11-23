// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

/**
 * How fast the spinning disc turns: a state and an angular speed, advanced frame by frame. Plain
 * Java so it can be tested; {@link DiscAnimation} draws it. See {@code docs/design/LLD-themes.md}
 * section 5.
 */
public final class DiscMotion {

    public enum State {
        IDLE,
        SPIN_UP,
        BURNING,
        DONE,
        STOPPED
    }

    public static final double BURNING_SPEED = 720;
    static final double SPIN_UP_ACCELERATION = 600;
    static final double DONE_DECELERATION = 480;
    static final double STOP_DECELERATION = 1440;

    private State state = State.IDLE;
    private double speed;
    private double angle;

    public State state() {
        return state;
    }

    /** Degrees per second. */
    public double speed() {
        return speed;
    }

    /** Degrees, 0 to 360. */
    public double angle() {
        return angle;
    }

    public void setState(State next) {
        if (next == State.IDLE) {
            speed = 0;
        }
        state = next;
    }

    /** Moves time on by {@code seconds} (one frame). */
    public void advance(double seconds) {
        if (seconds <= 0) {
            return;
        }
        switch (state) {
            case IDLE -> speed = 0;
            case SPIN_UP -> {
                speed = Math.min(BURNING_SPEED, speed + SPIN_UP_ACCELERATION * seconds);
                if (speed >= BURNING_SPEED) {
                    state = State.BURNING;
                }
            }
            case BURNING -> speed = BURNING_SPEED;
            case DONE -> speed = Math.max(0, speed - DONE_DECELERATION * seconds);
            case STOPPED -> speed = Math.max(0, speed - STOP_DECELERATION * seconds);
        }
        angle = (angle + speed * seconds) % 360;
    }
}
