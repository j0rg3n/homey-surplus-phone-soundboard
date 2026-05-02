package com.soundboard.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.soundboard.BuildConfig
import com.soundboard.Constants
import com.soundboard.R
import com.soundboard.databinding.FragmentSettingsBinding
import com.soundboard.service.PlaybackService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        val prefs = requireContext().getSharedPreferences(Constants.SETTINGS_PREFS, Context.MODE_PRIVATE)

        val currentName = prefs.getString(Constants.PREF_DEVICE_NAME, Build.MODEL) ?: Build.MODEL
        val currentPort = prefs.getInt(Constants.PREF_PORT, Constants.DEFAULT_PORT)

        binding.editDeviceName.setText(currentName)
        binding.editPort.setText(currentPort.toString())
        binding.textVersion.text = "Version ${BuildConfig.VERSION_NAME}"

        binding.btnAdvanced.setOnClickListener {
            val visible = binding.layoutAdvanced.visibility == View.VISIBLE
            binding.layoutAdvanced.visibility = if (visible) View.GONE else View.VISIBLE
            binding.btnAdvanced.text = if (visible) "Advanced ▸" else "Advanced ▾"
        }

        binding.btnDevTips.setOnClickListener {
            val visible = binding.layoutDevTips.visibility == View.VISIBLE
            binding.layoutDevTips.visibility = if (visible) View.GONE else View.VISIBLE
        }

        binding.btnSave.setOnClickListener {
            val name = binding.editDeviceName.text?.toString()?.trim()
                ?.ifBlank { Build.MODEL } ?: Build.MODEL
            val port = binding.editPort.text?.toString()?.toIntOrNull()
                ?.coerceIn(1, 65535) ?: Constants.DEFAULT_PORT

            val oldPort = prefs.getInt(Constants.PREF_PORT, Constants.DEFAULT_PORT)

            prefs.edit()
                .putString(Constants.PREF_DEVICE_NAME, name)
                .putInt(Constants.PREF_PORT, port)
                .apply()

            if (port != oldPort) {
                restartPlaybackService()
            }
        }

        binding.btnStopService.setOnClickListener {
            requireContext().stopService(Intent(requireContext(), PlaybackService::class.java))
        }
    }

    internal fun restartPlaybackService() {
        val ctx = requireContext()
        ctx.stopService(Intent(ctx, PlaybackService::class.java))
        val intent = Intent(ctx, PlaybackService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
