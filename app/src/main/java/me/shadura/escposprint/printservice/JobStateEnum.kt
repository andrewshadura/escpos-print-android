/**
 * Copyright (C) 2009 Harald Weyhing
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
enum class JobStateEnum private constructor(val text: String) {
    PENDING("pending"),
    PENDING_HELD("pending-held"),
    PROCESSING("processing"),
    PROCESSING_STOPPED("processing-stopped"),
    CANCELED("canceled"),
    ABORTED("aborted"),
    COMPLETED("completed");

    override fun toString(): String {
        return text
    }

    companion object {

        fun fromString(value: String?): JobStateEnum? {
            if (value != null) {
                for (jobState in JobStateEnum.values()) {
                    if (value.equals(jobState.text, ignoreCase = true)) {
                        return jobState
                    }
                }
            }
            return null
        }
    }
}
