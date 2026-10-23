package com.hupux.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 用于把阻塞/IO 工作从主线程挪开的调度器。
 * 注：Ktor/SQLDelight 本身已在自有线程上异步执行，这里主要是统一 offload 入口。
 */
internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
