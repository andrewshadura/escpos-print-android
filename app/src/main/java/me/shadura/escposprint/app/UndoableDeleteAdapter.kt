package me.shadura.escposprint.app

import androidx.recyclerview.widget.RecyclerView

abstract class UndoableDeleteAdapter<T: androidx.recyclerview.widget.RecyclerView.ViewHolder>: androidx.recyclerview.widget.RecyclerView.Adapter<T>() {
    abstract fun postRemove(item: Any)

    abstract fun postRemoveAt(position: Int)

    abstract fun isPendingRemoval(position: Int): Boolean

    abstract fun undoRemove(item: Any)

    abstract fun remove(item: Any)

    abstract fun removeAt(position: Int)
}