package com.soundboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.soundboard.R
import com.soundboard.data.ActivePlayback
import com.soundboard.databinding.FragmentNowPlayingBinding
import com.soundboard.databinding.ItemNowPlayingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NowPlayingFragment : Fragment(R.layout.fragment_now_playing) {

    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NowPlayingViewModel by viewModels()
    private lateinit var adapter: NowPlayingAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNowPlayingBinding.bind(view)

        adapter = NowPlayingAdapter { playback ->
            viewModel.stop(playback.handle)
        }
        binding.recyclerNowPlaying.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNowPlaying.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.active.collectLatest { map ->
                val list = map.values.toList()
                adapter.submitList(list)
                binding.textEmpty.visibility = if (list.isEmpty()) VISIBLE else GONE
                binding.recyclerNowPlaying.visibility = if (list.isEmpty()) GONE else VISIBLE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isMuted.collectLatest { muted ->
                binding.tvMutedBanner.visibility = if (muted) VISIBLE else GONE
            }
        }
        // TODO: tap banner to unmute (requires request channel from repository to PlaybackService)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class NowPlayingAdapter(
    private val onStop: (ActivePlayback) -> Unit,
) : ListAdapter<ActivePlayback, NowPlayingAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ActivePlayback>() {
            override fun areItemsTheSame(a: ActivePlayback, b: ActivePlayback) = a.handle == b.handle
            override fun areContentsTheSame(a: ActivePlayback, b: ActivePlayback) = a == b
        }
    }

    inner class ViewHolder(val binding: ItemNowPlayingBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playback: ActivePlayback) {
            binding.textSoundName.text = playback.sampleName
            binding.btnStop.setOnClickListener { onStop(playback) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemNowPlayingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
