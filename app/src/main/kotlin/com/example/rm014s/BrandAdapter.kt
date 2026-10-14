package com.example.rm014s

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BrandAdapter(
    private val brands: List<BrandPreset>,
    private val onBrandSelected: (BrandPreset) -> Unit
) : RecyclerView.Adapter<BrandAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBrandHex: TextView = view.findViewById(R.id.tvBrandHex)
        val tvBrandName: TextView = view.findViewById(R.id.tvBrandName)
        val tvBrandDesc: TextView = view.findViewById(R.id.tvBrandDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_brand_preset, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val preset = brands[position]
        val hex = String.format("0x%02X", preset.defaultAddress)
        holder.tvBrandHex.text = hex
        holder.tvBrandName.text = preset.name
        holder.tvBrandDesc.text = preset.description

        holder.itemView.setOnClickListener {
            onBrandSelected(preset)
        }
    }

    override fun getItemCount(): Int = brands.size
}
