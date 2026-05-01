package com.soundboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.soundboard.databinding.BottomSheetSampleEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SampleEditBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SampleEditBottomSheet"
        private const val ARG_SAMPLE_ID = "sample_id"

        fun newInstance(sampleId: String): SampleEditBottomSheet =
            SampleEditBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_SAMPLE_ID, sampleId) }
            }
    }

    private var _binding: BottomSheetSampleEditBinding? = null
    private val binding get() = _binding!!

    // Shared with LibraryFragment via the host fragment's parent viewmodel scope
    private val viewModel: LibraryViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetSampleEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sampleId = requireArguments().getString(ARG_SAMPLE_ID)!!

        viewLifecycleOwner.lifecycleScope.launch {
            val sample = viewModel.samples.value.firstOrNull { it.id == sampleId } ?: run {
                dismiss()
                return@launch
            }

            // Populate fields
            binding.editName.setText(sample.name)
            binding.sliderVolume.value = sample.defaultVolume.toFloat().coerceIn(0f, 400f)
            binding.switchLoop.isChecked = sample.loop
            updateLoopFieldsVisibility(sample.loop)
            binding.editLoopStart.setText(if (sample.loopStartMs > 0) sample.loopStartMs.toString() else "")
            binding.editLoopEnd.setText(if (sample.loopEndMs > 0) sample.loopEndMs.toString() else "")

            binding.switchLoop.setOnCheckedChangeListener { _, checked ->
                updateLoopFieldsVisibility(checked)
            }

            binding.btnSave.setOnClickListener {
                val updated = sample.copy(
                    name = binding.editName.text?.toString()?.trim()?.ifBlank { sample.name } ?: sample.name,
                    defaultVolume = binding.sliderVolume.value.toInt(),
                    loop = binding.switchLoop.isChecked,
                    loopStartMs = binding.editLoopStart.text?.toString()?.toLongOrNull() ?: 0L,
                    loopEndMs = binding.editLoopEnd.text?.toString()?.toLongOrNull() ?: 0L,
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.updateSample(updated)
                    dismiss()
                }
            }

            binding.btnDelete.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete \"${sample.name}\"?")
                    .setMessage("This will permanently remove the sound from your library.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            viewModel.deleteSample(sample)
                            dismiss()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun updateLoopFieldsVisibility(loopEnabled: Boolean) {
        val visibility = if (loopEnabled) View.VISIBLE else View.GONE
        binding.layoutLoopStart.visibility = visibility
        binding.layoutLoopEnd.visibility = visibility
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
