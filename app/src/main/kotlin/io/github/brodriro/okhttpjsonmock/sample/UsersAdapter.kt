package io.github.brodriro.okhttpjsonmock.sample

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.brodriro.okhttpjsonmock.sample.models.UserModel

class UsersAdapter : ListAdapter<UserModel, UsersAdapter.ViewHolder>(UserDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_users_adapter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val id: TextView = itemView.findViewById(R.id.user_id)
        private val name: TextView = itemView.findViewById(R.id.user_name)
        private val lastName: TextView = itemView.findViewById(R.id.user_last_name)
        private val age: TextView = itemView.findViewById(R.id.user_age)
        private val numbers: TextView = itemView.findViewById(R.id.user_numbers)

        fun bind(user: UserModel) {
            val context = itemView.context
            id.text = context.getString(R.string.user_id, user.id)
            name.text = context.getString(R.string.user_name, user.name)
            lastName.text = context.getString(R.string.user_last_name, user.lastName)
            age.text = context.getString(R.string.user_age, user.age)
            numbers.text = context.getString(R.string.user_numbers, user.phoneNumbers.orEmpty().joinToString("\n"))
        }
    }

    private object UserDiff : DiffUtil.ItemCallback<UserModel>() {
        override fun areItemsTheSame(oldItem: UserModel, newItem: UserModel) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UserModel, newItem: UserModel) = oldItem == newItem
    }
}
