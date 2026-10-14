package com.example.rm014s

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BrandAdapter(
    private val profiles: List<RemoteProfile>,
    private val onProfileSelected: (RemoteProfile) -> Unit,
    private val onTestProfile: (RemoteProfile) -> Unit
) : RecyclerView.Adapter<BrandAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBrandCombination: TextView = view.findViewById(R.id.tvBrandCombination)
        val tvBrandName: TextView = view.findViewById(R.id.tvBrandName)
        val tvBrandCodeInfo: TextView = view.findViewById(R.id.tvBrandCodeInfo)
        val btnTestPreset: Button = view.findViewById(R.id.btnTestPreset)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_brand_preset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val profile = profiles[position]
        holder.tvBrandCombination.text = profile.brandButtonLabel
        holder.tvBrandName.text = profile.name

        val khz = (profile.frequency / 100) / 10.0
        holder.tvBrandCodeInfo.text = holder.itemView.context.getString(
            R.string.brand_code_info, profile.codeNumber, profile.base.describe(),
            if (khz % 1.0 == 0.0) khz.toInt().toString() else khz.toString()
        )

        holder.itemView.setOnClickListener {
            onProfileSelected(profile)
        }

        holder.btnTestPreset.setOnClickListener {
            onTestProfile(profile)
        }
    }

    override fun getItemCount(): Int = profiles.size
}
