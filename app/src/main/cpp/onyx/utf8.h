// Onyx Engine — أدوات UTF-8 خفيفة (بدون ICU) لتعمل على NDK والمضيف
#pragma once
#include <string>
#include <cstdint>
#include <vector>

namespace onyx {

// يقرأ نقطة الكود التالية ويقدّم المؤشر i
uint32_t cp_next(const std::string& s, size_t& i);

// يقرأ من سلسلة 32-بت
uint32_t cp32_next(const std::u32string& s, size_t& i);

// يضيف نقطة كود إلى سلسلة UTF-8
void cp_append(std::string& s, uint32_t cp);

// تحويلات
std::u32string to32(const std::string& s);
std::string from32(const std::u32string& s);

// طول بنقاط الكود (لا بايتات)
size_t cp_len(const std::string& s);

// تحويل حرف إلى صغير (يغطي اللاتيني الموسع والسيريلي)
uint32_t cp_lower(uint32_t cp);

// تصغير سلسلة
std::u32string lower32(const std::u32string& s);

// كشف النطاقات
inline bool is_arabic_cp(uint32_t cp) { return cp >= 0x0600 && cp <= 0x06FF; }
inline bool is_cyrillic_cp(uint32_t cp) { return cp >= 0x0400 && cp <= 0x04FF; }

bool contains_arabic(const std::u32string& s);
bool contains_cyrillic(const std::u32string& s);

} // namespace onyx
