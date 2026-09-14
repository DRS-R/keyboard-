#include "engine.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <set>
#include <sstream>

namespace onyx {

namespace {

std::u32string trim32(const std::u32string& s) {
    size_t a = 0, b = s.size();
    auto is_ws = [](char32_t c) { return c == U' ' || c == U'\t' || c == U'\n' || c == U'\r'; };
    while (a < b && is_ws(s[a])) a++;
    while (b > a && is_ws(s[b - 1])) b--;
    return s.substr(a, b - a);
}

std::string trim(const std::string& s) {
    size_t a = 0, b = s.size();
    auto is_ws = [](char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r'; };
    while (a < b && is_ws(s[a])) a++;
    while (b > a && is_ws(s[b - 1])) b--;
    return s.substr(a, b - a);
}

std::vector<std::string> split_lines(const std::string& s) {
    std::vector<std::string> out;
    std::string cur;
    for (const char c : s) {
        if (c == '\n') { out.push_back(cur); cur.clear(); }
        else if (c != '\r') cur += c;
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}

std::vector<std::string> split_str(const std::string& s, char sep) {
    std::vector<std::string> out;
    std::string cur;
    for (const char c : s) {
        if (c == sep) { out.push_back(cur); cur.clear(); }
        else cur += c;
    }
    out.push_back(cur);
    return out;
}

bool is_digit_c(char c) { return c >= '0' && c <= '9'; }
bool is_word_c(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || is_digit_c(c)
        || c == '_' || c == '.' || c == '+' || c == '-' || c == '%';
}

// تقسيم سلسلة 32-بت على مسافات بيضاء (لتحليل المشاعر 34)
std::vector<std::u32string> split_ws32(const std::u32string& s) {
    std::vector<std::u32string> out;
    std::u32string cur;
    for (const char32_t c : s) {
        if (c == U' ' || c == U'\t' || c == U'\n' || c == U'\r') {
            if (!cur.empty()) { out.push_back(cur); cur.clear(); }
        } else cur += c;
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}

// هل الحرف ينتمي لكلمة (لاستخراج الكلمة الأخيرة في 35)
bool is_letter_cp(uint32_t cp) {
    return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9')
        || (cp >= 0x00C0 && cp <= 0x024F)   // لاتيني موسع (é ñ ü ğ ş ı…)
        || (cp >= 0x0600 && cp <= 0x06FF)   // عربي/فارسي/أردو
        || (cp >= 0x0400 && cp <= 0x04FF);  // سيريلي
}

// معجم المشاعر الحرفي من algorithms.ts 34 (عربي/إنجليزي)
const char* const POSITIVE_LEX[] = {
    "حب", "رائع", "جميل", "شكرا", "نجاح", "سعيد", "فرح", "ممتاز", "أفضل", "عظيم",
    "هلا", "مبروك", "love", "great", "happy", "awesome", "thanks", "perfect",
    "amazing", "win", "congrats" };
const char* const NEGATIVE_LEX[] = {
    "أكره", "حزين", "غضب", "فشل", "سيء", "مشكلة", "تعبان", "زعلان", "صعب",
    "hate", "sad", "angry", "fail", "bad", "awful", "terrible", "problem", "tired" };

} // namespace

/* ==================== اللغات ==================== */
Lang lang_from(const std::string& code) {
    if (code == "ar") return Lang::Ar;
    if (code == "fr") return Lang::Fr;
    if (code == "es") return Lang::Es;
    if (code == "tr") return Lang::Tr;
    if (code == "de") return Lang::De;
    if (code == "ur") return Lang::Ur;
    if (code == "fa") return Lang::Fa;
    if (code == "ru") return Lang::Ru;
    return Lang::En;
}

const char* lang_code(Lang l) {
    switch (l) {
        case Lang::Ar: return "ar";
        case Lang::Fr: return "fr";
        case Lang::Es: return "es";
        case Lang::Tr: return "tr";
        case Lang::De: return "de";
        case Lang::Ur: return "ur";
        case Lang::Fa: return "fa";
        case Lang::Ru: return "ru";
        default: return "en";
    }
}

Engine& Engine::get() {
    static Engine inst;
    return inst;
}

void Engine::set_config(int correction_offset) { correction_offset_ = correction_offset; }

Engine::LangPack& Engine::pack(Lang l) { return packs_[static_cast<int>(l)]; }

std::u32string Engine::norm_for(Lang lang, const std::u32string& w) {
    if (lang == Lang::Ar || lang == Lang::Ur || lang == Lang::Fa) return normalize_arabic(w);
    if (lang == Lang::Ru) return normalize_cyrillic(w);
    return normalize_latin(w);
}

std::string Engine::soundex_for(Lang lang, const std::u32string& w) {
    return (lang == Lang::Ar || lang == Lang::Ur || lang == Lang::Fa)
        ? soundex_arabic(w) : soundex_latin(w);
}

/* ==================== التحميل ==================== */
void Engine::load_dict(Lang lang, const std::string& tsv) {
    LangPack& P = pack(lang);
    P.entries.clear();
    P.norm_idx.clear();
    P.trie.clear();

    /* M2: القواميس الموسعة تصل بترددات خام (ملايين) — معادلات النقاط صُممت على
       نطاق الويب 1..100 (zipf_score(f, maxFreq=100)). نعاير لوغاريتمياً نسبة
       لأكبر تردد في الحزمة حتى تبقى سلم النقاط كما صُمم مهما كان مصدر البيانات. */
    std::vector<std::pair<std::u32string, int>> raw;
    double max_f = 1.0;
    for (const std::string& line : split_lines(tsv)) {
        const auto parts = split_str(line, '\t');
        if (parts.size() < 2) continue;
        const std::u32string w = to32(trim(parts[0]));
        const int freq = std::atoi(trim(parts[1]).c_str());
        if (w.empty() || freq <= 0) continue;
        raw.push_back({w, freq});
        if (freq > max_f) max_f = static_cast<double>(freq);
    }
    const double denom = std::log(1.0 + max_f);
    for (const auto& [w, f] : raw) {
        int scaled = static_cast<int>(std::lround(100.0 * std::log(1.0 + static_cast<double>(f)) / denom));
        if (scaled < 1) scaled = 1;
        if (scaled > 100) scaled = 100;
        const int idx = static_cast<int>(P.entries.size());
        P.entries.push_back({w, scaled});
        P.norm_idx[norm_for(lang, w)].push_back(idx);
        P.trie.insert(norm_for(lang, w), idx);
    }
}

void Engine::load_bigrams(Lang lang, const std::string& tsv) {
    LangPack& P = pack(lang);
    P.bigrams.clear();
    P.trigrams.clear();
    for (const std::string& line : split_lines(tsv)) {
        const auto parts = split_str(line, '\t');
        if (parts.size() < 2 || parts[0].empty()) continue;
        const std::u32string key = lower32(to32(trim(parts[0])));
        std::vector<std::u32string> nexts;
        for (const std::string& w : split_str(parts[1], ',')) {
            const std::u32string t = to32(trim(w));
            if (!t.empty()) nexts.push_back(t);
        }
        if (!nexts.empty()) P.bigrams[key] = nexts;
    }
    // خوارزمية 19: اشتقاق Trigram من البيغرامات المركبة
    for (const auto& [a, nexts] : P.bigrams) {
        for (const std::u32string& b : nexts) {
            const auto it = P.bigrams.find(lower32(b));
            if (it != P.bigrams.end()) {
                std::vector<std::u32string> inner = it->second;
                if (inner.size() > 3) inner.resize(3);
                P.trigrams[a + U" " + lower32(b)] = inner;
            }
        }
    }
}

void Engine::load_mistakes(Lang lang, const std::string& tsv) {
    LangPack& P = pack(lang);
    P.mistakes.clear();
    for (const std::string& line : split_lines(tsv)) {
        const auto parts = split_str(line, '\t');
        if (parts.size() < 2) continue;
        const std::u32string k = to32(trim(parts[0]));
        const std::u32string v = to32(trim(parts[1]));
        if (k.empty() || v.empty()) continue;
        P.mistakes[k] = v;                      // الشكل الخام
        P.mistakes[lower32(k)] = v;             // شكل صغير
        const std::u32string nk = norm_for(lang, k);
        P.mistakes[nk] = v;                     // شكل مطبع
        P.mistakes[lower32(nk)] = v;
    }
}

void Engine::load_starters(Lang lang, const std::string& txt) {
    LangPack& P = pack(lang);
    P.starters.clear();
    for (const std::string& line : split_lines(txt)) {
        const std::u32string w = to32(trim(line));
        if (!w.empty()) P.starters.push_back(w);
    }
}

/* ==================== التعلم التكيفي (24/25) ==================== */
void Engine::set_learned_tsv(const std::string& tsv) {
    learned_.clear();
    for (const std::string& line : split_lines(tsv)) {
        const auto parts = split_str(line, '\t');
        if (parts.size() < 2) continue;
        const std::u32string w = lower32(to32(trim(parts[0])));
        const int c = std::atoi(trim(parts[1]).c_str());
        if (w.size() >= 2 && c > 0) learned_[w] = std::min(c, 99);
    }
}

std::string Engine::learned_tsv() const {
    std::vector<std::pair<std::u32string, int>> v(learned_.begin(), learned_.end());
    std::sort(v.begin(), v.end(), [](const auto& a, const auto& b) {
        if (a.second != b.second) return a.second > b.second;
        return a.first < b.first;
    });
    std::string out;
    for (const auto& [w, c] : v) {
        out += from32(w);
        out += '\t';
        out += std::to_string(c);
        out += '\n';
    }
    return out;
}

int Engine::learn_word(const std::string& word_utf8) {
    const std::u32string w = lower32(trim32(to32(word_utf8)));
    if (w.size() < 2) return static_cast<int>(learned_.size());
    const auto it = learned_.find(w);
    const int cur = (it == learned_.end()) ? 0 : it->second;
    learned_[w] = std::min(cur + 1, 99);
    // خوارزمية 25: ذاكرة MRU بحد 200
    if (learned_.size() > 200) {
        std::vector<std::pair<std::u32string, int>> v(learned_.begin(), learned_.end());
        std::sort(v.begin(), v.end(), [](const auto& a, const auto& b) {
            if (a.second != b.second) return a.second > b.second;
            return a.first < b.first;
        });
        learned_.clear();
        for (size_t i = 0; i < 200 && i < v.size(); i++) learned_[v[i].first] = v[i].second;
    }
    return static_cast<int>(learned_.size());
}

int Engine::remove_learned(const std::string& word_utf8) {
    learned_.erase(lower32(trim32(to32(word_utf8))));
    return static_cast<int>(learned_.size());
}

void Engine::reset_learned() { learned_.clear(); }

/* ==================== Backoff (20) ==================== */
Engine::BackoffResult Engine::backoff_next(const std::vector<std::u32string>& history, LangPack& P) {
    std::vector<std::u32string> low;
    for (const auto& w : history) if (!w.empty()) low.push_back(lower32(w));
    if (low.size() >= 2) {
        const auto it = P.trigrams.find(low[low.size() - 2] + U" " + low.back());
        if (it != P.trigrams.end()) return {it->second, 0};
    }
    if (!low.empty()) {
        const auto it = P.bigrams.find(low.back());
        if (it != P.bigrams.end()) return {it->second, 1};
    }
    return {P.starters, 2};
}

/* ==================== التنبؤ بالكلمة التالية ==================== */
std::vector<Suggestion> Engine::next_word(const std::string& prev_utf8, const std::string& prev2_utf8,
                                          Lang active) {
    std::vector<Suggestion> out;
    std::set<std::u32string> seen;
    auto push = [&](const std::u32string& w, const char* t, int algo) {
        const std::u32string key = lower32(w);
        if (!seen.count(key) && out.size() < 3) {
            seen.insert(key);
            out.push_back({from32(w), t, algo});
        }
    };

    const LangPack& P = pack(active);
    const bool isArabicScript = (active == Lang::Ar || active == Lang::Ur || active == Lang::Fa);

    // كلمات متعلمة متكررة أولاً (24)
    std::vector<std::pair<std::u32string, int>> lrn;
    for (const auto& [w, c] : learned_) {
        const bool same = isArabicScript ? contains_arabic(w)
            : (active == Lang::Ru ? contains_cyrillic(w)
               : (!contains_arabic(w) && !contains_cyrillic(w)));
        if (same) lrn.emplace_back(w, c);
    }
    std::sort(lrn.begin(), lrn.end(), [](const auto& a, const auto& b) {
        if (a.second != b.second) return a.second > b.second;
        return a.first < b.first;
    });
    for (size_t i = 0; i < lrn.size() && i < 2; i++) push(lrn[i].first, "learned", 24);

    // Backoff: trigram ← bigram ← بدايات (20)
    std::vector<std::u32string> hist;
    const std::u32string p2 = trim32(to32(prev2_utf8));
    const std::u32string p1 = trim32(to32(prev_utf8));
    if (!p2.empty()) hist.push_back(p2);
    if (!p1.empty()) hist.push_back(p1);
    LangPack& Pm = pack(active);
    const BackoffResult res = backoff_next(hist, Pm);
    const int algoId = (res.source == 0) ? 19 : (res.source == 1) ? 16 : 20;
    for (const std::u32string& w : res.words) push(w, "next", algoId);
    (void)P;
    return out;
}

/* ==================== أنبوب الاقتراحات (11 مرحلة) ==================== */
std::vector<Suggestion> Engine::suggest(const std::string& word_utf8, const std::string& prev_utf8,
                                        Lang active, int wpm, const std::string& history_us) {
    (void)prev_utf8;
    (void)history_us;
    const std::u32string word = trim32(to32(word_utf8));
    Lang lang = active;
    if (!word.empty()) {
        if (contains_arabic(word)) {
            lang = (active == Lang::Ur) ? Lang::Ur : (active == Lang::Fa) ? Lang::Fa : Lang::Ar;
        } else if (contains_cyrillic(word)) {
            lang = Lang::Ru;
        }
    }
    LangPack& P = pack(lang);
    const bool isArabicScript = (lang == Lang::Ar || lang == Lang::Ur || lang == Lang::Fa);

    if (word.empty()) return next_word("", "", lang);

    const std::u32string nw = norm_for(lang, word);
    struct Cand { std::u32string w; double s; const char* t; int algo; };
    std::vector<Cand> scored;
    const int maxFreq = 100;
    const int maxDist = adaptive_max_dist(wpm <= 0 ? 30 : wpm, static_cast<int>(word.size()),
                                          correction_offset_);

    /* (1) الأخطاء الشائعة — أولوية قصوى */
    {
        std::u32string known;
        auto hit = [&](const std::u32string& k) -> bool {
            const auto it = P.mistakes.find(k);
            if (it != P.mistakes.end()) { known = it->second; return true; }
            return false;
        };
        if (hit(word) || hit(lower32(word)) || hit(nw) || hit(lower32(nw))) {
            if (known != word) scored.push_back({known, 500.0, "fix", 1});
        }
    }

    /* (2) نقل حرفين (Damerau) — teh→the */
    if (word.size() >= 3) {
        std::u32string swapped;
        if (transpose_fix(nw, swapped) && P.norm_idx.count(swapped)) {
            const auto& idxs = P.norm_idx[swapped];
            for (const int idx : idxs) scored.push_back({P.entries[idx].word, 420.0, "fix", 17});
        }
    }

    /* (3) انهيار التكرار — helllo→hello */
    if (word.size() >= 4) {
        for (const std::u32string& cand : collapse_duplicates(word)) {
            const auto it = P.norm_idx.find(norm_for(lang, cand));
            if (it != P.norm_idx.end()) {
                for (const int idx : it->second) scored.push_back({P.entries[idx].word, 400.0, "fix", 18});
            }
        }
    }

    /* (4) جيران اللوحة (Fat-Finger) */
    if (word.size() >= 2 && scored.size() < 4) {
        for (const std::u32string& cand : neighbor_candidates(nw, isArabicScript)) {
            const auto it = P.norm_idx.find(cand);
            if (it != P.norm_idx.end()) {
                for (const int idx : it->second) {
                    const double s = 210.0 + (static_cast<double>(P.entries[idx].freq) / maxFreq) * 30.0;
                    scored.push_back({P.entries[idx].word, s, "fix", isArabicScript ? 15 : 14});
                }
            }
        }
    }

    /* (5) البادئات عبر Trie + Score Fusion */
    {
        std::vector<int> idxs;
        P.trie.collect_prefix(nw, 64, idxs);
        for (const int idx : idxs) {
            const Entry& e = P.entries[idx];
            const std::u32string key = norm_for(lang, e.word);
            if (key == nw) {
                scored.push_back({e.word, 200.0, "prefix", 1});
            } else if (key.rfind(nw, 0) == 0) {
                const double jw = jaro_winkler(nw, key);
                const double s = score_fusion(zipf_score(e.freq, maxFreq),
                                              key.size() == nw.size() ? 1.0 : jw, 0.0);
                scored.push_back({e.word, s * 120.0, "prefix",
                                  e.word.size() == nw.size() + 1 ? 11 : 1});
            }
        }
    }

    /* (6) كلمات متعلمة بنفس البادئة */
    {
        std::vector<std::pair<std::u32string, int>> lrn(learned_.begin(), learned_.end());
        std::sort(lrn.begin(), lrn.end(), [](const auto& a, const auto& b) {
            if (a.second != b.second) return a.second > b.second;
            return a.first < b.first;
        });
        for (const auto& [w, c] : lrn) {
            const bool sameScript = isArabicScript ? contains_arabic(w)
                : (lang == Lang::Ru ? contains_cyrillic(w)
                   : (!contains_arabic(w) && !contains_cyrillic(w)));
            if (!sameScript || w.size() < 2) continue;
            if (norm_for(lang, w).rfind(nw, 0) == 0) {
                scored.push_back({w, 80.0 + c * 20.0, "learned", 24});
            }
        }
    }

    const size_t non_fix_count = std::count_if(scored.begin(), scored.end(),
                                               [](const Cand& c) { return std::string(c.t) != "fix"; });

    /* (7) إكمال من منتصف الكلمة */
    if (non_fix_count < 3 && word.size() >= 3) {
        for (const Entry& e : P.entries) {
            const std::u32string key = norm_for(lang, e.word);
            if (key.rfind(nw, 0) != 0 && key.find(nw) != std::u32string::npos) {
                const double s = 30.0 + zipf_score(e.freq, maxFreq) * 20.0;
                scored.push_back({e.word, s, "prefix", 2});
            }
        }
    }

    /* (8) تصحيح مسافة تحرير متكيفة (45) */
    if (scored.size() < 3 && word.size() >= 3) {
        for (const Entry& e : P.entries) {
            const std::u32string key = norm_for(lang, e.word);
            const long diff = std::labs(static_cast<long>(key.size()) - static_cast<long>(nw.size()));
            if (diff > maxDist) continue;
            const int d = damerau(nw, key);
            if (d > 0 && d <= maxDist) {
                const double s = 55.0 - d * 12.0 + zipf_score(e.freq, maxFreq) * 20.0;
                scored.push_back({e.word, s, "fix", 3});
            }
        }
    }

    /* (9) Soundex — مطابقة صوتية أخيرة (4) */
    if (scored.size() < 3 && word.size() >= 3) {
        const std::string sx = soundex_for(lang, nw);
        for (const Entry& e : P.entries) {
            const std::u32string key = norm_for(lang, e.word);
            if (key.size() >= 3 && soundex_for(lang, key) == sx && damerau(nw, key) <= 3) {
                const double s = 40.0 + zipf_score(e.freq, maxFreq) * 15.0;
                scored.push_back({e.word, s, "fix", 4});
            }
        }
    }

    /* (10) تجذيع عربي (12) */
    {
        const size_t nonfix2 = std::count_if(scored.begin(), scored.end(),
                                             [](const Cand& c) { return std::string(c.t) != "fix"; });
        if (isArabicScript && nonfix2 < 2 && word.size() >= 4) {
            const std::u32string stem = stem_arabic(word);
            if (stem != nw) {
                for (const Entry& e : P.entries) {
                    const std::u32string key = norm_for(lang, e.word);
                    if (key == stem) {
                        const double s = 90.0 + zipf_score(e.freq, maxFreq) * 20.0;
                        scored.push_back({e.word, s, "prefix", 12});
                    }
                }
            }
        }
    }

    /* (11) عربيزي → عربي (29) */
    if (lang == Lang::Ar && !contains_arabic(word) && word.size() >= 2) {
        bool latin_digits_only = true;
        for (const char32_t c : word) {
            const uint32_t cp = static_cast<uint32_t>(c);
            const bool ok = (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')
                || (cp >= '0' && cp <= '9');
            if (!ok) { latin_digits_only = false; break; }
        }
        if (latin_digits_only) {
            const std::u32string arabic = arabizi_to_arabic(lower32(word));
            if (arabic.size() >= 2) {
                for (const Entry& e : P.entries) {
                    const std::u32string key = norm_for(lang, e.word);
                    if (key.rfind(arabic, 0) == 0 || arabic.rfind(key, 0) == 0) {
                        const double s = 150.0 + zipf_score(e.freq, maxFreq) * 30.0;
                        scored.push_back({e.word, s, "fix", 29});
                    }
                }
            }
        }
    }

    /* الترتيب والدمج */
    std::stable_sort(scored.begin(), scored.end(),
                     [](const Cand& a, const Cand& b) { return a.s > b.s; });

    const bool hasFix = std::any_of(scored.begin(), scored.end(),
                                    [](const Cand& c) { return std::string(c.t) == "fix"; });
    std::set<std::u32string> seen;
    std::vector<Suggestion> out;
    for (const Cand& c : scored) {
        const std::u32string key = lower32(c.w);
        if (seen.count(key)) continue;
        seen.insert(key);
        const bool isFix = std::string(c.t) == "fix";
        out.push_back({from32(c.w), (hasFix && isFix) ? "fix" : c.t, c.algo});
        if (out.size() == 3) break;
    }
    return out;
}

/* ==================== محرك السحب (41/42 + F1) ==================== */
std::string Engine::glide(const std::vector<std::string>& seq_chars, Lang active,
                          const std::string& prev_utf8) {
    if (seq_chars.size() < 2) return "";
    LangPack& P = pack(active);
    const std::u32string prevKey = lower32(trim32(to32(prev_utf8)));
    const bool hasCtx = !prevKey.empty() && P.bigrams.count(prevKey) > 0;

    std::vector<std::u32string> seq;
    seq.reserve(seq_chars.size());
    for (const std::string& c : seq_chars) seq.push_back(norm_for(active, to32(c)));
    if (seq.empty()) return "";
    const std::u32string first = seq[0];

    const bool isArabicScript = (active == Lang::Ar || active == Lang::Ur || active == Lang::Fa);
    const bool isCyr = (active == Lang::Ru);

    std::string best_w;
    double best_s = 0;

    auto consider = [&](const std::u32string& wRaw, double freq) {
        const std::u32string w = norm_for(active, wRaw);
        if (w.size() < 2 || w.size() > seq.size() + 4) return;
        const bool starts = (w.rfind(first, 0) == 0);
        bool seq_has_first = false;
        for (const auto& ch : seq) if (ch == w.substr(0, 1)) { seq_has_first = true; break; }
        if (!starts && !seq_has_first) return;

        size_t i = 0, matched = 0, skipped = 0;
        const size_t maxSkips = std::max<size_t>(1, (w.size() + 2) / 3);
        for (const std::u32string& ch : seq) {
            if (i >= w.size()) break;
            if (ch == w.substr(i, 1)) { i++; matched++; }
            else if (skipped < maxSkips && i + 1 < w.size() && ch == w.substr(i + 1, 1)) {
                i += 2; matched++; skipped++;
            }
        }
        const double recall = static_cast<double>(matched) / static_cast<double>(w.size());
        if (recall < 0.6) return;
        const double precision = static_cast<double>(matched) / static_cast<double>(seq.size());
        const double f1 = (precision + recall == 0.0) ? 0.0
            : (2.0 * precision * recall) / (precision + recall);
        const double lenScale = 2.6 + static_cast<double>(w.size()) * 0.12;
        const double firstBonus = starts ? 0.55 : 0.0;
        double ctxBonus = 0.0;
        if (hasCtx) {
            const auto& nexts = P.bigrams[prevKey];
            for (const auto& n : nexts) if (n == wRaw) { ctxBonus = 0.9; break; }
        }
        const double score = f1 * lenScale + (freq / 100.0) * 0.5 + firstBonus + ctxBonus
            - static_cast<double>(skipped) * 0.18;
        if (best_w.empty() || score > best_s) { best_w = from32(wRaw); best_s = score; }
    };

    for (const Entry& e : P.entries) consider(e.word, static_cast<double>(e.freq));
    for (const auto& [w, c] : learned_) {
        const bool same = isArabicScript ? contains_arabic(w)
            : (isCyr ? contains_cyrillic(w) : (!contains_arabic(w) && !contains_cyrillic(w)));
        if (same) consider(w, static_cast<double>(c) * 2.0);
    }
    return best_w;
}

/* ==================== أدوات مكشوفة ==================== */
std::string Engine::normalize(const std::string& text, Lang lang) {
    return from32(norm_for(lang, to32(text)));
}

std::string Engine::arabizi(const std::string& word) {
    return from32(arabizi_to_arabic(lower32(to32(word))));
}

std::string Engine::latinize(const std::string& word) {
    return arabic_to_latin(to32(word));
}

/* ==================== استخراجات الشرائح الذكية (36-40) ==================== */
Engine::Extract Engine::extract_all(const std::string& text) {
    Extract e;

    /* 36: الرابط */
    {
        size_t best = std::string::npos;
        for (const char* pat : {"https://", "http://", "www."}) {
            const size_t p = text.find(pat);
            if (p != std::string::npos && (best == std::string::npos || p < best)) best = p;
        }
        if (best != std::string::npos) {
            size_t end = best;
            while (end < text.size()) {
                const unsigned char c = static_cast<unsigned char>(text[end]);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '"'
                    || c == '\'' || c == 0xD8) break; // 0xD8 بداية ، العربية
                end++;
            }
            std::string url = text.substr(best, end - best);
            while (!url.empty()) {
                const char last = url.back();
                if (last == '.' || last == ',' || last == ':' || last == ';' || last == '!'
                    || last == '?' || last == ')' || last == ']' || last == '}') url.pop_back();
                else break;
            }
            if (url.size() > 4) e.url = url;
        }
    }

    /* 38: الإيميل */
    for (size_t i = 1; i < text.size(); i++) {
        if (text[i] != '@') continue;
        size_t a = i;
        while (a > 0 && is_word_c(text[a - 1])) a--;
        size_t b = i + 1;
        while (b < text.size() && (is_word_c(text[b]) || text[b] == '-')) b++;
        const std::string local = text.substr(a, i - a);
        const std::string dom = text.substr(i + 1, b - i - 1);
        if (!local.empty() && !dom.empty() && dom.find('.') != std::string::npos
            && dom.front() != '.' && dom.back() != '.') {
            e.email = local + "@" + dom;
            break;
        }
    }

    /* 37: الهاتف */
    {
        size_t i = 0;
        while (i < text.size()) {
            const char c = text[i];
            if (c == '+' || is_digit_c(c)) {
                size_t j = i;
                size_t digits = 0;
                while (j < text.size()) {
                    const char ch = text[j];
                    if (is_digit_c(ch)) digits++;
                    else if (ch != ' ' && ch != '-') break;
                    j++;
                }
                const size_t raw_len = j - i;
                size_t end = j;
                while (end > i && (text[end - 1] == ' ' || text[end - 1] == '-')) end--;
                const size_t len = end - i;
                const bool first_ok = (c == '+')
                    ? (i + 1 < text.size() && is_digit_c(text[i + 1])) : is_digit_c(c);
                if (digits >= 8 && raw_len >= 10 && raw_len <= 16 && len >= 10 && len <= 15
                    && first_ok && is_digit_c(text[end - 1])) {
                    e.phone = trim(text.substr(i, len));
                    break;
                }
                i = (j > i) ? j : i + 1;
            } else {
                i++;
            }
        }
    }

    /* 39: OTP — رمز 4-8 أرقام معزول */
    {
        size_t i = 0;
        while (i < text.size()) {
            if (!is_digit_c(text[i])) { i++; continue; }
            size_t j = i;
            while (j < text.size() && is_digit_c(text[j])) j++;
            const size_t len = j - i;
            const bool isolated_before = (i == 0) || !is_word_c(text[i - 1]);
            const bool isolated_after = (j >= text.size()) || !is_word_c(text[j]);
            if (len >= 4 && len <= 8 && isolated_before && isolated_after) {
                e.otp = text.substr(i, len);
                break;
            }
            i = j;
        }
    }

    /* 40: التاريخ */
    {
        size_t i = 0;
        while (i < text.size()) {
            if (!is_digit_c(text[i])) { i++; continue; }
            size_t j = i;
            while (j < text.size() && is_digit_c(text[j])) j++;
            const size_t n1 = j - i;
            // 2026-09-12
            if (n1 == 4 && j < text.size() && text[j] == '-') {
                size_t k = j + 1;
                size_t m = k;
                while (m < text.size() && is_digit_c(text[m])) m++;
                const size_t n2 = m - k;
                if (n2 >= 1 && n2 <= 2 && m < text.size() && text[m] == '-') {
                    size_t p = m + 1, q = p;
                    while (q < text.size() && is_digit_c(text[q])) q++;
                    const size_t n3 = q - p;
                    if (n3 >= 1 && n3 <= 2) { e.date = text.substr(i, q - i); break; }
                }
            }
            // 31/12 أو 31/12/2026
            if (n1 >= 1 && n1 <= 2 && j < text.size() && text[j] == '/') {
                size_t k = j + 1, m = k;
                while (m < text.size() && is_digit_c(text[m])) m++;
                const size_t n2 = m - k;
                if (n2 >= 1 && n2 <= 2) {
                    size_t q = m;
                    if (m < text.size() && text[m] == '/') {
                        size_t p = m + 1, r = p;
                        while (r < text.size() && is_digit_c(text[r])) r++;
                        const size_t n3 = r - p;
                        if (n3 >= 2 && n3 <= 4) q = r;
                    }
                    e.date = text.substr(i, q - i);
                    break;
                }
            }
            i = (j > i) ? j : i + 1;
        }
    }

    return e;
}

/* ==================== المشاعر والإيموجي السياقي (34-35) — M2 Nova ==================== */
void Engine::load_emoji_tags(const std::string& tsv) {
    emoji_tags_.clear();
    for (const std::string& line : split_lines(tsv)) {
        const auto parts = split_str(line, '\t');
        if (parts.size() < 2) continue;
        const std::string emoji = trim(parts[0]);
        if (emoji.empty()) continue;
        std::vector<std::u32string> tags;
        for (const std::string& tok : split_str(trim(parts[1]), ' ')) {
            const std::string t = trim(tok);
            if (t.empty()) continue;
            tags.push_back(normalize_arabic(lower32(to32(t))));
        }
        emoji_tags_.push_back({emoji, std::move(tags)});
    }
}

/* 34: تحليل المشاعر المعجمي — مطابقة حرفية لمنطق الويب (word.includes(term)) */
int Engine::sentiment(const std::string& text_utf8) const {
    const std::vector<std::u32string> words = split_ws32(lower32(to32(text_utf8)));
    int score = 0;
    for (const std::u32string& w : words) {
        const std::u32string nw = normalize_arabic(w);
        for (const char* p : POSITIVE_LEX) {
            const std::u32string p32 = to32(p);
            if (w.find(p32) != std::u32string::npos || nw.find(p32) != std::u32string::npos) {
                score++;
                break;
            }
        }
        for (const char* n : NEGATIVE_LEX) {
            const std::u32string n32 = to32(n);
            if (w.find(n32) != std::u32string::npos || nw.find(n32) != std::u32string::npos) {
                score--;
                break;
            }
        }
    }
    return score;
}

/* 35: اقتراح إيموجي من السياق — وسم الكلمة الأخيرة ثم تعزيز بالمزاج (34) */
std::vector<std::string> Engine::emoji_suggest(const std::string& text_utf8, Lang active) const {
    (void)active;
    const std::u32string text = to32(text_utf8);
    // الكلمة الأخيرة (تتجاهل علامات الترقيم والمسافات الختامية)
    std::u32string last;
    for (size_t i = text.size(); i > 0; i--) {
        const uint32_t cp = static_cast<uint32_t>(text[i - 1]);
        if (is_letter_cp(cp)) last.insert(last.begin(), text[i - 1]);
        else if (!last.empty()) break;
    }
    const std::u32string nw = normalize_arabic(lower32(last));

    std::vector<std::string> out;
    auto push_unique = [&](const std::string& e) {
        for (const std::string& x : out) if (x == e) return;
        out.push_back(e);
    };

    if (!nw.empty()) {
        for (const auto& [emoji, tags] : emoji_tags_) {
            for (const std::u32string& t : tags) {
                if (!t.empty() && nw.find(t) != std::u32string::npos) {
                    push_unique(emoji);
                    break;
                }
            }
            if (out.size() >= 6) break;
        }
    }

    /* تعزيز بالمزاج العام عند نقص نتائج الوسوم — مجموعات افتراضية حسب 34 */
    static const char* const MOOD_POS[] = {"😊", "😍", "🥳", "👍", "🎉", "🔥"};
    static const char* const MOOD_NEG[] = {"😢", "😡", "🤦", "😤", "💔"};
    static const char* const MOOD_NEU[] = {"🤔", "👀", "😉"};
    const int mood = sentiment(text_utf8);
    const char* const* set = (mood > 0) ? MOOD_POS : (mood < 0) ? MOOD_NEG : MOOD_NEU;
    const int n = (mood > 0) ? 6 : (mood < 0) ? 5 : 3;
    for (int i = 0; i < n && out.size() < 6; i++) push_unique(set[i]);
    return out;
}

/* ==================== المصحح اللغوي (51) — M3 Legend ==================== */

namespace {
// هل تحتوي السلسلة على حرف يُدقق؟ (أرقام/رموز وحدها لا تُدقق)
bool has_letter_cp(const std::u32string& w) {
    for (const char32_t c : w) {
        const uint32_t cp = static_cast<uint32_t>(c);
        const bool latin = (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z')
            || (cp >= 0x00C0 && cp <= 0x024F);
        if (latin || is_arabic_cp(cp) || is_cyrillic_cp(cp)) return true;
    }
    return false;
}
} // namespace

Engine::SpellResult Engine::spell_check(const std::string& word_utf8, Lang active) {
    SpellResult r{false, {}};
    const std::u32string word = trim32(to32(word_utf8));
    if (word.empty() || word.size() > 40 || !has_letter_cp(word)) {
        r.correct = true;  // أرقام/إيموجي/رموز = سليمة عملياً
        return r;
    }

    Lang lang = active;
    if (contains_arabic(word)) {
        lang = (active == Lang::Ur) ? Lang::Ur : (active == Lang::Fa) ? Lang::Fa : Lang::Ar;
    } else if (contains_cyrillic(word)) {
        lang = Lang::Ru;
    }
    LangPack& P = pack(lang);
    const bool isArabicScript = (lang == Lang::Ar || lang == Lang::Ur || lang == Lang::Fa);
    const std::u32string nw = norm_for(lang, word);

    /* 1) موجودة حرفياً أو بعد التطبيع (تطبيع عربي خماسي / تصغير لاتيني) */
    if (P.norm_idx.count(lower32(word)) || P.norm_idx.count(nw)) {
        r.correct = true;
        return r;
    }
    /* 2) أو متعلمة (ذاكرة الملف الشخصي) */
    if (learned_.count(lower32(word)) || learned_.count(nw)) {
        r.correct = true;
        return r;
    }

    /* 3) خطأ شائع معروف؟ القاموس يعطي الصواب مباشرة */
    struct CandS { std::u32string w; double s; int algo; };
    std::vector<CandS> scored;
    std::set<std::u32string> seen_fix;   // إزالة التكرار بمفتاح صغير مع حفظ الشكل الأصلي
    auto add_fix = [&](const std::u32string& w, double s, int algo) {
        const std::u32string key = lower32(w);
        if (key.empty() || seen_fix.count(key)) return;
        seen_fix.insert(key);
        scored.push_back({w, s, algo});
    };
    auto mistakes_hit = [&](const std::u32string& k) {
        const auto it = P.mistakes.find(k);
        if (it != P.mistakes.end() && it->second != word) add_fix(it->second, 500.0, 1);
    };
    mistakes_hit(word); mistakes_hit(lower32(word)); mistakes_hit(nw); mistakes_hit(lower32(nw));

    const int maxFreq = 100;
    const int maxDist = adaptive_max_dist(45, static_cast<int>(word.size()), correction_offset_);

    /* 4) نقل حرفين متجاورين (17) */
    if (word.size() >= 3) {
        std::u32string swapped;
        if (transpose_fix(nw, swapped) && P.norm_idx.count(swapped)) {
            const auto& idxs = P.norm_idx[swapped];
            for (const int idx : idxs) add_fix(P.entries[idx].word, 420.0, 17);
        }
    }

    /* 5) انهيار التكرار (18) */
    if (word.size() >= 4) {
        for (const std::u32string& cand : collapse_duplicates(word)) {
            const auto it = P.norm_idx.find(norm_for(lang, cand));
            if (it != P.norm_idx.end()) {
                for (const int idx : it->second) add_fix(P.entries[idx].word, 400.0, 18);
            }
        }
    }

    /* 6) جيران اللوحة (14/15/16) */
    if (word.size() >= 2) {
        for (const std::u32string& cand : neighbor_candidates(nw, isArabicScript)) {
            const auto it = P.norm_idx.find(cand);
            if (it != P.norm_idx.end()) {
                for (const int idx : it->second) {
                    add_fix(P.entries[idx].word,
                            210.0 + (static_cast<double>(P.entries[idx].freq) / maxFreq) * 30.0,
                            isArabicScript ? 15 : 14);
                }
            }
        }
    }

    /* 7) مسافة تحرير متكيفة (3/45) — مرشّحة بطول مسبق لتبقى سريعة */
    for (const Entry& e : P.entries) {
        const std::u32string key = norm_for(lang, e.word);
        const long diff = std::labs(static_cast<long>(key.size()) - static_cast<long>(nw.size()));
        if (diff > maxDist || diff > 2) continue;
        const int d = damerau(nw, key);
        if (d > 0 && d <= maxDist) {
            add_fix(e.word, 55.0 - d * 12.0 + zipf_score(e.freq, maxFreq) * 20.0, 3);
        }
    }

    /* 8) تجذيع عربي (12) — كتب "المدارلس" → قاعدة "مدارس" */
    if (isArabicScript && word.size() >= 4) {
        const std::u32string stem = stem_arabic(word);
        if (stem != nw) {
            const auto it = P.norm_idx.find(stem);
            if (it != P.norm_idx.end()) {
                for (const int idx : it->second) {
                    add_fix(P.entries[idx].word, 320.0, 12);
                }
            }
        }
    }

    if (scored.empty()) return r;  // غير صحيحة ولا تصحيح واثق

    std::stable_sort(scored.begin(), scored.end(),
                     [](const CandS& a, const CandS& b) { return a.s > b.s; });
    for (const CandS& c : scored) {
        if (lower32(c.w) == lower32(word)) continue;   // لا نقترح الكلمة نفسها
        r.fixes.push_back(from32(c.w));
        if (r.fixes.size() == 4) break;
    }
    return r;
}

std::vector<std::string> Engine::spell_check_text(const std::string& text_utf8, Lang active) {
    std::vector<std::string> out;
    const std::u32string text = to32(text_utf8);
    std::u32string cur;
    auto flush = [&](std::u32string w) {
        w = trim32(w);
        if (w.empty()) return;
        const SpellResult r = spell_check(from32(w), active);
        if (!r.correct && !r.fixes.empty()) {
            std::string line = from32(w);
            for (size_t i = 0; i < r.fixes.size() && i < 3; i++) {
                line += '\u0001';
                line += r.fixes[i];
            }
            out.push_back(line);
        }
    };
    for (const char32_t c : text) {
        if (c == U' ' || c == U'\t' || c == U'\n' || c == U'\r'
            || c == U'.' || c == U',' || c == U'!' || c == U'?' || c == U'؛'
            || c == U'،' || c == U'؟' || c == U':') {
            flush(cur);
            cur.clear();
        } else {
            cur += c;
        }
    }
    flush(cur);
    return out;
}

} // namespace onyx
