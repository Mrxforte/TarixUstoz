from django.contrib import admin

from accounts.models import UserProfile, UserScore


@admin.register(UserProfile)
class UserProfileAdmin(admin.ModelAdmin):
    list_display = ("user", "display_name", "language", "auth_provider", "created_at")
    list_filter = ("auth_provider", "language")
    search_fields = ("user__email", "user__username", "display_name")


@admin.register(UserScore)
class UserScoreAdmin(admin.ModelAdmin):
    list_display = ("user", "points", "weekly_points", "streak", "updated_at")
    search_fields = ("user__email", "user__username")
    ordering = ("-points",)
