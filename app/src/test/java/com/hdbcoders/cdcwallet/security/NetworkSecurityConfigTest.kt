package com.hdbcoders.cdcwallet.security

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit C10 (spec 08 §8.2 anti-pattern table): the network security config
 * must carry `cleartextTrafficPermitted="false"` on the base config. Cheap
 * executable insurance against someone "temporarily" allowing cleartext for
 * debugging and committing it - the voucher link is a bearer credential and
 * must only ever travel over TLS (refactor H2; spec 03 §3.2).
 */
class NetworkSecurityConfigTest {

    @Test
    fun baseConfigDisallowsCleartextTraffic() {
        val file = File("src/main/res/xml/network_security_config.xml")
        assertTrue("network_security_config.xml must exist", file.exists())

        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)

        val baseConfig = document.getElementsByTagName("base-config").item(0)
        assertNotNull("base-config element must exist", baseConfig)
        val attribute = baseConfig.attributes
            .getNamedItem("cleartextTrafficPermitted")
        assertNotNull("base-config must set cleartextTrafficPermitted", attribute)
        assertEquals(
            "cleartext must be explicitly disallowed",
            "false",
            attribute.nodeValue,
        )
    }
}