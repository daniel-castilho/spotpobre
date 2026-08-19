package com.spotpobre.backend.domain.common.pagination;

/**
 * Pure domain pagination request. Free of any framework or infrastructure dependency.
 *
 * <p>{@code pageNumber} is zero-based, matching the semantics previously exposed through Spring
 * Data's {@code Pageable}. An optional {@link Sort} may be provided but is entirely up to the
 * persistence adapter to honor.
 */
public record PageRequest(int pageNumber, int pageSize, Sort sort) {

    /**
     * Upper bound for any single page, enforced by the search use cases (S8 of the
     * search-pagination epic). Kept in the domain so all layers agree on the limit.
     */
    public static final int MAX_PAGE_SIZE = 50;

    public PageRequest {
        if (pageNumber < 0) {
            throw new IllegalArgumentException("pageNumber must not be negative");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be at least 1");
        }
    }

    public static PageRequest of(int pageNumber, int pageSize) {
        return new PageRequest(pageNumber, pageSize, null);
    }

    public static PageRequest of(int pageNumber, int pageSize, Sort sort) {
        return new PageRequest(pageNumber, pageSize, sort);
    }

    /**
     * Offset of the first element of this page, assuming a zero-based {@code pageNumber}.
     */
    public long offset() {
        return (long) pageNumber * pageSize;
    }

    public record Sort(String property, Direction direction) {

        public enum Direction {
            ASC,
            DESC
        }

        public static Sort asc(String property) {
            return new Sort(property, Direction.ASC);
        }

        public static Sort desc(String property) {
            return new Sort(property, Direction.DESC);
        }
    }
}