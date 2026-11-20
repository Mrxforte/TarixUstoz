"""Repository port for certification programs (milliy sertifikat, attestatsiya)."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from domain.certification.entities import CertificationKind, CertificationProgram


class CertificationProgramRepository(Protocol):
    def list(
        self,
        *,
        kind: CertificationKind | None = None,
        subject_code: str | None = None,
        active_only: bool = True,
    ) -> Sequence[CertificationProgram]:
        ...

    def get(self, code: str) -> CertificationProgram | None:
        ...

    def save(self, program: CertificationProgram) -> CertificationProgram:
        ...
