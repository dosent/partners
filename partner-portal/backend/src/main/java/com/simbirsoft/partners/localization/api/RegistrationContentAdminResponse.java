package com.simbirsoft.partners.localization.api;

import com.simbirsoft.partners.localization.application.RegistrationContentService.AdminState;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.DraftState;
import com.simbirsoft.partners.localization.application.RegistrationContentStore.PublishedState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Черновик и текущая опубликованная версия локали")
public record RegistrationContentAdminResponse(
        String locale,
        long draftVersion,
        RegistrationContentValueResponse draft,
        String draftUpdatedBy,
        Instant draftUpdatedAt,
        PublishedRegistrationContentResponse published) {
    public static RegistrationContentAdminResponse from(AdminState state) {
        DraftState draft = state.draft();
        PublishedState published = state.published();
        return new RegistrationContentAdminResponse(state.locale(), draft == null ? 0 : draft.draftVersion(),
                draft == null ? null : RegistrationContentValueResponse.from(draft.content()),
                draft == null ? null : draft.updatedBy(), draft == null ? null : draft.updatedAt(),
                published == null ? null : PublishedRegistrationContentResponse.from(published));
    }

    @Schema(description = "Метаданные опубликованной неизменяемой версии")
    public record PublishedRegistrationContentResponse(
            int contentVersion,
            RegistrationContentValueResponse content,
            String contentSha256,
            String publishedBy,
            Instant publishedAt) {
        static PublishedRegistrationContentResponse from(PublishedState state) {
            return new PublishedRegistrationContentResponse(state.contentVersion(),
                    RegistrationContentValueResponse.from(state.content()), state.checksum(),
                    state.publishedBy(), state.publishedAt());
        }
    }
}
