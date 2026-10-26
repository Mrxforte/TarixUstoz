import os
import re
from django.db.models import Q
from django.conf import settings
from api.models import (
    HistoricalChunk, Topic, HistoricalEntity,
    HistoricalEntityAlias, Question, QuestionEvidence
)
from api.ingestion_engine import UzbekTextNormalizer

try:
    import google.generativeai as genai
    HAS_GEMINI = True
except ImportError:
    HAS_GEMINI = False

class HistoricalRAGEngine:
    def __init__(self, gemini_api_key=None):
        self.api_key = gemini_api_key or os.environ.get("GEMINI_API_KEY", getattr(settings, "GEMINI_API_KEY", ""))
        self.model_name = os.environ.get("GEMINI_MODEL", "gemini-1.5-flash")

        if HAS_GEMINI and self.api_key and self.api_key != "YOUR_API_KEY":
            genai.configure(api_key=self.api_key)
            self.model = genai.GenerativeModel(self.model_name)
        else:
            self.model = None

    def retrieve_evidence(self, query: str, grade: int = 0, subject: str = "", limit: int = 5):
        """
        Retrieves top relevant HistoricalChunks using Uzbek query expansion and metadata filters.
        """
        norm_query = UzbekTextNormalizer.normalize_apostrophes(query)
        cyrillic_query = UzbekTextNormalizer.latin_to_cyrillic(norm_query)

        # Extract search terms
        words = [w for w in re.split(r'\W+', norm_query) if len(w) > 2]
        cyr_words = [w for w in re.split(r'\W+', cyrillic_query) if len(w) > 2]
        all_terms = list(set(words + cyr_words))

        qs = HistoricalChunk.objects.all().select_related('document', 'topic', 'page')

        if grade and grade > 0:
            qs = qs.filter(document__grade=grade)
        if subject:
            qs = qs.filter(document__subject__icontains=subject)

        if not all_terms:
            return list(qs[:limit])

        # Build term query
        query_filter = Q()
        for term in all_terms:
            query_filter |= Q(text__icontains=term) | Q(normalized_text__icontains=term) | Q(topic__title__icontains=term)

        matched_chunks = list(qs.filter(query_filter)[:limit * 2])

        # Simple score based on term matches
        def score_chunk(chunk):
            score = 0
            text_lower = chunk.text.lower()
            for t in all_terms:
                if t.lower() in text_lower:
                    score += 1
            return score

        matched_chunks.sort(key=score_chunk, reverse=True)
        return matched_chunks[:limit]

    def answer_question(self, user_question: str, grade: int = 0, subject: str = ""):
        """
        Grounds user's question in textbook evidence using RAG.
        Returns source-backed answer with citations.
        """
        evidence_chunks = self.retrieve_evidence(user_question, grade=grade, subject=subject, limit=5)

        if not evidence_chunks:
            return {
                "answer": "Kechirasiz, berilgan darsliklar orasidan ushbu savol bo'yicha yetarli manba topilmadi.",
                "evidence": [],
                "source_citations": []
            }

        context_text = "\n\n".join([
            f"[Manba: {c.source_provenance}]\n{c.text}" for c in evidence_chunks
        ])

        citations = list(set([c.source_provenance for c in evidence_chunks]))

        if not self.model:
            # Fallback answer without Gemini API
            top_chunk = evidence_chunks[0]
            return {
                "answer": f"Darslik manbasiga ko'ra ({top_chunk.source_provenance}):\n\n{top_chunk.text[:400]}...",
                "evidence": [c.text for c in evidence_chunks[:3]],
                "source_citations": citations
            }

        prompt = f"""
            Sen O'zbekiston va Jahon tarixi bo'yicha darsliklarga asoslangan professional AI o'qituvchisisan.
            Faqat va faqat quyidagi manbalarga tayanib javob ber. Hech qanday tasdiqlanmagan sana yoki fakt qo'shma.

            Savol: {user_question}

            Tarixiy darslik manbalari:
            {context_text}

            Talablar:
            1. Javobing aniq, qisqa va darslikka asoslangan bo'lsin.
            2. Qaysi darslik va betdan olinganini qavs ichida ko'rsat.
            3. Agar manbalarda aniq javob bo'lmasa, uni ochiq ayting.
        """.trimIndent()

        try:
            response = self.model.generate_content(prompt)
            answer_text = response.text.strip() if response and response.text else "Javob shakllantirilmadi."
            return {
                "answer": answer_text,
                "evidence": [c.text for c in evidence_chunks[:3]],
                "source_citations": citations
            }
        except Exception as e:
            top_chunk = evidence_chunks[0]
            return {
                "answer": f"Darslik manbasiga ko'ra ({top_chunk.source_provenance}):\n\n{top_chunk.text[:400]}...",
                "evidence": [c.text for c in evidence_chunks[:3]],
                "source_citations": citations,
                "error": str(e)
            }
