from django.contrib import admin

from catalog.models import ExamProgram, ProgramSection, ScoreBand, Subject


class ProgramSectionInline(admin.TabularInline):
    model = ProgramSection
    extra = 0


class ScoreBandInline(admin.TabularInline):
    model = ScoreBand
    extra = 0


@admin.register(Subject)
class SubjectAdmin(admin.ModelAdmin):
    list_display = ("name", "code", "category", "order", "is_active")
    list_filter = ("category", "is_active")
    search_fields = ("name", "code", "name_en")


@admin.register(ExamProgram)
class ExamProgramAdmin(admin.ModelAdmin):
    list_display = ("name", "code", "kind", "subject", "max_score", "is_active")
    list_filter = ("kind", "is_active", "subject")
    search_fields = ("name", "code")
    inlines = (ProgramSectionInline, ScoreBandInline)
