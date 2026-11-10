package com.pasich.encly.editor.model

/**
 * What a link card shows: its [title], an optional [imageUrl], the [url] it opens, and whether loading its
 * content failed ([isError]).
 */
public data class LinkDataBlock(
    var title: String = "",
    var imageUrl: String = "",
    var url: String = "",
    var isError: Boolean = false,

)
