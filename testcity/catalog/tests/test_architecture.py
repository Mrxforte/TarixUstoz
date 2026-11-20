"""Architecture rules: the inner layers must not depend on the outer ones."""
from __future__ import annotations

import ast
from pathlib import Path

from django.test import SimpleTestCase

BASE_DIR = Path(__file__).resolve().parent.parent.parent
FORBIDDEN_IN_DOMAIN = {"django", "rest_framework", "api", "catalog", "infrastructure", "interfaces"}
FORBIDDEN_IN_APPLICATION = {"django", "rest_framework", "api", "catalog", "infrastructure", "interfaces"}


def _imported_roots(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    roots: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            roots.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            roots.add(node.module.split(".")[0])
    return roots


class LayerDependencyTests(SimpleTestCase):
    def _assert_clean(self, package: str, forbidden: set[str]) -> None:
        violations = []
        for path in (BASE_DIR / package).rglob("*.py"):
            leaked = _imported_roots(path) & forbidden
            if leaked:
                violations.append(f"{path.relative_to(BASE_DIR)} -> {sorted(leaked)}")
        self.assertEqual(violations, [], f"{package} qatlami tashqi qatlamlarga bog'lanmasligi kerak")

    def test_domain_layer_is_framework_free(self):
        self._assert_clean("domain", FORBIDDEN_IN_DOMAIN)

    def test_application_layer_only_depends_on_domain(self):
        self._assert_clean("application", FORBIDDEN_IN_APPLICATION)
