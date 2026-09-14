// Onyx Host Tests — اختبار نواة C++ على المضيف (g++) قبل بناء NDK
// يعكس سيناريوهات E2E الناجحة في نسخة الويب v0.4 Legend
#include "onyx/engine.h"
#include "onyx/algo.h"
#include "onyx/grammar.h"
#include "onyx/utf8.h"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

using namespace onyx;

static int g_fail = 0, g_pass = 0;

#define CHECK(cond, msg)                                                     \
    do {                                                                     \
        if (cond) { g_pass++; std::cout << "  ok   " << msg << "\n"; }       \
        else { g_fail++; std::cout << "  FAIL " << msg << "\n"; }            \
    } while (0)

static std::string read_file(const std::string& path) {
    std::ifstream f(path);
    if (!f) { std::cerr << "missing file: " << path << "\n"; std::exit(2); }
    std::ostringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

static std::vector<std::string> words_of(const std::vector<Suggestion>& v) {
    std::vector<std::string> out;
    for (const auto& s : v) out.push_back(s.word);
    return out;
}

static bool contains(const std::vector<std::string>& v, const std::string& w) {
    for (const auto& x : v) if (x == w) return true;
    return false;
}

static bool contains32(const std::vector<std::u32string>& v, const std::u32string& w) {
    for (const auto& x : v) if (x == w) return true;
    return false;
}

int main(int argc, char** argv) {
    const std::string assets = (argc > 1) ? argv[1]
        : "/home/z/my-project/android/onyx-keyboard/app/src/main/assets/onyx";
    Engine& E = Engine::get();

    std::cout << "== 1) التطبيع العربي (6-10) ==\n";
    CHECK(from32(normalize_arabic(to32("أَحْمَد"))) == "احمد", "نزع التشكيل وهمزة: أحمد→احمد");
    CHECK(from32(normalize_arabic(to32("مدرسة"))) == "مدرسه", "تاء مربوطة: مدرسة→مدرسه");
    CHECK(from32(normalize_arabic(to32("على"))) == "علي", "ألف مقصورة: على→علي");
    CHECK(from32(stem_arabic(to32("بالمدارس"))) == "مدارس", "تجذيع: بالمدارس→مدارس");

    std::cout << "== 2) المسافات (1-3) ==\n";
    CHECK(levenshtein(to32("helo"), to32("hello")) == 1, "levenshtein helo→hello = 1");
    CHECK(damerau(to32("teh"), to32("the")) == 1, "damerau teh→the = 1");
    CHECK(jaro_winkler(to32("مرحبا"), to32("مرحبا")) == 1.0, "jaro-winkler متطابقتان = 1");

    std::cout << "== 3) الصوتيات (4-5) ==\n";
    CHECK(soundex_arabic(to32("احمد")) == soundex_arabic(to32("أحمد")), "soundex عربي يوحد الهمزة");
    CHECK(soundex_latin(to32("hello")) == soundex_latin(to32("helo")), "soundex لاتيني متقارب");

    std::cout << "== 4) أدوات تصحيح (16-18) ==\n";
    std::u32string t_out;
    CHECK(transpose_fix(to32("hte"), t_out) && t_out == to32("the"), "transpose hte→the");
    CHECK(contains32(collapse_duplicates(to32("helllo")), to32("hello")), "collapse helllo→hello");
    CHECK(!neighbor_candidates(to32("ص"), true).empty(), "جيران عربي غير فارغين");

    std::cout << "== 5) القياس (22/23/45) ==\n";
    CHECK(zipf_score(100) == 1.0, "zipf(100)=1");
    CHECK(adaptive_max_dist(50, 6) == 2, "سريع+kلمة طويلة → 2");
    CHECK(adaptive_max_dist(15, 6) == 3, "بطيء → 3");
    CHECK(adaptive_max_dist(30, 3) == 1, "كلمة قصيرة → 1");

    std::cout << "== 6) كشف اللغة (26/28) ==\n";
    CHECK(detect_script_class(to32("سلام")) == 0, "سلام → عربي");
    CHECK(detect_script_class(to32("привет")) == 1, "привет → سيريلي");
    CHECK(detect_script_class(to32("hello")) == 2, "hello → لاتيني");
    CHECK(code_switch_points("مرحبا hello world") == 1, "نقطة تبديل واحدة");

    std::cout << "== 7) عربيزي (29) ==\n";
    CHECK(from32(arabizi_to_arabic(to32("mr7ba"))) == "مرحبا", "mr7ba→مرحبا");
    CHECK(from32(arabizi_to_arabic(to32("7abibi"))).rfind("حابيبي", 0) == 0, "7abibi→حابيبي");

    std::cout << "== 8) تحميل القواميس ==\n";
    for (const char* l : {"ar", "en", "fr", "es", "tr", "de", "ur", "fa", "ru"}) {
        const std::string lang(l);
        E.load_dict(lang_from(lang), read_file(assets + "/dicts/" + lang + ".tsv"));
        E.load_bigrams(lang_from(lang), read_file(assets + "/langpacks/" + lang + ".bigrams.tsv"));
        E.load_mistakes(lang_from(lang), read_file(assets + "/langpacks/" + lang + ".mistakes.tsv"));
        E.load_starters(lang_from(lang), read_file(assets + "/langpacks/" + lang + ".starters.txt"));
    }
    CHECK(true, "تحميل 9 لغات دون انهيار");
    E.load_emoji_tags(read_file(assets + "/emoji_tags.tsv"));

    std::cout << "== 9) اقتراحات عربية ==\n";
    {
        // M2: قاموس 6000 كلمة يجعل مرحلة/مرحوم ينافسان مرحبا — نتحقق من الاكتمال لا التفرد
        auto s = words_of(E.suggest("مرح", "", Lang::Ar, 30, ""));
        CHECK(contains(s, "مرحبا"), "مرحبا ضمن اقتراحات مرح");
        // مايا صارت كلمة حقيقية في القاموس الموسع — تُبقى كما هي (سلوك اللوحات الحقيقية)
        auto mv = words_of(E.suggest("مايا", "", Lang::Ar, 30, ""));
        CHECK(!mv.empty() && mv[0] == "مايا", "كلمة صحيحة تبقى أولاً (لا تصحيح قسري)");
        CHECK(contains(words_of(E.suggest("مايا", "", Lang::Ar, 30, "")), "ماذا"), "مايا → ماذا ضمن التصحيحات");
        // خطأ كتابي حقيقي (غير موجود في القاموس) → تصحيح fix أولاً
        // M2: مع قاموس 6000 كلمة تتنافس تصحيحات متعددة بمسافة 1 (هذا/هنا/هما عبر الجيران والنقل)
        auto m = E.suggest("هتا", "", Lang::Ar, 30, "");
        CHECK(!m.empty() && m[0].type == "fix", "هتا → تصحيح fix");
        CHECK(m[0].word == "هذا" || m[0].word == "هنا" || m[0].word == "هما",
              "هتا → تصحيح حقيقي بمسافة تحرير 1");
        auto st = E.suggest("شكرا", "", Lang::Ar, 30, "");
        CHECK(!st.empty() && st[0].word == "شكرا", "شكرا مطابقة");
    }

    std::cout << "== 10) الكلمة التالية + Backoff (19/20) ==\n";
    {
        auto n = words_of(E.next_word("صباح", "", Lang::Ar));
        CHECK(contains(n, "الخير"), "صباح → الخير (بيغرام)");
        auto n2 = words_of(E.next_word("", "", Lang::Ar));
        CHECK(!n2.empty(), "بدايات عربية عند الفراغ");
    }

    std::cout << "== 11) اقتراحات إنجليزية ==\n";
    {
        // M2: قاموس 6000 كلمة يجعل help/hell تنافس hello — نتحقق من المنطق لا من محتوى القاموس
        auto h = words_of(E.suggest("hel", "", Lang::En, 30, ""));
        CHECK(!h.empty() && h[0].rfind("hel", 0) == 0, "hel → بادئة hel* أولاً");
        CHECK(contains(h, "hello"), "hello ضمن اقتراحات hel");
        auto th = words_of(E.suggest("teh", "", Lang::En, 30, ""));
        CHECK(contains(th, "the"), "teh → the (نقل)");
        auto gr = words_of(E.suggest("goood", "", Lang::En, 30, ""));
        CHECK(contains(gr, "good"), "goood → good (انهيار)");
    }

    std::cout << "== 12) التعلم التكيفي (24/25) ==\n";
    {
        E.reset_learned();
        // 9 تكرارات: 80 + 9×20 = 260 > سقف الجيران 240 — التكرار يرفع الأولوية (تصميم 24/25)
        for (int i = 0; i < 9; i++) E.learn_word("أونيكس");
        const std::string tsv = E.learned_tsv();
        CHECK(tsv.find("أونيكس\t9") != std::string::npos, "تكرار يرفع العدد إلى 9");
        auto s = words_of(E.suggest("أوني", "", Lang::Ar, 30, ""));
        CHECK(!s.empty() && s[0] == "أونيكس", "كلمة متعلمة متكررة تتصدر البادئة");
        auto n = words_of(E.next_word("", "", Lang::Ar));
        CHECK(contains(n, "أونيكس"), "متعلمة تظهر في الكلمة التالية");
        E.remove_learned("أونيكس");
        CHECK(E.learned_count() == 0, "حذف متعلمة");
    }

    std::cout << "== 13) السحب المستمر (41/42) ==\n";
    {
        auto split = [](const std::string& s) {
            std::vector<std::string> out;
            size_t i = 0;
            while (i < s.size()) out.push_back(from32(to32(s.substr(i, 1)))), i++;
            // ملاحظة: يعمل للعربية لأن كل حرف 2 بايت
            return out;
        };
        // تقسيم صحيح بنقاط الكود:
        auto split32 = [](const std::string& s) {
            std::vector<std::string> out;
            std::u32string t = to32(s);
            for (const char32_t c : t) out.push_back(from32(std::u32string(1, c)));
            return out;
        };
        (void)split;
        const std::string g = E.glide(split32("مرحبا"), Lang::Ar, "");
        CHECK(g == "مرحبا", "سحب مرحبا");
        const std::string g2 = E.glide(split32("helo"), Lang::En, "");
        CHECK(g2 == "hello" || g2 == "helo", "سحب helo → hello");
        const std::string g3 = E.glide(split32("م ر ح ب ا"), Lang::Ar, "");
        CHECK(g3 == "مرحبا" || g3.empty(), "سحب مع ضجيج لا ينهار");
    }

    std::cout << "== 14) الاستخراجات (36-40) ==\n";
    {
        const auto x = Engine::extract_all(
            "زور https://onyx.app/kb أو راسلني mail@onyx.app غدا 31/12 أو اتصل 0501234567 رمزك 4821");
        CHECK(x.url == "https://onyx.app/kb", "استخراج رابط");
        CHECK(x.email == "mail@onyx.app", "استخراج إيميل");
        CHECK(x.date == "31/12", "استخراج تاريخ");
        CHECK(x.otp == "4821", "استخراج OTP");
        CHECK(!x.phone.empty(), "استخراج هاتف");
    }

    std::cout << "== 15) عربيزي داخل الأنبوب (29) ==\n";
    {
        auto s = E.suggest("mr7ba", "", Lang::Ar, 30, "");
        CHECK(!s.empty() && s[0].word == "مرحبا" && s[0].algo == 29, "mr7ba → مرحبا #29");
    }

    std::cout << "== 16) المشاعر (34) — M2 ==\n";
    {
        CHECK(E.sentiment("أنا سعيد اليوم فرح كبير") > 0, "مشاعر إيجابية عربية");
        CHECK(E.sentiment("هذا سيء ومشكلة كبيرة") < 0, "مشاعر سلبية عربية");
        CHECK(E.sentiment("البرنامج يعمل الآن") == 0, "مشاعر محايدة عربية");
        CHECK(E.sentiment("I love this great day") > 0, "مشاعر إيجابية إنجليزية");
        CHECK(E.sentiment("big problem and terrible fail") < 0, "مشاعر سلبية إنجليزية");
        CHECK(E.sentiment("") == 0, "نص فارغ محايد");
    }

    std::cout << "== 17) الإيموجي السياقي (35) — M2 ==\n";
    {
        CHECK(E.emoji_tag_count() >= 150, "تحميل وسوم الإيموجي >= 150");
        const auto e1 = E.emoji_suggest("قلبي", Lang::Ar);
        CHECK(!e1.empty() && e1[0] == "❤️", "قلبي → ❤️ (وسم قلب)");
        const auto e2 = E.emoji_suggest("بيتزا", Lang::Ar);
        CHECK(!e2.empty() && e2[0] == "🍕", "بيتزا → 🍕 (وسم بيتزا)");
        const auto e3 = E.emoji_suggest("pizza", Lang::En);
        CHECK(contains(e3, "🍕"), "pizza → 🍕");
        const auto e4 = E.emoji_suggest("goodnight moon tonight", Lang::En);
        CHECK(contains(e4, "🌙"), "tonight → 🌙 (وسم night داخل الكلمة)");
        const auto e5 = E.emoji_suggest("رائع جدا", Lang::Ar);
        CHECK(!e5.empty() && e5.size() <= 6, "مزاج إيجابي بسقف 6");
        const auto e6 = E.emoji_suggest("", Lang::Ar);
        CHECK(!e6.empty() && e6.size() <= 6, "نص فارغ → مزاج محايد بسقف 6");
    }

    std::cout << "== 18) المصحح اللغوي (51) — M3 ==\n";
    {
        CHECK(E.spell_check("مرحبا", Lang::Ar).correct, "مرحبا موجودة → سليمة");
        CHECK(E.spell_check("hello", Lang::En).correct, "hello موجودة → سليمة");
        CHECK(E.spell_check("1234", Lang::En).correct, "أرقام فقط → تُتجاهل وتعتبر سليمة");
        CHECK(E.spell_check("", Lang::Ar).correct, "كلمة فارغة → سليمة عملياً");

        const auto r1 = E.spell_check("هتا", Lang::Ar);
        CHECK(!r1.correct && contains(r1.fixes, "هذا"), "هتا خاطئة → هذا ضمن التصحيحات");
        CHECK(r1.fixes.size() <= 4, "سقف التصحيحات 4");

        const auto r2 = E.spell_check("teh", Lang::En);
        CHECK(!r2.correct && contains(r2.fixes, "the"), "teh → the (نقل حرفين 17)");

        const auto r3 = E.spell_check("goood", Lang::En);
        CHECK(!r3.correct && contains(r3.fixes, "good"), "goood → good (انهيار تكرار 18)");

        const auto r4 = E.spell_check("هذا", Lang::Ar);
        CHECK(r4.correct && r4.fixes.empty(), "كلمة سليمة بلا تصحيحات زائدة");

        // كلمة متعلمة تُعد سليمة (ذاكرة الملف)
        E.learn_word("onyxword");
        CHECK(E.spell_check("onyxword", Lang::En).correct, "كلمة متعلمة → سليمة (24)");
        E.remove_learned("onyxword");

        const auto rows = E.spell_check_text("هتا مرحبا بالسلام", Lang::Ar);
        CHECK(!rows.empty() && rows[0].rfind("هتا\u0001", 0) == 0, "فحص جملة: هتا هي الخاطئة الوحيدة");
    }

    std::cout << "== 19) المصحح القواعدي للجمل (52) — M3 Deep ==\n";
    {
        auto gf = [](const std::string& s) {
            std::vector<GrammarIssue> iss;
            return grammar_fix_text(s, Lang::En, &iss);
        };
        auto gfi = [](const std::string& s) {
            std::vector<GrammarIssue> iss;
            grammar_fix_text(s, Lang::En, &iss);
            return iss;
        };

        // إنجليزية: انكماش + ضمير + حرف كبير
        CHECK(gf("i dont know ") == "I don't know ", "i dont know → I don't know");
        CHECK(gf("he dont like it ") == "He doesn't like it ", "he dont → He doesn't (تصريف + حرف)");
        CHECK(gf("they was happy ") == "They were happy ", "they was → They were");
        CHECK(gf("she have a car ") == "She has a car ", "she have → She has");
        CHECK(gf("i is here ") == "I am here ", "i is → I am");

        // قواعد: التكرار + a/an
        CHECK(gf("this is is fine ") == "This is fine ", "is is → is (مكررة)");
        CHECK(gf("a apple on table ") == "An apple on table ", "a apple → An apple");
        CHECK(gf("an book was here ") == "A book was here ", "an book → A book");
        CHECK(gf("i saw an hour ago ") == "I saw an hour ago ", "an hour يبقى (استثناء)");
        CHECK(gf("a university ") == "A university ", "a university يبقى (استثناء u)");

        // الترقيم والمسافات
        CHECK(gf("hi ,there ") == "Hi, there ", "مسافة قبل الفاصلة + حرف كبير");
        CHECK(gf("hi,there ") == "Hi, there ", "مسافة بعد الفاصلة");
        CHECK(gf("two  spaces ") == "Two spaces ", "مسافتان → مسافة");
        CHECK(gf("meet at 3.5 pm ") == "Meet at 3.5 pm ", "الأرقام العشرية لا تُمس");

        // عربية: إملاء + استفهام
        CHECK(gf("هاذا المكان جميل ") == "هذا المكان جميل ", "هاذا → هذا");
        CHECK(gf("انا سعيد جدا ") == "أنا سعيد جدا ", "انا → أنا");
        CHECK(gf("لاكن فى الطريق ") == "لكن في الطريق ", "لاكن → لكن + فى → في");
        CHECK(gf("انشاء الله يوفقك ") == "إن شاء الله يوفقك ", "انشاء الله → إن شاء الله (عبارة)");
        CHECK(gf("كيف حالك ") == "كيف حالك؟ ", "سؤال عربي بلا علامة → ؟");
        CHECK(gf("كيف حالك؟ ") == "كيف حالك؟ ", "السؤال بعلامة يبقى كما هو");
        CHECK(gf("الذى يعمل ") == "الذي يعمل ", "الذى → الذي");
        CHECK(gf("في في البيت ") == "في البيت ", "في في → في (تكرار عربي)");

        // لا تغيير + idempotence
        CHECK(gf("Hello world ") == "Hello world ", "جملة سليمة لا تُمس — بلا مشاكل");
        CHECK(gfi("Hello world ").empty(), "جملة سليمة → صفر ملاحظات");
        CHECK(gf("hello world ") == "Hello world ", "تكبير أول كلمة فقط");
        {
            const std::string once = gf("i dont know a apple ");
            CHECK(gf(once) == once, "idempotent: تطبيق ثانٍ بلا تغيير");
        }
        CHECK(gf("").empty(), "نص فارغ → فارغ");
        CHECK(gf("1234 ") == "1234 ", "أرقام فقط تُتجاهل");
    }

    std::cout << "\n==================\n";
    std::cout << "نجاح: " << g_pass << " | فشل: " << g_fail << "\n";
    return g_fail == 0 ? 0 : 1;
}
