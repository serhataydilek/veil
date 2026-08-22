package com.veil.app.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemorySecurityMaterialRepositoryTest {
    @Test fun rollbackRestoresReplacesDeletesAndRemovesNewRows() {
        val store=InMemorySecurityMaterialRepository(); store.put("a","one",byteArrayOf(1)); store.put("b","two",byteArrayOf(2))
        try { store.transaction { put("a","one",byteArrayOf(9)); delete("b","two"); put("c","three",byteArrayOf(3)); error("fail") } } catch(_:IllegalStateException) {}
        assertArrayEquals(byteArrayOf(1),store.get("a","one")); assertArrayEquals(byteArrayOf(2),store.get("b","two")); assertNull(store.get("c","three"))
    }
    @Test fun copiesOnWriteAndRead() { val store=InMemorySecurityMaterialRepository(); val value=byteArrayOf(1); store.put("a","one",value); value[0]=9; val read=store.get("a","one")!!; read[0]=8; assertArrayEquals(byteArrayOf(1),store.get("a","one")) }
}
