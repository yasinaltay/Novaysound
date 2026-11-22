package com.novay.sound.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.novay.sound.data.model.Song
import com.novay.sound.data.youtube.SearchFilter
import com.novay.sound.data.youtube.SearchPage
import com.novay.sound.data.youtube.YouTubeRepository
import com.novay.sound.ui.components.EmptyState
import com.novay.sound.ui.components.SongActions
import com.novay.sound.ui.components.SongRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUi {
    data object Idle : SearchUi
    data object Loading : SearchUi
    data class Results(val songs: List<Song>, val canLoadMore: Boolean, val loadingMore: Boolean = false) : SearchUi
    data class Error(val message: String) : SearchUi
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val youtube: YouTubeRepository
) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(SearchFilter.SONGS)

    private val _ui = MutableStateFlow<SearchUi>(SearchUi.Idle)
    val ui = _ui.asStateFlow()

    val suggestions = query.debounce(300).mapLatest { q ->
        if (q.length < 2 || youtube.parseLink(q) != null) emptyList() else youtube.suggestions(q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var page: SearchPage? = null
    private var job: Job? = null

    fun isLink(text: String) = youtube.parseLink(text) != null

    fun search(q: String = query.value) {
        if (q.isBlank()) return
        query.value = q
        job?.cancel()
        job = viewModelScope.launch {
            _ui.value = SearchUi.Loading
            try {
                val result = youtube.search(q.trim(), filter.value)
                page = result
                _ui.value = SearchUi.Results(result.songs, result.hasMore)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = SearchUi.Error(e.message ?: "Arama başarısız")
            }
        }
    }

    fun setFilter(f: SearchFilter) {
        filter.value = f
        if (query.value.isNotBlank() && _ui.value !is SearchUi.Idle) search()
    }

    fun loadMore() {
        val p = page ?: return
        val cur = _ui.value as? SearchUi.Results ?: return
        if (!cur.canLoadMore || cur.loadingMore) return
        _ui.value = cur.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { youtube.searchMore(p) }
                .onSuccess {
                    page = it
                    _ui.value = SearchUi.Results((cur.songs + it.songs).distinctBy { s -> s.id }, it.hasMore)
                }
                .onFailure { _ui.value = cur.copy(loadingMore = false, canLoadMore = false) }
        }
    }

    fun clear() {
        job?.cancel()
        query.value = ""
        _ui.value = SearchUi.Idle
    }
}

@Composable
fun SearchScreen(
    actions: SongActions,
    currentId: String?,
    onLink: (String) -> Boolean,
    vm: SearchViewModel = hiltViewModel()
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }

    fun submit(text: String = query) {
        keyboard?.hide()
        if (vm.isLink(text)) { onLink(text); vm.clear() } else vm.search(text)
    }

    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            placeholder = { Text("Şarkı, sanatçı veya YouTube linki") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = vm::clear) { Icon(Icons.Default.Close, "Temizle") }
                    } else {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { submit(it) }
                        }) { Icon(Icons.Default.ContentPaste, "Linki yapıştır") }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus)
        )

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == SearchFilter.SONGS, onClick = { vm.setFilter(SearchFilter.SONGS) },
                label = { Text("Şarkılar") })
            FilterChip(selected = filter == SearchFilter.VIDEOS, onClick = { vm.setFilter(SearchFilter.VIDEOS) },
                label = { Text("Videolar") })
        }

        when (val state = ui) {
            SearchUi.Idle -> LazyColumn(Modifier.fillMaxSize()) {
                items(suggestions) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { submit(s) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.width(16.dp))
                        Text(s, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            SearchUi.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SearchUi.Error -> EmptyState(Icons.Default.ErrorOutline, state.message)
            is SearchUi.Results -> {
                val listState = rememberLazyListState()
                val nearEnd by remember {
                    derivedStateOf {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        last >= listState.layoutInfo.totalItemsCount - 4
                    }
                }
                LaunchedEffect(nearEnd) { if (nearEnd) vm.loadMore() }

                if (state.songs.isEmpty()) EmptyState(Icons.Default.Search, "Sonuç bulunamadı")
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(state.songs, key = { _, s -> s.id }) { _, song ->
                        // Arama sonucuna dokununca: şarkı + benzerleri (radyo)
                        SongRow(song, song.id == currentId, actions, onClick = { actions.radio(song) })
                    }
                    if (state.loadingMore) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
