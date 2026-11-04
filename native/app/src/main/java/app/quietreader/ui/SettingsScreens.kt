package app.quietreader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.quietreader.BuildConfig
import app.quietreader.MotionMode
import app.quietreader.QuietSettings
import app.quietreader.Rotation
import app.quietreader.TextSize
import app.quietreader.ThemeMode
import app.quietreader.phoneWantsLessMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Settings: a home list of sections; each opens on its own page, as in the web app. */
@Composable
fun SettingsScreen(state: AppState, settings: QuietSettings) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = state.page,
        transitionSpec = { pageTransition(reduced, deeper = targetState != SettingsPage.Home) },
        label = "settings-page",
    ) { page ->
        when (page) {
            SettingsPage.Home -> SettingsHome(state)
            SettingsPage.Interface -> InterfacePage(state, settings)
            SettingsPage.About -> AboutPage(state)
        }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
        content = content,
    )
}

private data class Section(val number: String, val title: String, val hint: String, val layer: String?, val page: SettingsPage?)

private val sections = listOf(
    Section("01", "Interface", "Day and night, app text size, motion, vibration", null, SettingsPage.Interface),
    Section("02", "Reader", "Scroll or pages, page turn, fonts, spacing, the reader bar", "L3", null),
    Section("03", "Library", "Book folders Quiet watches for new books", "L5", null),
    Section("04", "Files & storage", "Back up and restore, space used, delete everything", "L2", null),
    Section("05", "Privacy & translation", "What leaves your phone: only text you translate", "L4", null),
)

