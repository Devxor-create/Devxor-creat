package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AspectRatioOption
import com.example.ai.ImageResolutionOption
import com.example.ui.components.MarkdownText
import com.example.ui.state.MediaStudioState
import com.example.ui.state.SpaceXWorkspaceSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreativeStudioScreen(
    snapshot: SpaceXWorkspaceSnapshot,
    onSelectImageModel: (String) -> Unit,
    onSelectAspectRatio: (AspectRatioOption) -> Unit,
    onSelectResolution: (ImageResolutionOption) -> Unit,
    onGenerateOrEditImage: (prompt: String, source: Bitmap?) -> Unit,
    onAnalyzeImage: (bitmap: Bitmap, prompt: String) -> Unit,
    onAnalyzeVideoBytes: (bytes: ByteArray, mimeType: String, prompt: String, title: String) -> Unit,
    onSpeakText: (String) -> Unit,
    onSavePromptToLibrary: (title: String, content: String) -> Unit
) {
    val context = LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var ttsInput by remember { mutableStateOf("Welcome to SpaceX AI. All telemetry systems are nominal.") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                sourceBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false)
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                val mime = context.contentResolver.getType(it) ?: "video/mp4"
                val bytes = context.contentResolver.openInputStream(it)?.use { s -> s.readBytes() }
                if (bytes != null && bytes.size <= 18 * 1024 * 1024) {
                    onAnalyzeVideoBytes(
                        bytes,
                        mime,
                        promptText.ifBlank { "Summarize the key events, visuals, and details in this video." },
                        "Video Analysis"
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "MEDIA & IMAGE AI STUDIO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Create, Edit & Analyze Media",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Model selector (Nano Banana 2.1 vs Gemini 3 Pro Image)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "gemini-nano-banana-2.1" to "Nano Banana 2.1 (Fast)",
                "gemini-3-pro-image" to "Gemini 3 Pro Image (Studio)"
            ).forEach { (id, label) ->
                FilterChip(
                    selected = snapshot.selectedImageModel == id,
                    onClick = { onSelectImageModel(id) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // Aspect Ratio Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Aspect Ratio", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AspectRatioOption.entries.forEach { ratio ->
                    FilterChip(
                        selected = snapshot.selectedAspectRatio == ratio,
                        onClick = { onSelectAspectRatio(ratio) },
                        label = { Text(ratio.label) }
                    )
                }
            }
        }

        // Resolution Selector (1K, 2K, 4K)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Output Resolution", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageResolutionOption.entries.forEach { res ->
                    FilterChip(
                        selected = snapshot.selectedResolution == res,
                        onClick = { onSelectResolution(res) },
                        label = { Text(res.label) }
                    )
                }
            }
        }

        // Source Photo Preview (optional for editing or visual analysis)
        sourceBitmap?.let { bmp ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Selected source image",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Source image ready for AI editing or visual analysis.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { sourceBitmap = null }) {
                        Text("Clear")
                    }
                }
            }
        }

        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Creative Prompt or Analysis Question") },
            placeholder = { Text("e.g., Futuristic orbital colony at sunrise over Mars...") },
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("media_prompt_input")
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onGenerateOrEditImage(promptText, sourceBitmap) },
                enabled = promptText.isNotBlank() && snapshot.mediaState !is MediaStudioState.Loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("media_generate_button")
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (sourceBitmap != null) "Edit Image" else "Generate Image", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pick Photo")
            }

            if (sourceBitmap != null) {
                OutlinedButton(
                    onClick = { onAnalyzeImage(sourceBitmap!!, promptText) }
                ) {
                    Icon(Icons.Outlined.DocumentScanner, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Analyze Photo")
                }
            }

            OutlinedButton(
                onClick = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            ) {
                Icon(Icons.Outlined.VideoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Analyze Video")
            }
        }

        // Result Display
        when (val state = snapshot.mediaState) {
            is MediaStudioState.Idle -> Unit
            is MediaStudioState.Loading -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        Text("Processing with Gemini Media AI (may take 30-60s)...")
                    }
                }
            }
            is MediaStudioState.ImageSuccess -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = state.prompt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    sourceBitmap = state.bitmap
                                }
                            ) {
                                Text("Use as Edit Source")
                            }
                            OutlinedButton(
                                onClick = {
                                    onSavePromptToLibrary("Image Prompt: ${state.prompt.take(36)}", state.prompt)
                                }
                            ) {
                                Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save Prompt")
                            }
                        }
                    }
                }
            }
            is MediaStudioState.AnalysisSuccess -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        MarkdownText(markdown = state.text)
                    }
                }
            }
            is MediaStudioState.Error -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // Text-to-Speech Generator Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Gemini Neural Text-to-Speech", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = ttsInput,
                    onValueChange = { ttsInput = it },
                    label = { Text("Speech Script") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSpeakText(ttsInput) },
                    enabled = ttsInput.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Speak with Gemini TTS")
                }
            }
        }
    }
}
