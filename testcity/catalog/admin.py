from django.contrib import admin

from catalog.models import (
    ExamProgram,
    ExamProgramTranslation,
    Language,
    ProgramSection,
    ScoreBand,
    Subject,
    SubjectTranslation,
)


class ProgramSectionInline(admin.TabularInline):
    model = ProgramSection
    extra = 0


class ScoreBandInline(admin.TabularInline):
    model = ScoreBand
    extra = 0


class SubjectTranslationInline(admin.TabularInline):
    model = SubjectTranslation
    extra = 0


class ExamProgramTranslationInline(admin.TabularInline):
    model = ExamProgramTranslation
    extra = 0


@admin.register(Language)
class LanguageAdmin(admin.ModelAdmin):
    list_display = ("code", "name", "native_name", "is_default", "is_active", "order")
    list_editable = ("is_default", "is_active", "order")
    ordering = ("order", "code")


@admin.register(Subject)
class SubjectAdmin(admin.ModelAdmin):
    list_display = ("name", "code", "category", "order", "is_active")
    list_filter = ("category", "is_active")
    search_fields = ("name", "code", "name_en")
    inlines = (SubjectTranslationInline,)


@admin.register(ExamProgram)
class ExamProgramAdmin(admin.ModelAdmin):
    list_display = ("name", "code", "kind", "subject", "max_score", "is_active")
    list_filter = ("kind", "is_active", "subject")
    search_fields = ("name", "code")
    inlines = (ProgramSectionInline, ScoreBandInline, ExamProgramTranslationInline)
