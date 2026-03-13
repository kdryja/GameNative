package app.gamenative.utils

import app.gamenative.enums.PathType
import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyValueUtilsTest {

    /**
     * Blue Revolver (App ID 439490) has a Windows rootoverride that remaps `gameinstall`
     * to `WinAppDataRoaming` with an empty addpath. All three savefiles use `gameinstall`
     * as root, so they should all be remapped; paths should be unchanged.
     */
    @Test
    fun blueRevolverWindowsRootOverrideRemapsGameInstallToWinAppDataRoaming() {
        val kvString = """
            "appinfo"
            {
                "appid"     "439490"
                "ufs"
                {
                    "quota"         "1000000000"
                    "maxnumfiles"   "1000"
                    "savefiles"
                    {
                        "1"
                        {
                            "root"      "gameinstall"
                            "path"      "blue-revolver-final"
                            "pattern"   "save2.lua"
                        }
                        "2"
                        {
                            "root"      "gameinstall"
                            "path"      "blue-revolver-double-action"
                            "pattern"   "brda_save.sav"
                        }
                        "3"
                        {
                            "root"      "gameinstall"
                            "path"      "love/blue-revolver-final"
                            "pattern"   "save2.lua"
                        }
                    }
                    "rootoverrides"
                    {
                        "0"
                        {
                            "root"          "gameinstall"
                            "os"            "Windows"
                            "oscompare"     "="
                            "useinstead"    "WinAppDataRoaming"
                            "addpath"       ""
                        }
                        "1"
                        {
                            "root"          "gameinstall"
                            "os"            "MacOS"
                            "oscompare"     "="
                            "useinstead"    "MacAppSupport"
                            "addpath"       ""
                        }
                        "2"
                        {
                            "root"          "gameinstall"
                            "os"            "Linux"
                            "oscompare"     "="
                            "useinstead"    "LinuxHome"
                            "addpath"       ".local/share/"
                        }
                    }
                }
            }
        """.trimIndent()
        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(3, patterns.size)

        assertEquals(PathType.WinAppDataRoaming, patterns[0].root)
        assertEquals("blue-revolver-final", patterns[0].path)
        assertEquals("save2.lua", patterns[0].pattern)

        assertEquals(PathType.WinAppDataRoaming, patterns[1].root)
        assertEquals("blue-revolver-double-action", patterns[1].path)
        assertEquals("brda_save.sav", patterns[1].pattern)

        assertEquals(PathType.WinAppDataRoaming, patterns[2].root)
        assertEquals("love/blue-revolver-final", patterns[2].path)
        assertEquals("save2.lua", patterns[2].pattern)
    }

    /**
     * Hades has only a MacOS rootoverride. Windows save paths should be left untouched.
     */
    @Test
    fun hadesNonWindowsRootOverrideDoesNotAffectSaveFilePaths() {
        val kvString = """
            "appinfo"
            {
                "appid"     "1145360"
                "ufs"
                {
                    "quota"         "62914560"
                    "maxnumfiles"   "20"
                    "savefiles"
                    {
                        "0"
                        {
                            "root"      "WinMyDocuments"
                            "path"      "Saved Games/Hades"
                            "pattern"   "Profile*.sav"
                        }
                    }
                    "rootoverrides"
                    {
                        "1"
                        {
                            "root"          "WinMyDocuments"
                            "os"            "MacOS"
                            "oscompare"     "="
                            "useinstead"    "MacAppSupport"
                        }
                    }
                }
            }
        """.trimIndent()

        val kv = KeyValue.loadFromString(kvString)!!
        val steamApp = kv.generateSteamApp()

        val patterns = steamApp.ufs.saveFilePatterns
        assertEquals(1, patterns.size)
        assertEquals(PathType.WinMyDocuments, patterns[0].root)
        assertEquals("Saved Games/Hades", patterns[0].path)
        assertEquals("Profile*.sav", patterns[0].pattern)
    }
}
