package com.onyx.keyboard.ime

/**
 * Decor — ترحيل حرفي لمحرك الزخرفة من engine/decorate.ts
 * 18 نمطاً: 10 يونيكود لاتيني + 8 زخارف عربية.
 */
object Decor {

    data class Style(
        val id: String,
        val name: String,
        val arabic: Boolean,
        val preview: String,
        val apply: (String) -> String,
    )

    private const val L_ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private const val B_UPPER = "𝗔𝗕𝗖𝗗𝗘𝗙𝗚𝗛𝗜𝗝𝗞𝗟𝗠𝗡𝗢𝗣𝗤𝗥𝗦𝗧𝗨𝗩𝗪𝗫𝗬𝗭"
    private const val B_LOWER = "𝗮𝗯𝗰𝗱𝗲𝗳𝗴𝗵𝗶𝗷𝗸𝗹𝗺𝗻𝗼𝗽𝗾𝗿𝘀𝘁𝘂𝘃𝘄𝘅𝘆𝘇"
    private const val I_UPPER = "𝘈𝘉𝘊𝘋𝘌𝘍𝘎𝘏𝘐𝘑𝘒𝘓𝘔𝘕𝘖𝘗𝘘𝘙𝘚𝘛𝘜𝘝𝘞𝘟𝘠𝘡"
    private const val I_LOWER = "𝘢𝘣𝘤𝘥𝘦𝘧𝘨𝘩𝘪𝘫𝘬𝘭𝘮𝘯𝘰𝘱𝘲𝘳𝘴𝘵𝘶𝘷𝘸𝘹𝘺𝘻"
    private const val BI_UPPER = "𝘼𝘽𝘾𝘿𝙀𝙁𝙂𝙃𝙄𝙅𝙆𝙇𝙈𝙉𝙊𝙋𝙌𝙍𝙎𝙏𝙐𝙑𝙒𝙓𝙔𝙕"
    private const val BI_LOWER = "𝙖𝙗𝙘𝙙𝙚𝙛𝙜𝙝𝙞𝙟𝙠𝙡𝙢𝙣𝙤𝙥𝙦𝙧𝙨𝙩𝙪𝙫𝙬𝙭𝙮𝙯"
    private const val MONO_UPPER = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉"
    private const val MONO_LOWER = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣"
    private const val CIRC_UPPER = "ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ"
    private const val CIRC_LOWER = "ⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ"
    private const val SQ_UPPER = "🅰🅱🅲🅳🅴🅵🅶🅷🅸🅹🅺🅻🅼🅽🅾🅿🆀🆁🆂🆃🆄🆅🆆🆇🆈🆉"
    private const val FW_LOWER = "ａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ"
    private const val FW_UPPER = "ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺ"
    private const val FRK_UPPER = "𝔄𝔅ℭ𝔇𝔈𝔉𝔊ℌℑ𝔍𝔎𝔏𝔐𝔑𝔒𝔓𝔔ℜ𝔖𝔗𝔘𝔙𝔚𝔛𝔜ℨ"
    private const val FRK_LOWER = "𝔞𝔟𝔠𝔡𝔢𝔣𝔤𝔥𝔦𝔧𝔨𝔩𝔪𝔫𝔬𝔭𝔮𝔯𝔰𝔱𝔲𝔳𝔴𝔵𝔶𝔷"
    private const val DS_UPPER = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ"
    private const val DS_LOWER = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"

    private fun mapLatin(text: String, upper: String, lower: String): String = buildString {
        for (c in text) {
            val i = L_ALPHA.indexOf(c)
            if (i < 0) append(c)
            else if (i < 26) append(upper[i])
            else append(lower[i - 26])
        }
    }

