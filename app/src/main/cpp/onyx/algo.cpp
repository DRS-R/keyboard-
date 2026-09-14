#include "algo.h"
#include "utf8.h"

#include <algorithm>
#include <cmath>
#include <unordered_map>
#include <unordered_set>
#include <set>

namespace onyx {

/* ==================== 1) Levenshtein ==================== */
int levenshtein(const std::u32string& a, const std::u32string& b) {
    if (a == b) return 0;
    const size_t m = a.size(), n = b.size();
    if (m == 0) return static_cast<int>(n);
    if (n == 0) return static_cast<int>(m);
    std::vector<int> prev(n + 1), cur(n + 1);
    for (size_t j = 0; j <= n; j++) prev[j] = static_cast<int>(j);
    for (size_t i = 1; i <= m; i++) {
        cur[0] = static_cast<int>(i);
        for (size_t j = 1; j <= n; j++) {
            const int sub = prev[j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1);
            cur[j] = std::min({prev[j] + 1, cur[j - 1] + 1, sub});
        }
        std::swap(prev, cur);
    }
    return prev[n];
}

/* ==================== 2) Damerau-Levenshtein ==================== */
int damerau(const std::u32string& a, const std::u32string& b) {
    const size_t al = a.size(), bl = b.size();
    if (al == 0) return static_cast<int>(bl);
    if (bl == 0) return static_cast<int>(al);
    std::vector<std::vector<int>> d(al + 1, std::vector<int>(bl + 1, 0));
    for (size_t i = 0; i <= al; i++) d[i][0] = static_cast<int>(i);
    for (size_t j = 0; j <= bl; j++) d[0][j] = static_cast<int>(j);
    for (size_t i = 1; i <= al; i++) {
        for (size_t j = 1; j <= bl; j++) {
            const int cost = (a[i - 1] == b[j - 1]) ? 0 : 1;
            d[i][j] = std::min({d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost});
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                d[i][j] = std::min(d[i][j], d[i - 2][j - 2] + 1);
            }
        }
    }
    return d[al][bl];
}

/* ==================== 3) Jaro-Winkler ==================== */
double jaro_winkler(const std::u32string& a, const std::u32string& b) {
    if (a == b) return 1.0;
    const size_t al = a.size(), bl = b.size();
    if (al == 0 || bl == 0) return 0.0;
    const size_t md = (std::max(al, bl) / 2 >= 1) ? (std::max(al, bl) / 2 - 1) : 0;
    std::vector<bool> am(al, false), bm(bl, false);
    size_t matches = 0;
    for (size_t i = 0; i < al; i++) {
        const size_t lo = (i > md) ? i - md : 0;
        const size_t hi = std::min(i + md + 1, bl);
        for (size_t j = lo; j < hi; j++) {
            if (!bm[j] && a[i] == b[j]) { am[i] = true; bm[j] = true; matches++; break; }
        }
    }
    if (matches == 0) return 0.0;
    size_t transpositions = 0, k = 0;
    for (size_t i = 0; i < al; i++) {
        if (!am[i]) continue;
        while (k < bl && !bm[k]) k++;
        if (k < bl) { if (a[i] != b[k]) transpositions++; k++; }
    }
    const double m = static_cast<double>(matches);
    const double jaro = (m / static_cast<double>(al)
                       + m / static_cast<double>(bl)
                       + (m - static_cast<double>(transpositions) / 2.0) / m) / 3.0;
    size_t prefix = 0;
    const size_t lim = std::min<size_t>(4, std::min(al, bl));
    for (size_t i = 0; i < lim; i++) { if (a[i] == b[i]) prefix++; else break; }
    return jaro + static_cast<double>(prefix) * 0.1 * (1.0 - jaro);
}

/* ==================== 4) Soundex عربي ==================== */
static int ar_soundex_code(uint32_t cp) {
    // 0..9 ثم 'F'(15) 'Q' 'K' 'L' 'M' 'N' 'H' 'W' 'Y' — نستخدم أعداداً مميزة
    switch (cp) {
        case 0x0621: return '0';      // ء
        case 0x0627: case 0x0623: case 0x0625: case 0x0622: return '1'; // ا أ إ آ
        case 0x0628: return '2';      // ب
        case 0x062A: case 0x062B: return '3'; // ت ث
        case 0x062C: case 0x062D: case 0x062E: return '4'; // ج ح خ
        case 0x062F: case 0x0630: return '5'; // د ذ
        case 0x0631: case 0x0632: return '6'; // ر ز
        case 0x0633: case 0x0634: case 0x0635: case 0x0636: return '7'; // س ش ص ض
        case 0x0637: case 0x0638: return '8'; // ط ظ
        case 0x0639: case 0x063A: return '9'; // ع غ
        case 0x0641: return 'F';      // ف
        case 0x0642: return 'Q';      // ق
        case 0x0643: return 'K';      // ك
        case 0x0644: return 'L';      // ل
        case 0x0645: return 'M';      // م
        case 0x0646: return 'N';      // ن
        case 0x0647: return 'H';      // ه
        case 0x0648: return 'W';      // و
        case 0x064A: return 'Y';      // ي
        default: return 0;
    }
}

