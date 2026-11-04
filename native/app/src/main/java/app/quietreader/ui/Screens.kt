package app.quietreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Library, empty: the same welcome as the web app. Adding books arrives in L1. */
@Composable
fun LibraryScreen(state: AppState) {
    Screen {
        EmptyState(
            Icons.Library,
            "Make yourself at home.",
            "A quiet, distraction-free place for your books. Add an EPUB, MOBI, AZW3, FB2, CBZ, TXT or PDF file, " +
                "or a whole folder of them, and they’ll wait for you here, right where you left off.",
        ) {
            TextKey("Choose a book", { state.toast("Adding books comes in the next build (L1).") }, kind = KeyKind.Primary)
            TextKey("Add a book folder", { state.toast("Book folders come in L5.") })
        }
        Spacer(Modifier.height(18.dp))
        LayerTag("Books arrive in L1")
    }
}

@Composable
fun NotesScreen() {
    Screen {
        EmptyState(
            Icons.NotebookPen,
            "Your notes will gather here.",
            "Select text while reading to highlight it, write a note, or save it as a quote. " +
                "Everything from every book shows up on this page.",
        )
        LayerTag("Notes arrive in L4")
    }
}

@Composable
fun StatsScreen() {
    Screen {
        EmptyState(
            Icons.ChartColumn,
            "Your reading, at a glance.",
            "Minutes read today against your goal, the week as level lights, your streak, your pace and the books you finish.",
        )
        LayerTag("Stats arrive in L6")
    }
}

@Composable
private fun Screen(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    }
}
