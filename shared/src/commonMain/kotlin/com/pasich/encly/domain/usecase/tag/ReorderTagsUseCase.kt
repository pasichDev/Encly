package com.pasich.encly.domain.usecase.tag

import com.pasich.encly.data.model.Tag
import com.pasich.encly.domain.repository.TagsRepository
import dev.zacsweers.metro.Inject

/** Stores [tags]' list order as their positions. */
@Inject
class ReorderTagsUseCase(private val tagsRepository: TagsRepository) {
    suspend operator fun invoke(tags: List<Tag>): Result<Unit> = tagsRepository.updateTags(
        tags.mapIndexed { index, tag -> tag.copy(position = index) },
    )
}