std::string soundex_arabic(const std::u32string& w) {
    const std::u32string clean = strip_tashkeel(w);
    std::string out;
    int prev = 0;
    for (const char32_t c : clean) {
        const int code = ar_soundex_code(static_cast<uint32_t>(c));
        if (code != 0) {
            if (code != prev) out += static_cast<char>(code);
            prev = code;
        }
    }
    if (out.size() > 6) out.resize(6);
    return out;
}

/* ==================== 5) Soundex لاتيني ==================== */
static int lat_soundex_code(uint32_t cp) {
    switch (cp) {
        case 'B': case 'F': case 'P': case 'V': return '1';
        case 'C': case 'G': case 'J': case 'K': case 'Q': case 'S': case 'X': case 'Z': return '2';
        case 'D': case 'T': return '3';
        case 'L': return '4';
        case 'M': case 'N': return '5';
        case 'R': return '6';
        default: return 0;
    }
}

std::string soundex_latin(const std::u32string& w_in) {
    // TS: uppercase ثم استبعاد غير A-Z
    std::u32string s;
    for (const char32_t c : w_in) {
        const uint32_t cp = static_cast<uint32_t>(c);
        if (cp >= 'a' && cp <= 'z') s += static_cast<char32_t>(cp - 32);
        else if (cp >= 'A' && cp <= 'Z') s += c;
    }
    if (s.empty()) return "";
    std::string out;
    int prev = lat_soundex_code(static_cast<uint32_t>(s[0]));
    out += static_cast<char>(s[0]);
    for (size_t i = 1; i < s.size(); i++) {
        const uint32_t cp = static_cast<uint32_t>(s[i]);
        const int c = lat_soundex_code(cp);
        if (c != 0 && c != prev) out += static_cast<char>(c);
        if (cp != 'H' && cp != 'W') prev = c;
    }
    if (out.size() < 4) out += "000";
    out.resize(4);
    return out;
}

/* ==================== 6-10) التطبيع العربي ==================== */
static bool is_tashkeel_cp(uint32_t cp) {
    return (cp >= 0x064B && cp <= 0x0652) || cp == 0x0670 || cp == 0x0640
        || (cp >= 0x0653 && cp <= 0x0655);
}

std::u32string strip_tashkeel(const std::u32string& w) {
    std::u32string out;
    out.reserve(w.size());
    for (const char32_t c : w)
        if (!is_tashkeel_cp(static_cast<uint32_t>(c))) out += c;
    return out;
}

std::u32string unify_hamza(const std::u32string& w) {
    std::u32string out;
    out.reserve(w.size());
    for (const char32_t c : w) {
        const uint32_t cp = static_cast<uint32_t>(c);
        out += static_cast<char32_t>((cp == 0x0623 || cp == 0x0625 || cp == 0x0622 || cp == 0x0671) ? 0x0627 : cp);
    }
    return out;
}

std::u32string unify_taa_marbuta(const std::u32string& w) {
    std::u32string out;
    out.reserve(w.size());
    for (const char32_t c : w)
        out += static_cast<char32_t>(c == U'ة' ? 0x0647 : static_cast<uint32_t>(c));
    return out;
}

std::u32string unify_alef_maqsura(const std::u32string& w) {
    std::u32string out;
    out.reserve(w.size());
    for (const char32_t c : w)
        out += static_cast<char32_t>(c == U'ى' ? 0x064A : static_cast<uint32_t>(c));
    return out;
}

std::u32string normalize_arabic(const std::u32string& w) {
    return unify_alef_maqsura(unify_taa_marbuta(unify_hamza(strip_tashkeel(w))));
}

/* ==================== 11) التجذيع العربي ==================== */
std::u32string stem_arabic(const std::u32string& w_in) {
    std::u32string s = strip_tashkeel(w_in);
    static const char32_t* prefixes[] = {
        U"وبال", U"فبال", U"بال", U"كال", U"لل", U"وال", U"ال", U"و", U"ف", U"ب", U"ك", U"ل",
    };
    for (const char32_t* pfx : prefixes) {
        const std::u32string p(pfx);
        if (s.rfind(p, 0) == 0 && s.size() - p.size() >= 3) {
            return s.substr(p.size());
        }
    }
    return s;
}

