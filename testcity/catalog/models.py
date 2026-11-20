"""ORM models for the subject catalogue and exam blueprints.

These tables are additive: they never alter the existing ``api`` history tables,
so migrating a live database only creates new, empty tables.
"""
from __future__ import annotations

from django.db import models

from domain.certificates.entities import CertificateFamily, CertificateKind
from domain.subjects.entities import SubjectCategory

SUBJECT_CATEGORY_CHOICES = tuple((item.value, item.value) for item in SubjectCategory)

EXAM_KIND_CHOICES = (
    (CertificateKind.MILLIY_SERTIFIKAT.value, "Milliy sertifikat"),
    (CertificateKind.ATTESTATSIYA.value, "Attestatsiya"),
    (CertificateKind.SAT.value, "SAT"),
    (CertificateKind.IELTS.value, "IELTS"),
    (CertificateKind.TOEFL.value, "TOEFL"),
    (CertificateKind.CEFR.value, "CEFR"),
)

CERTIFICATION_KIND_VALUES = tuple(kind.value for kind in CertificateKind.for_family(CertificateFamily.NATIONAL))
INTERNATIONAL_KIND_VALUES = tuple(kind.value for kind in CertificateKind.for_family(CertificateFamily.INTERNATIONAL))


class Subject(models.Model):
    """A fan served by the platform (tarix, matematika, ingliz tili, ...)."""

    code = models.SlugField(max_length=64, unique=True)
    name = models.CharField(max_length=128)
    name_en = models.CharField(max_length=128, blank=True)
    category = models.CharField(max_length=32, choices=SUBJECT_CATEGORY_CHOICES, default=SubjectCategory.SCHOOL.value)
    grades = models.JSONField(default=list, blank=True)
    icon = models.CharField(max_length=64, blank=True)
    order = models.PositiveIntegerField(default=0)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ("order", "name")
        verbose_name = "Fan"
        verbose_name_plural = "1. Fanlar"

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"


class Language(models.Model):
    """Content language managed from the admin (uz, ru, en, kz, tj, ...)."""

    code = models.SlugField(max_length=8, unique=True)
    name = models.CharField(max_length=64)
    native_name = models.CharField(max_length=64, blank=True)
    is_active = models.BooleanField(default=True)
    is_default = models.BooleanField(default=False)
    order = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ("order", "code")
        verbose_name = "Til"
        verbose_name_plural = "1.1 Tillar"

    def __str__(self) -> str:
        return f"{self.name} ({self.code})"

    def save(self, *args, **kwargs):
        super().save(*args, **kwargs)
        if self.is_default:
            Language.objects.exclude(pk=self.pk).filter(is_default=True).update(is_default=False)


class SubjectTranslation(models.Model):
    subject = models.ForeignKey(Subject, on_delete=models.CASCADE, related_name="translations")
    language = models.ForeignKey(Language, on_delete=models.CASCADE, related_name="subject_translations")
    name = models.CharField(max_length=128)
    description = models.TextField(blank=True)

    class Meta:
        verbose_name = "Fan tarjimasi"
        verbose_name_plural = "1.2 Fan tarjimalari"
        constraints = [
            models.UniqueConstraint(fields=["subject", "language"], name="uniq_subject_language"),
        ]

    def __str__(self) -> str:
        return f"{self.subject.code} [{self.language.code}]"


class ExamProgram(models.Model):
    """An exam blueprint: milliy sertifikat, attestatsiya, SAT, IELTS or CEFR."""

    code = models.SlugField(max_length=96, unique=True)
    name = models.CharField(max_length=160)
    kind = models.CharField(max_length=32, choices=EXAM_KIND_CHOICES)
    subject = models.ForeignKey(
        Subject,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="exam_programs",
    )
    description = models.TextField(blank=True)
    duration_minutes = models.PositiveIntegerField(default=0)
    max_score = models.DecimalField(max_digits=7, decimal_places=2, default=100)
    passing_score = models.DecimalField(max_digits=7, decimal_places=2, null=True, blank=True)
    order = models.PositiveIntegerField(default=0)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ("order", "name")
        verbose_name = "Test dasturi"
        verbose_name_plural = "2. Test dasturlari"
        indexes = [models.Index(fields=["kind", "is_active"], name="exam_program_kind_idx")]

    def __str__(self) -> str:
        return f"{self.name} [{self.kind}]"

    @property
    def is_certification(self) -> bool:
        return self.kind in CERTIFICATION_KIND_VALUES

    @property
    def is_international(self) -> bool:
        return self.kind in INTERNATIONAL_KIND_VALUES


class ProgramSection(models.Model):
    """A scored block of a program (test bo'limi, math section, listening, ...)."""

    program = models.ForeignKey(ExamProgram, on_delete=models.CASCADE, related_name="sections")
    code = models.SlugField(max_length=64)
    name = models.CharField(max_length=160)
    question_count = models.PositiveIntegerField(default=0)
    max_score = models.DecimalField(max_digits=7, decimal_places=2, default=0)
    time_limit_minutes = models.PositiveIntegerField(default=0)
    order = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ("order", "code")
        verbose_name = "Dastur bo'limi"
        verbose_name_plural = "3. Dastur bo'limlari"
        constraints = [
            models.UniqueConstraint(fields=["program", "code"], name="uniq_program_section_code"),
        ]

    def __str__(self) -> str:
        return f"{self.program.code}/{self.code}"


class ScoreBand(models.Model):
    """A named score interval (A toifa, IELTS Band 7.0, CEFR B2, ...)."""

    program = models.ForeignKey(ExamProgram, on_delete=models.CASCADE, related_name="bands")
    label = models.CharField(max_length=64)
    min_score = models.DecimalField(max_digits=7, decimal_places=2, default=0)
    max_score = models.DecimalField(max_digits=7, decimal_places=2, default=0)
    description = models.CharField(max_length=255, blank=True)
    order = models.PositiveIntegerField(default=0)

    class Meta:
        ordering = ("min_score", "label")
        verbose_name = "Baho oralig'i"
        verbose_name_plural = "4. Baho oraliqlari"
        constraints = [
            models.UniqueConstraint(fields=["program", "label"], name="uniq_score_band_label"),
        ]

    def __str__(self) -> str:
        return f"{self.program.code}: {self.label}"


class ExamProgramTranslation(models.Model):
    program = models.ForeignKey(ExamProgram, on_delete=models.CASCADE, related_name="translations")
    language = models.ForeignKey(Language, on_delete=models.CASCADE, related_name="program_translations")
    name = models.CharField(max_length=160)
    description = models.TextField(blank=True)

    class Meta:
        verbose_name = "Dastur tarjimasi"
        verbose_name_plural = "2.2 Dastur tarjimalari"
        constraints = [
            models.UniqueConstraint(fields=["program", "language"], name="uniq_program_language"),
        ]

    def __str__(self) -> str:
        return f"{self.program.code} [{self.language.code}]"
