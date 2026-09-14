#include "utf8.h"

namespace onyx {

uint32_t cp_next(const std::string& s, size_t& i) {
    if (i >= s.size()) return 0;
    const unsigned char b0 = static_cast<unsigned char>(s[i]);
    if (b0 < 0x80) { i += 1; return b0; }
    if ((b0 >> 5) == 0x6 && i + 1 < s.size()) {
        uint32_t cp = ((b0 & 0x1F) << 6) | (static_cast<unsigned char>(s[i + 1]) & 0x3F);
        i += 2; return cp;
    }
    if ((b0 >> 4) == 0xE && i + 2 < s.size()) {
        uint32_t cp = ((b0 & 0x0F) << 12)
            | ((static_cast<unsigned char>(s[i + 1]) & 0x3F) << 6)
            | (static_cast<unsigned char>(s[i + 2]) & 0x3F);
        i += 3; return cp;
    }
    if ((b0 >> 3) == 0x1E && i + 3 < s.size()) {
        uint32_t cp = ((b0 & 0x07) << 18)
            | ((static_cast<unsigned char>(s[i + 1]) & 0x3F) << 12)
            | ((static_cast<unsigned char>(s[i + 2]) & 0x3F) << 6)
            | (static_cast<unsigned char>(s[i + 3]) & 0x3F);
        i += 4; return cp;
    }
    i += 1; return b0; // بايت تالف — تجاوز آمن
}

uint32_t cp32_next(const std::u32string& s, size_t& i) {
    if (i >= s.size()) return 0;
    return static_cast<uint32_t>(s[i++]);
}

void cp_append(std::string& s, uint32_t cp) {
    if (cp < 0x80) {
        s += static_cast<char>(cp);
    } else if (cp < 0x800) {
        s += static_cast<char>(0xC0 | (cp >> 6));
        s += static_cast<char>(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        s += static_cast<char>(0xE0 | (cp >> 12));
        s += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        s += static_cast<char>(0x80 | (cp & 0x3F));
    } else {
        s += static_cast<char>(0xF0 | (cp >> 18));
        s += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
        s += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        s += static_cast<char>(0x80 | (cp & 0x3F));
    }
}

std::u32string to32(const std::string& s) {
    std::u32string out;
    out.reserve(s.size());
    size_t i = 0;
    while (i < s.size()) out += static_cast<char32_t>(cp_next(s, i));
    return out;
}

std::string from32(const std::u32string& s) {
    std::string out;
    out.reserve(s.size() * 2);
    for (const char32_t c : s) cp_append(out, static_cast<uint32_t>(c));
    return out;
}

size_t cp_len(const std::string& s) {
    size_t i = 0, n = 0;
    while (i < s.size()) { cp_next(s, i); n++; }
    return n;
}

uint32_t cp_lower(uint32_t cp) {
    if (cp >= 'A' && cp <= 'Z') return cp + 32;
    if (cp >= 0x00C0 && cp <= 0x00DE && cp != 0x00D7) return cp + 32; // À..Þ
    if (cp == 0x0130) return 'i'; // İ → i (تركي)
    if (cp >= 0x0100 && cp <= 0x017F) { // Latin Extended-A: الزوجي حرف كبير
        if ((cp % 2) == 0) return cp + 1;
        return cp;
    }
    if (cp >= 0x0410 && cp <= 0x042F) return cp + 32; // А..Я
    if (cp == 0x0401) return 0x0451; // Ё → ё
    return cp;
}

std::u32string lower32(const std::u32string& s) {
    std::u32string out;
    out.reserve(s.size());
    for (const char32_t c : s) out += static_cast<char32_t>(cp_lower(static_cast<uint32_t>(c)));
    return out;
}

bool contains_arabic(const std::u32string& s) {
    for (const char32_t c : s) if (is_arabic_cp(static_cast<uint32_t>(c))) return true;
    return false;
}

bool contains_cyrillic(const std::u32string& s) {
    for (const char32_t c : s) if (is_cyrillic_cp(static_cast<uint32_t>(c))) return true;
    return false;
}

} // namespace onyx
