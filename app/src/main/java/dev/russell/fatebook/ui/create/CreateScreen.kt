package dev.russell.fatebook.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.russell.fatebook.ui.components.DatePickerField
import dev.russell.fatebook.ui.components.ProbabilitySlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    onBack: () -> Unit,
    viewModel: CreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Prediction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("What are you predicting?") },
                placeholder = { Text("Will X happen by Y?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )

            DatePickerField(
                selectedDate = state.resolveBy,
                onDateSelected = viewModel::setResolveBy,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProbabilitySlider(
                value = state.forecast,
                onValueChange = viewModel::setForecast,
            )

            Spacer(modifier = Modifier.weight(1f))

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting && state.title.isNotBlank(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator()
                } else {
                    Text("Create Prediction")
                }
            }
        }
    }
}
