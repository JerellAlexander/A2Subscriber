package com.example.a2subscriber

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

class StudentAdapter(
    private var studentList: MutableList<StudentInfo>,
    private val listener: OnMoreButtonClickListener
) : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentIdView: TextView = itemView.findViewById(R.id.studentId)
        val maxSpeedView: TextView = itemView.findViewById(R.id.maxspd)
        val minSpeedView: TextView = itemView.findViewById(R.id.minspd)
        val viewMoreButton: Button = itemView.findViewById(R.id.moreBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.student, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return studentList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val student = studentList[position]

        holder.studentIdView.text = student.id.toString()
        holder.maxSpeedView.text = "Max Speed: " + String.format("%.1f", student.maxSpd)
        holder.minSpeedView.text = "Min Speed: " + String.format("%.1f", student.minSpd)

        holder.viewMoreButton.setOnClickListener {
            listener.onMoreButtonClick(student)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshStudentList(newStudents: MutableList<StudentInfo>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = studentList.size
            override fun getNewListSize() = newStudents.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return studentList[oldItemPosition].id == newStudents[newItemPosition].id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return studentList[oldItemPosition] == newStudents[newItemPosition]
            }
        })

        studentList.clear()
        studentList.addAll(newStudents)
        diffResult.dispatchUpdatesTo(this)
    }
}
