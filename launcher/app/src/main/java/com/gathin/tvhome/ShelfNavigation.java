package com.gathin.tvhome;

/** Shared boundary rules for independent horizontal shelves. */
final class ShelfNavigation {
    private ShelfNavigation() {}
    static int clamp(int position, int count) {
        return count <= 0 ? -1 : Math.max(0, Math.min(position, count - 1));
    }
    static int horizontal(int position, int delta, int count) {
        return clamp(position + delta, count);
    }
}
