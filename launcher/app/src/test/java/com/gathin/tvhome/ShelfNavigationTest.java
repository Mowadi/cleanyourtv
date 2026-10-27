package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class ShelfNavigationTest {
    @Test public void emptyShelfHasNoTarget() {
        assertEquals(-1, ShelfNavigation.clamp(0, 0));
        assertEquals(-1, ShelfNavigation.horizontal(0, 1, 0));
    }
    @Test public void remoteCannotEscapeHorizontalEdges() {
        assertEquals(0, ShelfNavigation.horizontal(0, -1, 8));
        assertEquals(7, ShelfNavigation.horizontal(7, 1, 8));
        assertEquals(4, ShelfNavigation.horizontal(3, 1, 8));
        assertEquals(2, ShelfNavigation.horizontal(3, -1, 8));
    }
    @Test public void removingAppsClampsRememberedFocus() {
        assertEquals(2, ShelfNavigation.clamp(9, 3));
        assertEquals(0, ShelfNavigation.clamp(-1, 3));
        assertEquals(0, ShelfNavigation.horizontal(0, 1, 1));
    }
}
