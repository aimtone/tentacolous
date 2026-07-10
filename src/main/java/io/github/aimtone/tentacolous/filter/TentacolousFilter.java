package io.github.aimtone.tentacolous.filter;

/**
 * Base class for programmatic listener filters.
 *
 * @param <T> listener entity type
 */
public abstract class TentacolousFilter<T> {

    public abstract boolean accept(TentacolousFilterContext<T> context);

    /**
     * Sentinel used as the annotation default when no custom filter is configured.
     */
    public static final class None extends TentacolousFilter<Object> {
        private None() {
        }

        @Override
        public boolean accept(TentacolousFilterContext<Object> context) {
            return true;
        }
    }
}
