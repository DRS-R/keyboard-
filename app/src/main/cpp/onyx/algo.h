// Onyx Algorithms — الخوارزميات 1-18 و21-30 و45 (ترحيل حرفي من src/lib/engine/algorithms.ts)
#pragma once
#include <string>
#include <vector>
#include <utility>

namespace onyx {

/* 1) Levenshtein — مسافة التحرير الكلاسيكية */
int levenshtein(const std::u32string& a, const std::u32string& b);

/* 2) Damerau-Levenshtein — يكشف نقل حرفين متجاورين */
int damerau(const std::u32string& a, const std::u32string& b);

/* 3) Jaro-Winkler — تشابه يعزز البادئات المتطابقة */
double jaro_winkler(const std::u32string& a, const std::u32string& b);

/* 4) Soundex عربي — بصمة صوتية */
std::string soundex_arabic(const std::u32string& w);

/* 5) Soundex لاتيني (الدخل مفترض صغير latin) */
std::string soundex_latin(const std::u32string& w);

/* 6-10) التطبيع العربي: نزع التشكيل + توحيد الهمزات والتاء والمقصورة */
std::u32string strip_tashkeel(const std::u32string& w);
std::u32string unify_hamza(const std::u32string& w);
std::u32string unify_taa_marbuta(const std::u32string& w);
std::u32string unify_alef_maqsura(const std::u32string& w);
std::u32string normalize_arabic(const std::u32string& w);

/* 11) التجذيع العربي الخفيف — نزع السوابق الشائعة */
std::u32string stem_arabic(const std::u32string& w);

/* 12) التطبيع اللاتيني — تصغير + طي العلامات + ı→i (بديل NFD) */
std::u32string normalize_latin(const std::u32string& w);

/* 13) تطبيع سيريلي — صغير + ё→е */
std::u32string normalize_cyrillic(const std::u32string& w);

/* 14/15) خرائط الجيران تُقرأ من هنا */
extern const char* const QWERTY_NEIGHBORS[][2];   // {حرف، جيران}
extern const char* const ARABIC_NEIGHBORS[][2];

/* 16) مرشحو جار واحد (Fat-Finger) */
std::vector<std::u32string> neighbor_candidates(const std::u32string& w, bool arabic);

/* 17) كشف النقل المزدوج — أول تبديل متجاورين */
bool transpose_fix(const std::u32string& w, std::u32string& out);

/* 18) انهيار الحرف المكرر (helllo → hello) */
std::vector<std::u32string> collapse_duplicates(const std::u32string& w);

/* 21) اضمحلال أسّي — من عمر بالملي ثانية */
double recency_weight(long long age_ms, long long half_life_ms = 3600000LL);

/* 22) تردد Zipf معياري */
double zipf_score(int freq, int max_freq = 100);

/* 23) دمج الإشارات Score Fusion */
double score_fusion(double freq, double context, double personal,
                    double recency = -1.0, int len = 0);

/* 45) عتبة تصحيح متكيفة مع السرعة + انحراف إعدادات المستخدم */
int adaptive_max_dist(int wpm, int word_len, int offset = 0);

/* 29) عربيزي → عربي (الدخل لاتيني صغير) */
std::u32string arabizi_to_arabic(const std::u32string& w);

/* 30) عربي → لاتيني (Romanization) */
std::string arabic_to_latin(const std::u32string& w);

/* 26) كشف نظام الكتابة: 0=عربي 1=سيريلي 2=لاتيني */
int detect_script_class(const std::u32string& w);

/* 28) نقاط التبديل اللغوي داخل جملة */
int code_switch_points(const std::string& text_utf8);

} // namespace onyx
