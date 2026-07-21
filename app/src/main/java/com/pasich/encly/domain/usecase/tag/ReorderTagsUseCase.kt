package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.data.model.Tag
import com.pasich.encly.data.repository.TagsRepository
import javax.inject.Inject

class ReorderTagsUseCase @Inject constructor(
    private val tagsRepository: TagsRepository
) {
    suspend operator fun invoke(tags: List<Tag>) {
        // Update the position for each tag
        tags.forEachIndexed { index, tag ->
            val updatedTag = tag.copy(position = index)
            tagsRepository.updateTag(updatedTag)
        }
    }
}
