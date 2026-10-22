package ru.ark.radio

data class Station(val id: String, val name: String, val category: String, val url: String)

/** Direct stream URLs published for Zaycev.FM channels. Availability can change on the provider side. */
object StationCatalog {
    val all: List<Station> = listOf(
        Station("pop", "Zaycev.FM Pop", "Поп", "https://abs.zaycev.fm/pop256k"),
        Station("rus", "Zaycev.FM Русская", "Русская музыка", "https://abs.zaycev.fm/rus256k"),
        Station("rnb", "Zaycev.FM R&B", "R&B", "https://abs.zaycev.fm/rnb256k"),
        Station("rurock", "Zaycev.FM Русский рок", "Рок", "https://abs.zaycev.fm/rurock256k"),
        Station("rock", "Zaycev.FM Rock", "Рок", "https://abs.zaycev.fm/rock256k"),
        Station("club", "Zaycev.FM Club", "Клубная", "https://abs.zaycev.fm/club256k"),
        Station("disco", "Zaycev.FM Disco", "Ретро", "https://abs.zaycev.fm/disco256k"),
        Station("relax", "Zaycev.FM Relax", "Релакс", "https://abs.zaycev.fm/relax256k"),
        Station("shanson", "Zaycev.FM Шансон", "Шансон", "https://abs.zaycev.fm/shanson256k"),
        Station("rap", "Zaycev.FM Rap", "Рэп", "https://abs.zaycev.fm/rap256k"),
        Station("zaychata", "Zaycev.FM Зайчата", "Детская", "https://abs.zaycev.fm/zaychata256k"),
        Station("kpop", "Zaycev.FM K-Pop", "K-Pop", "https://abs.zaycev.fm/kpop256k"),
        Station("metal", "Zaycev.FM Metal", "Метал", "https://abs.zaycev.fm/metal256k"),
        Station("bass", "Zaycev.FM Bass", "Бас", "https://abs.zaycev.fm/bass256k"),
        Station("love", "Zaycev.FM Love", "Романтика", "https://abs.zaycev.fm/love256k"),
        Station("folk", "Zaycev.FM Folk", "Фолк", "https://abs.zaycev.fm/folk256k"),
        Station("classic", "Zaycev.FM Classic", "Классика", "https://abs.zaycev.fm/classic256k")
    )
}
