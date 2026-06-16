package com.example.arsens

import com.example.arsens.data.LongIntMap
import org.junit.Assert.assertEquals
import org.junit.Test

class LongIntMapTest {

    @Test
    fun insertAndLookup() {
        val map = LongIntMap()
        map.put(10L, 100)
        map.put(20L, 200)
        assertEquals(100, map.getOrDefault(10L, -1))
        assertEquals(200, map.getOrDefault(20L, -1))
        assertEquals(2, map.count)
    }

    @Test
    fun updateOverwritesValueWithoutGrowingCount() {
        val map = LongIntMap()
        map.put(7L, 1)
        map.put(7L, 2)
        map.put(7L, 3)
        assertEquals(3, map.getOrDefault(7L, -1))
        assertEquals(1, map.count)
    }

    @Test
    fun missingKeyReturnsDefault() {
        val map = LongIntMap()
        map.put(1L, 1)
        assertEquals(-1, map.getOrDefault(2L, -1))
        assertEquals(777, map.getOrDefault(2L, 777))
    }

    @Test
    fun growsAndKeepsAllEntries() {
        // Begin klein zodat er meermaals geresized wordt; alle entries moeten blijven kloppen.
        val map = LongIntMap(4)
        val n = 10_000
        for (i in 0 until n) map.put(i.toLong(), i * 3)
        assertEquals(n, map.count)
        for (i in 0 until n) assertEquals(i * 3, map.getOrDefault(i.toLong(), -1))
        assertEquals(-1, map.getOrDefault(n.toLong(), -1))
    }

    @Test
    fun handlesNegativeAndExtremeKeys() {
        val map = LongIntMap()
        val keys = longArrayOf(0L, -1L, 1L, Long.MAX_VALUE, Long.MIN_VALUE, -123_456_789L, 1L shl 62)
        for ((i, k) in keys.withIndex()) map.put(k, i)
        for ((i, k) in keys.withIndex()) assertEquals(i, map.getOrDefault(k, -1))
        assertEquals(keys.size, map.count)
        // Long.MIN_VALUE is intern de lege-slot-sentinel: expliciet checken dat hij als ECHTE
        // sleutel werkt (get/update) en losstaat van de gewone slots.
        assertEquals(4, map.getOrDefault(Long.MIN_VALUE, -1))
        map.put(Long.MIN_VALUE, 99)
        assertEquals(99, map.getOrDefault(Long.MIN_VALUE, -1))
        assertEquals(keys.size, map.count)
    }

    @Test
    fun freeSentinelKeyMissingReturnsDefault() {
        val map = LongIntMap()
        map.put(5L, 5)
        assertEquals(-1, map.getOrDefault(Long.MIN_VALUE, -1))
    }

    @Test
    fun matchesReferenceHashMapUnderCollisionsAndUpdates() {
        // Willekeurige sleutels/waarden (vaste seed) botsen en herhashen volop; kruiscontrole tegen
        // een HashMap bewijst identieke get/update-semantiek over duizenden entries.
        val rnd = java.util.Random(1234L)
        val ref = HashMap<Long, Int>()
        val map = LongIntMap(8)
        repeat(20_000) {
            val key = rnd.nextLong()
            val value = rnd.nextInt()
            ref[key] = value
            map.put(key, value)
        }
        for ((k, v) in ref) assertEquals(v, map.getOrDefault(k, Int.MIN_VALUE))
        assertEquals(ref.size, map.count)
        repeat(2_000) {
            val key = rnd.nextLong()
            if (!ref.containsKey(key)) assertEquals(-999, map.getOrDefault(key, -999))
        }
    }
}
