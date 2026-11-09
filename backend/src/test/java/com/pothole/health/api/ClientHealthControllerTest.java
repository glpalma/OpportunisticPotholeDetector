package com.pothole.health.api;

import java.time.Clock;

import com.pothole.client.domain.ClientRegistry;
import com.pothole.config.RateLimitFilter;
import com.pothole.health.domain.DatabaseProbe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClientHealthController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class ClientHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseProbe database;

    @MockitoBean
    private ClientRegistry clients;

    @MockitoBean
    private Clock clock;

    @Test
    void databaseDownReturns503() throws Exception {
        when(database.selectOne()).thenReturn(false);

        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.service").value("pothole-api"))
                .andExpect(jsonPath("$.status").value("db_unavailable"));

        verify(clients, never()).upsert(any(), any(), any());
    }
}
