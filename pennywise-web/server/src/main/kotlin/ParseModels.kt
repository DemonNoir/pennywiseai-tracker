package com.example

data class ParseRequest(val smsBody: String, val sender: String, val timestamp: Long)

data class ParseManyRequest(val items: List<ParseRequest>)

data class ParseResult<T>(val ok: Boolean, val data: T?, val message: String? = null)
