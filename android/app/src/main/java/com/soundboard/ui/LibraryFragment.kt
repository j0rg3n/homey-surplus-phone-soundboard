package com.soundboard.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.soundboard.R
import com.soundboard.data.SampleEntity
import com.soundboard.databinding.FragmentLibraryBinding
import com.soundboard.databinding.ItemLibraryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LibraryFragment : Fragment(R.layout.fragment_library) {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var adapter: LibraryAdapter

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val uris = if (data.clipData != null) {
                (0 until data.clipData!!.itemCount).map { data.clipData!!.getItemAt(it).uri }
            } else {
                listOfNotNull(data.data)
            }
            if (uris.isNotEmpty()) viewModel.importUris(requireContext(), uris)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLibraryBinding.bind(view)

        adapter = LibraryAdapter(
            onPreview = { sample -> viewModel.previewSound(sample) },
            onOverflow = { sample, anchor -> showOverflowMenu(sample, anchor) },
        )
        binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLibrary.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val sample = adapter.currentList[pos]
                adapter.notifyItemChanged(pos)
                if (direction == ItemTouchHelper.LEFT) {
                    SampleEditBottomSheet.newInstance(sample.id)
                        .show(childFragmentManager, SampleEditBottomSheet.TAG)
                } else {
                    showDeleteConfirmation(sample)
                }
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerLibrary)

        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        binding.fabImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            importLauncher.launch(Intent.createChooser(intent, "Select audio files"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.samples.collectLatest { samples ->
                adapter.submitList(samples)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.previewingSampleId.collectLatest { id ->
                adapter.setPreviewingId(id)
            }
        }
    }

    private fun showDeleteConfirmation(sample: SampleEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete \"${sample.name}\"?")
            .setMessage("This will permanently remove the sound from your library.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteSample(sample)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverflowMenu(sample: SampleEntity, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, MENU_EDIT, 0, "Edit")
            menu.add(0, MENU_DELETE, 1, "Delete")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> {
                        SampleEditBottomSheet.newInstance(sample.id)
                            .show(childFragmentManager, SampleEditBottomSheet.TAG)
                        true
                    }
                    MENU_DELETE -> {
                        showDeleteConfirmation(sample)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_DELETE = 2
    }
}

private class LibraryAdapter(
    private val onPreview: (SampleEntity) -> Unit,
    private val onOverflow: (SampleEntity, View) -> Unit,
) : ListAdapter<SampleEntity, LibraryAdapter.ViewHolder>(DIFF) {

    private var previewingId: String? = null

    fun setPreviewingId(id: String?) {
        val old = previewingId
        previewingId = id
        currentList.forEachIndexed { idx, s ->
            if (s.id == old || s.id == id) notifyItemChanged(idx)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SampleEntity>() {
            override fun areItemsTheSame(a: SampleEntity, b: SampleEntity) = a.id == b.id
            override fun areContentsTheSame(a: SampleEntity, b: SampleEntity) = a == b
        }

        fun formatDuration(ms: Long): String =
            "%d:%02d".format(ms / 60000, (ms % 60000) / 1000)
    }

    inner class ViewHolder(val binding: ItemLibraryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(sample: SampleEntity) {
            binding.textSampleName.text = sample.name
            binding.textDuration.text = formatDuration(sample.durationMs)
            val loopVisibility = if (sample.loop) View.VISIBLE else View.GONE
            binding.iconLoop.visibility = loopVisibility
            binding.textLoopIndicator.visibility = loopVisibility
            binding.iconPreview.visibility = if (sample.id == previewingId) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onPreview(sample) }
            binding.btnOverflow.setOnClickListener { onOverflow(sample, it) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
