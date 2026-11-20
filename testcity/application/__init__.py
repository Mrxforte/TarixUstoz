"""Application layer: use cases orchestrating domain ports.

Application modules may import ``domain`` only; repositories are injected, so the
same use cases are unit-testable with in-memory fakes.
"""
