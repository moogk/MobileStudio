package com.mobileide.presentation.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.mobileide.R
import com.mobileide.databinding.FragmentEditorBinding
import com.mobileide.databinding.DialogFindBinding
import com.mobileide.domain.model.File
import com.mobileide.editor.AutoCompleteProvider
import com.mobileide.editor.CodeEditorOptimizer
import com.mobileide.editor.SyntaxHighlighter
import com.mobileide.presentation.main.MainViewModel
import com.mobileide.presentation.ui.UIOptimizer
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class EditorFragment : Fragment() {
    
    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!
    
    private val mainViewModel: MainViewModel by activityViewModels()
    private val editorViewModel: EditorViewModel by viewModels()
    private val syntaxHighlighter = SyntaxHighlighter()
    
    @Inject
    lateinit var autoCompleteProvider: AutoCompleteProvider
    
    @Inject
    lateinit var uiOptimizer: UIOptimizer
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupEditor()
        setupButtons()
        setupFileNavigation()
        observeViewModel()
        
        // Optimize editor UI
        context?.let {
            val editorOptimizer = CodeEditorOptimizer(it)
            editorOptimizer.optimizeEditor(binding.codeEditor)
            editorOptimizer.setupMobileKeyboardHelpers(binding.codeEditor)
            editorOptimizer.setupCodeSnippets(binding.codeEditor)
        }
        
        // Optimize editor UI layout
        activity?.let {
            uiOptimizer.optimizeEditorUI(it)
        }
    }
    
    private fun setupEditor() {
        // Initialize code editor
        binding.codeEditor.apply {
            setTheme("monokai")
            setFontSize(14)
            
            setOnTextChangedListener { text ->
                editorViewModel.updateCurrentFile(text)
            }
            
            // Set up auto-completion
            autoCompleteProvider.setupAutoComplete(this)
        }
    }
    
    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            editorViewModel.saveCurrentFile()
        }
        
        binding.btnUndo.setOnClickListener {
            binding.codeEditor.undo()
        }
        
        binding.btnRedo.setOnClickListener {
            binding.codeEditor.redo()
        }
        
        binding.btnFind.setOnClickListener {
            showFindDialog()
        }
    }
    
    private fun setupFileNavigation() {
        // Setup file tree in the navigation drawer
        binding.fileTreeView.setOnFileSelectedListener { file ->
            editorViewModel.selectFile(file)
        }
    }
    
    private fun observeViewModel() {
        editorViewModel.currentFile.observe(viewLifecycleOwner) { file ->
            file?.let {
                updateEditorWithFile(it)
            }
        }
        
        editorViewModel.projectFiles.observe(viewLifecycleOwner) { files ->
            binding.fileTreeView.setFiles(files)
        }
        
        // Observe current project from MainViewModel
        mainViewModel.currentProject.observe(viewLifecycleOwner) { project ->
            project?.let {
                editorViewModel.loadProjectFiles(it)
            }
        }
    }
    
    private fun updateEditorWithFile(file: File) {
        Timber.d("Updating editor with file: ${file.name}")
        
        // Set file content in editor
        binding.codeEditor.setText(file.content)
        
        // Set language mode based on file extension
        val languageMode = syntaxHighlighter.getLanguageModeForFile(file.path)
        binding.codeEditor.setLanguage(languageMode)
        
        // Update file name in toolbar
        binding.fileNameTextView.text = file.name
    }
    
    private fun showFindDialog() {
        val dialogBinding = DialogFindBinding.inflate(layoutInflater)
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.find_text)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.find) { _, _ ->
                val searchText = dialogBinding.searchText.text.toString()
                val matchCase = dialogBinding.matchCase.isChecked
                val wholeWord = dialogBinding.wholeWord.isChecked
                val regex = dialogBinding.regex.isChecked
                
                binding.codeEditor.find(searchText, matchCase, wholeWord, regex)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            
        dialogBinding.searchText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                return@setOnEditorActionListener true
            }
            false
        }
        
        dialog.show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}