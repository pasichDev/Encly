package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagSelectionRepository
import javax.inject.Inject

class SelectTagUseCase @Inject constructor(
    private val tagSelectionRepository: TagSelectionRepository
) {
    operator fun invoke(tag: Tag) {
        tagSelectionRepository.selectTag(tag)
    }
}