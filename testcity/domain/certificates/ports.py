"""Repository port for certificate programs (milliy va xalqaro)."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from domain.certificates.entities import CertificateFamily, CertificateKind, CertificateProgram


class CertificateProgramRepository(Protocol):
    def list(
        self,
        *,
        family: CertificateFamily | None = None,
        kind: CertificateKind | None = None,
        subject_code: str | None = None,
        active_only: bool = True,
    ) -> Sequence[CertificateProgram]:
        ...

    def get(self, code: str) -> CertificateProgram | None:
        ...

    def save(self, program: CertificateProgram) -> CertificateProgram:
        ...
