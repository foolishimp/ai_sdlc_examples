# Implements: REQ-ITER-003 (Functor Encoding Tracking)
"""Functor dispatch table: (FunctionalUnit, Category) → callable."""

from typing import Callable

from . import fd_classify, fd_emit, fd_evaluate, fd_route, fd_sense
from .models import Category, FunctionalUnit


DISPATCH: dict[tuple[FunctionalUnit, Category], Callable] = {
    # F_D implementations
    (FunctionalUnit.EVALUATE, Category.F_D): fd_evaluate.run_check,
    (FunctionalUnit.CLASSIFY, Category.F_D): fd_classify.classify_req_tag,
    (FunctionalUnit.SENSE, Category.F_D): fd_sense.sense_req_tag_coverage,
    (FunctionalUnit.EMIT, Category.F_D): fd_emit.emit_event,
    (FunctionalUnit.ROUTE, Category.F_D): fd_route.select_next_edge,
    # F_P stubs (future: LLM calls)
    # F_H stubs (future: interactive prompts)
}


def dispatch(unit: FunctionalUnit, category: Category) -> Callable:
    """Look up the callable for a (unit, category) pair.

    Raises NotImplementedError for unregistered combinations.
    """
    fn = DISPATCH.get((unit, category))
    if fn is None:
        raise NotImplementedError(
            f"No implementation for ({unit.value}, {category.value})"
        )
    return fn


def lookup_and_dispatch(unit: FunctionalUnit, profile: dict) -> Callable:
    """Resolve category from profile encoding, then dispatch."""
    category = fd_route.lookup_encoding(profile, unit.value)
    return dispatch(unit, category)
