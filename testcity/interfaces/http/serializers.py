"""DRF serializers for domain entities (no ORM models are exposed directly)."""
from __future__ import annotations

from rest_framework import serializers


class SubjectSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.CharField()
    name_en = serializers.CharField()
    category = serializers.CharField()
    grades = serializers.ListField(child=serializers.IntegerField())
    icon = serializers.CharField()
    order = serializers.IntegerField()


class ProgramSectionSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.CharField()
    question_count = serializers.IntegerField()
    max_score = serializers.FloatField()
    time_limit_minutes = serializers.IntegerField()
    order = serializers.IntegerField()


class ScoreBandSerializer(serializers.Serializer):
    label = serializers.CharField()
    min_score = serializers.FloatField()
    max_score = serializers.FloatField()
    description = serializers.CharField()


class CertificationProgramSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.CharField()
    kind = serializers.CharField()
    subject_code = serializers.CharField()
    max_score = serializers.FloatField()
    passing_score = serializers.FloatField(allow_null=True)
    duration_minutes = serializers.IntegerField()
    sections = ProgramSectionSerializer(many=True)
    bands = ScoreBandSerializer(many=True)


class CefrLevelSerializer(serializers.Serializer):
    level = serializers.CharField()
    min_score = serializers.FloatField()
    description = serializers.CharField()


class InternationalExamSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.CharField()
    kind = serializers.CharField()
    max_score = serializers.FloatField()
    duration_minutes = serializers.IntegerField()
    sections = ProgramSectionSerializer(many=True)
    cefr_levels = CefrLevelSerializer(many=True)


class QuestionRefSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    text = serializers.CharField()
    option_a = serializers.CharField()
    option_b = serializers.CharField()
    option_c = serializers.CharField()
    option_d = serializers.CharField()
    correct_option = serializers.CharField()
    difficulty = serializers.CharField()
    topic_id = serializers.IntegerField(allow_null=True)
    subject = serializers.CharField()
    grade = serializers.IntegerField()
    explanation = serializers.CharField()
    direction_code = serializers.CharField()


class AssessmentOverviewSerializer(serializers.Serializer):
    subject = serializers.CharField()
    grade = serializers.IntegerField()
    question_count = serializers.IntegerField()
    topic_count = serializers.IntegerField()
    book_count = serializers.IntegerField()
    direction_counts = serializers.DictField(child=serializers.IntegerField())


class CertificationResultSerializer(serializers.Serializer):
    total = serializers.FloatField()
    max_score = serializers.FloatField()
    band_label = serializers.CharField(allow_null=True)
    passed = serializers.BooleanField()


class InternationalResultSerializer(serializers.Serializer):
    total = serializers.FloatField()
    cefr_level = serializers.CharField(allow_null=True)