@Composable
private fun SettingsHome(state: AppState) {
    Page {
        Card {
            sections.forEachIndexed { i, s ->
                if (i > 0) Divider()
                SettingsRow(
                    title = s.title, hint = s.hint, layer = s.layer,
                    lead = { Text(s.number, style = Type.mono.copy(color = LocalColors.current.muted)) },
                ) {
                    if (s.page != null) state.page = s.page else state.toast("${s.title} comes in ${s.layer}.")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card {
            SettingsRow("About Quiet", "Version, what’s new, the tour, your data", null, lead = { Logo(26.dp) }) {
                state.page = SettingsPage.About
            }
            Divider()
            SettingsRow("Report a problem", "You see the report and send it yourself", "L8", lead = { Spacer(Modifier.width(26.dp)) }) {
                state.toast("Report a problem comes in L8.")
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, hint: String, layer: String?, lead: @Composable () -> Unit, onClick: () -> Unit) {
    val c = LocalColors.current
    val haptics = LocalHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { haptics?.tick(); onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(34.dp)) { lead() }
        Column(Modifier.weight(1f).alpha(if (layer == null) 1f else 0.6f)) {
            Text(title, style = Type.rowTitle.copy(color = c.text))
            Spacer(Modifier.height(2.dp))
            Text(hint, style = Type.hint.copy(color = c.muted))
        }
        Spacer(Modifier.width(10.dp))
        if (layer != null) LayerTag(layer)
        else Icon(Icons.ChevronRight, null, Modifier.size(18.dp), tint = c.text)
    }
}

@Composable
private fun PageHeader(title: String, state: AppState) {
    val c = LocalColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconKey(Icons.ChevronLeft, "Back", { state.page = SettingsPage.Home })
        Spacer(Modifier.width(12.dp))
        Text(title, style = Type.pageTitle.copy(color = c.text))
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun SectionTitle(title: String, hint: String = "") {
    val c = LocalColors.current
    Text(title, style = Type.section.copy(color = c.text))
    if (hint.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(hint, style = Type.hint.copy(color = c.muted))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingBlock(label: String, hint: String = "", content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.padding(16.dp)) {
        Text(label, style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
        Spacer(Modifier.height(10.dp))
        content()
        if (hint.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = Type.hint.copy(color = c.muted))
        }
    }
}

@Composable
private fun InterfacePage(state: AppState, settings: QuietSettings) {
    val context = LocalContext.current
    Page {
        PageHeader("Interface", state)
        SectionTitle("Theme", "System follows your phone: Day while it’s light, Night while it’s in dark mode.")
        Card {
            SettingBlock("Theme") {
                ChoiceRow(ThemeMode.entries.map { it to it.label }, settings.theme, { settings.theme = it })
            }
        }
        Spacer(Modifier.height(28.dp))
        SectionTitle("The app")
        Card {
            SettingBlock("Text size") {
                ChoiceRow(TextSize.entries.map { it to it.label }, settings.textSize, { settings.textSize = it })
            }
            Divider()
            val lessMotion = phoneWantsLessMotion(context)
            SettingBlock(
                "Motion",
                if (lessMotion) "Your phone is asking apps for less motion right now (“Remove animations”). Choose Full to keep Quiet’s animations anyway."
                else "Your phone isn’t asking for less motion, so Follow phone means full motion.",
            ) {
                ChoiceRow(MotionMode.entries.map { it to it.label }, settings.motion, { settings.motion = it }, perRow = 3)
            }
            Divider()
            SettingBlock("Screen rotation") {
                ChoiceRow(Rotation.entries.map { it to it.label }, settings.rotation, { settings.rotation = it }, perRow = 3)
            }
            Divider()
            val c = LocalColors.current
            Row(
                Modifier.fillMaxWidth().clickable { settings.haptics = !settings.haptics }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vibration", style = Type.label.copy(color = c.text, fontWeight = FontWeight.Normal))
                    Spacer(Modifier.height(2.dp))
                    Text("A light tap when you press a key, bookmark, highlight or change a setting.", style = Type.hint.copy(color = c.muted))
                }
                Spacer(Modifier.width(12.dp))
                Switch(settings.haptics, { settings.haptics = it }, "Vibration")
            }
        }
    }
}

@Composable
private fun AboutPage(state: AppState) {
    val c = LocalColors.current
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val spaceUsed by produceState("…") {
        value = withContext(Dispatchers.IO) { fileSize(folderSize(context.filesDir.parentFile ?: context.filesDir)) }
    }
    Page {
        PageHeader("About Quiet", state)
        Card {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("quiet", style = Type.emptyTitle.copy(color = c.text, fontSize = 26.sp))
                        Text("Version ${BuildConfig.QUIET_VERSION} · ${BuildConfig.QUIET_LAYER} · native", style = Type.mono.copy(color = c.muted))
                        Text("A reader with nothing extra.", style = Type.body.copy(color = c.muted))
                    }
                }

                Overline("Your data · on this phone only")
                Row(
                    Modifier.fillMaxWidth().border(1.dp, c.line, RoundedCornerShape(8.dp)).padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Fact("0", "Books")
                    Fact(spaceUsed, "Space used")
                    Fact("Never", "Last backup")
                }

                Overline("Promises")
                listOf(
                    "No account, no ads, no tracking.",
                    "Books and notes never leave this phone. Only text you choose to translate is sent.",
                    "No AI features.",
                ).forEachIndexed { i, line ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(c.done, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = Type.body.copy(color = c.text))
                    }
                }

                Overline("Formats")
                Text(
                    "This native build doesn’t open books yet. EPUB, PDF and TXT arrive in L1, every other format in L7.",
                    style = Type.body.copy(color = c.text),
                )

                Overline("Made with")
                listOf(
                    Triple("Kotlin and Jetpack Compose", "the app, Apache License 2.0", "https://developer.android.com/compose"),
                    Triple("Archivo, DM Mono, Crimson Pro", "fonts, SIL Open Font License", "https://fonts.google.com"),
                    Triple("Lucide", "icons, ISC License", "https://lucide.dev"),
                ).forEach { (name, what, url) ->
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.accentStrong)) { append(name) }
                            append(" — $what")
                        },
                        style = Type.body.copy(color = c.muted),
                        modifier = Modifier.clickable { runCatching { uri.openUri(url) } }.padding(vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("made by extrasolarions on howling dark", style = Type.body.copy(color = c.muted))
            }
        }
    }
}

@Composable
private fun Overline(text: String) {
    val c = LocalColors.current
    Spacer(Modifier.height(22.dp))
    Text(text.uppercase(), style = Type.overline.copy(color = c.muted))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Fact(value: String, label: String) {
    val c = LocalColors.current
    Column {
        Text(value, style = Type.monoLarge.copy(color = c.text))
        Text(label, style = Type.hint.copy(color = c.muted))
    }
}

private fun folderSize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

fun fileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
