package com.hupux.data

import kotlinx.datetime.Clock

/** 当前毫秒时间戳。 */
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
