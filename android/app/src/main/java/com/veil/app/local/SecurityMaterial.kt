package com.veil.app.local

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.SecretKey

internal object SecurityMaterialCodec {
    const val MAX_PAYLOAD_BYTES = 64 * 1024
    private val magic = "VLSM".encodeToByteArray()
    fun encode(owner: String, slot: String, payload: ByteArray): ByteArray? {
        if (!SecurityMaterialIds.valid(owner) || !SecurityMaterialIds.valid(slot) || payload.isEmpty() || payload.size > MAX_PAYLOAD_BYTES) return null
        val o=owner.encodeToByteArray(); val s=slot.encodeToByteArray()
        return ByteBuffer.allocate(4+1+2+o.size+2+s.size+4+payload.size).order(ByteOrder.BIG_ENDIAN).put(magic).put(1).putShort(o.size.toShort()).put(o).putShort(s.size.toShort()).put(s).putInt(payload.size).put(payload).array()
    }
    fun parse(bytes: ByteArray, owner: String, slot: String): ByteArray? = try {
        val b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN); val m=ByteArray(4); b.get(m); if(!m.contentEquals(magic)||b.get().toInt()!=1) return null
        fun text():String { val n=b.short.toInt() and 0xffff; if(n !in 1..SecurityMaterialIds.MAX_BYTES || b.remaining()<n) throw IllegalArgumentException(); return ByteArray(n).also{b.get(it)}.decodeToString() }
        val actualOwner=text(); val actualSlot=text(); val n=b.int; if(n !in 1..MAX_PAYLOAD_BYTES || b.remaining()!=n) return null
        ByteArray(n).also{b.get(it)}.takeIf { actualOwner==owner && actualSlot==slot }
    } catch(_:RuntimeException){null}
}

internal class SecurityMaterialRepository(private val key: SecretKey, private val cipher: LocalRecordCipher, private val store: SqliteLocalRecordStore) {
    fun put(owner: String, slot: String, payload: ByteArray): Boolean {
        val plain=SecurityMaterialCodec.encode(owner,slot,payload) ?: return false
        try { val aad=LocalRecordAad.encodeSecurityMaterial(owner,slot) ?: return false; val enc= cipher.encryptLocal(key,plain,aad); if(enc !is LocalEncryptResult.Success) return false; val bytes=LocalRecordFormat.encode(enc.blob) ?: return false; store.upsertSecurityRecord(owner,slot,bytes); return true } finally { plain.fill(0) }
    }
    fun get(owner:String,slot:String):ByteArray? { if(!SecurityMaterialIds.valid(owner)||!SecurityMaterialIds.valid(slot))return null; val row=store.loadSecurityRecord(owner,slot)?:return null; val blob=LocalRecordFormat.decode(row)?:return null; val dec=cipher.decryptLocal(key,blob,LocalRecordAad.encodeSecurityMaterial(owner,slot)?:return null); return if(dec is LocalDecryptResult.Success) try { SecurityMaterialCodec.parse(dec.value,owner,slot)?.copyOf() } finally { dec.value.fill(0) } else null }
    fun delete(owner:String,slot:String) { if(SecurityMaterialIds.valid(owner)&&SecurityMaterialIds.valid(slot)) store.deleteSecurityRecord(owner,slot) }
}
