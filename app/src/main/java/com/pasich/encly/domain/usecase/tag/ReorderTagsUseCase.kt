package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.repository.TagsRepository
import javax.inject.Inject

class ReorderTagsUseCase @Inject constructor(
    private val tagsRepository: TagsRepository
) {
    suspend operator fun invoke(tags: List<Tag>): Boolean =
        tagsRepository.updateTags(
            tags.mapIndexed { index, tag -> tag.copy(position = index) }
        )
}
