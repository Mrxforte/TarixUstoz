from django.urls import path, include
from rest_framework.routers import DefaultRouter
from api.views import (
    CategoryViewSet, PdfBookViewSet, TopicViewSet,
    HistoricalEntityViewSet, ExamCategoryViewSet, QuestionViewSet,
    RAGSearchViewSet, QuizGenerationViewSet, UserProgressViewSet
)

router = DefaultRouter()
router.register(r'categories', CategoryViewSet)
router.register(r'books', PdfBookViewSet, basename='books')
router.register(r'pdf-books', PdfBookViewSet, basename='pdf-books')
router.register(r'topics', TopicViewSet)
router.register(r'entities', HistoricalEntityViewSet)
router.register(r'exams', ExamCategoryViewSet)
router.register(r'questions', QuestionViewSet)

urlpatterns = [
    path('', include(router.urls)),
    path('search/', RAGSearchViewSet.as_view({'get': 'list'}), name='rag-search'),
    path('quizzes/generate/', QuizGenerationViewSet.as_view({'post': 'create'}), name='quiz-generate'),
    path('quizzes/<str:pk>/', QuizGenerationViewSet.as_view({'get': 'retrieve'}), name='quiz-detail'),
    path('quizzes/<str:pk>/answers/', QuizGenerationViewSet.as_view({'post': 'submit_answer'}), name='quiz-submit-answer'),
    path('quizzes/<str:pk>/finish/', QuizGenerationViewSet.as_view({'post': 'finish'}), name='quiz-finish'),
    path('users/me/progress/', UserProgressViewSet.as_view({'get': 'list'}), name='user-progress'),
]
