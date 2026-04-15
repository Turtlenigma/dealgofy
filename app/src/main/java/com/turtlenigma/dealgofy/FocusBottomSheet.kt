package com.turtlenigma.dealgofy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FocusBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_CIRCLE_INDEX = "circle_index"

        fun newInstance(circleIndex: Int) = FocusBottomSheet().apply {
            arguments = Bundle().apply { putInt(ARG_CIRCLE_INDEX, circleIndex) }
        }
    }

    private var selectedMinutes = 10

    override fun getTheme() = R.style.DarkBottomSheetTheme

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_focus, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val card10 = view.findViewById<LinearLayout>(R.id.card10)
        val card20 = view.findViewById<LinearLayout>(R.id.card20)
        val card30 = view.findViewById<LinearLayout>(R.id.card30)
        val btnStart = view.findViewById<Button>(R.id.btnStartFocus)

        fun selectDuration(minutes: Int) {
            selectedMinutes = minutes
            card10.setBackgroundResource(
                if (minutes == 10) R.drawable.duration_card_selected else R.drawable.duration_card_unselected
            )
            card20.setBackgroundResource(
                if (minutes == 20) R.drawable.duration_card_selected else R.drawable.duration_card_unselected
            )
            card30.setBackgroundResource(
                if (minutes == 30) R.drawable.duration_card_selected else R.drawable.duration_card_unselected
            )
            // Update the number label colors to reflect selection
            updateCardLabelColors(card10, minutes == 10)
            updateCardLabelColors(card20, minutes == 20)
            updateCardLabelColors(card30, minutes == 30)
            btnStart.text = "Start $minutes min focus"
        }

        card10.setOnClickListener { selectDuration(10) }
        card20.setOnClickListener { selectDuration(20) }
        card30.setOnClickListener { selectDuration(30) }

        val circleIndex = arguments?.getInt(ARG_CIRCLE_INDEX, 0) ?: 0
        btnStart.setOnClickListener {
            FocusManager.startFocus(requireContext(), selectedMinutes)
            dismiss()
            (activity as? InterceptActivity)?.onFocusModeConfirmed(circleIndex, selectedMinutes)
        }

        // 10 min selected by default
        selectDuration(10)
    }

    /**
     * Tints the number TextView in a card lime when selected, grey when not.
     * Assumes the first child of the card LinearLayout is the number label.
     */
    private fun updateCardLabelColors(card: LinearLayout, selected: Boolean) {
        val numberColor = if (selected) 0xFFC8F542.toInt() else 0xFFE8E8E8.toInt()
        (card.getChildAt(0) as? android.widget.TextView)?.setTextColor(numberColor)
    }
}
