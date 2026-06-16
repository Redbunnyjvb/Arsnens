package com.example.arsens.data

/**
 * Primitieve open-addressing `long → int` hashmap (lineair probing) zónder autoboxing.
 *
 * Vervangt `HashMap<Long, Int>` in de zware STL-prep loops (welden, randtopologie, cluster-
 * decimatie). Een `HashMap` boxet per entry een `Long`-sleutel én `Int`-waarde en alloceert een
 * `Node`: bij honderdduizenden tot miljoenen hoekpunten/randen zijn dat evenzoveel wegwerp-objecten
 * → forse GC-druk en pieken in geheugengebruik. Hier staan sleutels en waarden in twee primitieve
 * arrays; put/get alloceren niets.
 *
 * BELANGRIJK voor correctheid: deze map wordt overal puur als sleutel→id-woordenboek gebruikt en
 * NOOIT geïtereerd. De ids worden door de aanroeper toegekend (een ophogende teller), niet door de
 * map. Daardoor is de uitvoer identiek aan `HashMap`: de hashfunctie/iteratievolgorde beïnvloeden
 * alleen de snelheid, niet het resultaat.
 *
 * Niet thread-safe: elke prep-functie gebruikt een eigen lokale instance.
 *
 * @param expectedSize verwacht aantal entries; de tabel wordt zo gekozen dat dit zonder herhashen past.
 */
internal class LongIntMap(expectedSize: Int = 16) {
    private var keys: LongArray
    private var values: IntArray
    private var mask: Int
    private var threshold: Int
    private var size = 0
    // Long.MIN_VALUE markeert een lege slot. Een echte sleutel met die waarde komt in de STL-prep
    // niet voor (raster-/randsleutels zijn niet-negatief), maar wordt voor de volledigheid apart
    // bijgehouden zodat ÁLLE long-waarden geldige sleutels zijn.
    private var hasFree = false
    private var freeValue = 0

    init {
        val cap = tableSizeFor(((expectedSize / LOAD_FACTOR).toInt()) + 1)
        keys = LongArray(cap) { FREE }
        values = IntArray(cap)
        mask = cap - 1
        threshold = (cap * LOAD_FACTOR).toInt()
    }

    /** Aantal opgeslagen entries. */
    val count: Int get() = size + if (hasFree) 1 else 0

    /** Waarde voor [key], of [default] als de sleutel ontbreekt. */
    fun getOrDefault(key: Long, default: Int): Int {
        if (key == FREE) return if (hasFree) freeValue else default
        var i = index(key)
        while (true) {
            val k = keys[i]
            if (k == FREE) return default
            if (k == key) return values[i]
            i = (i + 1) and mask
        }
    }

    /** Zet [value] voor [key] (overschrijft een bestaande waarde). */
    fun put(key: Long, value: Int) {
        if (key == FREE) {
            hasFree = true
            freeValue = value
            return
        }
        var i = index(key)
        while (true) {
            val k = keys[i]
            if (k == FREE) {
                keys[i] = key
                values[i] = value
                size++
                if (size >= threshold) resize()
                return
            }
            if (k == key) {
                values[i] = value
                return
            }
            i = (i + 1) and mask
        }
    }

    private fun index(key: Long): Int {
        // murmur3 64-bit finalizer: spreidt ook gestructureerde rastersleutels goed over de tabel.
        var h = key
        h = h xor (h ushr 33)
        h *= C1
        h = h xor (h ushr 33)
        h *= C2
        h = h xor (h ushr 33)
        return h.toInt() and mask
    }

    private fun resize() {
        val oldKeys = keys
        val oldValues = values
        val newCap = oldKeys.size shl 1
        keys = LongArray(newCap) { FREE }
        values = IntArray(newCap)
        mask = newCap - 1
        threshold = (newCap * LOAD_FACTOR).toInt()
        for (j in oldKeys.indices) {
            val k = oldKeys[j]
            if (k != FREE) {
                var i = index(k)
                while (keys[i] != FREE) i = (i + 1) and mask
                keys[i] = k
                values[i] = oldValues[j]
            }
        }
    }

    private companion object {
        const val FREE = Long.MIN_VALUE
        const val LOAD_FACTOR = 0.70f
        const val C1 = -0xae502812aa7333L   // 0xff51afd7ed558ccd
        const val C2 = -0x3b314601e57a13adL // 0xc4ceb9fe1a85ec53

        /** Kleinste macht van twee ≥ [c] (minimaal 4). */
        fun tableSizeFor(c: Int): Int {
            var n = 4
            while (n < c) n = n shl 1
            return n
        }
    }
}
