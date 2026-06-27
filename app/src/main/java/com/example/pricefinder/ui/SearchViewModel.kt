package com.example.pricefinder.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pricefinder.data.ImageRecognizer
import com.example.pricefinder.data.PriceRepository
import com.example.pricefinder.data.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val name: String = "",
    val article: String = "",
    val model: String = "",
    val loading: Boolean = false,
    val recognizing: Boolean = false,
    val recognizedLabels: List<String> = emptyList(),
    val error: String? = null,
    val result: SearchResult? = null
)

class SearchViewModel(
    private val repo: PriceRepository = PriceRepository(),
    private val recognizer: ImageRecognizer = ImageRecognizer()
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onName(v: String) { _state.value = _state.value.copy(name = v) }
    fun onArticle(v: String) { _state.value = _state.value.copy(article = v) }
    fun onModel(v: String) { _state.value = _state.value.copy(model = v) }

    fun search() {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null, result = null)
        viewModelScope.launch {
            try {
                val result = repo.search(s.name, s.article, s.model)
                _state.value = _state.value.copy(loading = false, result = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false, error = e.message ?: "Ошибка запроса"
                )
            }
        }
    }

    /** Recognizes the photo, fills the name field with labels, then searches. */
    fun recognizeAndSearch(context: Context, uri: Uri) {
        _state.value = _state.value.copy(
            recognizing = true, error = null, result = null, recognizedLabels = emptyList()
        )
        viewModelScope.launch {
            try {
                val labels = recognizer.recognize(context, uri)
                if (labels.query.isBlank()) {
                    _state.value = _state.value.copy(
                        recognizing = false,
                        error = "Не удалось распознать объект на фото. Попробуйте другое фото или введите запрос вручную."
                    )
                    return@launch
                }
                _state.value = _state.value.copy(
                    recognizing = false,
                    loading = true,
                    name = labels.query,
                    recognizedLabels = labels.all
                )
                val result = repo.searchByQuery(labels.query)
                _state.value = _state.value.copy(loading = false, result = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    recognizing = false, loading = false,
                    error = e.message ?: "Ошибка распознавания"
                )
            }
        }
    }
}
