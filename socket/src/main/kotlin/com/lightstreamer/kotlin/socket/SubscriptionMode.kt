/*
 * Copyright (C) 2024 Lightstreamer Srl
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lightstreamer.kotlin.socket

/**
 * Allowed subscription mode.
 *
 * @param supportSnapshot true if snapshot is supported
 * @param supportEndOfSnapshot true if protocol support the end of snapshot, otherwise it is implicit (MERGE) or unsupported (RAW)
 */
@Suppress("unused")
public enum class SubscriptionMode(public val supportSnapshot: Boolean, public val supportEndOfSnapshot: Boolean) {
    COMMAND(true, true),
    DISTINCT(true, true),
    MERGE(true, false),
    RAW(false, false)
}
