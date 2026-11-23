// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiscMotionTest {

    private static void run(DiscMotion motion, double seconds) {
        for (int frame = 0; frame < seconds * 60; frame++) {
            motion.advance(1 / 60.0);
        }
    }

    @Test
    void idleDiscStandsStill() {
        DiscMotion motion = new DiscMotion();
        run(motion, 2);
        assertEquals(0, motion.speed());
        assertEquals(0, motion.angle());
    }

    @Test
    void spinsUpToBurningSpeedInAboutASecondAndAHalf() {
        DiscMotion motion = new DiscMotion();
        motion.setState(DiscMotion.State.SPIN_UP);
        run(motion, 0.5);
        assertEquals(300, motion.speed(), 1e-6);
        assertEquals(DiscMotion.State.SPIN_UP, motion.state());
        run(motion, 1.0);
        assertEquals(DiscMotion.BURNING_SPEED, motion.speed(), 1e-6);
        assertEquals(DiscMotion.State.BURNING, motion.state());
        run(motion, 3);
        assertEquals(DiscMotion.BURNING_SPEED, motion.speed());
        assertTrue(motion.angle() >= 0 && motion.angle() < 360);
    }

    @Test
    void doneSlowsDownGentlyAndStoppedQuickly() {
        DiscMotion done = burning();
        done.setState(DiscMotion.State.DONE);
        run(done, 1);
        assertEquals(240, done.speed(), 1e-6);
        run(done, 1);
        assertEquals(0, done.speed());

        DiscMotion stopped = burning();
        stopped.setState(DiscMotion.State.STOPPED);
        run(stopped, 0.5);
        assertEquals(0, stopped.speed());
    }

    @Test
    void idleStopsAtOnceAndNoTimeChangesNothing() {
        DiscMotion motion = burning();
        double angle = motion.angle();
        motion.advance(0);
        assertEquals(angle, motion.angle());
        motion.setState(DiscMotion.State.IDLE);
        assertEquals(0, motion.speed());
    }

    private static DiscMotion burning() {
        DiscMotion motion = new DiscMotion();
        motion.setState(DiscMotion.State.SPIN_UP);
        run(motion, 2);
        return motion;
    }
}
