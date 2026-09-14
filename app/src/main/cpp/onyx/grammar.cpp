// Onyx Grammar — خوارزمية 52: المصحح القواعدي للجمل (M3 Deep)
// أنبوب حتمي: مسافات وترقيم ← إملاء شائع ← قواعد تصريف ← صياغة ← أحرف
// كل الجداول آمنة (لا تصحّح صيغة صحيحة إلى خاطئة) ومحصّنة ضد الحلقات
// بمسح أمامي لا يعيد فحص البدائل المُدرجة.
#include "onyx/grammar.h"
#include "onyx/utf8.h"
#include "onyx/algo.h"

#include <functional>
#include <unordered_map>

namespace onyx {

namespace {

/* ================= أدوات ================= */

std::u32string trim32_local(const std::u32string& s) {
    size_t a = 0, b = s.size();
    while (a < b && (s[a] == U' ' || s[a] == U'\t' || s[a] == U'\n' || s[a] == U'\r')) a++;
    while (b > a && (s[b - 1] == U' ' || s[b - 1] == U'\t' || s[b - 1] == U'\n' || s[b - 1] == U'\r')) b--;
    return s.substr(a, b - a);
}

inline bool is_ws32(const char32_t c) {
    return c == U' ' || c == U'\t' || c == U'\n' || c == U'\r';
}

inline bool is_word_cp(const uint32_t cp) {
    // علامات الترقيم العربية تقع داخل نطاق 06xx — تستثنى صراحة
    if (cp == 0x060C || cp == 0x061B || cp == 0x061F || cp == 0x066B || cp == 0x066C) return false;
    const bool latin = (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')
        || (cp >= '0' && cp <= '9');
    return latin || is_arabic_cp(cp) || is_cyrillic_cp(cp);
}

struct Tok {
    std::u32string w;   // محتوى الكلمة
    size_t start, end;  // بنقاط الكود داخل النص
};

std::vector<Tok> tokenize(const std::u32string& t) {
    std::vector<Tok> out;
    size_t i = 0;
    while (i < t.size()) {
        while (i < t.size() && !is_word_cp(static_cast<uint32_t>(t[i]))) i++;
        if (i >= t.size()) break;
        const size_t s = i;
        while (i < t.size() && is_word_cp(static_cast<uint32_t>(t[i]))) i++;
        out.push_back({t.substr(s, i - s), s, i});
    }
    return out;
}

// مفتاح المقارنة: نزع التشكيل + تصغير — يوحّد هاذا/هَاذا و Dont/dont
std::u32string key_of(const std::u32string& w) {
    return lower32(strip_tashkeel(w));
}

void push_issue(std::vector<GrammarIssue>* issues, const std::string& cat,
                const std::u32string& before, const std::u32string& after,
                const char* hint) {
    if (issues == nullptr || issues->size() >= 16) return;
    GrammarIssue gi;
    gi.cat = cat;
    gi.before = from32(before);
    gi.after = from32(after);
    gi.hint = hint;
    issues->push_back(gi);
}

/* ================= 1) الترقيم والمسافات (محايد للغتين) ================= */

bool is_punct32(const char32_t c) {
    return c == U',' || c == U'.' || c == U'!' || c == U'?'
        || c == U'،' || c == U'؛' || c == U'؟' || c == U':';
}

// مسافة قبل علامة ترقيم → تُحذف دائماً (آمن)
void fix_space_before_punct(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    std::u32string out;
    out.reserve(cur.size());
    bool changed = false;
    for (size_t i = 0; i < cur.size(); i++) {
        const char32_t c = cur[i];
        if (is_ws32(c) && i + 1 < cur.size() && is_punct32(cur[i + 1])) {
            changed = true;  // نُسقط المسافة
            continue;
        }
        out += c;
    }
    if (changed) {
        push_issue(issues, "ترقيم", U"كلمة × ،", U"كلمة،", "لا مسافة قبل علامة الترقيم");
        cur = out;
    }
}

// علامة ترقيم لاصقة بحرفين (hi,there) → نضيف مسافة بعدها
// حواجز: لا نلمس '.' (نطاقات وملفات)، ولا داخل روابط :// أو www.
void fix_space_after_punct(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    std::u32string out;
    out.reserve(cur.size() + 8);
    bool changed = false;
    for (size_t i = 0; i < cur.size(); i++) {
        const char32_t c = cur[i];
        out += c;
        if (!is_punct32(c) || c == U'.') continue;
        if (i + 1 >= cur.size()) continue;
        const char32_t nxt = cur[i + 1];
        if (!is_word_cp(static_cast<uint32_t>(nxt)) || nxt == U'.') continue;
        // الطرفان حرفان؟ (يستبعد 1,000 و3.5 آلياً لأن الطرف رقم)
        if (i == 0 || !is_word_cp(static_cast<uint32_t>(cur[i - 1]))) continue;
        // حارس الأرقام الصريح: فاصلة/شرطة بين أرقام لا تُمس
        const bool dig_b = cur[i - 1] >= U'0' && cur[i - 1] <= U'9';
        const bool dig_a = nxt >= U'0' && nxt <= U'9';
        if (dig_b || dig_a) continue;
        // حارس الروابط: :// أو www. خلال آخر 60 نقطة
        const size_t w0 = (i > 60) ? i - 60 : 0;
        bool in_url = false;
        for (size_t k = w0; k + 2 < i; k++) {
            if (cur[k] == U':' && cur[k + 1] == U'/' && cur[k + 2] == U'/') { in_url = true; break; }
        }
        for (size_t k = w0; k + 3 < i && !in_url; k++) {
            if (cur[k] == U'w' && cur[k + 1] == U'w' && cur[k + 2] == U'w' && cur[k + 3] == U'.') { in_url = true; break; }
        }
        if (in_url) continue;
        out += U' ';
        changed = true;
    }
    if (changed) {
        push_issue(issues, "ترقيم", U"مرحبا،كيف", U"مرحبا، كيف", "مسافة بعد علامة الترقيم");
        cur = out;
    }
}

void fix_double_space(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    if (cur.find(U"  ") == std::u32string::npos) return;
    std::u32string out;
    out.reserve(cur.size());
    bool prev_ws = false;
    for (const char32_t c : cur) {
        if (is_ws32(c)) {
            if (prev_ws) continue;  // ثانية متتالية تُسقط
            prev_ws = true;
        } else prev_ws = false;
        out += c;
    }
    push_issue(issues, "ترقيم", U"مسافتان", U"مسافة", "مسافة واحدة تكفي");
    cur = out;
}

/* ================= 2) جداول الإملاء الشائعة ================= */

// عربية — مفاتيحها بعد نزع التشكيل (آمنة: لا صيغة صحيحة تُفسد)
const char* const AR_WORD_FIXES[][2] = {
    {"هاذا", "هذا"}, {"هاذه", "هذه"}, {"هازا", "هذا"}, {"هازي", "هذه"},
    {"لاكن", "لكن"}, {"لآكن", "لكن"},
    {"فى", "في"}, {"الى", "إلى"}, {"الذى", "الذي"}, {"التى", "التي"},
    {"لكى", "لكي"}, {"حتي", "حتى"},
    {"انا", "أنا"}, {"او", "أو"},
    {"انه", "إنه"}, {"انها", "إنها"},
    {"شئ", "شيء"}, {"مسؤل", "مسؤول"}, {"مسوول", "مسؤول"},
    {"انشاءالله", "إن شاء الله"}, {"الحمدالله", "الحمد لله"},
    {"اكيد", "أكيد"}, {"احنا", "إحنا"},
};

// إنجليزية — مفاتيحها مصغّرة (انكماشات ناقصة الفاصلة العليا)
const char* const EN_WORD_FIXES[][2] = {
    {"dont", "don't"}, {"cant", "can't"}, {"wont", "won't"},
    {"doesnt", "doesn't"}, {"didnt", "didn't"}, {"isnt", "isn't"},
    {"arent", "aren't"}, {"wasnt", "wasn't"}, {"werent", "weren't"},
    {"couldnt", "couldn't"}, {"shouldnt", "shouldn't"}, {"wouldnt", "wouldn't"},
    {"hasnt", "hasn't"}, {"havent", "haven't"}, {"hadnt", "hadn't"},
    {"aint", "ain't"},
    {"im", "I'm"}, {"ive", "I've"},
    {"youre", "you're"}, {"youve", "you've"}, {"youll", "you'll"},
    {"theyre", "they're"}, {"theyve", "they've"}, {"theyll", "they'll"},
    {"weve", "we've"},
    {"lets", "let's"}, {"thats", "that's"}, {"whats", "what's"},
    {"heres", "here's"}, {"theres", "there's"},
    {"couldve", "could've"}, {"shouldve", "should've"}, {"wouldve", "would've"},
};

typedef std::unordered_map<std::u32string, std::u32string> FixMap;

FixMap build_map(const char* const (*pairs)[2], size_t n) {
    FixMap m;
    m.reserve(n * 2);
    for (size_t i = 0; i < n; i++) m.emplace(to32(pairs[i][0]), to32(pairs[i][1]));
    return m;
}

// مسح أمامي: يبدّل توكنات جدول معروف — لا يعيد فحص البديل (مانع الحلقات)
void apply_word_table(std::u32string& cur, const FixMap& table,
                      const std::string& cat, const char* hint,
                      std::vector<GrammarIssue>* issues) {
    std::vector<Tok> toks = tokenize(cur);
    size_t shift = 0;
    for (const Tok& t : toks) {
        if (issues != nullptr && issues->size() >= 16) return;
        const auto it = table.find(key_of(t.w));
        if (it == table.end()) continue;
        const size_t s = t.start + shift;
        const size_t e = t.end + shift;
        cur = cur.substr(0, s) + it->second + cur.substr(e);
        shift += it->second.size() - (e - s);
        push_issue(issues, cat, t.w, it->second, hint);
    }
}

/* ================= 3) عبارات من كلمتين ================= */

void fix_phrases(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    // انشاء الله → إن شاء الله (الشائعة جداً)
    for (int pass = 0; pass < 4; pass++) {
        bool changed = false;
        const std::vector<Tok> toks = tokenize(cur);
        for (size_t i = 0; i + 1 < toks.size(); i++) {
            if (key_of(toks[i].w) == to32("انشاء") && key_of(toks[i + 1].w) == to32("الله")) {
                const size_t s = toks[i].start;
                const size_t e = toks[i + 1].end;
                const std::u32string fix = to32("إن شاء الله");
                cur = cur.substr(0, s) + fix + cur.substr(e);
                push_issue(issues, "إملاء", U"انشاء الله", fix, "إن شاء الله بهمزتين ومسافة");
                changed = true;
                break;
            }
        }
        if (!changed) return;
    }
}

/* ================= 4) قواعد (تصريف + أداة التعريف) ================= */

// مضاعفة الكلمة: the the → the ، في في → في
void fix_doubled_words(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    for (int pass = 0; pass < 20; pass++) {
        bool changed = false;
        const std::vector<Tok> toks = tokenize(cur);
        for (size_t i = 0; i + 1 < toks.size(); i++) {
            if (toks[i].w.empty() || (toks[i].w[0] >= U'0' && toks[i].w[0] <= U'9')) continue;
            if (key_of(toks[i].w) == key_of(toks[i + 1].w)) {
                const std::u32string before = toks[i].w + U' ' + toks[i + 1].w;
                cur = cur.substr(0, toks[i].end) + cur.substr(toks[i + 1].end);
                push_issue(issues, "صياغة", before, toks[i].w, "كلمة مكررة حذف الثانية");
                changed = true;
                break;
            }
        }
        if (!changed) return;
    }
}

// a/an حسب الحرف الأول من الكلمة التالية
void fix_article(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    static const char* const NEEDS_AN[] = {"hour", "honest", "honor", "honour", "heir"};
    static const char* const NEEDS_A[] = {"university", "universal", "user", "usual",
        "utility", "unit", "union", "unicorn", "european", "europe", "one"};
    auto in_list = [](const char* const* list, size_t n, const std::u32string& k) {
        for (size_t i = 0; i < n; i++) if (k == to32(list[i])) return true;
        return false;
    };
    for (int pass = 0; pass < 8; pass++) {
        bool changed = false;
        const std::vector<Tok> toks = tokenize(cur);
        for (size_t i = 0; i + 1 < toks.size(); i++) {
            const std::u32string k = key_of(toks[i].w);
            if (k != U"a" && k != U"an") continue;
            const std::u32string nk = key_of(toks[i + 1].w);
            if (nk.empty()) continue;
            bool is_an;
            if (in_list(NEEDS_AN, 5, nk)) is_an = true;
            else if (in_list(NEEDS_A, 11, nk)) is_an = false;
            else {
                const char32_t c = lower32(nk.substr(0, 1))[0];
                const bool vowel = c == U'a' || c == U'e' || c == U'i' || c == U'o' || c == U'u';
                is_an = vowel;
            }
            const std::u32string want = is_an ? U"an" : U"a";
            if (k == want) continue;
            const size_t s = toks[i].start;
            const size_t e = toks[i].end;
            const std::u32string before = toks[i].w + U' ' + toks[i + 1].w;
            const std::u32string after = want + U' ' + toks[i + 1].w;
            cur = cur.substr(0, s) + want + cur.substr(e);
            push_issue(issues, "قواعد", before, after,
                       is_an ? "an قبل حرف متحرك" : "a قبل حرف ساكن");
            changed = true;
            break;
        }
        if (!changed) return;
    }
}

// تصريف الفعل مع الضمير: he dont → he doesn't …
void fix_subject_verb(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    struct SV { const char* a; const char* b; const char* fix; const char* hint; };
    static const SV RULES[] = {
        {"he", "dont", "doesn't", "he/she/it مع doesn't"},
        {"she", "dont", "doesn't", nullptr}, {"it", "dont", "doesn't", nullptr},
        {"i", "doesnt", "don't", "I/you/we/they مع don't"},
        {"you", "doesnt", "don't", nullptr}, {"we", "doesnt", "don't", nullptr},
        {"they", "doesnt", "don't", nullptr},
        {"you", "was", "were", "you/we/they مع were"},
        {"we", "was", "were", nullptr}, {"they", "was", "were", nullptr},
        {"they", "is", "are", "they مع are"},
        {"i", "is", "am", "I مع am"},
        {"i", "has", "have", "I مع have"},
        {"he", "have", "has", "he/she/it مع has"},
        {"she", "have", "has", nullptr}, {"it", "have", "has", nullptr},
        {"he", "are", "is", "he/she/it مع is"},
        {"she", "are", "is", nullptr}, {"it", "are", "is", nullptr},
        {"you", "wasnt", "weren't", "you/we/they مع weren't"},
        {"we", "wasnt", "weren't", nullptr}, {"they", "wasnt", "weren't", nullptr},
    };
    for (int pass = 0; pass < 8; pass++) {
        bool changed = false;
        const std::vector<Tok> toks = tokenize(cur);
        for (size_t i = 0; i + 1 < toks.size(); i++) {
            const std::u32string ka = key_of(toks[i].w);
            const std::u32string kb = key_of(toks[i + 1].w);
            for (const SV& r : RULES) {
                if (ka != to32(r.a) || kb != to32(r.b)) continue;
                const std::u32string fix = to32(r.fix);
                const size_t s = toks[i + 1].start;
                const size_t e = toks[i + 1].end;
                const std::u32string before = toks[i].w + U' ' + toks[i + 1].w;
                const std::u32string after = toks[i].w + U' ' + fix;
                cur = cur.substr(0, s) + fix + cur.substr(e);
                push_issue(issues, "قواعد", before, after,
                           r.hint != nullptr ? r.hint : "مطابقة الفعل للضمير");
                changed = true;
                break;
            }
            if (changed) break;
        }
        if (!changed) return;
    }
}

/* ================= 5) الأحرف الكبيرة (لاتيني فقط بطبيعته) ================= */

void fix_caps(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    const std::vector<Tok> toks = tokenize(cur);
    bool at_start = true;   // نحن عند بداية جملة؟
    for (const Tok& t : toks) {
        // علامة نهاية جملة بعد هذه الكلمة؟
        bool ends_sentence = false;
        for (size_t k = t.end; k < cur.size(); k++) {
            const char32_t c = cur[k];
            if (is_ws32(c)) continue;
            if (c == U'.' || c == U'!' || c == U'?' || c == U'…') ends_sentence = true;
            break;
        }
        if (at_start && !t.w.empty()) {
            const char32_t raw0 = t.w[0];   // الفحص على الحرف الأصلي — الكبير لا يُمس
            if (raw0 >= U'a' && raw0 <= U'z') {
                const char32_t upper = static_cast<char32_t>(raw0 - U'a' + U'A');
                cur = cur.substr(0, t.start) + upper + cur.substr(t.start + 1);
                push_issue(issues, "أحرف", t.w, upper + t.w.substr(1), "حرف كبير في بداية الجملة");
            }
            at_start = false;  // حرف واحد لكل جملة
        }
        if (ends_sentence) at_start = true;
    }
}

/* ================= 6) علامة الاستفهام العربية ================= */

void fix_arabic_question(std::u32string& cur, std::vector<GrammarIssue>* issues) {
    static const char* const QWORDS[] = {"هل", "ماذا", "كيف", "لماذا", "متى", "اين", "أين"};
    const std::u32string trimmed = trim32_local(cur);
    if (trimmed.empty()) return;
    const char32_t last = trimmed[trimmed.size() - 1];
    // يجب أن ينتهي بكلمة حقيقية (وليس علامة ترقيم عربية/لاتينية أو رقم)
    const uint32_t lcp = static_cast<uint32_t>(last);
    const bool ar_punct = (lcp == 0x061F || lcp == 0x060C || lcp == 0x061B);
    const bool digit = lcp >= U'0' && lcp <= U'9';
    if (ar_punct || digit || last == U'.' || last == U'!' || last == U'?' || last == U'…' || !is_word_cp(lcp)) return;
    // آخر المحتوى الفعلي يجب أن يكون كلمة (وليس رقماً فقط)
    const std::vector<Tok> toks = tokenize(trimmed);
    if (toks.empty()) return;
    const std::u32string first = key_of(toks[0].w);
    bool q = false;
    for (const char* w : QWORDS) if (first == to32(w)) { q = true; break; }
    if (!q) return;
    // أدخل «؟» قبل المسافة الختامية
    size_t tail = cur.size();
    while (tail > 0 && is_ws32(cur[tail - 1])) tail--;
    cur = cur.substr(0, tail) + U'؟' + cur.substr(tail);
    push_issue(issues, "ترقيم", U"كيف حالك", U"كيف حالك؟", "سؤال بلا علامة استفهام");
}

/* ================= الكشف اللاتيني ================= */

bool has_latin32(const std::u32string& t) {
    for (const char32_t c : t) {
        const uint32_t cp = static_cast<uint32_t>(c);
        if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')) return true;
    }
    return false;
}

} // namespace

std::string grammar_fix_text(const std::string& text_utf8, Lang lang,
                             std::vector<GrammarIssue>* issues) {
    (void)lang;  // القواعد محايدة لغوياً بطبيعة مفاتيحها — تدعم الخلط داخل الجملة
    std::u32string cur = to32(text_utf8);
    if (cur.size() < 2) return text_utf8;
    if (issues != nullptr) issues->clear();

    /* الترقيم والمسافات أولاً حتى تُقرأ التوكنات نظيفة */
    fix_double_space(cur, issues);
    fix_space_before_punct(cur, issues);
    fix_space_after_punct(cur, issues);

    /* الإملاء الشائع — العربية أولاً ثم قواعد الضمير (قبل جدول الانكماشات
       لأن he dont يجب أن يصبح doesn't لا don't) ثم بقية الجداول */
    static const FixMap AR = build_map(AR_WORD_FIXES, sizeof(AR_WORD_FIXES) / sizeof(AR_WORD_FIXES[0]));
    static const FixMap EN = build_map(EN_WORD_FIXES, sizeof(EN_WORD_FIXES) / sizeof(EN_WORD_FIXES[0]));
    apply_word_table(cur, AR, "إملاء", "إملاء عربي شائع", issues);

    fix_phrases(cur, issues);
    fix_doubled_words(cur, issues);
    fix_subject_verb(cur, issues);
    fix_article(cur, issues);

    apply_word_table(cur, EN, "إملاء", "انكماش ناقص الفاصلة", issues);

    /* الضمير المتكلم المستقل: i → I */
    if (has_latin32(cur)) {
        static const FixMap I = [] {
            FixMap m; m.emplace(to32("i"), to32("I")); return m;
        }();
        apply_word_table(cur, I, "أحرف", "ضمير المتكلم I بحرف كبير", issues);
        fix_caps(cur, issues);
    }

    /* الاستفهام العربي */
    fix_arabic_question(cur, issues);

    if (cur == to32(text_utf8)) return text_utf8;  // بلا تغيير — الأصل حرفياً
    return from32(cur);
}

} // namespace onyx
