package lab.lesson.order.web;

import lab.lesson.common.SecurityConfig;
import lab.lesson.idem.IdempotencyService;
import lab.lesson.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that add-line rejects a missing Idempotency-Key with HTTP 400
 * ({@code @RequestHeader(..., required = true)}), before the handler runs.
 */
@WebMvcTest(controllers = OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerAddLineMissingKeyMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private IdempotencyService idempotencyService;

    @Test
    void addLineWithoutIdempotencyKeyReturns400() throws Exception {
        mockMvc.perform(post("/orders/1/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","quantity":1,"unitPrice":10.00}
                                """))
                .andExpect(status().isBadRequest());
    }
}
