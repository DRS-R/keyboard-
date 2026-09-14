// Onyx JNI — جسر Kotlin ↔ محرك C++
#include "onyx/engine.h"
#include "onyx/grammar.h"

#include <jni.h>
#include <string>
#include <vector>

using onyx::Engine;
using onyx::Lang;
using onyx::lang_from;
using onyx::lang_code;
using onyx::Suggestion;

static std::string jstr_to_std(JNIEnv* env, jstring j) {
    if (j == nullptr) return "";
    const char* chars = env->GetStringUTFChars(j, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(j, chars);
    return out;
}

static jstring std_to_jstr(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

// الفصل الميداني داخل السلسلة الواحدة (كلمة\u0001نوع\u0001رقم_الخوارزمية)
static const char US = '\u0001';

static jobjectArray suggestions_to_java(JNIEnv* env, const std::vector<Suggestion>& out) {
    jclass cls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(out.size()), cls, nullptr);
    for (size_t i = 0; i < out.size(); i++) {
        std::string line = out[i].word;
        line += US;
        line += out[i].type;
        line += US;
        line += std::to_string(out[i].algo);
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), std_to_jstr(env, line));
    }
    env->DeleteLocalRef(cls);
    return arr;
}

static std::vector<std::string> jarr_to_vec(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> out;
    if (arr == nullptr) return out;
    const jsize n = env->GetArrayLength(arr);
    for (jsize i = 0; i < n; i++) {
        jstring s = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        out.push_back(jstr_to_std(env, s));
        env->DeleteLocalRef(s);
    }
    return out;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeSetConfig(JNIEnv*, jclass, jint offset) {
    Engine::get().set_config(static_cast<int>(offset));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLoadDict(JNIEnv* env, jclass,
                                                        jstring lang, jstring tsv) {
    Engine::get().load_dict(lang_from(jstr_to_std(env, lang)), jstr_to_std(env, tsv));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLoadBigrams(JNIEnv* env, jclass,
                                                           jstring lang, jstring tsv) {
    Engine::get().load_bigrams(lang_from(jstr_to_std(env, lang)), jstr_to_std(env, tsv));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLoadMistakes(JNIEnv* env, jclass,
                                                            jstring lang, jstring tsv) {
    Engine::get().load_mistakes(lang_from(jstr_to_std(env, lang)), jstr_to_std(env, tsv));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLoadStarters(JNIEnv* env, jclass,
                                                            jstring lang, jstring txt) {
    Engine::get().load_starters(lang_from(jstr_to_std(env, lang)), jstr_to_std(env, txt));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeSetLearnedTsv(JNIEnv* env, jclass, jstring tsv) {
    Engine::get().set_learned_tsv(jstr_to_std(env, tsv));
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeGetLearnedTsv(JNIEnv* env, jclass) {
    return std_to_jstr(env, Engine::get().learned_tsv());
}

JNIEXPORT jint JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLearnWord(JNIEnv* env, jclass, jstring word) {
    return static_cast<jint>(Engine::get().learn_word(jstr_to_std(env, word)));
}

JNIEXPORT jint JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeRemoveLearned(JNIEnv* env, jclass, jstring word) {
    return static_cast<jint>(Engine::get().remove_learned(jstr_to_std(env, word)));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeResetLearned(JNIEnv*, jclass) {
    Engine::get().reset_learned();
}

JNIEXPORT jobjectArray JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeSuggest(JNIEnv* env, jclass,
                                                       jstring word, jstring prev,
                                                       jstring lang, jint wpm, jstring history) {
    const std::vector<Suggestion> out = Engine::get().suggest(
        jstr_to_std(env, word), jstr_to_std(env, prev),
        lang_from(jstr_to_std(env, lang)), static_cast<int>(wpm),
        jstr_to_std(env, history));
    return suggestions_to_java(env, out);
}

JNIEXPORT jobjectArray JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeNextWord(JNIEnv* env, jclass,
                                                        jstring prev, jstring prev2, jstring lang) {
    const std::vector<Suggestion> out = Engine::get().next_word(
        jstr_to_std(env, prev), jstr_to_std(env, prev2),
        lang_from(jstr_to_std(env, lang)));
    return suggestions_to_java(env, out);
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeGlide(JNIEnv* env, jclass,
                                                     jobjectArray seq, jstring lang, jstring prev) {
    const std::string res = Engine::get().glide(
        jarr_to_vec(env, seq), lang_from(jstr_to_std(env, lang)), jstr_to_std(env, prev));
    return std_to_jstr(env, res);
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeExtract(JNIEnv* env, jclass, jstring text) {
    const Engine::Extract e = Engine::extract_all(jstr_to_std(env, text));
    std::string line = e.url;
    line += US; line += e.phone;
    line += US; line += e.email;
    line += US; line += e.otp;
    line += US; line += e.date;
    return std_to_jstr(env, line);
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeNormalize(JNIEnv* env, jclass,
                                                         jstring text, jstring lang) {
    return std_to_jstr(env, Engine::get().normalize(jstr_to_std(env, text),
                                                    lang_from(jstr_to_std(env, lang))));
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeArabizi(JNIEnv* env, jclass, jstring word) {
    return std_to_jstr(env, Engine::get().arabizi(jstr_to_std(env, word)));
}

JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLatinize(JNIEnv* env, jclass, jstring word) {
    return std_to_jstr(env, Engine::get().latinize(jstr_to_std(env, word)));
}

JNIEXPORT jint JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeDetectScript(JNIEnv* env, jclass, jstring text) {
    return static_cast<jint>(onyx::detect_script_class(onyx::to32(jstr_to_std(env, text))));
}

JNIEXPORT jint JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeCodeSwitch(JNIEnv* env, jclass, jstring text) {
    return static_cast<jint>(onyx::code_switch_points(jstr_to_std(env, text)));
}

JNIEXPORT void JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeLoadEmojiTags(JNIEnv* env, jclass, jstring tsv) {
    Engine::get().load_emoji_tags(jstr_to_std(env, tsv));
}

JNIEXPORT jint JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeSentiment(JNIEnv* env, jclass, jstring text) {
    return static_cast<jint>(Engine::get().sentiment(jstr_to_std(env, text)));
}

JNIEXPORT jobjectArray JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeEmojiSuggest(JNIEnv* env, jclass,
                                                             jstring text, jstring lang) {
    const std::vector<std::string> out = Engine::get().emoji_suggest(
        jstr_to_std(env, text), lang_from(jstr_to_std(env, lang)));
    jclass cls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(out.size()), cls, nullptr);
    for (size_t i = 0; i < out.size(); i++) {
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), std_to_jstr(env, out[i]));
    }
    env->DeleteLocalRef(cls);
    return arr;
}

// M3 Legend — المصحح اللغوي (51): "1" سليمة | "0\u0001صواب1\u0001صواب2…"
JNIEXPORT jstring JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeSpellCheck(JNIEnv* env, jclass,
                                                           jstring word, jstring lang) {
    const Engine::SpellResult r = Engine::get().spell_check(
        jstr_to_std(env, word), lang_from(jstr_to_std(env, lang)));
    std::string line = r.correct ? "1" : "0";
    if (!r.correct) {
        for (const std::string& f : r.fixes) { line += US; line += f; }
    }
    return std_to_jstr(env, line);
}

// M3 Legend — فحص نص كامل (لخدمة المصحح النظامية): سطر لكل كلمة خاطئة
JNIEXPORT jobjectArray JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeSpellCheckText(JNIEnv* env, jclass,
                                                               jstring text, jstring lang) {
    const std::vector<std::string> out = Engine::get().spell_check_text(
        jstr_to_std(env, text), lang_from(jstr_to_std(env, lang)));
    jclass cls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(out.size()), cls, nullptr);
    for (size_t i = 0; i < out.size(); i++) {
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), std_to_jstr(env, out[i]));
    }
    env->DeleteLocalRef(cls);
    return arr;
}

// M3 Deep — المصحح القواعدي للجمل (52): السطر الأول = النص المصحح، ثم "فئة\u0001قبل\u0001بعد\u0001شرح"
JNIEXPORT jobjectArray JNICALL
Java_com_onyx_keyboard_engine_OnyxEngine_nativeGrammarFix(JNIEnv* env, jclass,
                                                           jstring text, jstring lang) {
    std::vector<onyx::GrammarIssue> issues;
    const std::string fixed = onyx::grammar_fix_text(
        jstr_to_std(env, text), lang_from(jstr_to_std(env, lang)), &issues);
    jclass cls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(issues.size() + 1), cls, nullptr);
    env->SetObjectArrayElement(arr, 0, std_to_jstr(env, fixed));
    for (size_t i = 0; i < issues.size(); i++) {
        std::string line = issues[i].cat;
        line += US; line += issues[i].before;
        line += US; line += issues[i].after;
        line += US; line += issues[i].hint;
        env->SetObjectArrayElement(arr, static_cast<jsize>(i + 1), std_to_jstr(env, line));
    }
    env->DeleteLocalRef(cls);
    return arr;
}

} // extern "C"
