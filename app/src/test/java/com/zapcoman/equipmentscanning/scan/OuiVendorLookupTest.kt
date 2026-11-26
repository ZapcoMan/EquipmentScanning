package com.zapcoman.equipmentscanning.scan

import org.junit.Assert.*
import org.junit.Test

class OuiVendorLookupTest {

    @Test
    fun lookup_knownHikvision() {
        val v = OuiVendorLookup.lookup("28:57:BE:AA:BB:CC")
        assertNotNull(v)
        assertEquals("Hikvision(海康)", v!!.name)
        assertEquals(VendorCategory.CAMERA, v.category)
    }

    @Test
    fun lookup_knownDahua() {
        val v = OuiVendorLookup.lookup("3C:AD:C0:11:22:33")
        assertNotNull(v)
        assertEquals(VendorCategory.CAMERA, v!!.category)
    }

    @Test
    fun lookup_espressif_isIot() {
        val v = OuiVendorLookup.lookup("5C:CF:7F:12:34:56")
        assertNotNull(v)
        assertEquals(VendorCategory.IOT, v!!.category)
    }

    @Test
    fun lookup_tplink_isRouter() {
        val v = OuiVendorLookup.lookup("50:C7:BF:AA:BB:CC")
        assertNotNull(v)
        assertEquals(VendorCategory.ROUTER, v!!.category)
    }

    @Test
    fun lookup_unknownReturnsNull() {
        assertNull(OuiVendorLookup.lookup("AA:BB:CC:DD:EE:FF"))
        assertNull(OuiVendorLookup.lookup(null))
        assertNull(OuiVendorLookup.lookup(""))
        assertNull(OuiVendorLookup.lookup("not-a-mac"))
    }

    @Test
    fun lookup_acceptsDifferentSeparators() {
        val a = OuiVendorLookup.lookup("28-57-BE-aa-bb-cc")
        val b = OuiVendorLookup.lookup("2857BEAABBCC")
        assertNotNull(a)
        assertNotNull(b)
        assertEquals(a!!.name, b!!.name)
    }
}
