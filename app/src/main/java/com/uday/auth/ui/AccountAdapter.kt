package com.uday.auth.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.uday.auth.R
import com.uday.auth.data.Account

/** The account list. Codes aren't shown here; tapping a row opens the account. */
class AccountAdapter(
    private val onClick: (Account) -> Unit,
) : ListAdapter<Account, AccountAdapter.AccountViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: TextView = view.findViewById(R.id.avatar)
        private val issuer: TextView = view.findViewById(R.id.issuer)
        private val label: TextView = view.findViewById(R.id.label)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
            }
        }

        fun bind(account: Account) {
            issuer.text = account.displayName
            label.text = account.label
            label.isVisible = account.issuer.isNotBlank() && account.label.isNotBlank()
            avatar.showAvatar(account.displayName)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Account>() {
            override fun areItemsTheSame(oldItem: Account, newItem: Account) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Account, newItem: Account) = oldItem == newItem
        }
    }
}
