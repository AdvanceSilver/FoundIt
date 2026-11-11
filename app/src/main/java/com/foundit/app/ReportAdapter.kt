
package com.foundit.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ReportAdapter(
    private val onTap: (Report) -> Unit,
    private val onDelete: ((Report) -> Unit)? = null,
    private val onChangeStatus: ((Report) -> Unit)? = null
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    private val items = mutableListOf<Report>()

    fun submit(list: List<Report>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)

        return ReportViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(
        holder: ReportViewHolder,
        position: Int
    ) {
        holder.bind(
            items[position],
            onTap,
            onDelete,
            onChangeStatus
        )
    }

    class ReportViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        private val card: View =
            view.findViewById(R.id.reportCardRoot)

        private val title: TextView =
            view.findViewById(R.id.itemTitle)

        private val meta: TextView =
            view.findViewById(R.id.itemMeta)

        private val location: TextView =
            view.findViewById(R.id.itemLocation)

        private val image: ImageView =
            view.findViewById(R.id.itemImage)

        private val status: TextView =
            view.findViewById(R.id.itemStatus)

        private val changeStatusButton: Button =
            view.findViewById(R.id.itemChangeStatusButton)

        private val deleteButton: Button =
            view.findViewById(R.id.itemDeleteButton)

        private fun glassBackground(
            color: Int,
            borderColor: Int,
            radius: Float
        ): GradientDrawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = radius
                setStroke(2, borderColor)
            }
        }

        fun bind(
            report: Report,
            onTap: (Report) -> Unit,
            onDelete: ((Report) -> Unit)?,
            onChangeStatus: ((Report) -> Unit)?
        ) {
            val lost = report.reportType.equals("lost", true)

            val accent = if (lost) {
                Color.rgb(255, 82, 106)
            } else {
                Color.rgb(85, 217, 149)
            }

            val cardBackground = if (lost) {
                Color.rgb(28, 19, 30)
            } else {
                Color.rgb(19, 29, 27)
            }

            card.background = glassBackground(
                cardBackground,
                Color.argb(150, accent.red(), accent.green(), accent.blue()),
                24f
            )

            title.text =
                (if (lost) "🚨 LOST: " else "✓ FOUND: ") +
                report.title

            title.setTextColor(accent)

            meta.text = "🏷  ${report.category}"
            location.text = "📍  ${report.location}"

            meta.setTextColor(Color.rgb(190, 190, 205))
            location.setTextColor(Color.rgb(190, 190, 205))

            val photoUrl = report.imageUrl

            if (!photoUrl.isNullOrBlank()) {
                image.visibility = View.VISIBLE

                image.background = glassBackground(
                    Color.rgb(30, 30, 42),
                    Color.argb(100, accent.red(), accent.green(), accent.blue()),
                    18f
                )

                image.clipToOutline = true

                Glide.with(image)
                    .load(photoUrl)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(image)
            } else {
                Glide.with(image).clear(image)
                image.visibility = View.GONE
            }

            if (onChangeStatus != null) {
                status.visibility = View.VISIBLE

                val statusText = when (report.status.lowercase()) {
                    "found" -> "Item Found"
                    "returned" -> "Returned to Owner"
                    "closed" -> "Closed"
                    else -> "Active"
                }

                status.text = "●  Status: $statusText"
                status.setTextColor(accent)

                changeStatusButton.visibility = View.VISIBLE
                changeStatusButton.text = "Change Status"
                changeStatusButton.setTextColor(accent)
                changeStatusButton.background = glassBackground(
                    Color.argb(35, accent.red(), accent.green(), accent.blue()),
                    Color.argb(180, accent.red(), accent.green(), accent.blue()),
                    30f
                )
                changeStatusButton.backgroundTintList = null
                changeStatusButton.stateListAnimator = null

                changeStatusButton.setOnClickListener {
                    onChangeStatus(report)
                }
            } else {
                status.visibility = View.GONE
                changeStatusButton.visibility = View.GONE
                changeStatusButton.setOnClickListener(null)
            }

            if (onDelete != null) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.text = "Delete Report"
                deleteButton.setTextColor(Color.rgb(255, 110, 125))
                deleteButton.background = glassBackground(
                    Color.rgb(45, 23, 31),
                    Color.rgb(180, 65, 85),
                    30f
                )
                deleteButton.backgroundTintList = null
                deleteButton.stateListAnimator = null

                deleteButton.setOnClickListener {
                    onDelete(report)
                }
            } else {
                deleteButton.visibility = View.GONE
                deleteButton.setOnClickListener(null)
            }

            itemView.setOnClickListener {
                onTap(report)
            }
        }

        private fun Int.red() = Color.red(this)
        private fun Int.green() = Color.green(this)
        private fun Int.blue() = Color.blue(this)
    }
}
