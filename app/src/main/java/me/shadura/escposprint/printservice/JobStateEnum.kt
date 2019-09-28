/**
 * Copyright (C) 2019 Andrej Shadura
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details. You should have
 * received a copy of the GNU Lesser General Public License along with this
 * program; if not, see <http://www.gnu.org/licenses/>.
 */
package me.shadura.escposprint.printservice

/**
 * State of print jobs
 */
sealed class JobStateEnum {
    data class FAILED(val errorMessage: String? = null) : JobStateEnum()
    object CREATED : JobStateEnum()
    object QUEUED : JobStateEnum()
    object STARTED : JobStateEnum()
    object BLOCKED : JobStateEnum()
    object COMPLETED : JobStateEnum()
    object CANCELED : JobStateEnum()
}
