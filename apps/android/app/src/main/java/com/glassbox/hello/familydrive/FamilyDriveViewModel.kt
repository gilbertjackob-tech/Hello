package com.glassbox.hello.familydrive

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyDriveUiState(
    val items: List<DriveItem> = emptyList(),
    val total: Int = 0,
    val nextCursor: Long? = null,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isUploading: Boolean = false,
    val uploadDone: Int = 0,
    val uploadTotal: Int = 0,
    val error: String? = null
)

class FamilyDriveViewModel : ViewModel() {
    private val repository = FamilyDriveRepository()
    private val pageSize = 60

    private val _state = MutableStateFlow(FamilyDriveUiState())
    val state: StateFlow<FamilyDriveUiState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null, nextCursor = null, hasMore = false) }
        viewModelScope.launch {
            val result = repository.fetchItems(limit = pageSize, before = null)
            _state.update { current ->
                result.fold(
                    onSuccess = { response ->
                        current.copy(
                            items = response.items,
                            total = response.total,
                            nextCursor = response.nextCursor,
                            hasMore = response.hasMore,
                            isLoading = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        current.copy(isLoading = false, error = error.message ?: "Drive load failed")
                    }
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || !current.hasMore || current.nextCursor == null) return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        viewModelScope.launch {
            val result = repository.fetchItems(limit = pageSize, before = current.nextCursor)
            _state.update { latest ->
                result.fold(
                    onSuccess = { response ->
                        val merged = (latest.items + response.items).distinctBy { it.id }
                        latest.copy(
                            items = merged,
                            total = response.total,
                            nextCursor = response.nextCursor,
                            hasMore = response.hasMore,
                            isLoadingMore = false,
                            error = null
                        )
                    },
                    onFailure = { error ->
                        latest.copy(isLoadingMore = false, error = error.message ?: "More photos could not load")
                    }
                )
            }
        }
    }

    fun upload(context: Context, uploaderId: String, uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isUploading) return
        _state.update {
            it.copy(
                isUploading = true,
                uploadDone = 0,
                uploadTotal = uris.size,
                error = null
            )
        }
        viewModelScope.launch {
            val result = repository.uploadUris(
                context = context.applicationContext,
                uris = uris,
                uploaderId = uploaderId,
                onProgress = { done, total ->
                    _state.update { it.copy(uploadDone = done, uploadTotal = total) }
                }
            )
            result.fold(
                onSuccess = {
                    _state.update { it.copy(isUploading = false, uploadDone = 0, uploadTotal = 0) }
                    refresh()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isUploading = false,
                            uploadDone = 0,
                            uploadTotal = 0,
                            error = error.message ?: "Upload failed"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
