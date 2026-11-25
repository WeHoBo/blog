package com.blog.front.controller;

import com.blog.common.mapper.SiteConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SiteConfigControllerTest {
    private final SiteConfigMapper mapper = mock(SiteConfigMapper.class);
    private final SiteConfigController controller = new SiteConfigController(mapper);

    @Test
    void invalidLaterFieldPreventsAllWrites() {
        var changes = new LinkedHashMap<String, String>();
        changes.put("bgImage", "/uploads/new.png");
        changes.put("aboutContent", "x".repeat(20001));
        var error = assertThrows(ResponseStatusException.class, () -> controller.save(changes));
        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(mapper);
    }

    @Test
    void changingOnlyTheBackgroundWritesOneKey() {
        controller.save(Map.of("bgImage", "/uploads/new.png"));
        verify(mapper).upsert("bgImage", "/uploads/new.png");
        verifyNoMoreInteractions(mapper);
    }
}