/* ==================== 12) التطبيع اللاتيني ==================== */
// بديل NFD: طي المحارف المركبة الشائعة إلى الأساس + نزع U+0300..036F + ı→i
static uint32_t fold_latin_cp(uint32_t cp) {
    switch (cp) {
        case 0x00E0: case 0x00E1: case 0x00E2: case 0x00E3: case 0x00E4: case 0x00E5: return 'a';
        case 0x00E8: case 0x00E9: case 0x00EA: case 0x00EB: return 'e';
        case 0x00EC: case 0x00ED: case 0x00EE: case 0x00EF: return 'i';
        case 0x00F2: case 0x00F3: case 0x00F4: case 0x00F5: case 0x00F6: case 0x00F8: return 'o';
        case 0x00F9: case 0x00FA: case 0x00FB: case 0x00FC: return 'u';
        case 0x00FD: case 0x00FF: return 'y';
        case 0x00E7: return 'c';
        case 0x00F1: return 'n';
        case 0x0153: return 'o'; // œ
        case 0x0161: return 's'; // š
        case 0x017E: return 'z'; // ž
        case 0x0107: case 0x0109: case 0x010D: return 'c';
        case 0x0115: case 0x0117: case 0x011B: return 'e';
        case 0x0121: case 0x011F: return 'g';
        case 0x0129: case 0x012B: case 0x012F: return 'i';
        case 0x0135: return 'j';
        case 0x0137: case 0x013A: case 0x013C: case 0x0142: return 'l';
        case 0x0144: case 0x0148: return 'n';
        case 0x014D: case 0x0151: return 'o';
        case 0x0155: case 0x0159: return 'r';
        case 0x015B: case 0x015D: return 's';
        case 0x0165: return 't';
        case 0x016B: case 0x0171: case 0x0173: return 'u';
        case 0x0175: return 'w';
        case 0x0177: return 'y';
        case 0x017A: case 0x017C: return 'z';
        case 0x0105: case 0x0101: return 'a';
        case 0x0113: return 'e';
        case 0x0123: return 'g';
        case 0x012A: return 'i';
        case 0x014A: return 'n';
        case 0x016A: return 'u';
        case 0x0178: return 'y';
        default: return cp;
    }
}

std::u32string normalize_latin(const std::u32string& w) {
    std::u32string low;
    low.reserve(w.size());
    for (const char32_t c : w) low += static_cast<char32_t>(cp_lower(static_cast<uint32_t>(c)));
    std::u32string out;
    out.reserve(low.size());
    for (const char32_t c : low) {
        uint32_t cp = static_cast<uint32_t>(c);
        if (cp >= 0x0300 && cp <= 0x036F) continue; // علامات مركبة
        if (cp == 0x0131) { out += U'i'; continue; } // ı → i
        out += static_cast<char32_t>(fold_latin_cp(cp));
    }
    return out;
}

/* ==================== 13) تطبيع سيريلي ==================== */
std::u32string normalize_cyrillic(const std::u32string& w) {
    std::u32string low = lower32(w);
    std::u32string out;
    out.reserve(low.size());
    for (const char32_t c : low)
        out += static_cast<char32_t>(c == 0x0451 ? 0x0435 : static_cast<uint32_t>(c)); // ё→е
    return out;
}

/* ==================== 14/15) جيران اللوحة ==================== */
const char* const QWERTY_NEIGHBORS[][2] = {
    {"q", "wa"}, {"w", "qesad"}, {"e", "wrsdf"}, {"r", "etdfg"}, {"t", "ryfgh"},
    {"y", "tughj"}, {"u", "yihjk"}, {"i", "uojkl"}, {"o", "ipkl"}, {"p", "ol"},
    {"a", "qszwx"}, {"s", "awedxz"}, {"d", "sefcx"}, {"f", "drgvc"}, {"g", "fthvb"},
    {"h", "gyjbn"}, {"j", "huknm"}, {"k", "jilm"}, {"l", "ko"},
    {"z", "asx"}, {"x", "zsdc"}, {"c", "xdfv"}, {"v", "cfgb"}, {"b", "vghn"}, {"n", "bhjm"}, {"m", "njk"},
    {nullptr, nullptr},
};

