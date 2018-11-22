package me.shadura.escposprint.app

import android.support.v7.widget.RecyclerView

abstract class UndoableDeleteAdapter<T: RecyclerView.ViewHolder>: RecyclerView.Adapter<T>() {
    abstract fun postRemove(item: Any)

    abstract fun postRemoveAt(position: Int)

    abstract fun isPendingRemoval(position: Int): Boolean

    abstract fun undoRemove(item: Any)

    abstract fun remove(item: Any)

    abstract fun removeAt(position: Int)
}