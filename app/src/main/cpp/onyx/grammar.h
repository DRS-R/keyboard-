// Onyx Grammar — خوارزمية 52: المصحح القواعدي للجمل (M3 Deep)
// قواعد حتمية آمنة على مستوى الجملة: إملاء شائع + قواعد تصريف + صياغة + ترقيم + أحرف
// يعمل كله محلياً — بلا قواميس خارجية ولا إنترنت، وجداوله مدمجة في الثنائية.
#pragma once
#include "engine.h"

#include <string>
#include <vector>

namespace onyx {

struct GrammarIssue {
    std::string cat;    // إملاء | قواعد | صياغة | ترقيم | أحرف
    std::string before; // المقتطف قبل الإصلاح (للعرض)
    std::string after;  // المقتطف بعد الإصلاح (للعرض)
    std::string hint;   // شرح قصير
};

/**
 * خوارزمية 52 — يصحح الجملة كاملة ويُرجع النص المصحح.
 * issues (إن لم تكن null) تُملأ بكل قاعدة مُطبَّقة — حتمي وقابل للتكرار:
 * تطبيقه على ناتجه يعطي الناتج نفسه بلا مشاكل جديدة.
 */
std::string grammar_fix_text(const std::string& text_utf8, Lang lang,
                             std::vector<GrammarIssue>* issues);

} // namespace onyx
