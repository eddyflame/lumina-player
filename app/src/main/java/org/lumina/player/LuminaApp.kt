package org.lumina.player

import android.app.Application
import org.lumina.player.data.db.MusicDatabase
import org.lumina.player.data.preferences.AudioPreferences
import org.lumina.player.data.preferences.ThemePreferences
import org.lumina.player.data.repository.MusicRepository
import org.lumina.player.data.scanner.MediaStoreScanner
import org.lumina.player.service.PlayerController

class LuminaApp : Application() {

    lateinit var database: MusicDatabase
        private set

    lateinit var scanner: MediaStoreScanner
        private set

    lateinit var repository: MusicRepository
        private set

    lateinit var themePreferences: ThemePreferences
        private set

    lateinit var audioPreferences: AudioPreferences
        private set

    lateinit var playerController: PlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = MusicDatabase(this)
        scanner = MediaStoreScanner(this)
        repository = MusicRepository(database, scanner)
        themePreferences = ThemePreferences(this)
        audioPreferences = AudioPreferences(this)
        playerController = PlayerController(this, repository, audioPreferences)
    }

    companion object {
        lateinit var instance: LuminaApp
            private set
    }
}