const char* const ARABIC_NEIGHBORS[][2] = {
    {"ض", "ص"}, {"ص", "ضثق"}, {"ث", "صف"}, {"ق", "ثفغ"}, {"ف", "قغع"}, {"غ", "فعه"},
    {"ع", "غهخ"}, {"ه", "عخج"}, {"خ", "هجح"}, {"ح", "خجد"}, {"ج", "حذد"}, {"د", "جذ"},
    {"ش", "س"}, {"س", "شي"}, {"ي", "سب"}, {"ب", "يل"}, {"ل", "بات"}, {"ا", "لتن"},
    {"ت", "انم"}, {"ن", "تمك"}, {"م", "نكت"}, {"ك", "مط"}, {"ط", "كذ"},
    {"ذ", "دئء"}, {"ئ", "ذءؤ"}, {"ء", "ئئؤ"}, {"ؤ", "ءرز"}, {"ر", "ؤزى"}, {"ز", "رظ"}, {"ظ", "زو"},
    {nullptr, nullptr},
};

static std::string neighbors_of(const std::u32string& w, size_t i, bool arabic) {
    // يعيد جيران الحرف i كسلسلة UTF-8 (مفاتيح الجداول نصوص UTF-8)
    const std::string ch = from32(w.substr(i, 1));
    const auto* table = arabic ? ARABIC_NEIGHBORS : QWERTY_NEIGHBORS;
    for (int t = 0; table[t][0] != nullptr; t++) {
        if (ch == table[t][0]) return table[t][1];
    }
    return "";
}

std::vector<std::u32string> neighbor_candidates(const std::u32string& w, bool arabic) {
    std::set<std::u32string> uniq; // ترتيب حتمي
    for (size_t i = 0; i < w.size(); i++) {
        const std::string nbs = neighbors_of(w, i, arabic);
        if (nbs.empty()) continue;
        size_t j = 0;
        while (j < nbs.size()) {
            const uint32_t nb = cp_next(nbs, j);
            std::u32string cand = w;
            cand[i] = static_cast<char32_t>(nb);
            uniq.insert(cand);
        }
    }
    return std::vector<std::u32string>(uniq.begin(), uniq.end());
}

/* ==================== 17) النقل المزدوج ==================== */
bool transpose_fix(const std::u32string& w, std::u32string& out) {
    if (w.size() < 2) return false;
    for (size_t i = 0; i + 1 < w.size(); i++) {
        if (w[i] == w[i + 1]) continue;
        out = w;
        std::swap(out[i], out[i + 1]);
        return true;
    }
    return false;
}

/* ==================== 18) انهيار المكرر ==================== */
std::vector<std::u32string> collapse_duplicates(const std::u32string& w) {
    std::set<std::u32string> uniq;
    for (size_t i = 0; i + 1 < w.size(); i++) {
        if (w[i] == w[i + 1]) {
            uniq.insert(w.substr(0, i) + w.substr(i + 1));
        }
    }
    return std::vector<std::u32string>(uniq.begin(), uniq.end());
}

/* ==================== 21) اضمحلال أسّي ==================== */
double recency_weight(long long age_ms, long long half_life_ms) {
    if (age_ms < 0) age_ms = 0;
    return std::pow(0.5, static_cast<double>(age_ms) / static_cast<double>(half_life_ms));
}

/* ==================== 22) Zipf ==================== */
double zipf_score(int freq, int max_freq) {
    if (freq <= 0) return 0.0;
    return std::log10(1.0 + freq) / std::log10(1.0 + max_freq);
}

/* ==================== 23) Score Fusion ==================== */
double score_fusion(double freq, double context, double personal,
                    double recency, int len) {
    const double w_freq = 0.38, w_ctx = 0.32, w_per = 0.22, w_rec = 0.08;
    double base = freq * w_freq + context * w_ctx + personal * w_per;
    if (recency >= 0) base += recency * w_rec;
    if (len >= 3 && len <= 8) base += 0.03;
    return base;
}

/* ==================== 45) عتبة متكيفة ==================== */
int adaptive_max_dist(int wpm, int word_len, int offset) {
    int base = (word_len <= 4) ? 1 : 2;
    if (wpm > 45) base = base;                       // سريع → متحفظ
    else if (wpm < 20) base = std::min(3, base + 1); // بطيء → سخي
    int d = base + offset;
    if (d < 1) d = 1;
    if (d > 3) d = 3;
    return d;
}

