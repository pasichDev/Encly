package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.repository.TagsRepository
import javax.inject.Inject

class UpdateTagUseCase @Inject constructor(private val tagsRepository: TagsRepository) {
    suspend operator fun invoke(tag: Tag): Boolean = tagsRepository.updateTag(tag)
}
