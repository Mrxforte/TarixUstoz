"""DRF serializers for domain entities (no ORM models are exposed directly)."""
from __future__ import annotations

from rest_framework import serializers


class LanguageSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.CharField()
    native_name = serializers.CharField()
    is_default = serializers.BooleanField()


class SubjectSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.SerializerMethodField()
    name_en = serializers.CharField()
    category = serializers.CharField()
    grades = serializers.ListField(child=serializers.IntegerField())
    icon = serializers.CharField()
    order = serializers.IntegerField()

    def get_name(self, obj) -> str:
        return obj.name_in(self.context.get("language"))


class CertificateSectionSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.CharField()
    question_count = serializers.IntegerField()
    max_score = serializers.FloatField()
    time_limit_minutes = serializers.IntegerField()
    order = serializers.IntegerField()


class CertificateBandSerializer(serializers.Serializer):
    label = serializers.CharField()
    min_score = serializers.FloatField()
    max_score = serializers.FloatField()
    description = serializers.CharField()


class CefrLevelSerializer(serializers.Serializer):
    level = serializers.CharField()
    min_score = serializers.FloatField()
    description = serializers.CharField()


class CertificateProgramSerializer(serializers.Serializer):
    code = serializers.CharField()
    name = serializers.SerializerMethodField()
    kind = serializers.CharField()
    family = serializers.CharField()
    subject_code = serializers.CharField()
    max_score = serializers.FloatField()
    passing_score = serializers.FloatField(allow_null=True)
    duration_minutes = serializers.IntegerField()
    sections = CertificateSectionSerializer(many=True)
    bands = CertificateBandSerializer(many=True)
    cefr_levels = CefrLevelSerializer(many=True)

    def get_name(self, obj) -> str:
        return obj.name_in(self.context.get("language"))


class CertificateResultSerializer(serializers.Serializer):
    total = serializers.FloatField()
    max_score = serializers.FloatField()
    band_label = serializers.CharField(allow_null=True)
    cefr_level = serializers.CharField(allow_null=True)
    passed = serializers.BooleanField()


class CertificateKindSerializer(serializers.Serializer):
    code = serializers.CharField()
    title = serializers.CharField()


class CertificateFamilySerializer(serializers.Serializer):
    code = serializers.CharField()
    title = serializers.CharField()
    kinds = CertificateKindSerializer(many=True)


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


class AccountSerializer(serializers.Serializer):
    id = serializers.IntegerField()
    email = serializers.CharField()
    display_name = serializers.CharField()
    language = serializers.CharField()
    auth_provider = serializers.CharField()
    points = serializers.IntegerField()
    streak = serializers.IntegerField()


class LeaderboardEntrySerializer(serializers.Serializer):
    rank = serializers.IntegerField()
    user_id = serializers.IntegerField()
    display_name = serializers.CharField()
    points = serializers.IntegerField()
    streak = serializers.IntegerField()
