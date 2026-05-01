package com.soundboard.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
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
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // TODO: implement file import via SampleRepository in a future group
            // Selected URIs available via result.data?.clipData (multi) or result.data?.data (single)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLibraryBinding.bind(view)

        adapter = LibraryAdapter { sample ->
            SampleEditBottomSheet.newInstance(sample.id)
                .show(childFragmentManager, SampleEditBottomSheet.TAG)
        }
        binding.recyclerLibrary.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLibrary.adapter = adapter

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class LibraryAdapter(
    private val onItemClick: (SampleEntity) -> Unit,
) : ListAdapter<SampleEntity, LibraryAdapter.ViewHolder>(DIFF) {

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
            binding.root.setOnClickListener { onItemClick(sample) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
