package com.agriconnect.controller;

import com.agriconnect.config.SecurityConfig;
import com.agriconnect.dto.PaymentResponse;
import com.agriconnect.dto.RazorpayConfigResponse;
import com.agriconnect.security.CustomUserDetailsService;
import com.agriconnect.security.JwtAuthenticationFilter;
import com.agriconnect.service.PaymentService;
import com.agriconnect.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PaymentControllerSecurityTest {

    @Autowired private MockMvc mvc;
    @MockBean private PaymentService paymentService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(authorities = "ROLE_BUYER")
    void buyerCanReadTheCheckoutConfig() throws Exception {
        when(paymentService.getConfig()).thenReturn(RazorpayConfigResponse.builder()
                .enabled(true)
                .keyId("rzp_test_key")
                .currency("INR")
                .build());

        mvc.perform(get("/api/payments/config")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BUYER")
    void buyerCanVerifyAPayment() throws Exception {
        when(paymentService.verifyPayment(any(), anyString()))
                .thenReturn(PaymentResponse.builder().status("SUCCESS").build());

        mvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":1,\"razorpayOrderId\":\"o\",\"razorpayPaymentId\":\"p\",\"razorpaySignature\":\"s\"}"))
                .andExpect(status().isOk());

        verify(paymentService).verifyPayment(any(), anyString());
    }

    /** A farmer has no cart, so the checkout prefix must stay closed to them. */
    @Test
    @WithMockUser(authorities = "ROLE_FARMER")
    void farmerCannotStartACheckout() throws Exception {
        mvc.perform(post("/api/payments/create-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).createCheckout(any(), anyString());
    }

    @Test
    void anonymousCallerCannotUseTheCheckout() throws Exception {
        mvc.perform(post("/api/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        verify(paymentService, never()).verifyPayment(any(), anyString());
    }
}
