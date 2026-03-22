package app.gamenative.service

import android.os.FileObserver
import app.gamenative.ui.util.AchievementNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File

class AchievementWatcher(
    private val appId: Int,
    private val watchDirs: List<File>,
    private val displayNameMap: Map<String, String>,
    private val iconUrlMap: Map<String, String?>,
    private val configDirectory: String?,
) {
    private val observers = mutableListOf<FileObserver>()
    private val notifiedNames = mutableSetOf<String>()
    private val uploadedNames = mutableSetOf<String>()
    private var sessionStartTime: Long = 0L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var uploadJob: Job? = null

    fun start() {
        sessionStartTime = System.currentTimeMillis() / 1000

        for (dir in watchDirs) {
            dir.mkdirs()
            val observer = object : FileObserver(dir, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == "achievements.json") {
                        checkForNewUnlocks(File(dir, "achievements.json"))
                    }
                }
            }
            observer.startWatching()
            observers.add(observer)
        }
        Timber.d("AchievementWatcher started, watching ${watchDirs.size} dirs")
    }

    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
        scope.cancel()
        Timber.d("AchievementWatcher stopped")
    }

    private fun checkForNewUnlocks(achFile: File) {
        if (!achFile.exists()) return
        var hasNewUnlocks = false
        try {
            val json = JSONObject(achFile.readText(Charsets.UTF_8))
            for (name in json.keys()) {
                val entry = json.optJSONObject(name) ?: continue
                if (!entry.optBoolean("earned", false)) continue
                if (name in notifiedNames) continue

                val earnedTime = entry.optLong("earned_time", 0L)
                if (earnedTime >= sessionStartTime - 30) {
                    notifiedNames.add(name)
                    hasNewUnlocks = true
                    val displayName = displayNameMap[name] ?: name
                    val iconUrl = iconUrlMap[name]
                    AchievementNotificationManager.show(displayName, iconUrl)
                    Timber.i("Achievement unlocked: $name ($displayName)")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse achievements.json for watcher")
        }

        if (hasNewUnlocks) {
            scheduleUpload()
        }
    }

    /**
     * Debounces achievement uploads: waits 5 seconds after the last unlock
     * before uploading to Steam, so rapid unlocks are batched together.
     */
    private fun scheduleUpload() {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            delay(UPLOAD_DEBOUNCE_MS)
            uploadToSteam()
        }
    }

    private suspend fun uploadToSteam() {
        if (configDirectory == null) {
            Timber.w("No configDirectory set, skipping real-time achievement upload for appId=$appId")
            return
        }
        if (!SteamService.isConnected) {
            Timber.w("Not connected to Steam, skipping real-time achievement upload for appId=$appId")
            return
        }

        val (allUnlocked, gseStatsDir) = SteamService.collectGseUnlocksAndStats(watchDirs)

        // Only upload if there are new unlocks we haven't uploaded yet
        val newToUpload = allUnlocked - uploadedNames
        if (newToUpload.isEmpty()) {
            Timber.d("No new achievements to upload for appId=$appId")
            return
        }

        Timber.i("Real-time uploading ${newToUpload.size} new achievements (${allUnlocked.size} total) for appId=$appId")
        val result = SteamService.storeAchievementUnlocks(appId, configDirectory, allUnlocked, gseStatsDir ?: watchDirs.first().resolve("stats"))
        result.onSuccess {
            uploadedNames.addAll(allUnlocked)
            Timber.i("Real-time achievement upload succeeded for appId=$appId")
        }.onFailure { e ->
            Timber.e(e, "Real-time achievement upload failed for appId=$appId, will retry on next unlock or at exit")
        }
    }

    companion object {
        private const val UPLOAD_DEBOUNCE_MS = 5_000L
    }
}
