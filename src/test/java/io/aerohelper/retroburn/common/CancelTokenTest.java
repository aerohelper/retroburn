// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CancelTokenTest {

    @Test
    void aChildFollowsItsParentButNotTheOtherWayRound() {
        CancelToken parent = new CancelToken();
        CancelToken child = parent.child();

        child.cancel();
        assertTrue(child.isCancelled());
        assertFalse(parent.isCancelled());
        assertDoesNotThrow(parent::throwIfCancelled);

        CancelToken sibling = parent.child();
        parent.cancel();
        assertTrue(sibling.isCancelled());
        assertThrows(OperationCancelledException.class, sibling::throwIfCancelled);
    }
}
