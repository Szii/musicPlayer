package org.dnd.utils;

import org.dnd.api.model.UserAuthDTO;
import org.dnd.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserAuthDTO) {
            return ((UserAuthDTO) authentication.getPrincipal()).getId();
        }
        throw new ForbiddenException("Not authenticated");
    }
}
