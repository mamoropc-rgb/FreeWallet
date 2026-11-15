package com.mamoro.karman

/** Validation rules shared by editing UI, SQLite and JSON backup recovery. */
object CategoryTagRules {
    /** Handy presets, not a restrictive list: any opaque 24-bit RGB color is accepted. */
    val colors = listOf(
        "#A773FF", "#48DBAB", "#FF858E", "#FFC46C", "#63B3FF", "#E675D5",
        "#B5C5E7", "#8CD970", "#F1946C", "#8D86FF", "#F44336", "#E91E63",
        "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107",
        "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B", "#607DFF",
        "#D500F9", "#00E5FF", "#76FF03", "#FF6D00", "#FFFFFF", "#121212"
    )
    val icons: Set<String> get() = WalletIconCatalog.keys
    fun name(input: String): String {
        val clean=input.trim().replace(Regex("\\s+"), " ")
        require(clean.length in 1..60 && clean.none { it.isISOControl() }) {
            "Название должно содержать от 1 до 60 символов"
        }
        return clean
    }
    fun color(input: String): String {
        val normalized=input.trim().uppercase(java.util.Locale.ROOT)
        require(Regex("^#[0-9A-F]{6}$").matches(normalized)) {
            "Цвет должен быть в формате #RRGGBB"
        }
        return normalized
    }
    fun icon(input: String): String = input.also {
        require(it in icons) { "Выберите значок из каталога" }
    }
    fun categoryKind(input: String): String = input.also {
        require(it==Kind.EXPENSE || it==Kind.INCOME) { "Категория должна относиться к доходам или расходам" }
    }
    fun tagIds(ids: List<Long>): List<Long> = ids.distinct().also {
        require(it.size<=12 && it.all { id->id>0 }) { "Не более 12 тегов на операцию" }
    }
}
