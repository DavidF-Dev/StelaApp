package dev.davidfdev.stela.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davidfdev.stela.R
import dev.davidfdev.stela.ui.SectionHeader
import dev.davidfdev.stela.ui.appVersionName
import dev.davidfdev.stela.ui.openUrl

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    AboutScreen(
        version = appVersionName(context),
        onBack = onBack,
        onViewSource = { url -> openUrl(context, url) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    onViewSource: (String) -> Unit,
) {
    val sourceUrl = stringResource(R.string.about_source_url)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_author),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AboutSection(stringResource(R.string.about_privacy_title), stringResource(R.string.about_privacy_body))
            AboutSection(stringResource(R.string.about_how_title), stringResource(R.string.about_how_body))
            AboutSection(stringResource(R.string.about_license_title), stringResource(R.string.about_license_body))
            AboutSection(stringResource(R.string.about_licenses_title), stringResource(R.string.about_licenses_body))

            ListItem(
                headlineContent = { Text(stringResource(R.string.action_view_source)) },
                supportingContent = { Text(sourceUrl) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                },
                modifier = Modifier.clickable { onViewSource(sourceUrl) },
            )
            // Breathing room so the last item can scroll clear of the bottom edge.
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    SectionHeader(title)
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}