    val STYLES: List<Style> = listOf(
        Style("bold", "عريض", false, "𝗢𝗻𝘆𝘅") { t -> mapLatin(t, B_UPPER, B_LOWER) },
        Style("italic", "مائل", false, "𝘖𝘯𝘺𝘹") { t -> mapLatin(t, I_UPPER, I_LOWER) },
        Style("bolditalic", "عريض مائل", false, "𝙊𝙣𝙮𝙭") { t -> mapLatin(t, BI_UPPER, BI_LOWER) },
        Style("mono", "برمجي", false, "𝙾𝚗𝚢𝚡") { t -> mapLatin(t, MONO_UPPER, MONO_LOWER) },
        Style("circled", "دائري", false, "Ⓞⓝⓨⓧ") { t -> mapLatin(t, CIRC_UPPER, CIRC_LOWER) },
        Style("squared", "مربع", false, "🅾🅽🆈🆇") { t -> mapLatin(t, SQ_UPPER, SQ_UPPER) },
        Style("fullwidth", "عريض اليابان", false, "Ｏｎｙｘ") { t -> mapLatin(t, FW_UPPER, FW_LOWER) },
        Style("fraktur", "قوطي", false, "𝔒𝔫𝔶𝔵") { t -> mapLatin(t, FRK_UPPER, FRK_LOWER) },
        Style("double", "مزدوج", false, "𝕆𝕟𝕪𝕩") { t -> mapLatin(t, DS_UPPER, DS_LOWER) },
        Style("smallcaps", "أحرف كبيرة صغيرة", false, "𝗢𝗡𝗬𝗫") { t -> mapLatin(t, B_UPPER, B_UPPER) },
        Style("ornate", "زخرفة عربية ﴿﴾", true, "﴿نص﴾") { t -> "﴿ $t ﴾" },
        Style("sparkle", "بريق ✨", true, "✦نص✦") { t -> "✦$t✦" },
        Style("rose", "ورد 🌹", true, "🌹نص🌹") { t -> "🌹$t🌹" },
        Style("floral", "زهور", true, "✿نص✿") { t -> "✿•$t•✿" },
        Style("wave", "موج", true, "≈نص≈") { t -> "彡${t}彡" },
        Style("crown", "تاج 👑", true, "👑نص👑") { t -> "👑 $t 👑" },
        Style("stars", "نجوم", true, "⋆نص⋆") { t -> "⋆｡°✩$t✩°｡⋆" },
        Style("fire", "نار 🔥", true, "🔥نص🔥") { t -> "🔥$t🔥" },
    )

    fun styleAt(index: Int): Style = STYLES[((index % STYLES.size) + STYLES.size) % STYLES.size]

    /** زخارف جاهزة للإدراج الفوري */
    val SNIPPETS = listOf(
        "﴿ ﴾", "«»", "【】", "❰❱", "╔═╗║╚╝", "━━━━━━━", "✦✧✦", "❖ ❖",
        "彡 日 彡", "▁ ▂ ▃ ▅ ▆ ▇", "⌜⌝⌞⌟", "┊ ┊ ┊", "•°• ∞ •°•", "✿°•∘ɷ∘•°✿",
    )

    /** كاوموجي حسب المزاج (خوارزمية 48) */
    val KAOMOJI_POS = listOf("(◕‿◕)", "(｡◕‿◕｡)", "(✿◠‿◠)", "(◡‿◡✿)", "(◕ᴗ◕✿)", "٩(◕‿◕)۶", "(＾▽＾)", "(｡♥‿♥｡)", "(☆▽☆)", "(≧◡≦)", "ヽ(♡‿♡)ノ")
    val KAOMOJI_NEG = listOf("(╥﹏╥)", "(ಥ﹏ಥ)", "(◕︿◕✿)", "(＞﹏＜)", "(T▽T)", "(－‸ლ)", "(⊙_⊙)", "┌(。Д。)┐")
    val KAOMOJI_NEU = listOf("(¬‿¬)", "(¬_¬)", "ಠ_ಠ", "ʕ•ᴥ•ʔ", "(^-^*)", "(·_·)", "(o_o)", "(ᵔᴥᵔ)", "¯\\_(ツ)_/¯")

    /** خوارزمية 49: الترحيب حسب الساعة */
    fun greetingForHour(h: Int): String = when (h) {
        in 5..11 -> "صباح الخير ☀️"
        in 12..16 -> "نهار سعيد 🌤️"
        in 17..20 -> "مساء الخير 🌆"
        else -> "ليلة سعيدة 🌙"
    }
}
