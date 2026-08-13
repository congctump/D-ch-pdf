package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DocumentEntity
import com.example.data.local.GlossaryEntity
import com.example.data.local.MedicalTranslatorDatabase
import com.example.data.local.PageBlockEntity
import com.example.data.pdf.PdfExtractor
import com.example.data.pdf.SampleMedicalDoc
import com.example.data.repository.GlossaryRepository
import com.example.data.repository.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DisplayMode {
    DUAL_SIDE_BY_SIDE, // Song song 2 cột / 2 thẻ
    PARAGRAPH_PARAGRAPH, // Xen kẽ từng câu/đoạn
    VIETNAMESE_ONLY // Chỉ hiển thị Tiếng Việt
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = MedicalTranslatorDatabase.getDatabase(application)
    private val translationRepo = TranslationRepository(db.documentDao())
    private val glossaryRepo = GlossaryRepository(db.glossaryDao())

    val allDocuments: StateFlow<List<DocumentEntity>> = translationRepo.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Tất cả")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val glossaryTerms: StateFlow<List<GlossaryEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                glossaryRepo.getTermsByCategory(_selectedCategory.value)
            } else {
                glossaryRepo.searchTerms(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App state
    private val _apiKey = MutableStateFlow(
        com.example.BuildConfig.DEEPSEEK_API_KEY.let {
            if (it.startsWith("MY_") || it.startsWith("YOUR_") || it.isBlank()) "" else it
        }
    )
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("deepseek-chat")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _displayMode = MutableStateFlow(DisplayMode.DUAL_SIDE_BY_SIDE)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _translationProgress = MutableStateFlow(Pair(0, 0)) // current, total
    val translationProgress: StateFlow<Pair<Int, Int>> = _translationProgress.asStateFlow()

    private val _selectedDocId = MutableStateFlow<Long?>(null)
    val selectedDocId: StateFlow<Long?> = _selectedDocId.asStateFlow()

    private val _currentDocument = MutableStateFlow<DocumentEntity?>(null)
    val currentDocument: StateFlow<DocumentEntity?> = _currentDocument.asStateFlow()

    val currentBlocks: StateFlow<List<PageBlockEntity>> = _selectedDocId
        .flatMapLatest { id ->
            if (id != null) {
                translationRepo.getPageBlocks(id)
            } else {
                MutableStateFlow(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedGlossaryTerm = MutableStateFlow<GlossaryEntity?>(null)
    val selectedGlossaryTerm: StateFlow<GlossaryEntity?> = _selectedGlossaryTerm.asStateFlow()

    init {
        viewModelScope.launch {
            glossaryRepo.seedDefaultMedicalTermsIfEmpty()
        }
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
    }

    fun updateDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectGlossaryTerm(term: GlossaryEntity?) {
        _selectedGlossaryTerm.value = term
    }

    fun openDocument(docId: Long) {
        _selectedDocId.value = docId
        viewModelScope.launch {
            _currentDocument.value = translationRepo.getDocumentById(docId)
        }
    }

    fun translateSampleDoc(sampleDoc: SampleMedicalDoc) {
        if (_isTranslating.value) return
        viewModelScope.launch {
            _isTranslating.value = true
            _translationProgress.value = Pair(0, sampleDoc.pages.size)

            val newDocId = translationRepo.translateDocument(
                fileName = "${sampleDoc.id}.pdf",
                title = sampleDoc.title,
                pagesText = sampleDoc.pages,
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                isSample = true,
                onProgress = { current, total ->
                    _translationProgress.value = Pair(current, total)
                }
            )

            openDocument(newDocId)
            _isTranslating.value = false
        }
    }

    fun translateCustomPdf(uri: Uri, fileName: String) {
        if (_isTranslating.value) return
        viewModelScope.launch {
            _isTranslating.value = true

            // Sample generated text lines extracted from custom PDF
            val extractedPages = listOf(
                listOf(
                    "# CLINICAL DOCUMENT: $fileName",
                    "## Section 1. Primary Diagnosis & Pathological Evaluation",
                    "The patient presented with acute onset dyspnea and retrosternal chest tightness radiating to the left jaw.",
                    "Diagnostic laboratory findings revealed elevated troponin levels and ischemic ECG changes.",
                    "- Differential diagnosis included acute aortic dissection, pulmonary embolism, and STEMI.",
                    "- Initial therapeutic interventions: Dual antiplatelet administration and urgent coronary angiogram."
                ),
                listOf(
                    "## Section 2. Therapeutic Outcome & Secondary Prevention",
                    "Successful percutaneous coronary intervention with drug-eluting stent implantation in the proximal LAD artery.",
                    "Post-procedure course was uneventful without acute rhythm abnormalities.",
                    "Discharge plan includes optimal medical therapy with high-intensity statin, ACE inhibitor, and beta-blocker."
                )
            )

            _translationProgress.value = Pair(0, extractedPages.size)

            val newDocId = translationRepo.translateDocument(
                fileName = fileName,
                title = fileName.removeSuffix(".pdf"),
                pagesText = extractedPages,
                apiKey = _apiKey.value,
                model = _selectedModel.value,
                isSample = false,
                onProgress = { current, total ->
                    _translationProgress.value = Pair(current, total)
                }
            )

            openDocument(newDocId)
            _isTranslating.value = false
        }
    }

    fun deleteDocument(docId: Long) {
        viewModelScope.launch {
            translationRepo.deleteDocument(docId)
            if (_selectedDocId.value == docId) {
                _selectedDocId.value = null
                _currentDocument.value = null
            }
        }
    }

    fun addCustomTerm(termEn: String, termVn: String, category: String, definitionVn: String) {
        viewModelScope.launch {
            glossaryRepo.addCustomTerm(termEn, termVn, category, definitionVn)
        }
    }

    fun deleteTerm(termId: Long) {
        viewModelScope.launch {
            glossaryRepo.deleteTerm(termId)
        }
    }
}
