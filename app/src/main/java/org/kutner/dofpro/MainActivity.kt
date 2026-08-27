package org.kutner.dofpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.kutner.dofpro.model.DofState
import org.kutner.dofpro.model.Settings
import org.kutner.dofpro.ui.DofScreen
import org.kutner.dofpro.ui.DofTheme

class MainActivity : ComponentActivity() {

    private lateinit var state: DofState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = DofState(Settings.load(this))
        state.onPersist = { Settings.save(this, state.toSettings()) }

        setContent {
            DofTheme(state.theme) {
                DofScreen(state)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Settings are saved on the way out and restored on the way in, like dof.txt.
        Settings.save(this, state.toSettings())
    }
}
