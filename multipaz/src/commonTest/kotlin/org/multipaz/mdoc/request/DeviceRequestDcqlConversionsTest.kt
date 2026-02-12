package org.multipaz.mdoc.request

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.multipaz.cbor.buildCborArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceRequestDcqlConversionsTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    @Test
    fun sdjwtInDcqlFails() {
        val e = assertFailsWith(IllegalArgumentException::class) {
            val deviceRequest = buildDeviceRequestFromDcql(
                sessionTranscript = buildCborArray { add("doesn't"); add("matter") },
                dcqlString = """
                    {
                      "credentials": [
                        {
                          "id": "my_credential",
                          "format": "dc+sd-jwt",
                          "meta": {
                            "vct_values": ["urn:eudi:pid:1"]
                          },
                          "claims": [
                            {"path": ["given_name"]},
                            {"path": ["address", "street_address"]}
                          ]
                        }
                      ]
                    }
                """.trimIndent()
            ) {}
        }
        assertEquals("Credential format dc+sd-jwt is not supported", e.message)
    }

    @Test
    fun singleMdl() {
        val deviceRequest = buildDeviceRequestFromDcql(
            sessionTranscript = buildCborArray { add("doesn't"); add("matter") },
            dcqlString =
                """
                    {
                      "credentials": [
                        {
                          "id": "my_credential",
                          "format": "mso_mdoc",
                          "meta": {
                            "doctype_value": "org.iso.18013.5.1.mDL"
                          },
                          "claims": [
                            {"path": ["org.iso.18013.5.1", "given_name"]},
                            {"path": ["org.iso.18013.5.1", "resident_address"]}
                          ]
                        }
                      ]
                    }                    
                """.trimIndent()
        ) {}
        assertEquals(
            """
                {
                  "credentials": [
                    {
                      "id": "cred0",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "org.iso.18013.5.1",
                            "given_name"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim1",
                          "path": [
                            "org.iso.18013.5.1",
                            "resident_address"
                          ],
                          "intent_to_retain": false
                        }
                      ]
                    }
                  ],
                  "credential_sets": [
                    {
                      "required": true,
                      "options": [
                        [
                          "cred0"
                        ]
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            prettyJson.encodeToString(deviceRequest.toDcql())
        )
    }

    @Test
    fun mdlAndPid() {
        val deviceRequest = buildDeviceRequestFromDcql(
            sessionTranscript = buildCborArray { add("doesn't"); add("matter") },
            dcqlString = """
                {
                  "credentials": [
                    {
                      "id": "my_mdl",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                      },
                      "claims": [
                        {"path": ["org.iso.18013.5.1", "given_name"]},
                        {"path": ["org.iso.18013.5.1", "resident_address"]}
                      ]
                    },
                    {
                      "id": "pid",
                      "format": "mso_mdoc",
                      "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" },
                      "claims": [
                        { "path": [ "eu.europa.ec.eudi.pid.1", "given_name" ] },
                        { "path": [ "eu.europa.ec.eudi.pid.1", "family_name" ] }
                      ]
                    }
                  ]
                }
            """.trimIndent()
        ) {}
        assertEquals(
            """
                {
                  "credentials": [
                    {
                      "id": "cred0",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "org.iso.18013.5.1",
                            "given_name"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim1",
                          "path": [
                            "org.iso.18013.5.1",
                            "resident_address"
                          ],
                          "intent_to_retain": false
                        }
                      ]
                    },
                    {
                      "id": "cred1",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "eu.europa.ec.eudi.pid.1"
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "eu.europa.ec.eudi.pid.1",
                            "given_name"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim1",
                          "path": [
                            "eu.europa.ec.eudi.pid.1",
                            "family_name"
                          ],
                          "intent_to_retain": false
                        }
                      ]
                    }
                  ],
                  "credential_sets": [
                    {
                      "required": true,
                      "options": [
                        [
                          "cred0"
                        ]
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            prettyJson.encodeToString(deviceRequest.toDcql())
        )
    }

    @Test
    fun mdlOrPid() {
        val deviceRequest = buildDeviceRequestFromDcql(
            sessionTranscript = buildCborArray { add("doesn't"); add("matter") },
            dcqlString = """
                {
                  "credentials": [
                    {
                      "id": "mdl",
                      "format": "mso_mdoc",
                      "meta": { "doctype_value": "org.iso.18013.5.1.mDL" },
                      "claims": [
                        { "path": [ "org.iso.18013.5.1", "given_name" ] },
                        { "path": [ "org.iso.18013.5.1", "family_name" ] }
                      ]
                    },
                    {
                      "id": "pid",
                      "format": "mso_mdoc",
                      "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" },
                      "claims": [
                        { "path": [ "eu.europa.ec.eudi.pid.1", "given_name" ] },
                        { "path": [ "eu.europa.ec.eudi.pid.1", "family_name" ] }
                      ]
                    }
                  ],
                  "credential_sets": [
                    {
                      "options": [
                        [ "mdl" ],
                        [ "pid" ]
                      ]
                    }
                  ]
                }
            """.trimIndent()
        ) {}
        assertEquals(
            """
                {
                  "credentials": [
                    {
                      "id": "cred0",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "org.iso.18013.5.1",
                            "given_name"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim1",
                          "path": [
                            "org.iso.18013.5.1",
                            "family_name"
                          ],
                          "intent_to_retain": false
                        }
                      ]
                    },
                    {
                      "id": "cred1",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "eu.europa.ec.eudi.pid.1"
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "eu.europa.ec.eudi.pid.1",
                            "given_name"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim1",
                          "path": [
                            "eu.europa.ec.eudi.pid.1",
                            "family_name"
                          ],
                          "intent_to_retain": false
                        }
                      ]
                    }
                  ],
                  "credential_sets": [
                    {
                      "required": true,
                      "options": [
                        [
                          "cred0"
                        ],
                        [
                          "cred1"
                        ]
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            prettyJson.encodeToString(deviceRequest.toDcql())
        )
    }

    @Test
    fun privacyPreservingAgeRequests() {
        val deviceRequest = buildDeviceRequestFromDcql(
            sessionTranscript = buildCborArray { add("doesn't"); add("matter") },
            dcqlString = """
                {
                  "credentials": [
                    {
                      "id": "my_credential",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                      },
                      "claims": [
                        {"id": "a", "path": ["org.iso.18013.5.1", "given_name"]},
                        {"id": "b", "path": ["org.iso.18013.5.1", "age_over_18"]},
                        {"id": "c", "path": ["org.iso.18013.5.1", "age_in_years"]},
                        {"id": "d", "path": ["org.iso.18013.5.1", "birth_date"]}
                      ],
                      "claim_sets": [
                        ["a", "b"],
                        ["a", "c"],
                        ["a", "d"]
                      ]
                    }
                  ]
                }
            """.trimIndent()
        ) {}
        assertEquals(
            """
                {
                  "credentials": [
                    {
                      "id": "cred0",
                      "format": "mso_mdoc",
                      "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "org.iso.18013.5.1",
                            "given_name"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim1",
                          "path": [
                            "org.iso.18013.5.1",
                            "age_over_18"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim2",
                          "path": [
                            "org.iso.18013.5.1",
                            "age_in_years"
                          ],
                          "intent_to_retain": false
                        },
                        {
                          "id": "claim3",
                          "path": [
                            "org.iso.18013.5.1",
                            "birth_date"
                          ],
                          "intent_to_retain": false
                        }
                      ],
                      "claim_sets": [
                        [
                          "claim0",
                          "claim1"
                        ],
                        [
                          "claim0",
                          "claim2"
                        ],
                        [
                          "claim0",
                          "claim3"
                        ]
                      ]
                    }
                  ],
                  "credential_sets": [
                    {
                      "required": true,
                      "options": [
                        [
                          "cred0"
                        ]
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            prettyJson.encodeToString(deviceRequest.toDcql())
        )
    }

    @Test
    fun zkp() {
        val deviceRequest = buildDeviceRequestFromDcql(
            sessionTranscript = buildCborArray { add("doesn't"); add("matter") },
            dcqlString = """
                {
                  "credentials": [
                    {
                      "id": "mdoc",
                      "format": "mso_mdoc_zk",
                      "meta": {
                        "doctype_value": "org.iso.23220.photoid.1",
                        "zk_system_type": [
                          {
                            "system": "longfellow-libzk-v1",
                            "id": "longfellow-libzk-v1_6_1_4096_2945_137e5a75ce72735a37c8a72da1a8a0a5df8d13365c2ae3d2c2bd6a0e7197c7c6",
                            "version": 6,
                            "circuit_hash": "137e5a75ce72735a37c8a72da1a8a0a5df8d13365c2ae3d2c2bd6a0e7197c7c6",
                            "num_attributes": 1,
                            "block_enc_hash": 4096,
                            "block_enc_sig": 2945
                          }
                        ]
                      },
                      "claims": [
                        {
                          "path": [
                            "org.iso.23220.1",
                            "age_over_18"
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent()
        ) {}
        assertEquals(
            """
                {
                  "credentials": [
                    {
                      "id": "cred0",
                      "format": "mso_mdoc_zk",
                      "meta": {
                        "doctype_value": "org.iso.23220.photoid.1",
                        "zk_system_type": [
                          {
                            "system": "longfellow-libzk-v1",
                            "id": "longfellow-libzk-v1_6_1_4096_2945_137e5a75ce72735a37c8a72da1a8a0a5df8d13365c2ae3d2c2bd6a0e7197c7c6",
                            "version": 6,
                            "circuit_hash": "137e5a75ce72735a37c8a72da1a8a0a5df8d13365c2ae3d2c2bd6a0e7197c7c6",
                            "num_attributes": 1,
                            "block_enc_hash": 4096,
                            "block_enc_sig": 2945
                          }
                        ]
                      },
                      "claims": [
                        {
                          "id": "claim0",
                          "path": [
                            "org.iso.23220.1",
                            "age_over_18"
                          ],
                          "intent_to_retain": false
                        }
                      ]
                    }
                  ],
                  "credential_sets": [
                    {
                      "required": true,
                      "options": [
                        [
                          "cred0"
                        ]
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            prettyJson.encodeToString(deviceRequest.toDcql())
        )
    }
}