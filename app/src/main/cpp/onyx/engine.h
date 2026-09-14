// Onyx Engine — محرك التنبؤ المحلي (ترحيل حرفي من src/lib/engine/prediction.ts)
// أنبوب getSuggestions بمراحله الـ 11 + backoffNext + matchSwipe (F1) + التعلم التكيفي
#pragma once
#include "trie.h"
#include "utf8.h"
#include "algo.h"

#include <string>
#include <vector>
#include <unordered_map>

namespace onyx {

enum class Lang { Ar, En, Fr, Es, Tr, De, Ur, Fa, Ru };

// "ar" → Lang (الافتراضي En)
Lang lang_from(const std::string& code);
const char* lang_code(Lang l);

struct Suggestion {
    std::string word;  // الكلمة للعرض (شكلها الأصلي)
    std::string type;  // next | prefix | fix | learned
    int algo = 0;      // رقم الخوارزمية المنتجة — للعرض التعليمي
};

class Engine {
public:
    static Engine& get();

    void set_config(int correction_offset); // انحراف شدة التصحيح -1..1

    // تحميل الحزم — TSV بسيطة "a\tb" لكل سطر
    void load_dict(Lang lang, const std::string& tsv);        // كلمة\tتردد
    void load_bigrams(Lang lang, const std::string& tsv);     // مفتاح\tو1,و2,و3
    void load_mistakes(Lang lang, const std::string& tsv);    // خطأ\tصواب
    void load_starters(Lang lang, const std::string& txt);    // سطر لكل بادئة

    // التعلم التكيفي — التخزين الدائم طرف Kotlin، وهذه نسخة العمل الساخنة
    void set_learned_tsv(const std::string& tsv);
    std::string learned_tsv() const;
    int learn_word(const std::string& word_utf8);   // يعيد عدد الكلمات المتعلمة
    int remove_learned(const std::string& word_utf8);
    void reset_learned();
    int learned_count() const { return static_cast<int>(learned_.size()); }

    // الأنابيب الرئيسية
    std::vector<Suggestion> suggest(const std::string& word_utf8, const std::string& prev_utf8,
                                    Lang active, int wpm, const std::string& history_us);
    std::vector<Suggestion> next_word(const std::string& prev_utf8, const std::string& prev2_utf8,
                                      Lang active);
    // السحب المستمر: seq = حروف المسار بالترتيب (حرف لكل عنصر)
    std::string glide(const std::vector<std::string>& seq_chars, Lang active,
                      const std::string& prev_utf8);

    // أدوات مساعدة مكشوفة للـ JNI
    std::string normalize(const std::string& text, Lang lang);
    std::string arabizi(const std::string& word);
    std::string latinize(const std::string& word);

    // استخراجات الشرائح الذكية (36-40): الحقول مفصولة بـ \u0001
    struct Extract { std::string url, phone, email, otp, date; };
    static Extract extract_all(const std::string& text);

    // المشاعر والإيموجي السياقي (34-35) — M2 Nova
    void load_emoji_tags(const std::string& tsv);              // إيموجي<TAB>وسوم
    int sentiment(const std::string& text_utf8) const;         // 34: درجة المشاعر -n..+n
    std::vector<std::string> emoji_suggest(const std::string& text_utf8, Lang active) const;  // 35

    // المصحح اللغوي (51) — M3 Legend: فحص كلمة مكتملة (لا كلمة جارية)
    struct SpellResult {
        bool correct;                              // موجودة في القاموس أو المتعلم
        std::vector<std::string> fixes;            // أفضل التصحيحات (حتى 4) إن لم تكن صحيحة
    };
    SpellResult spell_check(const std::string& word_utf8, Lang active);
    // فحص جملة كاملة — يُرجع أسطراً: خطأ\u0001تصحيح1\u0001تصحيح2 (سطر لكل كلمة خاطئة)
    std::vector<std::string> spell_check_text(const std::string& text_utf8, Lang active);

private:
    struct Entry { std::u32string word; int freq; };
    struct LangPack {
        std::vector<Entry> entries;
        std::unordered_map<std::u32string, std::vector<int>> norm_idx;
        std::unordered_map<std::u32string, std::vector<std::u32string>> bigrams;  // مفتاح صغير
        std::unordered_map<std::u32string, std::vector<std::u32string>> trigrams; // "أ ب" صغير
        std::unordered_map<std::u32string, std::u32string> mistakes;              // خام + صغير + مطبع
        std::vector<std::u32string> starters;
        Trie trie;
    };

    LangPack& pack(Lang l);
    static std::u32string norm_for(Lang lang, const std::u32string& w);
    static std::string soundex_for(Lang lang, const std::u32string& w);
    struct BackoffResult { std::vector<std::u32string> words; int source; }; // 0=tri 1=bi 2=uni
    BackoffResult backoff_next(const std::vector<std::u32string>& history, LangPack& P);

    LangPack packs_[9];
    std::unordered_map<std::u32string, int> learned_;
    int correction_offset_ = 0;
    // وسوم الإيموجي بترتيب الملف (للاستجابة الحتمية) — إيموجي UTF-8 → وسوم مطبعة
    std::vector<std::pair<std::string, std::vector<std::u32string>>> emoji_tags_;
public:
    int emoji_tag_count() const { return static_cast<int>(emoji_tags_.size()); }
};

} // namespace onyx