/* ==================== 29) عربيزي → عربي ==================== */
std::u32string arabizi_to_arabic(const std::u32string& w_in) {
    // الدخل مفترض لاتيني صغير (المستدعي يصغّر)
    static const std::pair<std::u32string, std::u32string> digraphs[] = {
        {U"kh", U"خ"}, {U"gh", U"غ"}, {U"sh", U"ش"}, {U"th", U"ث"}, {U"dh", U"ذ"}, {U"ch", U"تش"},
    };
    // استبدال الثنائيات أولاً (كما في TS /g/g)
    std::u32string s;
    s.reserve(w_in.size());
    for (size_t i = 0; i < w_in.size();) {
        bool matched = false;
        for (const auto& dg : digraphs) {
            if (w_in.compare(i, dg.first.size(), dg.first) == 0) {
                s += dg.second;
                i += dg.first.size();
                matched = true;
                break;
            }
        }
        if (!matched) { s += w_in[i]; i++; }
    }

    static const std::unordered_map<char32_t, std::u32string> single = {
        {U'3', U"ع"}, {U'7', U"ح"}, {U'2', U"ء"}, {U'5', U"خ"}, {U'9', U"ق"},
        {U'6', U"ط"}, {U'8', U"ة"}, {U'a', U"ا"}, {U'b', U"ب"}, {U't', U"ت"},
        {U'j', U"ج"}, {U'h', U"ه"}, {U'd', U"د"}, {U'r', U"ر"}, {U'z', U"ز"},
        {U's', U"س"}, {U'f', U"ف"}, {U'q', U"ق"}, {U'k', U"ك"}, {U'l', U"ل"},
        {U'm', U"م"}, {U'n', U"ن"}, {U'w', U"و"}, {U'y', U"ي"}, {U'e', U"ي"},
        {U'i', U"ي"}, {U'o', U"و"}, {U'u', U"و"}, {U'g', U"ج"}, {U'v', U"ف"},
        {U'c', U"س"}, {U'p', U"ب"}, {U'x', U"كس"},
    };
    std::u32string out;
    for (const char32_t c : s) {
        const auto it = single.find(c);
        if (it != single.end()) out += it->second;
    }
    return out;
}

/* ==================== 30) عربي → لاتيني ==================== */
std::string arabic_to_latin(const std::u32string& w) {
    static const std::unordered_map<char32_t, const char*> map = {
        {U'ا', "a"}, {U'أ', "a"}, {U'إ', "i"}, {U'آ', "aa"}, {U'ب', "b"}, {U'ت', "t"},
        {U'ث', "th"}, {U'ج', "j"}, {U'ح', "h"}, {U'خ', "kh"}, {U'د', "d"}, {U'ذ', "dh"},
        {U'ر', "r"}, {U'ز', "z"}, {U'س', "s"}, {U'ش', "sh"}, {U'ص', "s"}, {U'ض', "d"},
        {U'ط', "t"}, {U'ظ', "z"}, {U'ع', "3"}, {U'غ', "gh"}, {U'ف', "f"}, {U'ق', "q"},
        {U'ك', "k"}, {U'ل', "l"}, {U'م', "m"}, {U'ن', "n"}, {U'ه', "h"}, {U'و', "w"},
        {U'ي', "y"}, {U'ى', "a"}, {U'ة', "a"}, {U'ء', "'"}, {U'ئ', "e"}, {U'ؤ', "o"},
    };
    std::string out;
    for (const char32_t c : w) {
        const auto it = map.find(c);
        if (it != map.end()) out += it->second;
        else out += from32(std::u32string(1, c));
    }
    return out;
}

/* ==================== 26) كشف نظام الكتابة ==================== */
int detect_script_class(const std::u32string& w) {
    if (contains_arabic(w)) return 0;
    if (contains_cyrillic(w)) return 1;
    return 2;
}

/* ==================== 28) نقاط التبديل اللغوي ==================== */
int code_switch_points(const std::string& text_utf8) {
    int switches = 0;
    int prev_class = -1;
    size_t i = 0;
    while (i < text_utf8.size()) {
        // مقطع = حتى فراغ
        while (i < text_utf8.size() && (text_utf8[i] == ' ' || text_utf8[i] == '\t' || text_utf8[i] == '\n')) i++;
        if (i >= text_utf8.size()) break;
        const size_t start = i;
        while (i < text_utf8.size() && text_utf8[i] != ' ' && text_utf8[i] != '\t' && text_utf8[i] != '\n') i++;
        const std::u32string chunk = to32(text_utf8.substr(start, i - start));
        const int cls = detect_script_class(chunk);
        if (prev_class != -1 && cls != prev_class) switches++;
        prev_class = cls;
    }
    return switches;
}

} // namespace onyx
