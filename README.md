# Onyx Keyboard ⚡ | لوحة مفاتيح أونيكس الذكية

<div align="center">

![Onyx Keyboard Banner](docs/images/hero_banner.jpg)

**لوحة مفاتيح أندرويد ذكية فائقة السرعة، مبنية بنواة C++17 أصلية (NDK) ومعمارية Kotlin حديثة مع تركيز كامل على الخصوصية بصلاحيات صفرية.**

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen?style=for-the-badge&logo=android)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin_%7C_C++17_(NDK)-blue?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Privacy](https://img.shields.io/badge/Privacy-100%25_Offline_%7C_Zero_Permissions-success?style=for-the-badge&logo=shield)](https://github.com)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

[**العربية**](#-عن-المشروع) • [**English Overview**](#-english-overview) • [**المميزات**](#-المميزات-الرئيسية) • [**المعمارية**](#-معمارية-المشروع) • [**طريقة البناء**](#-طريقة-البناء-والتشغيل)

</div>

---

## 📸 لقطات شاشة من داخل التطبيق (Screenshots)

<div align="center">
  <table>
    <tr>
      <td align="center" width="33%">
        <b>لوحة المفاتيح وشريط التنبؤ الذكي</b><br/><br/>
        <img src="docs/images/keyboard_preview.jpg" alt="Keyboard Preview" width="100%"/>
      </td>
      <td align="center" width="33%">
        <b>لوحة الإعدادات وتخصيص Material You</b><br/><br/>
        <img src="docs/images/settings_preview.jpg" alt="Settings Dashboard" width="100%"/>
      </td>
      <td align="center" width="33%">
        <b>الوضع العائم ووضع اليد الواحدة</b><br/><br/>
        <img src="docs/images/features_showcase.jpg" alt="Floating Mode & Features" width="100%"/>
      </td>
    </tr>
  </table>
</div>

---

## 🌟 عن المشروع (About The Project)

تم تصميم **Onyx Keyboard** لتقديم تجربة كتابة رائدة على أجهزة أندرويد توازن بين:
1. **السرعة والذكاء الفائق**: عبر نقل كامل عبء المعالجة اللغوية، التنبؤ، والتصحيح إلى محرك C++17 أصلي فائق الأداء (`libonyx_engine.so`) يعمل مباشرة على عتاد الجهاز بزمن استجابة أقل من 1 ميلي ثانية.
2. **الخصوصية المطلقة (Zero-Permission)**: التطبيق **لا يطلب صلاحية الإنترنت نهائياً**، ولا صلاحية الميكروفون المباشرة، ولا صلاحية قراءة الملفات، ولا صلاحية نافذة النظام `SYSTEM_ALERT_WINDOW`. كل نقرة، كلمة، وسجل حافظة يبقى مشفراً ومحلياً داخل جهازك 100%.
3. **التصميم العصري**: توافق تام مع منظومة **Material You (Dynamic Color)** لاستخراج ألوان اللوحة تلقائياً من خلفية الشاشة بنظام Material 3، مع دعم النمط العائم الذكي، ووضع اليد الواحدة، والتحكم السلس بالأبعاد.

---

## 🚀 المميزات الرئيسية (Key Features)

### 1. 🧠 نواة الذكاء اللغوي (C++17 Native Engine)
* **52 خوارزمية ذكاء لغوي**: مختبرة بالكامل على مستوى المضيف (Host Unit Tests).
* **شجرة بادئات Trie مضغوطة**: بحث واسترجاع واقتراح الكلمات في أجزاء من الميلي ثانية مع استهلاك بالغ الانخفاض للذاكرة العشوائية (RAM).
* **تصحيح إملائي متقدم (Weighted Levenshtein)**: مراعاة المسافات بين المفاتيح الفيزيائية على الشاشة عند اقتراح التصحيحات للأخطاء الطباعية.
* **التنبؤ بالكلمة التالية (N-Gram & Markov Chains)**: توقع الكلمات بناءً على سياق الجملة، مع التعلم التكيفي الذاتي من أسلوب كتابتك دون إرسال أي حرف خارج الجهاز.
* **مصحح القواعد اللغوية والجمل**:
  - تصحيح همزات الوصل والقطع (إ/أ/ا).
  - معالجة التكرار الزائد للأحرف الناتجة عن السرعة.
  - ضبط علامات الترقيم، الفواصل، والمسافات تلقائياً.
* **استخراج البيانات التلقائي (Smart Entity Extraction)**: التعرف التلقائي الذكي على الروابط (URLs)، رموز التحقق (OTP)، أرقام الهواتف، والبريد الإلكتروني لإتاحة نسخها أو استخدامها بنقرة واحدة.

### 2. 🪟 اللوحة العائمة ووضع اليد الواحدة (Floating & Ergonomic Modes)
* **لوحة عائمة قابلة للتحريك والسحب**: حرية نقل لوحة المفاتيح إلى أي مكان على الشاشة.
* **تحكم دقيق بالحجم (40% إلى 90%)**: شريط تمرير سلس ومقبض زوايا تفاعلي لتكبير وتصغير اللوحة بما يناسب راحة اليد.
* **نقاط ضبط سريعة (Presets)**: نقر مزدوج للتنقل الفوري بين الأحجام القياسية (50%، 62%، 78%).
* **وضع اليد الواحدة (One-Handed Mode)**: إزاحة اللوحة إلى اليمين أو اليسار لتسهيل الكتابة بيد واحدة على الشاشات الكبيرة والهواتف اللوحية.

### 3. 🎨 التخصيص والمظهر (Themes & Material You)
* **ألوان النظام الديناميكية (Material You)**: تكيف مظهر اللوحة تلقائياً مع نظام ألوان خلفية جهاز المستخدم على أندرويد 12 وما بعده.
* **حزم ثيمات جاهزة فائقة التباين**:
  - Onyx Black (AMOLED خالص لتوفير البطارية).
  - Midnight Blue (كحلي ليلي أنيق).
  - Emerald Green (أخضر زمردي مريح للعين).
  - Amethyst Purple (بنفسجي ملكي هادئ).
  - Sunset & Ruby (ألوان دافئة مفعمة بالحيوية).
* **أصوات نقرات ميكانيكية وواقعية**: حزم أصوات (Modern, Tactile, Typewriter, Mechanical) مع شريط تحكم بالشدة واهتزاز لمسي (Haptic Feedback) دقيق.

### 4. 📋 الحافظة الذكية والإدخال الصوتي (Productivity Tools)
* **الحافظة التاريخية (Offline Clipboard)**: حفظ النصوص المنسوخة محلياً مع إمكانية التثبيت، والمسح بنقرة واحدة.
* **الإدخال الصوتي بدون صلاحيات (Zero-Permission Voice Bridge)**: الكتابة بالصوت عن طريق استدعاء محرك التعرف الصوتي الموثوق بالنظام دون حاجة التطبيق نفسه لطلب إذن الميكروفون الحساس.
* **لوحة إيموجي ذكية وتنبؤ سياقي**: اقتراح الرموز التعبيرية المناسبة لسياق الكلام، مع لوحة إيموجي كاملة مقسمة ومصنفة مع خاصية البحث الفوري.
* **طبقة تشكيل كاملة وزخرفة النصوص (Text Decorator)**: أكثر من 60 نمطاً خطياً وزخرفياً للحروف والرموز بضغطة زر.

### 5. 🛡️ مدقق أمان ونزاهة الروابط (DownloadUrlValidator)
* مكتبة مساعدة متكاملة لفحص الروابط المشفرة والتحقق من النزاهة قبل التنزيل:
  - الحماية التلقائية من ثغرات تزوير الطلبات من جانب الخادم (SSRF).
  - حظر العناوين المحلية والشبكات الخاصة (`127.0.0.1`, `10.0.0.0/8`, `192.168.0.0/16`, `169.254.169.254`, `localhost`).
  - منع هجمات حقن الترويسات (CRLF) ومحاولات اختراق المسارات (`..`, `%2e%2e`).
  - التحقق من الهاش والتجزئة (SHA-256 Checksum) بمقارنة زمنية آمنة ضد هجمات التوقيت (Timing Attacks).

---

## 🏗️ معمارية المشروع (Project Architecture)

تم بناء المشروع وفق معمارية فصل المهام الصارمة (Clean Separation of Concerns):

```
Onyx-Keyboard/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── cpp/                    # 🚀 نواة الذكاء بلغة C++17
│   │   │   │   ├── onyx/
│   │   │   │   │   ├── algo.cpp / .h   # خوارزميات التنبؤ وMarkov Models
│   │   │   │   │   ├── engine.cpp / .h # المحرك المركزي واستخراج البيانات
│   │   │   │   │   ├── grammar.cpp/.h  # مصحح القواعد والجمل وعلامات الترقيم
│   │   │   │   │   ├── trie.cpp / .h   # شجرة الكلمات السريعة (Trie)
│   │   │   │   │   └── utf8.cpp / .h   # معالجة النصوص وحروف اليونيكود
│   │   │   │   ├── onyx_jni.cpp        # جسر الربط JNI بين C++ و Kotlin
│   │   │   │   └── CMakeLists.txt      # إعدادات بناء NDK وCMake
│   │   │   │
│   │   │   ├── java/com/onyx/keyboard/
│   │   │   │   ├── data/               # إدارة البيانات، الحافظة والتفضيلات (Prefs)
│   │   │   │   ├── engine/             # واجهة استدعاء محرك OnyxEngine
│   │   │   │   ├── ime/                # خدمة لوحة المفاتيح والتحكم العائم (OnyxImeService)
│   │   │   │   ├── model/              # نماذج المفاتيح، الثيمات، واللغات
│   │   │   │   ├── spell/              # مدقق الإملاء النظامي (OnyxSpellCheckerService)
│   │   │   │   ├── ui/                 # واجهات الإعدادات وMaterial You
│   │   │   │   └── util/               # أدوات مساعدة وتدقيق أمان الروابط (DownloadUrlValidator)
│   │   │   │
│   │   │   ├── assets/onyx/dicts/      # قواميس موسعة (العربية + 8 لغات عالمية)
│   │   │   └── AndroidManifest.xml     # مانيفست بدون أي صلاحيات خطيرة أو إنترنت
│   │   │
│   │   └── test/java/                  # اختبارات الوحدة واختبارات الأمان
│   └── build.gradle.kts
├── docs/
│   └── images/                         # صور وتصميمات المشروع لملف التوثيق
├── gradle/
├── metadata.json
├── LICENSE
└── README.md
```

---

## 🛠️ طريقة البناء والتشغيل (Build & Setup)

### المتطلبات الأساسية (Prerequisites)
- **Android Studio** (Koala / Ladybug أو أحدث)
- **Android SDK** (API 34 أو أحدث، الحد الأدنى API 26)
- **Android NDK** (الإصدار 26.x أو أحدث)
- **CMake** (الإصدار 3.22.1 أو أحدث)
- **JDK 17**

### خطوات التثبيت والبناء عبر سطر الأوامر (Command Line)
```bash
# 1. استنساخ المستودع
git clone https://github.com/your-username/onyx-keyboard.git
cd onyx-keyboard

# 2. تشغيل اختبارات الوحدة المحلية
./gradlew testDebugUnitTest

# 3. بناء نسخة APK للتطوير (Debug APK)
./gradlew assembleDebug

# 4. تثبيت التطبيق على جهازك المتصل أو المحاكي
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔒 الخصوصية والأمان (Zero-Permission Proof)

يلتزم مشروع **Onyx Keyboard** بأعلى معايير الخصوصية الممكنة:
- ❌ **لا يوجد إذن `android.permission.INTERNET`** في ملف المانيفست. يستحيل على التطبيق إرسال أي بايت عبر الشبكة.
- ❌ **لا يوجد إذن قراءة الحافظة الخلفية بدون إذن المستخدم**.
- ❌ **لا يوجد إذن تسجيل صوت `RECORD_AUDIO` مباشر**؛ الإدخال الصوتي يعتمد حصرياً على واجهة نظام أندرويد المعزولة.
- ❌ **لا يوجد أي كود تتبع (Analytics) أو مكتبات إعلانية (Ads)**.

---

<div dir="ltr">

## 🌐 English Overview

**Onyx Keyboard** is a high-performance, privacy-first smart keyboard for Android powered by an offline native C++17 engine (`libonyx_engine.so`).

### Key Highlights
- **100% Offline & Zero-Permission**: No internet permission, no microphone permission, no tracking, no ads.
- **Native C++17 Prediction Engine**: Packed with 52 linguistic algorithms, weighted Levenshtein spell correction, and N-gram predictions under 1ms.
- **Floating & Ergonomic Modes**: Freely draggable floating card mode with fine-grained scaling (40%-90%), double-tap presets (50%, 62%, 78%), and one-handed layouts.
- **Material You Dynamic Theming**: Real-time palette extraction from system wallpaper and multiple high-contrast AMOLED themes.
- **Zero-Permission Voice Bridge**: Secure voice-to-text input via standard platform intents.
- **Built-in Security Validator**: Comprehensive URL integrity and SSRF-safe download validator.

</div>

---

## 📜 الترخيص (License)

هذا المشروع مرخص تحت رخصة **MIT** المفتوحة المصدر - راجع ملف [LICENSE](LICENSE) لمزيد من التفاصيل.

---

<div align="center">
  صنع بكل إتقان واعتزاز باللغة العربية والبرمجيات المفتوحة المصدر ⚡
</div>
